package com.pulse.android.sdk.replay.internal

import android.util.Log
import com.pulse.utils.BuildConfig

internal object ReplayLog {
    const val TAG = "PulseSessionReplay"

    private val isEnabled: Boolean
        get() = BuildConfig.DEBUG

    fun debug(message: String) {
        if (isEnabled) Log.d(TAG, message)
    }

    fun info(message: String) {
        if (isEnabled) Log.i(TAG, message)
    }

    fun warn(message: String) {
        if (isEnabled) Log.w(TAG, message)
    }

    fun warn(
        message: String,
        throwable: Throwable?,
    ) {
        if (isEnabled) Log.w(TAG, message, throwable)
    }

    fun logError(message: String) {
        if (isEnabled) Log.e(TAG, message)
    }

    fun logError(
        message: String,
        throwable: Throwable?,
    ) {
        if (isEnabled) Log.e(TAG, message, throwable)
    }
}
