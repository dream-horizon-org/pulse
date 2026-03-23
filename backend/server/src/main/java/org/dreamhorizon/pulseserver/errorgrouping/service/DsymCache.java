package org.dreamhorizon.pulseserver.errorgrouping.service;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata;

/**
 * Async cache for dSYM zip bytes .
 * Missing or failed loads yield empty {@link Optional}.
 */
public class DsymCache {

  private final AsyncLoadingCache<UploadMetadata, Optional<byte[]>> cache;

  @Inject
  public DsymCache(Vertx vertx, SymbolFileService symbolFileService) {
    Context ctx = vertx.getOrCreateContext();
    Objects.requireNonNull(ctx, "DsymCache must be created on a Vert.x context thread");

    this.cache = Caffeine.newBuilder()
        .maximumSize(100)
        .executor(cmd -> ctx.runOnContext(v -> cmd.run()))
        .expireAfterAccess(Duration.ofHours(24))
        .recordStats()
        .buildAsync((UploadMetadata key, java.util.concurrent.Executor executor) ->
            symbolFileService.readFileAsBytes(key)
                .map(Optional::<byte[]>of)
                .onErrorReturnItem(Optional.empty())
                .toCompletionStage()
                .toCompletableFuture());
  }

  public Single<Optional<byte[]>> getDsym(UploadMetadata key) {
    CompletableFuture<Optional<byte[]>> fut = cache.get(key);
    return Single.create(emitter -> fut.whenComplete((result, throwable) -> {
      if (throwable != null) {
        emitter.onError(throwable);
      } else {
        emitter.onSuccess(result);
      }
    }));
  }
}
