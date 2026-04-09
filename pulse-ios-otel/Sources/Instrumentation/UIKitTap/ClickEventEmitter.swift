/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi

/// Handles all click event emission for UIKit tap instrumentation:
/// buffers taps, detects rage clusters, and emits good / dead / rage events.
internal class ClickEventEmitter {
    private let logger: OpenTelemetryApi.Logger
    
    init(logger: OpenTelemetryApi.Logger) {
        self.logger = logger
    }
    
    func emitGoodClick(_ click: PendingClick) {
        var attrs: [String: AttributeValue] = [
            PulseAttributes.clickType: .string(PulseAttributes.ClickTypeValues.good),
            "app.widget.name": .string(click.widgetName ?? ""),
            "app.widget.id": .string(click.widgetId ?? ""),
            "app.screen.coordinate.x": .int(Int(click.x)),
            "app.screen.coordinate.y": .int(Int(click.y)),
        ]
        
        if let context = click.clickContext {
            attrs["app.click.context"] = .string(context)
        }
        
        applyViewportAttrs(&attrs, click.viewportWidthPt, click.viewportHeightPt, click.x, click.y)
        
        let timestamp = Date(timeIntervalSince1970: TimeInterval(click.tapEpochMs) / 1000.0)
        let record = logger.logRecordBuilder()
            .setEventName("app.widget.click")
            .setTimestamp(timestamp)
            .setAttributes(attrs)
        record.emit()
    }
    
    func emitDeadClick(_ click: PendingClick) {
        var attrs: [String: AttributeValue] = [
            PulseAttributes.clickType: .string(PulseAttributes.ClickTypeValues.dead),
            "app.screen.coordinate.x": .int(Int(click.x)),
            "app.screen.coordinate.y": .int(Int(click.y)),
        ]
        
        applyViewportAttrs(&attrs, click.viewportWidthPt, click.viewportHeightPt, click.x, click.y)
        
        let timestamp = Date(timeIntervalSince1970: TimeInterval(click.tapEpochMs) / 1000.0)
        let record = logger.logRecordBuilder()
            .setEventName("app.widget.click")
            .setTimestamp(timestamp)
            .setAttributes(attrs)
        record.emit()
    }
    
    func emitRageClick(_ rage: RageEvent) {
        let clickType = rage.hasTarget ? PulseAttributes.ClickTypeValues.good : PulseAttributes.ClickTypeValues.dead
        var attrs: [String: AttributeValue] = [
            PulseAttributes.clickType: .string(clickType),
            PulseAttributes.clickIsRage: .bool(true),
            PulseAttributes.clickRageCount: .int(rage.count),
            "app.screen.coordinate.x": .int(Int(rage.x)),
            "app.screen.coordinate.y": .int(Int(rage.y)),
        ]
        
        if let name = rage.widgetName {
            attrs["app.widget.name"] = .string(name)
        }
        if let id = rage.widgetId {
            attrs["app.widget.id"] = .string(id)
        }
        if let context = rage.clickContext {
            attrs["app.click.context"] = .string(context)
        }
        
        applyViewportAttrs(&attrs, rage.viewportWidthPt, rage.viewportHeightPt, rage.x, rage.y)
        
        let timestamp = Date(timeIntervalSince1970: TimeInterval(rage.tapEpochMs) / 1000.0)
        let record = logger.logRecordBuilder()
            .setEventName("app.widget.click")
            .setTimestamp(timestamp)
            .setAttributes(attrs)
        record.emit()
    }
    
    private func applyViewportAttrs(_ attrs: inout [String: AttributeValue], _ widthPt: Int, _ heightPt: Int, _ x: Float, _ y: Float) {
        if widthPt > 0 && heightPt > 0 {
            attrs[PulseAttributes.deviceScreenWidth] = .int(widthPt)
            attrs[PulseAttributes.deviceScreenHeight] = .int(heightPt)
            attrs[PulseAttributes.appScreenCoordinateNx] = .double(Double(x) / Double(widthPt))
            attrs[PulseAttributes.appScreenCoordinateNy] = .double(Double(y) / Double(heightPt))
        } 
    }
}
