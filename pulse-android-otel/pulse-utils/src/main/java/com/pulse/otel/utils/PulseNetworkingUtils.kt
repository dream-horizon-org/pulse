package com.pulse.otel.utils

import com.pulse.otel.utils.PulseOtelUtils.ALPHANUMERIC
import com.pulse.otel.utils.PulseOtelUtils.DIGITS
import com.pulse.otel.utils.PulseOtelUtils.HEX_CHARS
import com.pulse.otel.utils.PulseOtelUtils.REDACTED
import com.pulse.otel.utils.PulseOtelUtils.ULID_CHARS
import okhttp3.OkHttpClient
import java.net.URL

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
        val url = URL(fullUrl)
        return "${url.protocol}://${url.host}/"
    }
}
