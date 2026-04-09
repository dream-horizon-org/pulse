import Foundation
import OpenTelemetryApi

public enum PulseAttributes {
    public static let pulseType = "pulse.type"
    public static let pulseName = "pulse.name"
    public static let pulseSpanId = "pulse.span.id"
    public static let screenName = "screen.name"
    public static let lastScreenName = "last.screen.name"
    public static let userId = "user.id"
    public static let appInstallationId = "app.installation.id"
    public static let pulseUserPrefix = "pulse.user"
    public static let pulseUserPreviousId = "pulse.user.previous_id"
    public static let pulseUserSessionStartEventName = "pulse.user.session.start"
    public static let pulseUserSessionEndEventName = "pulse.user.session.end"
    public static let startType = "start.type"
    public static let viewControllerName = "view_controller.name"
    
    public static let exceptionMessage = "exception.message"
    public static let exceptionType = "exception.type"
    public static let exceptionStacktrace = "exception.stacktrace"

    /// GraphQL span attributes (set when URL contains "graphql" and name/type are derivable).
    public static let graphqlOperationName = "graphql.operation.name"
    public static let graphqlOperationType = "graphql.operation.type"

    /// Project identifier; used as resource attribute "project.id" and in HTTP header X-API-KEY.
    public static let projectId = "project.id"
    internal static let apiKeyHeaderKey = "X-API-KEY"
    
    // Click instrumentation attributes
    public static let clickType = "click.type"
    public static let clickIsRage = "click.is_rage"
    public static let clickRageCount = "click.rage_count"
    public static let deviceScreenWidth = "device.screen.width"
    public static let deviceScreenHeight = "device.screen.height"
    public static let deviceScreenAspectRatio = "device.screen.aspect_ratio"
    public static let appScreenCoordinateNx = "app.screen.coordinate.nx"
    public static let appScreenCoordinateNy = "app.screen.coordinate.ny"

    public static func pulseUserParameter(_ key: String) -> String {
        return "\(pulseUserPrefix).\(key)"
    }
    
    public enum PulseSdkNames {
        public static let iosSwift = "pulse_ios_swift"
        public static let iosRn = "pulse_ios_rn"
    }

    public enum ClickTypeValues {
        public static let good = "good"
        public static let dead = "dead"
    }
    
    public enum AppClickContext {
        public static func buildContext(label: String?) -> String? {
            guard let trimmed = label?.trimmingCharacters(in: .whitespaces), !trimmed.isEmpty else { return nil }
            return "label=\(trimmed)"
        }
    }

    public enum PulseTypeValues {
        // Custom events
        public static let customEvent = "custom_event"
        public static let nonFatal = "non_fatal"
        
        // Span types
        public static let network = "network"
        public static let screenLoad = "screen_load"
        public static let appStart = "app_start"
        public static let screenSession = "screen_session"
        
        // Log types
        public static let crash = "device.crash"
        public static let anr = "anr"
        public static let frozen = "frozen"
        public static let slow = "slow"
        public static let touch = "touch"
        public static let appClick = "app.click"
        public static let networkChange = "network_change"
        public static let appInstallationStart = "pulse.app.installation.start"
        /// App session lifecycle (matches Otel semantic convention event names)
        public static let appSessionStart = "session.start"
        public static let appSessionEnd = "session.end"
        /// Session replay
        public static let sessionReplay = "session_replay"
        public static func isNetworkType(_ pulseType: String) -> Bool {
            return pulseType == network || pulseType.hasPrefix("\(network).")
        }
    }
}
