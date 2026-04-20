package com.pulse.utils

import com.pulse.utils.PulseOtelUtils.ALPHANUMERIC
import com.pulse.utils.PulseOtelUtils.DIGITS
import com.pulse.utils.PulseOtelUtils.HEX_CHARS
import com.pulse.utils.PulseOtelUtils.REDACTED
import com.pulse.utils.PulseOtelUtils.ULID_CHARS
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import java.net.URI

public object PulseNetworkingUtils {
    public val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    private val urlNormalizationPatterns =
        listOf(
            "(?<=/)($HEX_CHARS{64}|$HEX_CHARS{40})(?=/|$)".toRegex(),
            "(?<=/)($HEX_CHARS{32}|$HEX_CHARS{8}-$HEX_CHARS{4}-$HEX_CHARS{4}-$HEX_CHARS{4}-$HEX_CHARS{12})(?=/|$)".toRegex(),
            "(?<=/)($HEX_CHARS{24})(?=/|$)".toRegex(),
            "(?<=/)($ULID_CHARS{26})(?=/|$)".toRegex(),
            "(?<=/)($DIGITS{3,})(?=/|$)".toRegex(),
            "(?<=/)($ALPHANUMERIC{16,})(?=/|$)".toRegex(),
        )

    public fun redactUrl(originalUrl: String): String {
        var normalized = originalUrl.substringBefore("?")

        urlNormalizationPatterns.forEach { pattern ->
            normalized = pattern.replace(normalized, REDACTED)
        }

        return normalized
    }

    public fun endWithSlash(url: String): String = url.trimEnd('/') + "/"

    public fun extractBaseUrlWithSlash(fullUrl: String): String {
        val url = URI.create(fullUrl).toURL()
        return "${url.protocol}://${url.host}/"
    }

    // false +ve see https://github.com/detekt/detekt/issues/8902
    @Suppress("SuspendFunSwallowedCancellation")
    public suspend fun <T> runNetworkCatching(
        tag: String,
        url: String,
        okHttpClient: OkHttpClient?,
        removeCacheInFailure: Boolean,
        block: suspend () -> T,
    ): Result<T> =
        runCatching {
            block()
        }.onFailure { throwable ->
            currentCoroutineContext().ensureActive()
            if (removeCacheInFailure && okHttpClient != null) {
                // removing cache as api has failed
                val urlIterator = okHttpClient.cache?.urls()
                urlIterator?.forEach { if (it == url) urlIterator.remove() }
            }
            val throwableMsg =
                if (throwable is retrofit2.HttpException) {
                    "retrofit2.HttpException ${throwable.response()?.errorBody()?.string() ?: "no-err-msg"}"
                } else {
                    throwable.message ?: "no-err-msg"
                }
            PulseOtelUtils.logDebug(tag) { "onFailure in runCatching, url = $url error msg = $throwableMsg" }
        }
}
