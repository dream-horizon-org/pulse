import { DateTime } from 'luxon'
import { z } from 'zod'

// Raw event message from Kafka (produced by the Rust capture service)
export const RawEventMessageSchema = z.object({
    user_id: z.string(),
    data: z.string(),
})

export type RawEventMessage = z.infer<typeof RawEventMessageSchema>

// Kafka message metadata
export const MessageMetadataSchema = z.object({
    partition: z.number(),
    topic: z.string(),
    rawSize: z.number(),
    offset: z.number(),
    timestamp: z.number(),
})

export type MessageMetadata = z.infer<typeof MessageMetadataSchema>

// Snapshot event — we only require a timestamp, pass everything else through
export const SnapshotEventSchema = z
    .object({
        timestamp: z.number(),
    })
    .passthrough()

// Inner event structure (the JSON-parsed `data` field)
const EventPropertiesSchema = z
    .object({
        snapshot_items: z.array(z.unknown()).optional(),
        session_id: z.string().optional(),
        snapshot_source: z.string().optional(),
        lib: z.string().optional(),
        user_id: z.string().optional(),
    })
    .partial()
    .passthrough()

export const EventSchema = z.object({
    event: z.string(),
    properties: EventPropertiesSchema,
})

// Fully parsed message ready for recording
export interface ParsedMessageData {
    user_id: string
    session_id: string
    project_id: string
    events: SnapshotEvent[]
    eventsRange: { start: DateTime; end: DateTime }
    snapshot_source: string | null
    snapshot_library: string | null
    metadata: MessageMetadata
}

export type SnapshotEvent = z.infer<typeof SnapshotEventSchema>
