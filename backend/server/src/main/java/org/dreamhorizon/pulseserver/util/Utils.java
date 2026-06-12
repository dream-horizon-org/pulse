package org.dreamhorizon.pulseserver.util;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.rxjava3.core.Vertx;
import java.util.concurrent.Executor;
import org.dreamhorizon.pulseserver.guice.GuiceInjector;

public class Utils {

  public static Scheduler fromVertxEventLoop() {
    Vertx vertx = GuiceInjector.getGuiceInjector().getInstance(Vertx.class);
    Executor executor = command -> vertx.runOnContext(v -> command.run());
    return Schedulers.from(executor);
  }

}
