package org.dreamhorizon.pulseserver.util;

import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public final class CompletableFutureUtils {

  public static <T> VertxCompletableFuture<T> fromSingle(io.reactivex.rxjava3.core.Single<T> single) {

    VertxCompletableFuture<T> vertxCompletableFuture = new VertxCompletableFuture<>();
    single.subscribe(vertxCompletableFuture::complete, vertxCompletableFuture::completeExceptionally);
    return vertxCompletableFuture;
  }

}
