package com.pulse.android.sdk.internal

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pulse.sampling.core.providers.PulseSdkConfigRestProvider
import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.utils.PulseNetworkingUtils
import com.pulse.utils.PulseOtelUtils
import com.pulse.utils.PulseSerialisationUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Reads the cached [PulseSdkConfig] from [SharedPreferences] for immediate use, then fires a
 * background fetch to refresh it for the next SDK initialisation. Also resolves the config URL
 * from caller-supplied overrides or the base endpoint URL.
 */
internal object PulseSdkConfigRefresher {
    internal fun loadAndRefresh(
        cacheDir: File,
        configUrl: String,
        headers: Map<String, String>,
        sharedPrefs: SharedPreferences,
        prefsKey: String,
        scope: CoroutineScope,
        ioDispatcher: CoroutineDispatcher,
    ): PulseSdkConfig? {
        val currentSdkConfig =
            sharedPrefs.getString(prefsKey, null)?.let {
                PulseSerialisationUtils.jsonConfigForSerialisation.decodeFromString<PulseSdkConfig>(it)
            }
        PulseOtelUtils.logDebug(TAG) { "currentSdkConfig version = ${currentSdkConfig?.version ?: "null"}" }

        scope.launch(ioDispatcher) {
            val apiCache = File(cacheDir, "pulse${File.separatorChar}apiCache")
            apiCache.mkdirs()
            val newConfig =
                PulseSdkConfigRestProvider(
                    cacheDir = apiCache,
                    okHttpClient = PulseNetworkingUtils.okHttpClient,
                    headers = headers,
                ) { configUrl }.provide()
            val isDifferentVersion = newConfig != null && newConfig.version != currentSdkConfig?.version
            PulseOtelUtils.logDebug(TAG) {
                "newConfigVersion = ${newConfig?.version ?: "newConfig is null"}, " +
                    "oldConfigVersion = ${currentSdkConfig?.version ?: "currentSdkConfig is null"}, " +
                    "shouldUpdate = $isDifferentVersion"
            }
            if (isDifferentVersion) {
                sharedPrefs.edit(commit = true) {
                    putString(
                        prefsKey,
                        PulseSerialisationUtils.jsonConfigForSerialisation.encodeToString(newConfig),
                    )
                }
            }
        }

        return currentSdkConfig
    }

    internal fun resolveConfigUrl(
        configEndpointUrl: String?,
        endpointBaseUrl: String,
    ): String =
        configEndpointUrl
            ?: "${PulseNetworkingUtils.endWithSlash(endpointBaseUrl.replace(":4318", ":8080"))}v1/configs/active/"

    private const val TAG = "PulseSdkConfigRefresher"
}
