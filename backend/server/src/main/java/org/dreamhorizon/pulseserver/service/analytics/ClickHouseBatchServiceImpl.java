package org.dreamhorizon.pulseserver.service.analytics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.AnalyticsEngineConfig;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.FunnelDefinitionDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.JourneyDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.models.JourneyRow;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobDao;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobStatus;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobType;

/**
 * ClickHouse-backed implementation of {@link AnalyticsBatchService}.
 *
 * <p>On-save path ({@code triggerFunnelOnSaveJob}/{@code triggerJourneyOnSaveJob}): inserts
 * {@code analytics_jobs} as {@code RUNNING}, schedules ClickHouse compute on
 * {@link io.reactivex.rxjava3.schedulers.Schedulers#io()}, and returns {@code true} immediately.
 * When compute finishes, status is updated to {@code SUCCEEDED} or {@code FAILED} in the background.
 *
 * <p>Batch cron path: one {@code analytics_jobs} row per daily run ({@code FUNNELS_DAILY} /
 * {@code JOURNEYS_DAILY}). Compute may run one query per project, or batch multiple projects in a
 * single query when configured; that grouping is not stored in MySQL.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ClickHouseBatchServiceImpl implements AnalyticsBatchService {

  private final AnalyticsJobDao analyticsJobDao;
  private final ClickHouseComputeService computeService;
  private final FunnelDefinitionDao funnelDefinitionDao;
  private final JourneyDao journeyDao;
  private final AnalyticsEngineConfig analyticsEngineConfig;

  // ── On-save path ─────────────────────────────────────────────────────────────

  @Override
  public Single<Boolean> triggerFunnelOnSaveJob(Long funnelId) {
    return analyticsJobDao.insertJob(AnalyticsJobType.FUNNEL, funnelId, null, AnalyticsJobStatus.RUNNING)
        .flatMap(dbId -> {
          runFunnelComputeAsync(dbId, funnelId);
          return Single.just(true);
        });
  }

  @Override
  public Single<Boolean> triggerJourneyOnSaveJob(Long journeyId) {
    return analyticsJobDao.insertJob(AnalyticsJobType.JOURNEY, journeyId, null, AnalyticsJobStatus.RUNNING)
        .flatMap(dbId -> {
          runJourneyComputeAsync(dbId, journeyId);
          return Single.just(true);
        });
  }

  private void runFunnelComputeAsync(long dbId, long funnelId) {
    computeService.computeFunnel(funnelId)
        .flatMap(ignored ->
            analyticsJobDao.updateJobStatus(
                    dbId, AnalyticsJobStatus.SUCCEEDED, null,
                    LocalDateTime.now(), LocalDateTime.now())
                .map(rowCount -> true))
        .onErrorResumeNext(err -> {
          log.error("ClickHouse funnel compute failed for funnelId={}", funnelId, err);
          return analyticsJobDao.updateJobStatus(
                  dbId, AnalyticsJobStatus.FAILED, err.getMessage(),
                  null, LocalDateTime.now())
              .map(rowCount -> false);
        })
        .subscribeOn(Schedulers.io())
        .subscribe(
            unused -> { },
            e -> log.error(
                "Failed to finalize analytics_jobs row id={} after funnel on-save compute (funnelId={})",
                dbId, funnelId, e));
  }

  private void runJourneyComputeAsync(long dbId, long journeyId) {
    computeService.computeJourney(journeyId)
        .flatMap(ignored ->
            analyticsJobDao.updateJobStatus(
                    dbId, AnalyticsJobStatus.SUCCEEDED, null,
                    LocalDateTime.now(), LocalDateTime.now())
                .map(rowCount -> true))
        .onErrorResumeNext(err -> {
          log.error("ClickHouse journey compute failed for journeyId={}", journeyId, err);
          return analyticsJobDao.updateJobStatus(
                  dbId, AnalyticsJobStatus.FAILED, err.getMessage(),
                  null, LocalDateTime.now())
              .map(rowCount -> false);
        })
        .subscribeOn(Schedulers.io())
        .subscribe(
            unused -> { },
            e -> log.error(
                "Failed to finalize analytics_jobs row id={} after journey on-save compute (journeyId={})",
                dbId, journeyId, e));
  }

  // ── Batch path ────────────────────────────────────────────────────────────────

  @Override
  public Single<Boolean> triggerFunnelsBatch() {
    return funnelDefinitionDao.listAllAuto()
        .flatMap(all -> {
          if (all.isEmpty()) {
            return Single.just(true);
          }
          return shouldSkipDailyBatch(AnalyticsJobType.FUNNELS_DAILY)
              .flatMap(skip -> {
                if (skip) {
                  return Single.just(false);
                }
                LocalDateTime startedAt = LocalDateTime.now();
                return analyticsJobDao.insertJob(
                        AnalyticsJobType.FUNNELS_DAILY, null, null, AnalyticsJobStatus.RUNNING)
                    .flatMap(dbId -> {
                      runFunnelsBatchAsync(dbId, all, startedAt);
                      return Single.just(true);
                    });
              });
        });
  }

  @Override
  public Single<Boolean> triggerJourneysBatch() {
    return journeyDao.listAllAuto()
        .flatMap(all -> {
          if (all.isEmpty()) {
            log.info("Journeys batch: no definitions with journey_type=AUTO; nothing to do");
            return Single.just(true);
          }
          return shouldSkipDailyBatch(AnalyticsJobType.JOURNEYS_DAILY)
              .flatMap(skip -> {
                if (skip) {
                  return Single.just(false);
                }
                LocalDateTime startedAt = LocalDateTime.now();
                return analyticsJobDao.insertJob(
                        AnalyticsJobType.JOURNEYS_DAILY, null, null, AnalyticsJobStatus.RUNNING)
                    .flatMap(dbId -> {
                      runJourneysBatchAsync(dbId, all, startedAt);
                      return Single.just(true);
                    });
              });
        });
  }

  private Single<Boolean> shouldSkipDailyBatch(AnalyticsJobType jobType) {
    return analyticsJobDao.getLatestJobByType(jobType)
        .map(
            latest -> {
              LocalDate today = LocalDate.now(ZoneOffset.UTC);
              LocalDate latestDate = latest.getCreatedAt().toLocalDate();
              if (latestDate.isEqual(today)) {
                log.info(
                    "Batch job {} already ran today (job id={}). Skipping.",
                    jobType,
                    latest.getId());
                return true;
              }
              return false;
            })
        .switchIfEmpty(Maybe.just(false))
        .toSingle();
  }

  private void runFunnelsBatchAsync(
      long dbId, List<FunnelDefinitionRow> all, LocalDateTime startedAt) {
    int concurrency = Math.max(1, analyticsEngineConfig.getBatchProjectConcurrency());
    Map<String, List<FunnelDefinitionRow>> byProject =
        all.stream().collect(Collectors.groupingBy(FunnelDefinitionRow::getProjectId));
    Observable.fromIterable(byProject.entrySet())
        .flatMap(
            e ->
                computeService
                    .computeFunnelBatch(e.getKey(), e.getValue())
                    .doOnError(
                        err ->
                            log.error(
                                "Batch funnel compute failed for project={}", e.getKey(), err))
                    .onErrorReturnItem(false)
                    .toObservable(),
            concurrency)
        .toList()
        .flatMap(
            results -> {
              boolean allOk = results.stream().allMatch(Boolean::booleanValue);
              return analyticsJobDao.updateJobStatus(
                  dbId,
                  allOk ? AnalyticsJobStatus.SUCCEEDED : AnalyticsJobStatus.FAILED,
                  allOk ? null : "One or more project batches failed",
                  startedAt,
                  LocalDateTime.now());
            })
        .flatMap(
            rowCount -> {
              // Touch updated_at for all funnels so listing shows latest auto-run
              return Observable.fromIterable(all)
                  .flatMap(
                      funnel ->
                          funnelDefinitionDao
                              .touchUpdatedAt(funnel.getId())
                              .doOnError(
                                  err ->
                                      log.warn(
                                          "Failed to touch updated_at for funnel id={}",
                                          funnel.getId(),
                                          err))
                              .onErrorReturnItem(0)
                              .toObservable())
                  .toList()
                  .map(ignored -> rowCount);
            })
        .subscribeOn(Schedulers.io())
        .subscribe(
            unused -> { },
            err -> log.error("Failed to finalize FUNNELS_DAILY job id={}", dbId, err));
  }

  private void runJourneysBatchAsync(long dbId, List<JourneyRow> all, LocalDateTime startedAt) {
    int concurrency = Math.max(1, analyticsEngineConfig.getBatchProjectConcurrency());
    Map<String, List<JourneyRow>> byProject =
        all.stream().collect(Collectors.groupingBy(JourneyRow::getProjectId));
    log.info(
        "JOURNEYS_DAILY job id={}: running ClickHouse batch for {} journey(s) across {} project(s) "
            + "(async on io scheduler)",
        dbId,
        all.size(),
        byProject.size());
    Observable.fromIterable(byProject.entrySet())
        .flatMap(
            e ->
                computeService
                    .computeJourneyBatch(e.getKey(), e.getValue())
                    .doOnError(
                        err ->
                            log.error(
                                "Batch journey compute failed for project={}", e.getKey(), err))
                    .onErrorReturnItem(false)
                    .toObservable(),
            concurrency)
        .toList()
        .flatMap(
            results -> {
              boolean allOk = results.stream().allMatch(Boolean::booleanValue);
              return analyticsJobDao.updateJobStatus(
                  dbId,
                  allOk ? AnalyticsJobStatus.SUCCEEDED : AnalyticsJobStatus.FAILED,
                  allOk ? null : "One or more project batches failed",
                  startedAt,
                  LocalDateTime.now());
            })
        .flatMap(
            rowCount -> {
              // Touch updated_at for all journeys so listing shows latest auto-run
              return Observable.fromIterable(all)
                  .flatMap(
                      journey ->
                          journeyDao
                              .touchUpdatedAt(journey.getId())
                              .doOnError(
                                  err ->
                                      log.warn(
                                          "Failed to touch updated_at for journey id={}",
                                          journey.getId(),
                                          err))
                              .onErrorReturnItem(0)
                              .toObservable())
                  .toList()
                  .map(ignored -> rowCount);
            })
        .subscribeOn(Schedulers.io())
        .subscribe(
            unused -> { },
            err -> log.error("Failed to finalize JOURNEYS_DAILY job id={}", dbId, err));
  }

  @Override
  public Single<Boolean> triggerEventsBatch() {
    // No-op — custom events are already in ClickHouse via otel.otel_logs
    return Single.just(true);
  }
}
