/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

public enum PulseRedaction {
    public static func redactUrl(_ urlString: String) -> String {
        guard let components = URLComponents(string: urlString) else {
            return "[invalid-url]"
        }
        var redacted = URLComponents()
        redacted.scheme = components.scheme
        redacted.host = components.host
        redacted.port = components.port
        redacted.path = components.path
        if components.queryItems?.isEmpty == false {
            redacted.query = "***"
        }
        return redacted.string ?? "[invalid-url]"
    }

    public static func classifyError(_ error: Error) -> String {
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
