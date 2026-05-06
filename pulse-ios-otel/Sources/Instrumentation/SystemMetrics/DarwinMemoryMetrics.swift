/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * System-wide “used” memory ≈ active + wired + compressor pages × page size (host VM stats).
 * App memory ≈ `phys_footprint` for this task (parity with Android JVM heap / process heap usage signal).
 */

import Darwin
import Foundation

internal enum DarwinMemoryMetrics {
    /// Bytes attributed to kernel + userspace pressure proxy (stable definition for analytics).
    static func systemMemoryUsedBytes() -> Int64? {
        var stats = vm_statistics64_data_t()
        var hostCount = mach_msg_type_number_t(
            MemoryLayout<vm_statistics64_data_t>.size / MemoryLayout<integer_t>.size
        )
        let result = withUnsafeMutablePointer(to: &stats) { ptr -> kern_return_t in
            ptr.withMemoryRebound(to: integer_t.self, capacity: Int(hostCount)) {
                host_statistics64(mach_host_self(), HOST_VM_INFO64, $0, &hostCount)
            }
        }
        guard result == KERN_SUCCESS else { return nil }
        let pageSize = Int64(vm_kernel_page_size)
        let usedPages =
            Int64(stats.active_count)
                + Int64(stats.wire_count)
                + Int64(stats.compressor_page_count)
        return usedPages * pageSize
    }

    /// This process physical footprint (bytes).
    static func appPhysFootprintBytes() -> Int64? {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(
            MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size
        )
        let kerr = withUnsafeMutablePointer(to: &info) { ptr -> kern_return_t in
            ptr.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }
        guard kerr == KERN_SUCCESS else { return nil }
        return Int64(info.phys_footprint)
    }
}
