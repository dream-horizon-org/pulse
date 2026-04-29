/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Stable error buckets for diagnostic logs (no raw messages).
public enum PulseErrorClassification {
    public static func classify(_ error: Error) -> String {
        let nsError = error as NSError
        switch nsError.domain {
        case NSURLErrorDomain:
            switch nsError.code {
            case NSURLErrorTimedOut:
                return "timeout"
            case NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed:
                return "dns_resolution_failed"
            case NSURLErrorCannotConnectToHost:
                return "connection_refused"
            case NSURLErrorSecureConnectionFailed,
                 NSURLErrorServerCertificateHasBadDate,
                 NSURLErrorServerCertificateUntrusted,
                 NSURLErrorServerCertificateHasUnknownRoot,
                 NSURLErrorServerCertificateNotYetValid,
                 NSURLErrorClientCertificateRejected,
                 NSURLErrorClientCertificateRequired:
                return "ssl_error"
            case NSURLErrorNotConnectedToInternet, NSURLErrorNetworkConnectionLost:
                return "network_unavailable"
            default:
                return "url_error_\(nsError.code)"
            }
        default:
            return "unknown"
        }
    }
}
