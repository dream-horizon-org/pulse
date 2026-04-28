/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

enum DiskUsageBytes {
  /// Sum of file sizes under `directoryURL` (for `sdk.disk.usage_bytes`).
  static func bytesUnderDirectory(_ directoryURL: URL) -> Int64 {
    guard let enumerator = FileManager.default.enumerator(
      at: directoryURL,
      includingPropertiesForKeys: [.fileSizeKey],
      options: [.skipsHiddenFiles]
    ) else {
      return 0
    }
    var total: Int64 = 0
    for case let fileURL as URL in enumerator {
      guard let vals = try? fileURL.resourceValues(forKeys: [.fileSizeKey]),
        let n = vals.fileSize
      else { continue }
      total += Int64(n)
    }
    return total
  }
}
