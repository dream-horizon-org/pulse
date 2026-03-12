package com.pulse.sampling.core.providers

import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.remote.PulseSdkConfigApiService
import com.pulse.sampling.remote.PulseSdkConfigRetrofitClient
import com.pulse.utils.PulseOtelUtils
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

public class PulseSdkConfigRestProvider(
    private val cacheDir: File,
    private val okHttpClient: OkHttpClient,
    private val headers: Map<String, String> = emptyMap(),
    private val urlProvider: () -> String,
) : PulseSdkConfigProvider {
    private val restClients = ConcurrentHashMap<String, PulseSdkConfigApiService>()
    private var retrofitClient: PulseSdkConfigRetrofitClient? = null

    override suspend fun provide(): PulseSdkConfig? {
        val url = urlProvider()
        val finalOkHttpClient =
            if (okHttpClient.cache == null) {
                okHttpClient
                    .newBuilder()
                    .apply {
                        val cache = Cache(cacheDir, MAX_CACHE_SIZE_BYTE)
                        cache(cache)
                    }.build()
            } else {
                okHttpClient
            }
        val restClient =
            restClients
                .getOrPut(url) {
                    (
                        retrofitClient?.newInstance(url)
                            ?: run {
                                PulseSdkConfigRetrofitClient(
                                    url = url,
                                    okhttpClient = finalOkHttpClient,
                                ).apply {
                                    retrofitClient = this
                                }
                            }
                    ).apiService
                }

        @Suppress("SuspendFunSwallowedCancellation")
        val restResponseResult =
            runCatching {
                restClient.getConfig(
                    fullFileUrl = url,
                    headers = headers,
                )
            }.onFailure { throwable ->
                currentCoroutineContext().ensureActive()
                // removing cache as api has failed
                val urlIterator = finalOkHttpClient.cache?.urls()
                urlIterator?.forEach { if (it == url) urlIterator.remove() }
                PulseOtelUtils.logDebug(TAG) { "onFailure in runCatching, url = $url error msg = ${throwable.message ?: "no-err-msg"}" }
            }
        return if (restResponseResult.isSuccess) {
            restResponseResult.getOrThrow()
        } else {
            PulseOtelUtils.logDebug(TAG) {
                "Failed to fetch sdk config: ${
                    (
                        restResponseResult.exceptionOrNull() ?: error(
                            "error is null in getConfigs",
                        )
                    ).message ?: "no-err-msg"
                }"
            }
            null
        }
    }

    internal companion object {
        private const val TAG = "PulseSdkConfigRestProvider"
        private const val MAX_CACHE_SIZE_BYTE: Long = 10 * 1024 * 1024
    }
}
