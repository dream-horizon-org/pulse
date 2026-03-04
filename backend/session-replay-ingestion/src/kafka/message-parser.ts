import { DateTime } from 'luxon'
import { promisify } from 'node:util'
import { gunzip } from 'zlib'

import {
    EventSchema,
    MessageMetadata,
    ParsedMessageData,
    RawEventMessageSchema,
    SnapshotEvent,
    SnapshotEventSchema,
} from './types'

const MESSAGE_TIMESTAMP_DIFF_THRESHOLD_DAYS = 7
const GZIP_HEADER = Uint8Array.from([0x1f, 0x8b, 0x08, 0x00])
const decompressWithGzip = promisify(gunzip)

interface RawKafkaMessage {
    value: Buffer | null
    timestamp: number
    partition: number
    topic: string
    size: number
    offset: number
    headers?: Array<{ [key: string]: Buffer }>
}

function getValidEvents(
    events: unknown[]
): { validEvents: SnapshotEvent[]; startDateTime: DateTime; endDateTime: DateTime } | null {
    const eventsWithDates = events
        .map((event) => {
            const parseResult = SnapshotEventSchema.safeParse(event)
            if (!parseResult.success || parseResult.data.timestamp <= 0) {
                return null
            }
            return {
                event: parseResult.data,
                dateTime: DateTime.fromMillis(parseResult.data.timestamp),
            }
        })
        .filter((x): x is { event: SnapshotEvent; dateTime: DateTime } => x !== null)
        .filter(({ dateTime }) => dateTime.isValid)

    if (!eventsWithDates.length) {
        return null
    }

    let startDateTime = eventsWithDates[0].dateTime
    let endDateTime = eventsWithDates[0].dateTime
    for (const { dateTime } of eventsWithDates) {
        if (dateTime < startDateTime) startDateTime = dateTime
        if (dateTime > endDateTime) endDateTime = dateTime
    }

    return {
        validEvents: eventsWithDates.map(({ event }) => event),
        startDateTime,
        endDateTime,
    }
}

/**
 * Parses raw Kafka messages into structured ParsedMessageData.
 * - Decompresses gzip if needed
 * - Validates JSON structure
 * - Extracts session_id, snapshot_items, user_id
 * - Reads project_id from Kafka headers
 * - Filters out events with invalid timestamps
 */
export class KafkaMessageParser {
    public async parseBatch(messages: RawKafkaMessage[]): Promise<ParsedMessageData[]> {
        const parsed = await Promise.all(messages.map((m) => this.parseMessage(m)))
        return parsed.filter((msg): msg is ParsedMessageData => msg !== null)
    }

    private async parseMessage(message: RawKafkaMessage): Promise<ParsedMessageData | null> {
        if (!message.value || !message.timestamp) {
            return this.drop('missing value or timestamp', message)
        }

        // Decompress gzip if needed
        let messageBytes = message.value
        try {
            if (this.isGzipped(message.value)) {
                messageBytes = await decompressWithGzip(message.value as any)
            }
        } catch {
            return this.drop('invalid gzip', message)
        }

        // Parse outer JSON
        let rawPayload: unknown
        try {
            rawPayload = JSON.parse(messageBytes.toString())
        } catch {
            return this.drop('invalid JSON', message)
        }

        const messageResult = RawEventMessageSchema.safeParse(rawPayload)
        if (!messageResult.success) {
            return this.drop('invalid message schema', message)
        }

        // Parse inner `data` field (JSON string)
        let eventData: unknown
        try {
            eventData = JSON.parse(messageResult.data.data)
        } catch {
            return this.drop('invalid inner JSON', message)
        }

        const eventResult = EventSchema.safeParse(eventData)
        if (!eventResult.success) {
            return this.drop('invalid event schema', message)
        }

        const { snapshot_items, session_id, snapshot_source } = eventResult.data.properties

        if (eventResult.data.event !== 'snapshot_items' || !snapshot_items || !session_id) {
            return this.drop('not a snapshot event', message)
        }

        // Validate event timestamps
        const result = getValidEvents(snapshot_items)
        if (!result) {
            return this.drop('no valid events', message)
        }

        const { validEvents, startDateTime, endDateTime } = result

        // Reject events with timestamps too far from now
        const startDiff = Math.abs(startDateTime.diffNow('day').days)
        const endDiff = Math.abs(endDateTime.diffNow('day').days)
        if (
            startDiff >= MESSAGE_TIMESTAMP_DIFF_THRESHOLD_DAYS ||
            endDiff >= MESSAGE_TIMESTAMP_DIFF_THRESHOLD_DAYS
        ) {
            return this.drop('timestamp too far from now', message)
        }

        // Extract project_id from Kafka headers
        const projectIdHeader = message.headers?.find((h) => h['project_id'])
        const projectId = projectIdHeader
            ? projectIdHeader['project_id'].toString()
            : 'unknown'

        const metadata: MessageMetadata = {
            partition: message.partition,
            topic: message.topic,
            rawSize: message.size,
            offset: message.offset,
            timestamp: message.timestamp,
        }

        return {
            user_id: messageResult.data.user_id,
            session_id,
            project_id: projectId,
            events: validEvents,
            eventsRange: { start: startDateTime, end: endDateTime },
            snapshot_source: snapshot_source ?? null,
            metadata,
        }
    }

    private drop(reason: string, message: RawKafkaMessage): null {
        console.warn(
            `[MessageParser] Dropping message: ${reason}`,
            `partition=${message.partition} offset=${message.offset}`
        )
        return null
    }

    private isGzipped(buffer: Buffer): boolean {
        return buffer.subarray(0, GZIP_HEADER.length).equals(Buffer.from(GZIP_HEADER))
    }
}
