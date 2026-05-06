/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pulse.utils

import java.io.File

/** Total byte size of all files under a directory tree (for `sdk.disk.usage_bytes`). */
public object DiskUsageBytes {
    @JvmStatic
    public fun forDirectoryRoot(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        return dir
            .walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
