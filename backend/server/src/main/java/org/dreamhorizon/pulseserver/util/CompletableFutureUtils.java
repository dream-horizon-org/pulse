package org.dreamhorizon.pulseserver.util;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.SingleHelper;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public final class CompletableFutureUtils {

  /**
   * Convert a single to completable future
   */
  public static <T> VertxCompletableFuture<T> fromSingle(io.reactivex.rxjava3.core.Single<T> single) {

    VertxCompletableFuture<T> vertxCompletableFuture = new VertxCompletableFuture<>();
    single.subscribe(vertxCompletableFuture::complete, vertxCompletableFuture::completeExceptionally);
    return vertxCompletableFuture;
  }


  /**
   * Convert a vertx completable future to single
   */
  public static <T> Single<T> toSingle(VertxCompletableFuture<T> vertxCompletableFuture) {
    return SingleHelper.toSingle(asyncResultHandler ->
        vertxCompletableFuture
            .toFuture()
            .onComplete(asyncResultHandler));
  }

}
