package org.dreamhorizon.pulseserver.errorgrouping.archive;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.guice.GuiceInjector;

@Slf4j
public class StackTraceParquetFlushVerticle extends AbstractVerticle {

  private long flushTimerId = -1;

  @Override
  public void start(Promise<Void> startPromise) {
    StackTraceArchiveService archiveService =
        GuiceInjector.getGuiceInjector().getInstance(StackTraceArchiveService.class);
    if (!archiveService.isEnabled()) {
      log.info("[StackTraceArchive] Flush verticle skipped (archive disabled)");
      startPromise.complete();
      return;
    }
    StackTraceArchiveConfig config = StackTraceArchiveConfig.fromEnvironment();
    flushTimerId = vertx.setPeriodic(config.getFlushIntervalMs(), id -> archiveService.flushIfDue());
    log.info("[StackTraceArchive] Flush verticle started intervalMs={}", config.getFlushIntervalMs());
    startPromise.complete();
  }

  @Override
  public void stop() {
    if (flushTimerId >= 0) {
      vertx.cancelTimer(flushTimerId);
    }
  }
}
