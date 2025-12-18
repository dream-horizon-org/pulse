package org.dreamhorizon.pulsealertscron;

import com.google.inject.Module;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.Launcher;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.impl.cpu.CpuCoreSensor;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.guice.GuiceInjector;
import org.dreamhorizon.pulsealertscron.module.ConfigModule;
import org.dreamhorizon.pulsealertscron.util.MaintenanceUtil;
import org.dreamhorizon.pulsealertscron.util.SharedDataUtils;

@Slf4j
public class MainApplication extends Launcher {

  public static void main(String[] args) {
    MainApplication app = new MainApplication();
    app.dispatch(args);
  }

  @Override
  public void beforeStartingVertx(VertxOptions vertxOptions) {
    vertxOptions
        .setEventLoopPoolSize(this.getNumOfCores())
        .setPreferNativeTransport(true)
        .setFileSystemOptions(new FileSystemOptions().setClassPathResolvingEnabled(true))
        .setWorkerPoolSize(10);
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
    long shutdownDelayInterval = config != null ? config.getShutdownGracePeriod() : 5;
    Completable.complete()
        .doOnComplete(() -> MaintenanceUtil.setShutdownStatus(vertx))
        .delay(shutdownDelayInterval, TimeUnit.SECONDS)
        .doOnComplete(() -> log.info("Successfully stopped application"))
        .blockingSubscribe();
  }

  protected Module[] getGoogleGuiceModules(Vertx vertx) {
    return new Module[] {
        new MainModule(vertx),
        new ConfigModule(vertx)
    };
  }
}

