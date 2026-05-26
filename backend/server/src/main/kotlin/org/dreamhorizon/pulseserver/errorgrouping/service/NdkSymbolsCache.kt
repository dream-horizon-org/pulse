package org.dreamhorizon.pulseserver.errorgrouping.service

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.inject.Inject
import io.reactivex.rxjava3.core.Single
import io.vertx.core.Context
import io.vertx.core.Vertx
import java.time.Duration
import java.util.Optional
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadataKeyView

/** Async cache for NDK obj zip bytes (unstripped `.so` tree uploaded from Gradle). */
class NdkSymbolsCache @Inject constructor(
    vertx: Vertx,
    private val symbolFileService: SymbolFileService,
) {
    private val recentMiss: Cache<String, Boolean>
    private val cache: AsyncLoadingCache<UploadMetadata, Optional<ByteArray>>

    init {
        val ctx = vertx.orCreateContext
            ?: error("NdkSymbolsCache must be created on a Vert.x context thread")

        recentMiss = Caffeine.newBuilder()
            .expireAfterWrite(NEGATIVE_CACHE_TTL)
            .maximumSize(500)
            .build()

        cache = Caffeine.newBuilder()
            .maximumSize(100)
            .executor { cmd -> ctx.runOnContext { cmd.run() } }
            .expireAfterAccess(CACHE_TTL)
            .recordStats()
            .buildAsync { key, _ ->
                symbolFileService.readFileAsBytes(key)
                    .map { Optional.of(it) }
                    .toCompletionStage()
                    .toCompletableFuture()
            }
    }

    fun getNdkSymbols(key: UploadMetadata): Single<Optional<ByteArray>> {
        val negativeKey = negativeKey(key)
        if (recentMiss.getIfPresent(negativeKey) == false) {
            return Single.just(Optional.empty())
        }
        val future = cache.get(key)
        return Single.create { emitter ->
            future.whenComplete { result, throwable ->
                if (throwable != null) {
                    recentMiss.put(negativeKey, false)
                    emitter.onSuccess(Optional.empty())
                    return@whenComplete
                }
                val bytes = result.orElse(null)
                if (bytes == null || bytes.isEmpty()) {
                    recentMiss.put(negativeKey, false)
                    emitter.onSuccess(Optional.empty())
                } else {
                    recentMiss.invalidate(negativeKey)
                    emitter.onSuccess(result)
                }
            }
        }
    }

    private fun negativeKey(key: UploadMetadataKeyView): String =
        listOf(
            key.getProjectId(),
            key.getPlatform(),
            key.getAppVersion(),
            key.getVersionCode(),
            key.getBundleId(),
            "NDK",
        ).joinToString("|")

    private companion object {
        val CACHE_TTL: Duration = Duration.ofHours(24)
        val NEGATIVE_CACHE_TTL: Duration = Duration.ofMinutes(5)
    }
}
