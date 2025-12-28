package com.pulse.sampling.core.providers

import com.pulse.otel.utils.PulseOtelUtils
import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.remote.PulseSdkConfigApiService
import com.pulse.sampling.remote.PulseSdkConfigRetrofitClient
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.concurrent.ConcurrentHashMap

public class PulseSdkConfigRestProvider(
    private val cacheDir: File,
    private val urlProvider: () -> String,
) : PulseSdkConfigProvider {
    private val restClients = ConcurrentHashMap<String, PulseSdkConfigApiService>()
    private var retrofitClient: PulseSdkConfigRetrofitClient? = null

    override suspend fun provide(): PulseSdkConfig? {
        val url = urlProvider()
        val restClient =
            restClients
                .getOrPut(url) {
                    (
                        retrofitClient?.newInstance(url)
                            ?: run {
                                PulseSdkConfigRetrofitClient(url, cacheDir).apply {
                                    retrofitClient = this
                                }
                            }
                    ).apiService
                }

        @Suppress("SuspendFunSwallowedCancellation")
        val restResponseResult =
            runCatching {
                restClient.getConfig()
            }.onFailure {
                currentCoroutineContext().ensureActive()
                PulseOtelUtils.logDebug(TAG) { "onFailure in runCatching, error msg = ${it.message ?: "no-err-msg"}" }
            }
        return if (restResponseResult.isSuccess) {
            val restResponse = restResponseResult.getOrThrow()
            if (restResponse.error == null) {
                restResponse.data
            } else {
                PulseOtelUtils.logDebug(TAG) {
                    "Sdk config returned error = ${(restResponse.error ?: error("error is null in getConfigs")).message}"
                }
                null
            }
        } else {
            PulseOtelUtils.logDebug(TAG) {
                "Failed to fetch sdk config: ${(
                    restResponseResult.exceptionOrNull() ?: error(
                        "error is null in getConfigs",
                    )
                ).message ?: "no-err-msg"}"
            }
            null
        }
    }

    internal companion object {
        private const val TAG = "PulseSdkConfigRestProvider"
    }
}
