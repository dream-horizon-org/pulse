package org.dreamhorizon.pulseserver.service.rca;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaReportJobDao;

/**
 * Periodically marks PENDING/PROCESSING RCA jobs that exceed the stale threshold as FAILED.
 * Prevents jobs abandoned after a worker crash from staying stuck indefinitely.
 * Registered as an eager singleton so the timer starts at application startup.
 */
@Slf4j
@Singleton
public class RcaStaleJobCleanup {

  static final long CLEANUP_INTERVAL_MS = 5 * 60_000L; // 5 minutes
  static final int STALE_THRESHOLD_MINUTES = 120; // 2 hours

  @Inject
  public RcaStaleJobCleanup(final Vertx vertx, final RcaReportJobDao jobDao) {
    vertx.setPeriodic(
        CLEANUP_INTERVAL_MS,
        id ->
            jobDao
                .markStaleJobsFailed(STALE_THRESHOLD_MINUTES)
                .subscribe(
                    count -> {
                      if (count > 0) {
                        log.info("RCA stale job cleanup: marked {} abandoned job(s) as FAILED", count);
                      }
                    },
                    e -> log.warn("RCA stale job cleanup error: {}", e.getMessage())));
  }
}
