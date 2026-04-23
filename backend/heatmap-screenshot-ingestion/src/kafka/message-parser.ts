/**
 * Mirror of `backend/session-replay-ingestion/src/kafka/message-parser.ts`.
 * Keep in sync when gzip / envelope / validation rules change.
 */
import { DateTime } from "luxon";
import { promisify } from "node:util";
import { gunzip } from "zlib";

import {
  EventSchema,
  ParsedMessageData,
  RawEventMessageSchema,
  SnapshotEvent,
  SnapshotEventSchema,
} from "./types";

const MESSAGE_TIMESTAMP_DIFF_THRESHOLD_DAYS = 7;
const GZIP_HEADER = Uint8Array.from([0x1f, 0x8b, 0x08, 0x00]);
const decompressWithGzip = promisify(gunzip);

export interface RawKafkaMessage {
  value: Buffer | null;
  timestamp: number;
  partition: number;
  topic: string;
  size: number;
  offset: number;
  headers?: Array<{ [key: string]: Buffer }>;
}

function getValidEvents(
  events: unknown[],
): {
  validEvents: SnapshotEvent[];
  startDateTime: DateTime;
  endDateTime: DateTime;
} | null {
  const eventsWithDates = events
    .map((event) => {
      const parseResult = SnapshotEventSchema.safeParse(event);
      if (!parseResult.success || parseResult.data.timestamp <= 0) {
        return null;
      }
      return {
        event: parseResult.data,
        dateTime: DateTime.fromMillis(parseResult.data.timestamp),
      };
    })
    .filter((x): x is { event: SnapshotEvent; dateTime: DateTime } => x !== null)
    .filter(({ dateTime }) => dateTime.isValid);

  if (!eventsWithDates.length) {
    return null;
  }

  let startDateTime = eventsWithDates[0].dateTime;
  let endDateTime = eventsWithDates[0].dateTime;
  for (const { dateTime } of eventsWithDates) {
    if (dateTime < startDateTime) startDateTime = dateTime;
    if (dateTime > endDateTime) endDateTime = dateTime;
  }

  return {
    validEvents: eventsWithDates.map(({ event }) => event),
    startDateTime,
    endDateTime,
  };
}

export class KafkaMessageParser {
  private readonly dropCounts = new Map<string, number>();

  public async parseMessage(
    message: RawKafkaMessage,
  ): Promise<ParsedMessageData | null> {
    if (!message.value || !message.timestamp) {
      return this.drop("missing value or timestamp", message);
    }

    let messageBytes = message.value;
    try {
      if (this.isGzipped(message.value)) {
        messageBytes = await decompressWithGzip(message.value);
      }
    } catch {
      return this.drop("invalid gzip", message);
    }

    let rawPayload: unknown;
    try {
      rawPayload = JSON.parse(messageBytes.toString());
    } catch {
      return this.drop("invalid JSON", message);
    }

    const messageResult = RawEventMessageSchema.safeParse(rawPayload);
    if (!messageResult.success) {
      return this.drop("invalid message schema", message);
    }

    let eventData: unknown;
    try {
      eventData = JSON.parse(messageResult.data.data);
    } catch {
      return this.drop("invalid inner JSON", message);
    }

    const eventResult = EventSchema.safeParse(eventData);
    if (!eventResult.success) {
      return this.drop("invalid event schema", message);
    }

    const { snapshot_items, session_id, snapshot_source, app_version } =
      eventResult.data.properties;

    if (eventResult.data.event !== "snapshot_items" || !snapshot_items || !session_id) {
      return this.drop("not a snapshot event", message);
    }

    const result = getValidEvents(snapshot_items);
    if (!result) {
      return this.drop("no valid events", message);
    }

    const { validEvents, startDateTime, endDateTime } = result;

    const startDiff = Math.abs(startDateTime.diffNow("day").days);
    const endDiff = Math.abs(endDateTime.diffNow("day").days);
    if (
      startDiff >= MESSAGE_TIMESTAMP_DIFF_THRESHOLD_DAYS ||
      endDiff >= MESSAGE_TIMESTAMP_DIFF_THRESHOLD_DAYS
    ) {
      return this.drop("timestamp too far from now", message);
    }

    const projectIdHeader = message.headers?.find((h) => h["project_id"]);
    const projectId = projectIdHeader
      ? projectIdHeader["project_id"].toString()
      : "unknown";

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
      app_version:
        typeof app_version === "string" && app_version.length > 0
          ? app_version
          : null,
      events: validEvents,
      eventsRange: { start: startDateTime, end: endDateTime },
      snapshot_source: snapshot_source ?? null,
      metadata,
    };
  }

  private drop(reason: string, message: RawKafkaMessage): null {
    const count = (this.dropCounts.get(reason) || 0) + 1;
    this.dropCounts.set(reason, count);

    console.warn(
      `[HeatmapMessageParser] Dropping message: ${reason} (total=${count})`,
      `partition=${message.partition} offset=${message.offset}`,
    );
    return null;
  }

  private isGzipped(buffer: Buffer): boolean {
    return buffer.subarray(0, GZIP_HEADER.length).equals(Buffer.from(GZIP_HEADER));
  }
}
