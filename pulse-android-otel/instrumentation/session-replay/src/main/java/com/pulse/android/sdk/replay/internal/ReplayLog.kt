package com.pulse.android.sdk.replay.internal

import android.util.Log
import com.pulse.utils.BuildConfig
import com.pulse.utils.PulseOtelUtils

/**
 * Central logger for session replay. Logs only when the app is a debug build ([PulseOtelUtils.isDebug]).
 * Use this instead of [Log] for all replay-related output so release builds stay quiet.
 */
internal object ReplayLog {
    const val TAG = "PulseSessionReplay"

    private val enabled: Boolean
        get() = BuildConfig.DEBUG

    fun d(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun i(message: String) {
        if (enabled) Log.i(TAG, message)
    }

    fun w(message: String) {
        if (enabled) Log.w(TAG, message)
    }

    fun w(
        message: String,
        throwable: Throwable?,
    ) {
        if (enabled) Log.w(TAG, message, throwable)
    }

    fun e(message: String) {
        if (enabled) Log.e(TAG, message)
    }

    fun e(
        message: String,
        throwable: Throwable?,
    ) {
        if (enabled) Log.e(TAG, message, throwable)
    }
}
