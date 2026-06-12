package com.pulse.android.core.config

import com.pulse.android.core.InteractionConstant
import com.pulse.android.remote.InteractionApiService
import com.pulse.android.remote.InteractionRetrofitClient
import com.pulse.android.remote.models.InteractionConfig
import com.pulse.utils.PulseLogger
import com.pulse.utils.PulseNetworkingUtils
import com.pulse.utils.RedactionUtils
import kotlinx.serialization.SerializationException
import java.util.concurrent.ConcurrentHashMap

/**
 * Get api implementation of [InteractionConfigFetcher]
 * [urlProvider] takes a lambda which returns the url from which the configs should be fetched using
 * `Get` api call
 * [headers] are HTTP headers to include in all requests
 */
public class InteractionConfigRestFetcher(
    private val urlProvider: () -> String,
    private val headers: Map<String, String> = emptyMap(),
) : InteractionConfigFetcher {
    private val restClients = ConcurrentHashMap<String, InteractionApiService>()
    private var interactionRetrofitClient: InteractionRetrofitClient? = null

    override suspend fun getConfigs(): List<InteractionConfig> {
        val url = urlProvider()
        PulseLogger.logInfo(InteractionConstant.LOG_TAG) {
            "interaction_config_fetch " +
                "resolved_url=${PulseNetworkingUtils.redactUrl(url)} " +
                "has_headers=${headers.isNotEmpty()}"
        }
        return try {
            restClients
                .getOrPut(url) {
                    (
                        interactionRetrofitClient?.newInstance(url)
                            ?: run {
                                InteractionRetrofitClient(
                                    url = url,
                                    okHttpClient = PulseNetworkingUtils.okHttpClient,
                                ).apply {
                                    interactionRetrofitClient = this
                                }
                            }
                    ).apiService
                }.getInteractions(fullFileUrl = url, headers = headers)
        } catch (e: SerializationException) {
            PulseLogger.logError(InteractionConstant.LOG_TAG, e) {
                "sdk.interaction.parse_failure error_class=${RedactionUtils.classifyError(e)}"
            }
            throw e
        }
    }
}
