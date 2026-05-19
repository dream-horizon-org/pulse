package org.dreamhorizon.pulseserver.service.insight;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightJobDao;

@Slf4j
@Singleton
public class InsightStaleJobCleanup {

  static final long CLEANUP_INTERVAL_MS = 5 * 60_000L;
  static final int STALE_THRESHOLD_MINUTES = 120;

  @Inject
  public InsightStaleJobCleanup(final Vertx vertx, final InsightJobDao jobDao) {
    vertx.setPeriodic(
        CLEANUP_INTERVAL_MS,
        id ->
            jobDao
                .markStaleJobsFailed(STALE_THRESHOLD_MINUTES)
                .subscribe(
                    count -> {
                      if (count > 0) {
                        log.info(
                            "Insight stale job cleanup: marked {} abandoned job(s) as FAILED",
                            count);
                      }
                    },
                    e -> log.warn("Insight stale job cleanup error: {}", e.getMessage())));
  }
}
