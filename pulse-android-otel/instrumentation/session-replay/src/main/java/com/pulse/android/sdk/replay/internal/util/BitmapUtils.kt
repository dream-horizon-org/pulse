package com.pulse.android.sdk.replay.internal.util

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi

internal fun Bitmap.isValid(): Boolean {
    return try {
        @Suppress("DEPRECATION")
        !isRecycled && width > 0 && height > 0
    } catch (_: Throwable) {
        false
    }
}

/**
 * Encode bitmap as WebP (lossy) base64.
 * @param quality WebP lossy quality 0–100. Lower = smaller payload, lower fidelity.
 * Extension point: replace with custom encoder if needed.
 */
@RequiresApi(Build.VERSION_CODES.R)
internal fun Bitmap.webpBase64(quality: Int = 30): String? {
    return try {
        if (!isValid()) return null
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        val out = java.io.ByteArrayOutputStream()
        if (!compress(format, quality, out)) return null
        android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    } catch (_: Throwable) {
        null
    }
}
