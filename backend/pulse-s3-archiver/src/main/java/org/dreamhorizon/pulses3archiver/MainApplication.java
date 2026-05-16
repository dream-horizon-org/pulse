package org.dreamhorizon.pulses3archiver;

import com.google.inject.Module;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.Launcher;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.impl.cpu.CpuCoreSensor;
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulses3archiver.config.ArchiverConfig;
import org.dreamhorizon.pulses3archiver.guice.GuiceInjector;
import org.dreamhorizon.pulses3archiver.module.ConfigModule;
import org.dreamhorizon.pulses3archiver.util.SharedDataUtils;

@Slf4j
public class MainApplication extends Launcher {

  public static void main(String[] args) {
    MainApplication app = new MainApplication();
    app.dispatch(args);
  }

  @Override
  public void beforeStartingVertx(VertxOptions vertxOptions) {
    vertxOptions
      .setEventLoopPoolSize(CpuCoreSensor.availableProcessors())
      .setPreferNativeTransport(true)
      .setFileSystemOptions(new FileSystemOptions().setClassPathResolvingEnabled(true))
      .setWorkerPoolSize(10)
      .setTracingOptions(new OpenTelemetryOptions());
  }

  @Override
  public void afterStartingVertx(Vertx vertx) {
    GuiceInjector.initialize(Arrays.asList(this.getGoogleGuiceModules(vertx)));
    SharedDataUtils.put(vertx, GuiceInjector.class);
  }

  @Override
  public void beforeStoppingVertx(Vertx vertx) {
    ArchiverConfig config = SharedDataUtils.get(vertx, ArchiverConfig.class);
    long shutdownDelay = config != null ? config.getShutdownGracePeriod() : 5L;
    Completable.complete()
      .delay(shutdownDelay, TimeUnit.SECONDS)
      .doOnComplete(() -> log.info("[MainApplication] Application stopped"))
      .blockingSubscribe();
  }

  protected Module[] getGoogleGuiceModules(Vertx vertx) {
    return new Module[]{
      new MainModule(vertx),
      new ConfigModule(vertx)
    };
  }
}
