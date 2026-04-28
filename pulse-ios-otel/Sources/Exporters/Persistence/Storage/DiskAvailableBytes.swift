/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

enum DiskAvailableBytes {
  /// Best-effort bytes available on the volume containing `directoryURL` (for `disk_available_bytes` in diagnostics).
  static func forDirectoryURL(_ directoryURL: URL) -> Int64? {
    let keys: Set<URLResourceKey> = [.volumeAvailableCapacityForImportantUsageKey]
    guard let values = try? directoryURL.resourceValues(forKeys: keys),
      let n = values.volumeAvailableCapacityForImportantUsage
    else {
      return nil
    }
    return Int64(n)
  }
}
