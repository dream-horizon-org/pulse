import { DateTime } from "luxon";
import { compress } from "snappy";

import { ParsedMessageData } from "../kafka/types";

export interface EndResult {
  /** The complete snappy-compressed session block */
  buffer: Buffer;
  /** Number of events in the session block */
  eventCount: number;
  /** Timestamp of the first event in the session block */
  startDateTime: DateTime;
  /** Timestamp of the last event in the session block */
  endDateTime: DateTime;
  /** Source of the snapshot (web/mobile) */
  snapshotSource: string | null;
  /** ID of the batch this session belongs to */
  batchId: string;
}

/**
 * Records events for a single session recording using Snappy compression.
 *
 * Buffers events and provides them as a snappy-compressed session recording block that can be
 * stored in a session batch file. The session recording block can be read as an independent unit.
 *
 * ```
 * Session Batch File
 * ├── Snappy Session Recording Block 1 <── One SnappySessionRecorder corresponds to one block
 * │   └── JSONL Session Recording Block
 * │       ├── {"timestamp":..., "type":..., "data":{...}}
 * │       ├── {"timestamp":..., "type":..., "data":{...}}
 * │       └── ...
 * ├── Snappy Session Recording Block 2
 * │   └── ...
 * └── ...
 * ```
 *
 * The session block format (after decompression) is a sequence of newline-delimited JSON records.
 */
export class SnappySessionRecorder {
  private readonly uncompressedChunks: Buffer[] = [];
  private eventCount: number = 0;
  private ended = false;
  private startDateTime: DateTime | null = null;
  private endDateTime: DateTime | null = null;
  private _userId: string | null = null;
  private snapshotSource: string | null = null;

  constructor(
    public readonly sessionId: string,
    public readonly projectId: string,
    public readonly batchId: string,
  ) {}

  /**
   * Records a message containing events for this session.
   * Events are buffered until end() is called.
   *
   * @param message - Parsed message from Kafka
   * @returns Number of raw bytes written (before compression)
   * @throws If called after end()
   */
  public recordMessage(message: ParsedMessageData): number {
    if (this.ended) {
      throw new Error("Cannot record message after end() has been called");
    }

    if (!this._userId) {
      this._userId = message.user_id;
    }

    if (!this.snapshotSource) {
      this.snapshotSource = message.snapshot_source ?? "mobile";
    }

    if (!this.startDateTime || message.eventsRange.start < this.startDateTime) {
      this.startDateTime = message.eventsRange.start;
    }
    if (!this.endDateTime || message.eventsRange.end > this.endDateTime) {
      this.endDateTime = message.eventsRange.end;
    }

    let rawBytesWritten = 0;

    for (const event of message.events) {
      const line = JSON.stringify(event) + "\n";
      const chunk = Buffer.from(line);
      this.uncompressedChunks.push(chunk);
      this.eventCount++;
      rawBytesWritten += chunk.length;
    }

    return rawBytesWritten;
  }

  /**
   * The user_id associated with this session recording.
   */
  public get userId(): string {
    if (!this._userId) {
      throw new Error("No user_id set. No messages recorded yet.");
    }
    return this._userId;
  }

  public get isEmpty(): boolean {
    return this.eventCount === 0;
  }

  /**
   * Finalizes the session recording and returns the compressed buffer with metadata.
   * Uses Snappy compression (native, no WASM memory limits).
   *
   * @returns The compressed session recording block with metadata
   * @throws If called more than once
   */
  public async end(): Promise<EndResult> {
    if (this.ended) {
      throw new Error("end() has already been called");
    }
    this.ended = true;

    const uncompressedBuffer = Buffer.concat(this.uncompressedChunks);
    // Snappy compression uses native bindings (no WASM limits) - can handle large buffers
    const compressed = await compress(uncompressedBuffer);

    return {
      buffer: compressed,
      eventCount: this.eventCount,
      startDateTime: this.startDateTime ?? DateTime.fromMillis(0),
      endDateTime: this.endDateTime ?? DateTime.fromMillis(0),
      snapshotSource: this.snapshotSource,
      batchId: this.batchId,
    };
  }
}
