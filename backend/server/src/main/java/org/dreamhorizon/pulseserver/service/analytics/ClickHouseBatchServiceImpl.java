package org.dreamhorizon.pulseserver.service.analytics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.LocalDateTime;
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
import org.dreamhorizon.pulseserver.dao.spark.SparkJobDao;
import org.dreamhorizon.pulseserver.dao.spark.SparkJobStatus;
import org.dreamhorizon.pulseserver.dao.spark.SparkJobType;

/**
 * ClickHouse-backed implementation of {@link AnalyticsBatchService}.
 *
 * <p>On-save path ({@code triggerFunnelOnSaveJob}/{@code triggerJourneyOnSaveJob}): inserts
 * {@code spark_jobs} as {@code RUNNING}, schedules ClickHouse compute on
 * {@link io.reactivex.rxjava3.schedulers.Schedulers#io()}, and returns {@code true} immediately.
 * When compute finishes, status is updated to {@code SUCCEEDED} or {@code FAILED} in the background.
 *
 * <p>Batch cron path ({@code triggerFunnelsBatch}/{@code triggerJourneysBatch}): groups AUTO
 * definitions by project and issues one query per project using
 * {@link ClickHouseComputeService}. No {@code spark_jobs} tracking on the batch path.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ClickHouseBatchServiceImpl implements AnalyticsBatchService {

  private final SparkJobDao sparkJobDao;
  private final ClickHouseComputeService computeService;
  private final FunnelDefinitionDao funnelDefinitionDao;
  private final JourneyDao journeyDao;
  private final AnalyticsEngineConfig analyticsEngineConfig;

  // ── On-save path ─────────────────────────────────────────────────────────────

  @Override
  public Single<Boolean> triggerFunnelOnSaveJob(Long funnelId) {
    return sparkJobDao.insertJob(SparkJobType.FUNNEL, funnelId, null, SparkJobStatus.RUNNING)
        .flatMap(dbId -> {
          runFunnelComputeAsync(dbId, funnelId);
          return Single.just(true);
        });
  }

  @Override
  public Single<Boolean> triggerJourneyOnSaveJob(Long journeyId) {
    return sparkJobDao.insertJob(SparkJobType.JOURNEY, journeyId, null, SparkJobStatus.RUNNING)
        .flatMap(dbId -> {
          runJourneyComputeAsync(dbId, journeyId);
          return Single.just(true);
        });
  }

  private void runFunnelComputeAsync(long dbId, long funnelId) {
    computeService.computeFunnel(funnelId)
        .flatMap(__ ->
            sparkJobDao.updateJobStatus(
                    dbId, SparkJobStatus.SUCCEEDED, null,
                    LocalDateTime.now(), LocalDateTime.now())
                .map(__ -> true))
        .onErrorResumeNext(err -> {
          log.error("ClickHouse funnel compute failed for funnelId={}", funnelId, err);
          return sparkJobDao.updateJobStatus(
                  dbId, SparkJobStatus.FAILED, err.getMessage(),
                  null, LocalDateTime.now())
              .map(__ -> false);
        })
        .subscribeOn(Schedulers.io())
        .subscribe(
            __ -> { },
            e -> log.error(
                "Failed to finalize spark_jobs row id={} after funnel on-save compute (funnelId={})",
                dbId, funnelId, e));
  }

  private void runJourneyComputeAsync(long dbId, long journeyId) {
    computeService.computeJourney(journeyId)
        .flatMap(__ ->
            sparkJobDao.updateJobStatus(
                    dbId, SparkJobStatus.SUCCEEDED, null,
                    LocalDateTime.now(), LocalDateTime.now())
                .map(__ -> true))
        .onErrorResumeNext(err -> {
          log.error("ClickHouse journey compute failed for journeyId={}", journeyId, err);
          return sparkJobDao.updateJobStatus(
                  dbId, SparkJobStatus.FAILED, err.getMessage(),
                  null, LocalDateTime.now())
              .map(__ -> false);
        })
        .subscribeOn(Schedulers.io())
        .subscribe(
            __ -> { },
            e -> log.error(
                "Failed to finalize spark_jobs row id={} after journey on-save compute (journeyId={})",
                dbId, journeyId, e));
  }

  // ── Batch path ────────────────────────────────────────────────────────────────

  @Override
  public Single<Boolean> triggerFunnelsBatch() {
    int concurrency = Math.max(1, analyticsEngineConfig.getBatchProjectConcurrency());
    return funnelDefinitionDao.listAllAuto()
        .flatMapObservable(all -> {
          Map<String, List<FunnelDefinitionRow>> byProject = all.stream()
              .collect(Collectors.groupingBy(FunnelDefinitionRow::getProjectId));
          return Observable.fromIterable(byProject.entrySet());
        })
        .flatMap(
            e -> computeService.computeFunnelBatch(e.getKey(), e.getValue())
                .doOnError(err -> log.error(
                    "Batch funnel compute failed for project={}", e.getKey(), err))
                .onErrorReturnItem(false)
                .toObservable(),
            concurrency)
        .all(b -> b);
  }

  @Override
  public Single<Boolean> triggerJourneysBatch() {
    int concurrency = Math.max(1, analyticsEngineConfig.getBatchProjectConcurrency());
    return journeyDao.listAllAuto()
        .flatMapObservable(all -> {
          Map<String, List<JourneyRow>> byProject = all.stream()
              .collect(Collectors.groupingBy(JourneyRow::getProjectId));
          return Observable.fromIterable(byProject.entrySet());
        })
        .flatMap(
            e -> computeService.computeJourneyBatch(e.getKey(), e.getValue())
                .doOnError(err -> log.error(
                    "Batch journey compute failed for project={}", e.getKey(), err))
                .onErrorReturnItem(false)
                .toObservable(),
            concurrency)
        .all(b -> b);
  }

  @Override
  public Single<Boolean> triggerEventsBatch() {
    // No-op — custom events are already in ClickHouse via otel.otel_logs
    return Single.just(true);
  }
}
