"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.KafkaMessageParser = void 0;
const luxon_1 = require("luxon");
const node_util_1 = require("node:util");
const zlib_1 = require("zlib");
const types_1 = require("./types");
const MESSAGE_TIMESTAMP_DIFF_THRESHOLD_DAYS = 7;
const GZIP_HEADER = Uint8Array.from([0x1f, 0x8b, 0x08, 0x00]);
const decompressWithGzip = (0, node_util_1.promisify)(zlib_1.gunzip);
function getValidEvents(events) {
    const eventsWithDates = events
        .map((event) => {
        const parseResult = types_1.SnapshotEventSchema.safeParse(event);
        if (!parseResult.success || parseResult.data.timestamp <= 0) {
            return null;
        }
        return {
            event: parseResult.data,
            dateTime: luxon_1.DateTime.fromMillis(parseResult.data.timestamp),
        };
    })
        .filter((x) => x !== null)
        .filter(({ dateTime }) => dateTime.isValid);
    if (!eventsWithDates.length) {
        return null;
    }
    let startDateTime = eventsWithDates[0].dateTime;
    let endDateTime = eventsWithDates[0].dateTime;
    for (const { dateTime } of eventsWithDates) {
        if (dateTime < startDateTime)
            startDateTime = dateTime;
        if (dateTime > endDateTime)
            endDateTime = dateTime;
    }
    return {
        validEvents: eventsWithDates.map(({ event }) => event),
        startDateTime,
        endDateTime,
    };
}
/**
 * Parses raw Kafka messages into structured ParsedMessageData.
 * - Decompresses gzip if needed
 * - Validates JSON structure
 * - Extracts session_id, snapshot_items, user_id
 * - Reads project_id from Kafka headers
 * - Filters out events with invalid timestamps
 */
class KafkaMessageParser {
    constructor() {
        this.dropCounts = new Map();
    }
    async parseBatch(messages) {
        const parsed = await Promise.all(messages.map((m) => this.parseMessage(m)));
        return parsed.filter((msg) => msg !== null);
    }
    async parseMessage(message) {
        if (!message.value || !message.timestamp) {
            return this.drop('missing value or timestamp', message);
        }
        // Decompress gzip if needed
        let messageBytes = message.value;
        try {
            if (this.isGzipped(message.value)) {
                messageBytes = await decompressWithGzip(message.value);
            }
        }
        catch {
            return this.drop('invalid gzip', message);
        }
        // Parse outer JSON
        let rawPayload;
        try {
            rawPayload = JSON.parse(messageBytes.toString());
        }
        catch {
            return this.drop('invalid JSON', message);
        }
        const messageResult = types_1.RawEventMessageSchema.safeParse(rawPayload);
        if (!messageResult.success) {
            return this.drop('invalid message schema', message);
        }
        // Parse inner `data` field (JSON string)
        let eventData;
        try {
            eventData = JSON.parse(messageResult.data.data);
        }
        catch {
            return this.drop('invalid inner JSON', message);
        }
        const eventResult = types_1.EventSchema.safeParse(eventData);
        if (!eventResult.success) {
            return this.drop('invalid event schema', message);
        }
        const { snapshot_items, session_id, snapshot_source } = eventResult.data.properties;
        if (eventResult.data.event !== 'snapshot_items' || !snapshot_items || !session_id) {
            return this.drop('not a snapshot event', message);
        }
        // Validate event timestamps
        const result = getValidEvents(snapshot_items);
        if (!result) {
            return this.drop('no valid events', message);
        }
        const { validEvents, startDateTime, endDateTime } = result;
        // Reject events with timestamps too far from now
        const startDiff = Math.abs(startDateTime.diffNow('day').days);
        const endDiff = Math.abs(endDateTime.diffNow('day').days);
        if (startDiff >= MESSAGE_TIMESTAMP_DIFF_THRESHOLD_DAYS ||
            endDiff >= MESSAGE_TIMESTAMP_DIFF_THRESHOLD_DAYS) {
            return this.drop('timestamp too far from now', message);
        }
        // Extract project_id from Kafka headers
        const projectIdHeader = message.headers?.find((h) => h['project_id']);
        const projectId = projectIdHeader
            ? projectIdHeader['project_id'].toString()
            : 'unknown';
        const metadata = {
            partition: message.partition,
            topic: message.topic,
            rawSize: message.size,
            offset: message.offset,
            timestamp: message.timestamp,
        };
        return {
            user_id: messageResult.data.user_id,
            session_id,
            project_id: projectId,
            events: validEvents,
            eventsRange: { start: startDateTime, end: endDateTime },
            snapshot_source: snapshot_source ?? null,
            metadata,
        };
    }
    drop(reason, message) {
        const count = (this.dropCounts.get(reason) || 0) + 1;
        this.dropCounts.set(reason, count);
        console.warn(`[MessageParser] Dropping message: ${reason} (total=${count})`, `partition=${message.partition} offset=${message.offset}`);
        return null;
    }
    /**
     * Returns and resets the drop counters. Useful for periodic logging.
     */
    getAndResetDropCounts() {
        const counts = new Map(this.dropCounts);
        this.dropCounts.clear();
        return counts;
    }
    isGzipped(buffer) {
        return buffer.subarray(0, GZIP_HEADER.length).equals(Buffer.from(GZIP_HEADER));
    }
}
exports.KafkaMessageParser = KafkaMessageParser;
//# sourceMappingURL=message-parser.js.map