import Foundation
import OpenTelemetryApi

public enum PulseAttributes {
    public static let pulseType = "pulse.type"
    public static let pulseName = "pulse.name"
    public static let pulseSpanId = "pulse.span.id"

    public enum PulseTypeValues {
        public static let customEvent = "custom_event"
        public static let nonFatal = "non_fatal"
    }
}
