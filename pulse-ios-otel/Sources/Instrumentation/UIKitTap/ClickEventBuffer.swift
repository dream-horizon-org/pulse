/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Detects rage-click clusters on the UI thread with zero background threads.

internal class ClickEventBuffer {
    private struct ActiveRageCluster {
        var rageEvent: RageEvent
        var lastTapTimestampMs: Int64
    }

    private static let maxActiveClusters = 5

    private let rageConfig: RageConfig
    private let onRage: (RageEvent) -> Void
    private let onEmit: (PendingClick) -> Void
    private let clock: () -> Int64
    
    private var buffer: [PendingClick] = []
    private var activeClusters: [ActiveRageCluster] = []
    private var emitTimer: DispatchSourceTimer?
    
    private let radiusPxSquared: Float
    
    init(
        rageConfig: RageConfig,
        onRage: @escaping (RageEvent) -> Void,
        onEmit: @escaping (PendingClick) -> Void,
        clock: @escaping () -> Int64 = { Int64(ProcessInfo.processInfo.systemUptime * 1000) }
    ) {
        self.rageConfig = rageConfig
        self.onRage = onRage
        self.onEmit = onEmit
        self.clock = clock
        self.radiusPxSquared = rageConfig.radiusPt * rageConfig.radiusPt
    }
    
    func record(_ click: PendingClick) {
        dispatchPrecondition(condition: .onQueue(.main))

        emitExpiredClusters(nowMs: click.timestampMs)
        evictStale(click.timestampMs)

        if let nearestClusterIndex = nearestClusterIndex(for: click) {
            updateCluster(at: nearestClusterIndex, with: click)
            scheduleDelayedEmit()
            return
        }

        processNormal(click)
    }
    
    func flush() {
        dispatchPrecondition(condition: .onQueue(.main))

        cancelDelayedEmit()

        // Emit all remaining active rage clusters first.
        for cluster in activeClusters {
            onRage(cluster.rageEvent)
        }
        activeClusters.removeAll()

        // Then emit all pending non-rage taps.
        while !buffer.isEmpty {
            onEmit(buffer.removeFirst())
        }
    }
    
    private func processNormal(_ click: PendingClick) {
        dispatchPrecondition(condition: .onQueue(.main))

        let nearbyIndices = buffer.enumerated().compactMap { index, pending in
            withinRadius(pending.x, pending.y, click.x, click.y) ? index : nil
        }
        let nearbyCount = nearbyIndices.count + 1 // Include current tap.

        if nearbyCount >= rageConfig.rageThreshold {
            // Keep only non-nearby buffered taps (selective eviction parity).
            let nearbySet = Set(nearbyIndices)
            buffer = buffer.enumerated().compactMap { index, pending in
                nearbySet.contains(index) ? nil : pending
            }

            let rageEvent = RageEvent(
                count: nearbyCount,
                hasTarget: click.hasTarget,
                x: click.x,
                y: click.y,
                tapEpochMs: click.tapEpochMs,
                widgetName: click.widgetName,
                widgetId: click.widgetId,
                clickContext: click.clickContext,
                viewportWidthPt: click.viewportWidthPt,
                viewportHeightPt: click.viewportHeightPt
            )

            if activeClusters.count >= Self.maxActiveClusters {
                emitOldestCluster()
            }
            activeClusters.append(
                ActiveRageCluster(
                    rageEvent: rageEvent,
                    lastTapTimestampMs: click.timestampMs
                )
            )
            scheduleDelayedEmit()
        } else {
            buffer.append(click)
        }
    }
    
    private func evictStale(_ nowMs: Int64) {
        dispatchPrecondition(condition: .onQueue(.main))
        
        let cutoff = nowMs - Int64(rageConfig.timeWindowMs)
        while !buffer.isEmpty && buffer.first!.timestampMs < cutoff {
            onEmit(buffer.removeFirst())
        }
    }
    
    private func withinRadius(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float) -> Bool {
        let dx = x1 - x2
        let dy = y1 - y2
        return dx * dx + dy * dy <= radiusPxSquared
    }

    private func nearestClusterIndex(for click: PendingClick) -> Int? {
        var nearestIndex: Int?
        var nearestDistSq: Float = .greatestFiniteMagnitude

        for (index, cluster) in activeClusters.enumerated() {
            let dx = cluster.rageEvent.x - click.x
            let dy = cluster.rageEvent.y - click.y
            let distSq = dx * dx + dy * dy
            guard distSq <= radiusPxSquared else { continue }

            if distSq < nearestDistSq {
                nearestDistSq = distSq
                nearestIndex = index
            }
        }

        return nearestIndex
    }

    private func updateCluster(at index: Int, with click: PendingClick) {
        dispatchPrecondition(condition: .onQueue(.main))

        var cluster = activeClusters[index]
        cluster.lastTapTimestampMs = click.timestampMs
        cluster.rageEvent.count += 1
        cluster.rageEvent = RageEvent(
            count: cluster.rageEvent.count,
            hasTarget: click.hasTarget,
            x: click.x,
            y: click.y,
            tapEpochMs: click.tapEpochMs,
            widgetName: click.widgetName,
            widgetId: click.widgetId,
            clickContext: click.clickContext,
            viewportWidthPt: click.viewportWidthPt,
            viewportHeightPt: click.viewportHeightPt
        )
        activeClusters[index] = cluster
    }

    private func emitExpiredClusters(nowMs: Int64) {
        dispatchPrecondition(condition: .onQueue(.main))

        guard !activeClusters.isEmpty else { return }
        let expiryMs = Int64(rageConfig.timeWindowMs)

        var retained: [ActiveRageCluster] = []
        retained.reserveCapacity(activeClusters.count)
        for cluster in activeClusters {
            if nowMs - cluster.lastTapTimestampMs >= expiryMs {
                onRage(cluster.rageEvent)
            } else {
                retained.append(cluster)
            }
        }
        activeClusters = retained
    }

    private func emitOldestCluster() {
        dispatchPrecondition(condition: .onQueue(.main))

        guard
            let oldest = activeClusters.enumerated().min(by: {
                $0.element.lastTapTimestampMs < $1.element.lastTapTimestampMs
            })
        else { return }

        onRage(oldest.element.rageEvent)
        activeClusters.remove(at: oldest.offset)
    }
    
    private func scheduleDelayedEmit() {
        dispatchPrecondition(condition: .onQueue(.main))

        cancelDelayedEmit()
        guard !activeClusters.isEmpty else { return }

        let nowMs = clock()
        let expiryMs = Int64(rageConfig.timeWindowMs)
        let nextExpiryAtMs = activeClusters
            .map { $0.lastTapTimestampMs + expiryMs }
            .min() ?? (nowMs + expiryMs)
        let delayMs = max(1, Int(nextExpiryAtMs - nowMs))

        emitTimer = DispatchSource.makeTimerSource(queue: .main)
        emitTimer?.schedule(deadline: .now() + .milliseconds(delayMs))
        emitTimer?.setEventHandler { [weak self] in
            guard let self = self else { return }
            self.emitExpiredClusters(nowMs: self.clock())
            self.scheduleDelayedEmit()
        }
        emitTimer?.resume()
    }
    
    private func cancelDelayedEmit() {
        emitTimer?.cancel()
        emitTimer = nil
    }
}
