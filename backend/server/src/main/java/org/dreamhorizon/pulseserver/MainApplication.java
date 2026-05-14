package org.dreamhorizon.pulseserver;

import com.google.inject.Module;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.Launcher;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.impl.cpu.CpuCoreSensor;
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.guice.GuiceInjector;
import org.dreamhorizon.pulseserver.module.*;
import org.dreamhorizon.pulseserver.util.MaintenanceUtil;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MainApplication extends Launcher {

  public static void main(String[] args) {
    MainApplication app = new MainApplication();
    app.dispatch(args);
  }

  @Override
  public void beforeStartingVertx(VertxOptions vertxOptions) {
    vertxOptions
      .setEventLoopPoolSize(2)
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

  private Integer getNumOfCores() {
    return CpuCoreSensor.availableProcessors();
  }

  @Override
  public void beforeStoppingVertx(Vertx vertx) {
    ApplicationConfig config = SharedDataUtils.get(vertx, ApplicationConfig.class);
    long shutdownDelayInterval = config.shutdownGracePeriod;
    Completable.complete()
      .doOnComplete(() -> MaintenanceUtil.setShutdownStatus(vertx))
      .delay(shutdownDelayInterval, TimeUnit.SECONDS)
      .doOnComplete(() -> log.info("Successfully stopped application"))
      .blockingSubscribe();
  }

  protected Module[] getGoogleGuiceModules(Vertx vertx) {
    return new Module[]{
      new MainModule(vertx),
      new RcaModule(),
      new ConfigModule(vertx),
      new ValidationModule(),
      new UploadInteractionDetailModule(vertx),
      new InteractionModule(),
      new HeatmapModule(),
      new QueryEngineModule(),
      new EventDefinitionModule(),
      new WebVitalsModule()
    };
  }
}
