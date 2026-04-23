package com.pulse.sampling.core.providers

import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.remote.PulseSdkConfigApiService
import com.pulse.sampling.remote.PulseSdkConfigRetrofitClient
import com.pulse.utils.PulseLogger
import com.pulse.utils.PulseNetworkingUtils
import com.pulse.utils.RedactionUtils
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
        val startNs = System.nanoTime()
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

        val restResponseResult =
            PulseNetworkingUtils.runNetworkCatching(
                tag = TAG,
                url = url,
                okHttpClient = finalOkHttpClient,
                removeCacheInFailure = true,
            ) {
                restClient.getConfig(
                    fullFileUrl = url,
                    headers = headers,
                )
            }
        val durationMs = (System.nanoTime() - startNs) / 1_000_000
        val transportOk = restResponseResult.isSuccess
        val httpStatus = if (transportOk) "ok" else "error"
        val errClass =
            restResponseResult.exceptionOrNull()?.let { RedactionUtils.classifyError(it) } ?: ""

        val resolved: PulseSdkConfig? =
            if (transportOk) {
                val config = restResponseResult.getOrThrow()
                if (config.version >= 0) {
                    config
                } else {
                    val urlIterator = finalOkHttpClient.cache?.urls()
                    urlIterator?.forEach { if (it == url) urlIterator.remove() }
                    null
                }
            } else {
                null
            }

        val versionStr = resolved?.version?.toString() ?: "none"
        val configOk = resolved != null
        if (configOk) {
            PulseLogger.logInfo(TAG) {
                "sdk.config.fetch success=true duration_ms=$durationMs http_status=$httpStatus config_version=$versionStr"
            }
        } else {
            val errSuffix = if (errClass.isNotEmpty()) " error_class=$errClass" else ""
            PulseLogger.logWarn(TAG) {
                "sdk.config.fetch success=false duration_ms=$durationMs http_status=$httpStatus config_version=$versionStr$errSuffix"
            }
        }

        return resolved
    }

    internal companion object {
        private const val TAG = "SdkConfigRestProvider"
        private const val MAX_CACHE_SIZE_BYTE: Long = 10 * 1024 * 1024
    }
}
