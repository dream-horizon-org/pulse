package com.pulse.utils

import android.util.Log

/**
 * Centralized Pulse SDK logging. Configure via [logLevel] (set during SDK initialization).
 */
public object PulseLogger {
    /** Logcat tag prefix (`PulseSDK:<caller-tag>`). Must be public for cross-module inlining of the log helpers. */
    public const val TAG_PREFIX: String = "PulseSDK"

    @Volatile
    public var logLevel: PulseLogLevel = PulseLogLevel.NONE

    @PublishedApi
    internal inline fun getTag(tag: () -> String): String = "$TAG_PREFIX:${tag()}"

    public inline fun logError(
        tag: String,
        throwable: Throwable,
        body: () -> String,
    ) {
        if (logLevel <= PulseLogLevel.ERROR) Log.e(getTag { tag }, body(), throwable)
    }

    public inline fun logError(
        tag: String,
        body: () -> String,
    ) {
        if (logLevel <= PulseLogLevel.ERROR) Log.e(getTag { tag }, body())
    }

    public inline fun logWarn(
        tag: String,
        body: () -> String,
    ) {
        if (logLevel <= PulseLogLevel.WARN) Log.w(getTag { tag }, body())
    }

    public inline fun logWarn(
        tag: String,
        throwable: Throwable?,
        body: () -> String,
    ) {
        if (logLevel <= PulseLogLevel.WARN) Log.w(getTag { tag }, body(), throwable)
    }

    public inline fun logInfo(
        tag: String,
        body: () -> String,
    ) {
        if (logLevel <= PulseLogLevel.INFO) Log.i(getTag { tag }, body())
    }

    public inline fun logDebug(
        tag: String,
        body: () -> String,
    ) {
        if (logLevel <= PulseLogLevel.DEBUG) Log.d(getTag { tag }, body())
    }

    public inline fun logVerbose(
        tag: String,
        body: () -> String,
    ) {
        if (logLevel <= PulseLogLevel.VERBOSE) Log.v(getTag { tag }, body())
    }
}
