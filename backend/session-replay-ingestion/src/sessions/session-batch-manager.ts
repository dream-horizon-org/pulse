import { KafkaOffsetManager } from '../kafka/offset-manager'
import { SessionBatchFileStorage } from './session-batch-file-storage'
import { SessionBatchRecorder } from './session-batch-recorder'
import { SessionMetadataStore } from './session-metadata-store'

export interface SessionBatchManagerConfig {
    /** Maximum raw size (before compression) of a batch in bytes before it should be flushed */
    maxBatchSizeBytes: number
    /** Maximum age of a batch in milliseconds before it should be flushed */
    maxBatchAgeMs: number
    /** Manages Kafka offset tracking and commits */
    offsetManager: KafkaOffsetManager
    /** Handles writing session batch files to storage */
    fileStorage: SessionBatchFileStorage
    /** Manages storing session metadata */
    metadataStore: SessionMetadataStore
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
export class SessionBatchManager {
    private currentBatch: SessionBatchRecorder
    private readonly maxBatchSizeBytes: number
    private readonly maxBatchAgeMs: number
    private readonly offsetManager: KafkaOffsetManager
    private readonly fileStorage: SessionBatchFileStorage
    private readonly metadataStore: SessionMetadataStore
    private lastFlushTime: number

    constructor(config: SessionBatchManagerConfig) {
        this.maxBatchSizeBytes = config.maxBatchSizeBytes
        this.maxBatchAgeMs = config.maxBatchAgeMs
        this.offsetManager = config.offsetManager
        this.fileStorage = config.fileStorage
        this.metadataStore = config.metadataStore

        this.currentBatch = new SessionBatchRecorder(
            this.offsetManager,
            this.fileStorage,
            this.metadataStore
        )
        this.lastFlushTime = Date.now()
    }

    /**
     * Returns the current batch for the consumer to record into.
     */
    public getCurrentBatch(): SessionBatchRecorder {
        return this.currentBatch
    }

    /**
     * Flushes the current batch and replaces it with a new one.
     */
    public async flush(): Promise<void> {
        const size = this.currentBatch.size

        console.log(`[BatchManager] Flushing batch: ${(size / 1024 / 1024).toFixed(1)}MB`)

        const startTime = Date.now()
        const blocks = await this.currentBatch.flush()
        const elapsed = Date.now() - startTime

        console.log(`[BatchManager] Flush complete: ${blocks.length} blocks in ${elapsed}ms`)

        this.currentBatch = new SessionBatchRecorder(
            this.offsetManager,
            this.fileStorage,
            this.metadataStore
        )
        this.lastFlushTime = Date.now()
    }

    /**
     * Checks if the current batch should be flushed based on:
     * - Size of the batch exceeding maxBatchSizeBytes
     * - Age of the batch exceeding maxBatchAgeMs
     */
    public shouldFlush(): boolean {
        const batchSize = this.currentBatch.size
        if (batchSize === 0) {
            return false
        }

        if (batchSize >= this.maxBatchSizeBytes) {
            console.log(
                `[BatchManager] Size threshold reached: ${(batchSize / 1024 / 1024).toFixed(1)}MB`
            )
            return true
        }

        const batchAge = Date.now() - this.lastFlushTime
        if (batchAge >= this.maxBatchAgeMs) {
            console.log(`[BatchManager] Age threshold reached: ${batchAge}ms`)
            return true
        }

        return false
    }

    /**
     * Discard data for revoked partitions (called during Kafka rebalancing).
     */
    public discardPartitions(partitions: number[]): void {
        console.log(`[BatchManager] Discarding partitions: ${partitions.join(', ')}`)
        for (const partition of partitions) {
            this.currentBatch.discardPartition(partition)
        }
    }
}
