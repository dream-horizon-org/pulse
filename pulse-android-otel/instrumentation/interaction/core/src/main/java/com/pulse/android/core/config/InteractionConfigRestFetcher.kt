package com.pulse.android.core.config

import com.pulse.android.core.logDebug
import com.pulse.android.remote.InteractionApiService
import com.pulse.android.remote.InteractionRetrofitClient
import com.pulse.android.remote.models.InteractionConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Get api implementation of [InteractionConfigFetcher]
 * [urlProvider] takes a lambda which returns the url from which the configs should be fetched using
 * `Get` api call
 */
public class InteractionConfigRestFetcher(
    private val urlProvider: () -> String,
) : InteractionConfigFetcher {
    private val restClients = ConcurrentHashMap<String, InteractionApiService>()
    private var interactionRetrofitClient: InteractionRetrofitClient? = null

    override suspend fun getConfigs(): List<InteractionConfig>? {
        val url = urlProvider()
        val restResponse =
            restClients
                .getOrPut(url) {
                    (
                        interactionRetrofitClient?.newInstance(url)
                            ?: run {
                                InteractionRetrofitClient(url).apply {
                                    interactionRetrofitClient = this
                                }
                            }
                    ).apiService
                }.getInteractions()
        return if (restResponse.error == null) {
            restResponse.data
        } else {
            logDebug {
                "Failed to fetch interactions: ${(restResponse.error ?: error("error is null in getConfigs")).message}"
            }
            null
        }
    }
}
