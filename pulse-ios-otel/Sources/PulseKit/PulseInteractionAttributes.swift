import Foundation
import OpenTelemetryApi

public enum PulseInteractionAttributes {
    public static let pulseType = "pulse.type"
    public static let interactionNames = "pulse.interaction.names"
    public static let interactionIds = "pulse.interaction.ids"
    public static let interactionName = "pulse.interaction.name"
    public static let interactionId = "pulse.interaction.id"
    public static let interactionConfigId = "pulse.interaction.config.id"
    public static let interactionLastEventTime = "pulse.interaction.last_event_time"
}
