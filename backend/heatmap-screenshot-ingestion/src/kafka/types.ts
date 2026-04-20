/**
 * Mirror of `backend/session-replay-ingestion/src/kafka/types.ts`.
 * Keep in sync when the session-capture Kafka contract changes.
 */
import { DateTime } from "luxon";
import { z } from "zod";

export const RawEventMessageSchema = z.object({
  user_id: z.string(),
  data: z.string(),
});

export type RawEventMessage = z.infer<typeof RawEventMessageSchema>;

export const MessageMetadataSchema = z.object({
  partition: z.number(),
  topic: z.string(),
  rawSize: z.number(),
  offset: z.number(),
  timestamp: z.number(),
});

export type MessageMetadata = z.infer<typeof MessageMetadataSchema>;

export const SnapshotEventSchema = z
  .object({
    timestamp: z.number(),
  })
  .passthrough();

const EventPropertiesSchema = z
  .object({
    snapshot_items: z.array(z.unknown()).optional(),
    session_id: z.string().optional(),
    snapshot_source: z.string().optional(),
    user_id: z.string().optional(),
    app_version: z.string().optional(),
  })
  .partial()
  .passthrough();

export const EventSchema = z.object({
  event: z.string(),
  properties: EventPropertiesSchema,
});

export interface ParsedMessageData {
  user_id: string;
  session_id: string;
  project_id: string;
  app_version: string | null;
  events: SnapshotEvent[];
  eventsRange: { start: DateTime; end: DateTime };
  snapshot_source: string | null;
  metadata: MessageMetadata;
}

export type SnapshotEvent = z.infer<typeof SnapshotEventSchema>;
