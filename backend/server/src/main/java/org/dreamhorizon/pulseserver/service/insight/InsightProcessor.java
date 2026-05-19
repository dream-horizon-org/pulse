package org.dreamhorizon.pulseserver.service.insight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.insightdayreport.InsightDayReportCacheDao;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightExecutionMode;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightJobDao;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightJobKey;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightJobStatus;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;
import org.dreamhorizon.pulseserver.dao.insightjob.models.InsightJob;
import org.dreamhorizon.pulseserver.dao.insightreport.InsightReportCacheDao;
import org.dreamhorizon.pulseserver.dao.insightsnapshot.InsightSnapshotDao;
import org.dreamhorizon.pulseserver.dao.insightsnapshot.models.DailySnapshot;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.dreamhorizon.pulseserver.service.ai.impl.AiUpstreamProxyExecutor;

/** Worker-side insight pipeline: data fetch → daily snapshot → AI summarise → MySQL cache. */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InsightProcessor {

  private static final int ERR_MSG_MAX = 4000;
  private static final int DAY_BATCH_SIZE = 7;
  private static final String FIELD_ENTITY_KEY = "entityKey";

  private final Vertx vertx;
  private final InsightJobDao jobDao;
  private final InsightReportCacheDao reportCacheDao;
  private final InsightDayReportCacheDao dayReportCacheDao;
  private final InsightSnapshotResolver snapshotResolver;
  private final InsightDataFetcherResolver dataFetcherResolver;
  private final InsightAgentResolver agentResolver;
  private final ObjectMapper objectMapper;

  public void enqueueProcess(
      final InsightJob job,
      final boolean regenerate,
      final String authorization,
      final String rawQuery) {
    vertx.executeBlocking(
        () -> {
          runPipeline(job, regenerate, authorization, rawQuery).blockingAwait();
          return null;
        },
        false,
        ar -> {
          if (ar.failed()) {
            log.error("Insight job worker failed for {}", job.jobId(), ar.cause());
            markJobFailed(job, truncate(ar.cause().getMessage()))
                .subscribe(() -> {}, e -> log.warn("markFailed fallback error: {}", e.getMessage()));
          }
        });
  }

  private Completable runPipeline(
      final InsightJob job,
      final boolean regenerate,
      final String authorization,
      final String rawQuery) {
    log.info("Insight job {} starting pipeline mode={}", job.jobId(), job.executionMode());
    if (job.executionMode() == InsightExecutionMode.DATE_RANGE) {
      return runDateRangePipeline(job, regenerate, authorization, rawQuery);
    }
    return runRealtimePipeline(job, authorization, rawQuery);
  }

  // ---------------------------------------------------------------------------
  // DATE_RANGE pipeline
  // ---------------------------------------------------------------------------

  /**
   * Optimised date-range pipeline with two-level caching:
   *
   * <ol>
   *   <li>MySQL {@code insight_day_report} — per-day AI summaries. A hit skips both the
   *       ClickHouse data-fetch and the AI day call entirely.
   *   <li>ClickHouse {@code insight_daily_snapshots} — raw metric snapshots. A hit skips the 3-query
   *       ClickHouse fetch but still needs an AI day call.
   * </ol>
   *
   * Only dates that are missing from the MySQL day cache go through the full pipeline.
   * After the AI day calls, new summaries are persisted to MySQL before the merge step.
   */
  private Completable runDateRangePipeline(
      final InsightJob job,
      final boolean regenerate,
      final String authorization,
      final String rawQuery) {
    InsightSnapshotDao snapshotDao = snapshotResolver.resolve(job.insightType());
    InsightDataFetcher dataFetcher = dataFetcherResolver.resolve(job.insightType());
    List<LocalDate> allDates = allDates(job);

    // When regenerate=true skip both caches and recompute everything.
    Single<Map<LocalDate, String>> cachedDaysSingle = regenerate
        ? Single.just(Map.of())
        : dayReportCacheDao.getForDates(
            job.projectId(), job.insightType(), job.entityKey(), allDates);

    return jobDao.updateStatus(job.jobId(), InsightJobStatus.PROCESSING)
        .andThen(cachedDaysSingle)
        .flatMapCompletable(cachedDayReports -> {
          List<LocalDate> uncachedDates = allDates.stream()
              .filter(d -> !cachedDayReports.containsKey(d))
              .toList();

          if (uncachedDates.isEmpty()) {
            // All day summaries already in MySQL — go straight to merge.
            log.debug("Insight job {} all {} days cached, skipping to merge",
                job.jobId(), allDates.size());
            AiUpstreamProxyExecutor upstream = agentResolver.resolve(job.insightType());
            return callMerge(job, buildOrderedDayInsights(allDates, cachedDayReports, Map.of()),
                upstream, authorization, rawQuery);
          }

          // For uncached dates: resolve which ClickHouse snapshots are also missing.
          return snapshotDao.getExistingDates(
                  job.projectId(), job.insightType(), job.entityKey(), uncachedDates)
              .flatMapCompletable(existingSnapshotDates -> {
                List<LocalDate> missingSnapshotDates = uncachedDates.stream()
                    .filter(d -> !existingSnapshotDates.contains(d))
                    .toList();

                // Fetch and store missing ClickHouse snapshots (sequential per date).
                Completable fetchMissing = Observable.fromIterable(missingSnapshotDates)
                    .concatMapCompletable(date ->
                        dataFetcher.fetchForDate(job.projectId(), job.entityKey(), date)
                            .flatMapCompletable(data ->
                                snapshotDao.upsert(
                                    job.projectId(),
                                    job.insightType(),
                                    job.entityKey(),
                                    date,
                                    serializeNode(data))));

                return fetchMissing
                    // Load ClickHouse snapshots for all uncached-AI dates.
                    .andThen(snapshotDao.getSnapshotsForDates(
                        job.projectId(), job.insightType(), job.entityKey(), uncachedDates))
                    .flatMapCompletable(uncachedSnapshots -> {
                      AiUpstreamProxyExecutor upstream = agentResolver.resolve(job.insightType());
                      return callDayInsightsAndMerge(
                          job, uncachedSnapshots, cachedDayReports, upstream,
                          authorization, rawQuery);
                    });
              });
        })
        .onErrorResumeNext(error -> {
          log.error("Insight DATE_RANGE job {} failed", job.jobId(), error);
          String msg = error.getMessage();
          boolean alreadyMarked = msg != null && msg.contains("AI upstream error:");
          if (alreadyMarked) {
            return Completable.complete();
          }
          return markJobFailed(job, truncate(msg));
        });
  }

  /**
   * Calls the AI day endpoint for each uncached snapshot (batched, 7 concurrent per batch),
   * stores results in MySQL, then sends all day insights (cached + new) to the merge endpoint.
   */
  private Completable callDayInsightsAndMerge(
      final InsightJob job,
      final List<DailySnapshot> uncachedSnapshots,
      final Map<LocalDate, String> cachedDayReports,
      final AiUpstreamProxyExecutor upstream,
      final String authorization,
      final String rawQuery) {
    if (uncachedSnapshots.isEmpty() && cachedDayReports.isEmpty()) {
      return markJobFailed(job, "No snapshot data available for date range");
    }

    List<List<DailySnapshot>> batches = partition(uncachedSnapshots, DAY_BATCH_SIZE);

    // Call AI day for all uncached snapshots; collect date → body map.
    Single<Map<LocalDate, String>> newDayInsightsSingle = Observable.fromIterable(batches)
        .concatMapSingle(batch -> {
          List<Single<Map.Entry<LocalDate, String>>> batchCalls = batch.stream()
              .<Single<Map.Entry<LocalDate, String>>>map(snap ->
                  callDayInsight(job, snap, upstream, authorization, rawQuery)
                      .map(body -> Map.entry(snap.snapshotDate(), body)))
              .toList();
          return zipToList(batchCalls);
        })
        .toList()
        .map(allBatches -> {
          Map<LocalDate, String> result = new HashMap<>();
          for (List<Map.Entry<LocalDate, String>> batch : allBatches) {
            for (Map.Entry<LocalDate, String> entry : batch) {
              result.put(entry.getKey(), entry.getValue());
            }
          }
          return result;
        });

    return newDayInsightsSingle
        .flatMapCompletable(newDayInsights -> {
          // Persist new day AI summaries to MySQL (parallel, fire-and-forget per day).
          Completable storeNew = dayReportCacheDao.putAll(
              job.projectId(), job.insightType(), job.entityKey(), newDayInsights)
              .onErrorComplete(); // never fail the job on cache write errors

          // Build chronologically ordered list for the merge endpoint.
          List<String> orderedDayInsights =
              buildOrderedDayInsights(allDates(job), cachedDayReports, newDayInsights);

          return storeNew
              .andThen(callMerge(job, orderedDayInsights, upstream, authorization, rawQuery));
        });
  }

  /** Calls the AI day endpoint for a single snapshot. Returns "{}" on failure (non-fatal). */
  private Single<String> callDayInsight(
      final InsightJob job,
      final DailySnapshot snapshot,
      final AiUpstreamProxyExecutor upstream,
      final String authorization,
      final String rawQuery) {
    String dayBody = buildDayBody(job, snapshot);
    String dayUrl = upstream.buildTargetUrl(insightPath(job.insightType(), "day"), rawQuery);
    return Single.fromCompletionStage(
        upstream.executeProxy("POST", dayUrl, dayBody, authorization, job.projectId()))
        .map(result -> {
          if (AiProxyUpstreamResult.isSuccessfulBuffered(result)) {
            return result.getBufferedBody();
          }
          log.warn("Insight day call failed for date={} status={}",
              snapshot.snapshotDate(), result.getStatusCode());
          return "{}";
        });
  }

  /** Calls the AI merge endpoint and stores the final report on success. */
  private Completable callMerge(
      final InsightJob job,
      final List<String> orderedDayInsights,
      final AiUpstreamProxyExecutor upstream,
      final String authorization,
      final String rawQuery) {
    String mergeBody = buildMergeBody(job, orderedDayInsights);
    String mergeUrl = upstream.buildTargetUrl(insightPath(job.insightType(), "merge"), rawQuery);
    return Single.fromCompletionStage(
        upstream.executeProxy("POST", mergeUrl, mergeBody, authorization, job.projectId()))
        .flatMapCompletable(mergeResult -> {
          if (!AiProxyUpstreamResult.isSuccessfulBuffered(mergeResult)) {
            String errMsg = extractUpstreamError(
                mergeResult.getStatusCode(), mergeResult.getBufferedBody());
            return markJobFailed(job, truncate(errMsg))
                .andThen(Completable.error(
                    new RuntimeException("AI upstream error: " + errMsg)));
          }
          return reportCacheDao.put(
              job.projectId(), job.insightType(), job.entityKey(),
              job.executionMode(), job.startDate(), job.endDate(),
              mergeResult.getBufferedBody())
              .andThen(markJobCompleted(job));
        });
  }

  // ---------------------------------------------------------------------------
  // REALTIME pipeline (unchanged)
  // ---------------------------------------------------------------------------

  private Completable runRealtimePipeline(
      final InsightJob job,
      final String authorization,
      final String rawQuery) {
    InsightDataFetcher dataFetcher = dataFetcherResolver.resolve(job.insightType());

    return jobDao.updateStatus(job.jobId(), InsightJobStatus.PROCESSING)
        .andThen(dataFetcher.fetchLive(job.projectId(), job.entityKey()))
        .flatMapCompletable(liveData -> {
          AiUpstreamProxyExecutor upstream = agentResolver.resolve(job.insightType());
          String liveBody = buildLiveBody(job, liveData);
          String liveUrl = upstream.buildTargetUrl(
              insightPath(job.insightType(), "live"), rawQuery);
          return Single.fromCompletionStage(
              upstream.executeProxy("POST", liveUrl, liveBody, authorization, job.projectId()))
              .flatMapCompletable(result -> {
                if (!AiProxyUpstreamResult.isSuccessfulBuffered(result)) {
                  String errMsg = extractUpstreamError(
                      result.getStatusCode(), result.getBufferedBody());
                  return markJobFailed(job, truncate(errMsg))
                      .andThen(Completable.error(
                          new RuntimeException("AI upstream error: " + errMsg)));
                }
                return reportCacheDao.put(
                    job.projectId(), job.insightType(), job.entityKey(),
                    job.executionMode(), null, null,
                    result.getBufferedBody())
                    .andThen(markJobCompleted(job));
              });
        })
        .onErrorResumeNext(error -> {
          log.error("Insight REALTIME job {} failed", job.jobId(), error);
          String msg = error.getMessage();
          boolean alreadyMarked = msg != null && msg.contains("AI upstream error:");
          if (alreadyMarked) {
            return Completable.complete();
          }
          return markJobFailed(job, truncate(msg));
        });
  }

  // ---------------------------------------------------------------------------
  // Job state helpers
  // ---------------------------------------------------------------------------

  private Completable markJobCompleted(final InsightJob job) {
    return jobDao.markCompleted(
        job.jobId(),
        new InsightJobKey(
            job.projectId(), job.insightType(), job.entityKey(),
            job.executionMode(), job.startDate(), job.endDate()));
  }

  private Completable markJobFailed(final InsightJob job, final String errorMessage) {
    return jobDao.markFailed(
        job.jobId(),
        new InsightJobKey(
            job.projectId(), job.insightType(), job.entityKey(),
            job.executionMode(), job.startDate(), job.endDate()),
        errorMessage);
  }

  // ---------------------------------------------------------------------------
  // Request body builders
  // ---------------------------------------------------------------------------

  private String buildDayBody(final InsightJob job, final DailySnapshot snapshot) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put(FIELD_ENTITY_KEY, job.entityKey());
      body.put("date", snapshot.snapshotDate().toString());
      body.set("data", objectMapper.readTree(snapshot.computedData()));
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      log.warn("Failed to build day insight body: {}", e.getMessage());
      return "{}";
    }
  }

  private String buildMergeBody(final InsightJob job, final List<String> dayInsights) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put(FIELD_ENTITY_KEY, job.entityKey());
      body.put("startDate", job.startDate().toString());
      body.put("endDate", job.endDate().toString());
      ArrayNode dayArray = body.putArray("dayInsights");
      for (String di : dayInsights) {
        dayArray.add(objectMapper.readTree(di));
      }
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      log.warn("Failed to build merge body: {}", e.getMessage());
      return "{}";
    }
  }

  private String buildLiveBody(final InsightJob job, final JsonNode liveData) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put(FIELD_ENTITY_KEY, job.entityKey());
      body.set("liveData", liveData);
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      log.warn("Failed to build live insight body: {}", e.getMessage());
      return "{}";
    }
  }

  // ---------------------------------------------------------------------------
  // Utilities
  // ---------------------------------------------------------------------------

  /**
   * Builds a chronologically ordered list of day-insight JSON strings for the merge call.
   * Cached days take priority over new days; missing dates fall back to "{}".
   */
  private static List<String> buildOrderedDayInsights(
      final List<LocalDate> allDates,
      final Map<LocalDate, String> cachedDayReports,
      final Map<LocalDate, String> newDayInsights) {
    List<String> ordered = new ArrayList<>(allDates.size());
    for (LocalDate date : allDates) {
      String cached = cachedDayReports.get(date);
      if (cached != null) {
        ordered.add(cached);
        continue;
      }
      String fresh = newDayInsights.get(date);
      ordered.add(fresh != null ? fresh : "{}");
    }
    return ordered;
  }

  private String extractUpstreamError(final int statusCode, final String responseBody) {
    if (responseBody != null && !responseBody.isBlank()) {
      try {
        JsonNode node = objectMapper.readTree(responseBody);
        for (String field : new String[]{"error", "message", "detail"}) {
          JsonNode candidate = node.get(field);
          if (candidate != null && candidate.isTextual()) {
            String text = candidate.asText().trim();
            if (!text.isEmpty()) {
              return text;
            }
          }
        }
      } catch (Exception ignored) {
        // non-JSON body — fall through
      }
    }
    return "AI service returned an error (HTTP " + statusCode + "). Please try again.";
  }

  private String serializeNode(final JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (Exception e) {
      return "{}";
    }
  }

  /** Derives the AI agent path segment, e.g. "insight/anr/day". */
  private static String insightPath(final InsightType type, final String segment) {
    return "insight/" + type.name().toLowerCase() + "/" + segment;
  }

  private static String truncate(final String message) {
    if (message == null) {
      return "Unknown error";
    }
    return message.length() <= ERR_MSG_MAX ? message : message.substring(0, ERR_MSG_MAX);
  }

  private static List<LocalDate> allDates(final InsightJob job) {
    return InsightDateRangeResolver.enumerateDates(job.startDate(), job.endDate());
  }

  private static <T> List<List<T>> partition(final List<T> list, final int size) {
    List<List<T>> partitions = new ArrayList<>();
    for (int i = 0; i < list.size(); i += size) {
      partitions.add(list.subList(i, Math.min(i + size, list.size())));
    }
    return partitions;
  }

  @SuppressWarnings("unchecked")
  private static <T> Single<List<T>> zipToList(final List<Single<T>> singles) {
    if (singles.isEmpty()) {
      return Single.just(List.of());
    }
    if (singles.size() == 1) {
      return singles.get(0).map(List::of);
    }
    return Single.zip(
        singles,
        results -> {
          List<T> out = new ArrayList<>();
          for (Object r : results) {
            out.add((T) r);
          }
          return out;
        });
  }

  @SuppressWarnings("unused")
  private static String formatInstant(final Instant instant) {
    return DateTimeFormatter.ISO_INSTANT.format(instant);
  }
}
