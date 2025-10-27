package io.opentelemetry.android.common.internal.utils

import android.os.Build

inline val Thread.threadIdCompat: Long
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            threadId()
        } else {
            @Suppress("DEPRECATION")
            id
        }