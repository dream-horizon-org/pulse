/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Logging sources live under `PulseKit/Logging` but compile as the internal `PulseLogging`
 * SwiftPM target (shared with exporters). This re-export makes `import PulseKit` sufficient
 * for app code (e.g. `PulseLogLevel`). CocoaPods builds a single `PulseKit` module, so this
 * block is skipped there. `PulseLogger` is `package`-only (SPM) / internal to the pod module.
 */

#if canImport(PulseLogging)
@_exported import PulseLogging
#endif
