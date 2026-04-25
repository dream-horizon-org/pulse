import { KafkaOffsetManager } from '../kafka/offset-manager';
import { SessionBatchFileStorage } from './session-batch-file-storage';
import { SessionBatchRecorder } from './session-batch-recorder';
import { SessionMetadataStore } from './session-metadata-store';
export interface SessionBatchManagerConfig {
    /** Maximum raw size (before compression) of a batch in bytes before it should be flushed */
    maxBatchSizeBytes: number;
    /** Maximum age of a batch in milliseconds before it should be flushed */
    maxBatchAgeMs: number;
    /** Manages Kafka offset tracking and commits */
    offsetManager: KafkaOffsetManager;
    /** Handles writing session batch files to storage */
    fileStorage: SessionBatchFileStorage;
    /** Manages storing session metadata */
    metadataStore: SessionMetadataStore;
}
/**
 * Coordinates the creation and flushing of session batches.
 *
 * The manager ensures there is always one active batch for recording events.
 * It handles:
 * - Providing the current batch to the consumer via getCurrentBatch()
 * - Replacing flushed batches with new ones
 * - Providing hints for when to flush the current batch
 *
 * Each flush creates a new session batch file:
 * ```
 * Session Batch File 1 (flushed)
 * ├── Compressed Session Recording Block 1
 * │   └── JSONL Session Recording Block
 * └── ...
 *
 * Session Batch File 2 (current, returned to consumer)
 * ├── Compressed Session Recording Block 1
 * │   └── JSONL Session Recording Block (still recording)
 * └── ...
 * ```
 */
export declare class SessionBatchManager {
    private currentBatch;
    private readonly maxBatchSizeBytes;
    private readonly maxBatchAgeMs;
    private readonly offsetManager;
    private readonly fileStorage;
    private readonly metadataStore;
    private lastFlushTime;
    constructor(config: SessionBatchManagerConfig);
    /**
     * Returns the current batch for the consumer to record into.
     */
    getCurrentBatch(): SessionBatchRecorder;
    /**
     * Flushes the current batch and replaces it with a new one.
     */
    flush(): Promise<void>;
    /**
     * Checks if the current batch should be flushed based on:
     * - Size of the batch exceeding maxBatchSizeBytes
     * - Age of the batch exceeding maxBatchAgeMs
     */
    shouldFlush(): boolean;
    /**
     * Discard data for revoked partitions (called during Kafka rebalancing).
     */
    discardPartitions(partitions: number[]): void;
}
