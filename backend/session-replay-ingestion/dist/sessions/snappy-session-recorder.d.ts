import { DateTime } from "luxon";
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
export declare class SnappySessionRecorder {
    readonly sessionId: string;
    readonly projectId: string;
    readonly batchId: string;
    private readonly uncompressedChunks;
    private eventCount;
    private ended;
    private startDateTime;
    private endDateTime;
    private _userId;
    private snapshotSource;
    constructor(sessionId: string, projectId: string, batchId: string);
    /**
     * Records a message containing events for this session.
     * Events are buffered until end() is called.
     *
     * @param message - Parsed message from Kafka
     * @returns Number of raw bytes written (before compression)
     * @throws If called after end()
     */
    recordMessage(message: ParsedMessageData): number;
    /**
     * The user_id associated with this session recording.
     */
    get userId(): string;
    get isEmpty(): boolean;
    /**
     * Finalizes the session recording and returns the compressed buffer with metadata.
     * Uses Snappy compression (native, no WASM memory limits).
     *
     * @returns The compressed session recording block with metadata
     * @throws If called more than once
     */
    end(): Promise<EndResult>;
}
