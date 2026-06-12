package io.opentelemetry.android.common.internal.utils

val Thread.threadIdCompat: Long
    get() =
        @Suppress("DEPRECATION")
        id
