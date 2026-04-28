/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pulse.utils

import android.os.Environment
import android.os.StatFs
import java.io.File

/** Best-effort free space on a volume for diagnostic logs (e.g. `disk_available_bytes`). */
public object DiskFreeSpaceBytes {
    @JvmStatic
    public fun forPath(path: File): Long? =
        runCatching {
            StatFs(path.path).availableBytes
        }.getOrNull()

    @JvmStatic
    public fun forDataPartition(): Long? {
        val dir = Environment.getDataDirectory() ?: return null
        return forPath(dir)
    }
}
