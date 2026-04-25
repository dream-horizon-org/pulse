import { DateTime } from 'luxon';
import { z } from 'zod';
export declare const RawEventMessageSchema: z.ZodObject<{
    user_id: z.ZodString;
    data: z.ZodString;
}, "strip", z.ZodTypeAny, {
    user_id: string;
    data: string;
}, {
    user_id: string;
    data: string;
}>;
export type RawEventMessage = z.infer<typeof RawEventMessageSchema>;
export declare const MessageMetadataSchema: z.ZodObject<{
    partition: z.ZodNumber;
    topic: z.ZodString;
    rawSize: z.ZodNumber;
    offset: z.ZodNumber;
    timestamp: z.ZodNumber;
}, "strip", z.ZodTypeAny, {
    partition: number;
    topic: string;
    rawSize: number;
    offset: number;
    timestamp: number;
}, {
    partition: number;
    topic: string;
    rawSize: number;
    offset: number;
    timestamp: number;
}>;
export type MessageMetadata = z.infer<typeof MessageMetadataSchema>;
export declare const SnapshotEventSchema: z.ZodObject<{
    timestamp: z.ZodNumber;
}, "passthrough", z.ZodTypeAny, z.objectOutputType<{
    timestamp: z.ZodNumber;
}, z.ZodTypeAny, "passthrough">, z.objectInputType<{
    timestamp: z.ZodNumber;
}, z.ZodTypeAny, "passthrough">>;
export declare const EventSchema: z.ZodObject<{
    event: z.ZodString;
    properties: z.ZodObject<{
        snapshot_items: z.ZodOptional<z.ZodOptional<z.ZodArray<z.ZodUnknown, "many">>>;
        session_id: z.ZodOptional<z.ZodOptional<z.ZodString>>;
        snapshot_source: z.ZodOptional<z.ZodOptional<z.ZodString>>;
        user_id: z.ZodOptional<z.ZodOptional<z.ZodString>>;
    }, "passthrough", z.ZodTypeAny, z.objectOutputType<{
        snapshot_items: z.ZodOptional<z.ZodOptional<z.ZodArray<z.ZodUnknown, "many">>>;
        session_id: z.ZodOptional<z.ZodOptional<z.ZodString>>;
        snapshot_source: z.ZodOptional<z.ZodOptional<z.ZodString>>;
        user_id: z.ZodOptional<z.ZodOptional<z.ZodString>>;
    }, z.ZodTypeAny, "passthrough">, z.objectInputType<{
        snapshot_items: z.ZodOptional<z.ZodOptional<z.ZodArray<z.ZodUnknown, "many">>>;
        session_id: z.ZodOptional<z.ZodOptional<z.ZodString>>;
        snapshot_source: z.ZodOptional<z.ZodOptional<z.ZodString>>;
        user_id: z.ZodOptional<z.ZodOptional<z.ZodString>>;
    }, z.ZodTypeAny, "passthrough">>;
}, "strip", z.ZodTypeAny, {
    event: string;
    properties: {
        user_id?: string | undefined;
        snapshot_items?: unknown[] | undefined;
        session_id?: string | undefined;
        snapshot_source?: string | undefined;
    } & {
        [k: string]: unknown;
    };
}, {
    event: string;
    properties: {
        user_id?: string | undefined;
        snapshot_items?: unknown[] | undefined;
        session_id?: string | undefined;
        snapshot_source?: string | undefined;
    } & {
        [k: string]: unknown;
    };
}>;
export interface ParsedMessageData {
    user_id: string;
    session_id: string;
    project_id: string;
    events: SnapshotEvent[];
    eventsRange: {
        start: DateTime;
        end: DateTime;
    };
    snapshot_source: string | null;
    metadata: MessageMetadata;
}
export type SnapshotEvent = z.infer<typeof SnapshotEventSchema>;
