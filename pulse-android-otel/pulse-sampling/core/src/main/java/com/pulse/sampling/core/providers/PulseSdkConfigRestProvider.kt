package com.pulse.sampling.core.providers

import com.pulse.otel.utils.PulseOtelUtils
import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.remote.PulseSdkConfigApiService
import com.pulse.sampling.remote.PulseSdkConfigRetrofitClient
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
        val restResponse =
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
                }.getConfig()
        return if (restResponse.error == null) {
            restResponse.data
        } else {
            PulseOtelUtils.logDebug("PulseSdkConfigRestProvider") {
                "Failed to fetch sdk config: ${(restResponse.error ?: error("error is null in getConfigs")).message}"
            }
            null
        }
    }
}
