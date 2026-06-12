package org.dreamhorizon.pulseserver.util;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.util.concurrent.CompletableFuture;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public final class CompletableFutureUtils {

  public static <T> CompletableFuture<T> fromSingle(io.reactivex.rxjava3.core.Single<T> single) {
    Context context = Vertx.currentContext();
    CompletableFuture<T> future =
        context != null ? new VertxCompletableFuture<>(context) : new CompletableFuture<>();
    single.subscribe(future::complete, future::completeExceptionally);
    return future;
  }

}
