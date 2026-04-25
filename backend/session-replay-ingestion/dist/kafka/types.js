"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.EventSchema = exports.SnapshotEventSchema = exports.MessageMetadataSchema = exports.RawEventMessageSchema = void 0;
const zod_1 = require("zod");
// Raw event message from Kafka (produced by the Rust capture service)
exports.RawEventMessageSchema = zod_1.z.object({
    user_id: zod_1.z.string(),
    data: zod_1.z.string(),
});
// Kafka message metadata
exports.MessageMetadataSchema = zod_1.z.object({
    partition: zod_1.z.number(),
    topic: zod_1.z.string(),
    rawSize: zod_1.z.number(),
    offset: zod_1.z.number(),
    timestamp: zod_1.z.number(),
});
// Snapshot event — we only require a timestamp, pass everything else through
exports.SnapshotEventSchema = zod_1.z
    .object({
    timestamp: zod_1.z.number(),
})
    .passthrough();
// Inner event structure (the JSON-parsed `data` field)
const EventPropertiesSchema = zod_1.z
    .object({
    snapshot_items: zod_1.z.array(zod_1.z.unknown()).optional(),
    session_id: zod_1.z.string().optional(),
    snapshot_source: zod_1.z.string().optional(),
    user_id: zod_1.z.string().optional(),
})
    .partial()
    .passthrough();
exports.EventSchema = zod_1.z.object({
    event: zod_1.z.string(),
    properties: EventPropertiesSchema,
});
//# sourceMappingURL=types.js.map