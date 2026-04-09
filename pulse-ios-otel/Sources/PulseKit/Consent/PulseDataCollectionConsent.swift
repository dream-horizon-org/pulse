/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Controls when the Pulse SDK exports telemetry. Used for consent flows (e.g. GDPR, opt-in dialogs).
public enum PulseDataCollectionConsent {
    /// Telemetry is buffered in memory; nothing is exported. Buffer limit: 5000 per signal (spans, logs; metrics: up to 5000 MetricData values). Newest dropped when full.
    case pending
    /// Buffered data is flushed and subsequent data is exported normally.
    case allowed
    /// Buffered data is cleared and the SDK is shut down. Terminal in this process.
    case denied
}
