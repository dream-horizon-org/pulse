/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.ccrash

import android.app.Application
import java.io.File

internal object PulseNativeCrashStorage {
    private val DIR_NAME = "pulse${File.separatorChar}nativeCrash"

    fun reportsDir(app: Application): File {
        val dir = File(app.cacheDir, DIR_NAME)
        dir.mkdirs()
        return dir
    }
}

