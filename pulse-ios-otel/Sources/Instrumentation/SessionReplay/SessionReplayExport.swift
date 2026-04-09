/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

struct SessionReplayWireframe: Encodable, Equatable {
    let id: Int
    let type: String
    let x: Double
    let y: Double
    let width: Double
    let height: Double
    let base64: String?
}

struct SessionReplayMetaEvent: Encodable {
    let type: Int = 4
    let timestamp: Int64
    let data: MetaEventData
}

struct MetaEventData: Encodable {
    let href: String
    let width: Int
    let height: Int
    let aspectRatio: String?
    
    enum CodingKeys: String, CodingKey {
        case href
        case width
        case height
        case aspectRatio = "aspect_ratio"
    }
}

struct SessionReplayFullSnapshotEvent: Encodable {
    let type: Int = 2
    let timestamp: Int64
    let data: FullSnapshotData
}

struct FullSnapshotData: Encodable {
    let wireframes: [SessionReplayWireframe]
    let initialOffset: InitialOffset
}

struct InitialOffset: Encodable {
    let top: Double
    let left: Double
}

struct SessionReplayIncrementalSnapshotEvent: Encodable {
    let type: Int = 3
    let timestamp: Int64
    let data: IncrementalSnapshotData
}

struct IncrementalSnapshotData: Encodable {
    let source: Int
    let updates: [WireframeUpdate]?
}

struct WireframeUpdate: Encodable {
    let parentId: Int
    let wireframe: SessionReplayWireframe
}

struct SessionReplayPayload: Encodable {
    let event: String = "snapshot"
    let projectId: String
    let userId: String
    let properties: SessionReplayProperties

    enum CodingKeys: String, CodingKey {
        case event
        case projectId = "project_id"
        case userId = "user_id"
        case properties
    }
}

struct SessionReplayProperties: Encodable {
    let sessionId: String
    let snapshotSource: String
    let snapshotData: [SessionReplayEvent]
    let appVersion: String?

    enum CodingKeys: String, CodingKey {
        case sessionId = "session_id"
        case snapshotSource = "snapshot_source"
        case snapshotData = "snapshot_data"
        case appVersion = "app_version"
    }
}

enum SessionReplayEvent: Encodable {
    case meta(SessionReplayMetaEvent)
    case fullSnapshot(SessionReplayFullSnapshotEvent)
    case incrementalSnapshot(SessionReplayIncrementalSnapshotEvent)

    func encode(to encoder: Encoder) throws {
        switch self {
        case .meta(let event): try event.encode(to: encoder)
        case .fullSnapshot(let event): try event.encode(to: encoder)
        case .incrementalSnapshot(let event): try event.encode(to: encoder)
        }
    }
}

struct WindowSnapshotStatus {
    var sentFullSnapshot: Bool = false
    var sentMetaEvent: Bool = false
    var lastSnapshot: SessionReplayWireframe?
}

class SessionReplayEventTransformer {

    private var currentSessionId: String?
    private let wireframeId: Int = 1

    func reset() {
        currentSessionId = nil
    }

    func transformFrame(
        frame: SessionReplayFrame,
        windowStatus: inout WindowSnapshotStatus,
        projectId: String,
        userId: String,
        appVersion: String? = nil,
        aspectRatio: String? = nil
    ) -> [SessionReplayEvent] {
        var events: [SessionReplayEvent] = []
        
        if frame.sessionId != currentSessionId {
            currentSessionId = frame.sessionId
        }
        
        if !windowStatus.sentMetaEvent {
            events.append(.meta(makeMetaEvent(from: frame, aspectRatio: aspectRatio)))
            windowStatus.sentMetaEvent = true
        }
        
        let wireframe = makeWireframe(from: frame)
        
        if !windowStatus.sentFullSnapshot {
            events.append(.fullSnapshot(makeFullSnapshotEvent(from: frame)))
            windowStatus.sentFullSnapshot = true
            windowStatus.lastSnapshot = wireframe
        } else {
            if let lastSnapshot = windowStatus.lastSnapshot, lastSnapshot != wireframe {
                events.append(.incrementalSnapshot(makeIncrementalSnapshotEvent(from: frame)))
                windowStatus.lastSnapshot = wireframe
            }
        }
        
        return events
    }

    private func makeMetaEvent(from frame: SessionReplayFrame, aspectRatio: String? = nil) -> SessionReplayMetaEvent {
        return SessionReplayMetaEvent(
            timestamp: unixMs(from: frame.timestamp),
            data: MetaEventData(
                href: frame.screenName,
                width: frame.width,
                height: frame.height,
                aspectRatio: aspectRatio
            )
        )
    }

    private func makeFullSnapshotEvent(from frame: SessionReplayFrame) -> SessionReplayFullSnapshotEvent {
        SessionReplayFullSnapshotEvent(
            timestamp: unixMs(from: frame.timestamp),
            data: FullSnapshotData(
                wireframes: [makeWireframe(from: frame)],
                initialOffset: InitialOffset(top: 0, left: 0)
            )
        )
    }

    private func makeIncrementalSnapshotEvent(from frame: SessionReplayFrame) -> SessionReplayIncrementalSnapshotEvent {
        SessionReplayIncrementalSnapshotEvent(
            timestamp: unixMs(from: frame.timestamp),
            data: IncrementalSnapshotData(
                source: 0,
                updates: [WireframeUpdate(parentId: wireframeId, wireframe: makeWireframe(from: frame))]
            )
        )
    }

    private func makeWireframe(from frame: SessionReplayFrame) -> SessionReplayWireframe {
        SessionReplayWireframe(
            id: wireframeId,
            type: "screenshot",
            x: 0, y: 0,
            width: Double(frame.width),
            height: Double(frame.height),
            base64: base64Encode(frame: frame)
        )
    }

    private func base64Encode(frame: SessionReplayFrame) -> String {
        frame.imageData.base64EncodedString()
    }

    private func unixMs(from date: Date) -> Int64 {
        Int64(date.timeIntervalSince1970 * 1000)
    }
}

public class SessionReplayExporter {
    internal let transport: SessionReplayTransport
    internal let projectId: String
    internal let userIdProvider: () -> String?

    /// Returns `nil` if `endpointBaseUrl` cannot be resolved to a valid session-capture URL (invalid or missing scheme).
    public init?(
        endpointBaseUrl: String,
        headers: [String: String],
        projectId: String,
        userIdProvider: @escaping () -> String? = { nil }
    ) {
        guard let transport = SessionReplayTransport(
            endpointBaseUrl: endpointBaseUrl,
            headers: headers
        ) else {
            return nil
        }
        self.transport = transport
        self.projectId = projectId
        self.userIdProvider = userIdProvider
    }
}
