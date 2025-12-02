package org.dreamhorizon.pulseserver.errorgrouping.service;

import com.android.tools.r8.retrace.ProguardMapProducer;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.debugging.sourcemap.SourceMapConsumerV3;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata;

public class SourceMapCache {
  private final AsyncLoadingCache<UploadMetadata, SourceMapConsumerV3> sourceMapCache;
  private final AsyncLoadingCache<UploadMetadata, ProguardMapProducer> r8Cache;

  @Inject
  public SourceMapCache(Vertx vertx, SymbolFileService symbolFileService) {
    Context ctx = vertx.getOrCreateContext();
    Objects.requireNonNull(ctx, "SourceMapCache must be created on a Vert.x context thread");

    this.sourceMapCache = Caffeine.newBuilder()
        .maximumSize(200)
        .executor(
            cmd -> {
              ctx.runOnContext(v -> cmd.run());
            }
        ).expireAfterAccess(Duration.ofHours(24))
        .recordStats()
        .buildAsync((UploadMetadata key, java.util.concurrent.Executor executor) -> {
          // DB -> bytes -> parse
          return symbolFileService.readFileAsString(key)
              .map(file -> {
                try {
                  SourceMapConsumerV3 sourcemap = new SourceMapConsumerV3();
                  sourcemap.parse(file);
                  return sourcemap;
                } catch (Exception e) {
                  throw new RuntimeException("Failed to parse source map for " + key, e);
                }
              })
              .toCompletionStage()
              .toCompletableFuture();
        });

    this.r8Cache = Caffeine.newBuilder()
        .maximumSize(200)
        .executor(cmd -> {
              Objects.requireNonNull(Vertx.currentContext());
              Vertx.currentContext().runOnContext(v -> cmd.run());
            }


        ).expireAfterAccess(Duration.ofHours(24))
        .recordStats()
        .buildAsync((UploadMetadata key, java.util.concurrent.Executor executor) -> {
          // DB -> bytes -> parse
          return symbolFileService.readFileAsString(key)
              .map(file -> {
                try {
                  return ProguardMapProducer.fromString(file);
                } catch (Exception e) {
                  throw new RuntimeException("Failed to parse source map for " + key, e);
                }
              })
              .toCompletionStage()
              .toCompletableFuture();
        });
  }

  public Single<SourceMapConsumerV3> getSourceMap(UploadMetadata key) {
    CompletableFuture<SourceMapConsumerV3> fut = sourceMapCache.get(key);
    // Use Single.create to avoid blocking the event loop
    // Single.fromFuture() calls .get() which blocks!
    return Single.create(emitter -> {
      fut.whenComplete((result, throwable) -> {
        if (throwable != null) {
          emitter.onError(throwable);
        } else {
          emitter.onSuccess(result);
        }
      });
    });
  }

  public Single<ProguardMapProducer> getProguardMap(UploadMetadata key) {
    CompletableFuture<ProguardMapProducer> fut = r8Cache.get(key);
    // Use Single.create to avoid blocking the event loop
    // Single.fromFuture() calls .get() which blocks!
    return Single.create(emitter -> {
      fut.whenComplete((result, throwable) -> {
        if (throwable != null) {
          emitter.onError(throwable);
        } else {
          emitter.onSuccess(result);
        }
      });
    });
  }
}
