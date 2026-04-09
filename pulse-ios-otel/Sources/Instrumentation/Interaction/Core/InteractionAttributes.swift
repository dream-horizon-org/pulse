/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Constants for Interaction Instrumentation
internal enum InteractionAttributes {
    static let name = "pulse.interaction.name"
    static let configName = "pulse.interaction.config.name"
    static let configId = "pulse.interaction.config.id"
    static let id = "pulse.interaction.id"
    static let lastEventTimeInNano = "pulse.interaction.last_event_time"
    static let apdexScore = "pulse.interaction.apdex_score"
    static let userCategory = "pulse.interaction.user_category"
    static let timeToCompleteInNano = "pulse.interaction.complete_time"
    static let isError = "pulse.interaction.is_error"
    static let localEvents = "internal_events"
    static let markerEvents = "internal_marker"
    static let logTag = "InteractionManager"
    
    enum Operators: String {
        case equals = "EQUALS"
        case notEquals = "NOTEQUALS"
        case contains = "CONTAINS"
        case notContains = "NOTCONTAINS"
        case startsWith = "STARTSWITH"
        case endsWith = "ENDSWITH"
    }
    
    enum TimeCategory: String {
        case excellent = "Excellent"
        case good = "Good"
        case average = "Average"
        case poor = "Poor"
    }
    
    // Pulse type constants
    static let pulseType = "pulse.type"
    static let pulseTypeInteraction = "interaction"
}

