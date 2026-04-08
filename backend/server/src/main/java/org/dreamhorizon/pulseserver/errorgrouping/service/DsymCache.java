package org.dreamhorizon.pulseserver.errorgrouping.service;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
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
 * Async cache for dSYM zip bytes.
 *
 * <p>Successful loads are cached for {@link #CACHE_TTL}. Load failures do not populate the async cache
 * (so a later upload is not ignored for 24h). Instead, a short {@link #NEGATIVE_CACHE_TTL} entry records
 * “recent miss” so we avoid hammering DB/S3; after that TTL we retry automatically.
 */
public class DsymCache {

  private static final Duration CACHE_TTL = Duration.ofHours(24);
  private static final Duration NEGATIVE_CACHE_TTL = Duration.ofMinutes(5);

  private final AsyncLoadingCache<UploadMetadata, Optional<byte[]>> cache;
  /** Value {@code false} means “known miss / error recently”; skip loader until TTL expires. */
  private final Cache<String, Boolean> recentMiss;

  @Inject
  public DsymCache(Vertx vertx, SymbolFileService symbolFileService) {
    Context ctx = vertx.getOrCreateContext();
    Objects.requireNonNull(ctx, "DsymCache must be created on a Vert.x context thread");

    this.recentMiss = Caffeine.newBuilder()
        .expireAfterWrite(NEGATIVE_CACHE_TTL)
        .maximumSize(500)
        .build();

    this.cache = Caffeine.newBuilder()
        .maximumSize(100)
        .executor(cmd -> ctx.runOnContext(v -> cmd.run()))
        .expireAfterAccess(CACHE_TTL)
        .recordStats()
        .buildAsync((UploadMetadata key, java.util.concurrent.Executor executor) ->
            symbolFileService.readFileAsBytes(key)
                .map(Optional::<byte[]>of)
                .toCompletionStage()
                .toCompletableFuture());
  }

  /**
   * Returns cached dSYM bytes for {@code key}, or loads from symbol file service.
   * Misses/errors return empty; misses are remembered for {@link #NEGATIVE_CACHE_TTL} only.
   */
  public Single<Optional<byte[]>> getDsym(UploadMetadata key) {
    String nk = negativeKey(key);
    if (Boolean.FALSE.equals(recentMiss.getIfPresent(nk))) {
      return Single.just(Optional.empty());
    }
    CompletableFuture<Optional<byte[]>> fut = cache.get(key);
    return Single.create(emitter -> fut.whenComplete((result, throwable) -> {
      if (throwable != null) {
        recentMiss.put(nk, Boolean.FALSE);
        emitter.onSuccess(Optional.empty());
        return;
      }
      Optional<byte[]> r = result;
      byte[] b = r.orElse(null);
      if (b == null || b.length == 0) {
        recentMiss.put(nk, Boolean.FALSE);
        emitter.onSuccess(Optional.empty());
      } else {
        recentMiss.invalidate(nk);
        emitter.onSuccess(r);
      }
    }));
  }

  private static String negativeKey(UploadMetadata k) {
    return String.join("|",
        String.valueOf(k.getProjectId()),
        String.valueOf(k.getPlatform()),
        String.valueOf(k.getAppVersion()),
        String.valueOf(k.getVersionCode()),
        String.valueOf(k.getBundleId()),
        "DSYM");
  }
}
