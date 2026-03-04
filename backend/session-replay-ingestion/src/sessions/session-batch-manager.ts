import { DateTime } from 'luxon'
import { Config } from '../config'
import { KafkaOffsetManager } from '../kafka/offset-manager'
import { ParsedMessageData } from '../kafka/types'
import { SessionBatchFileStorage } from './session-batch-file-storage'
import { SessionBatchRecorder } from './session-batch-recorder'
import { SessionMetadataStore } from './session-metadata-store'

/**
 * Coordinates session batch lifecycle:
 *   1. Accumulates parsed messages into the current batch
 *   2. Decides when to flush based on size and age thresholds
 *   3. Creates a new batch after each flush
 *
 * Thresholds:
 *   - maxBatchSizeBytes: 100MB default — triggers flush when exceeded
 *   - maxBatchAgeMs: 10s default — triggers flush when batch is too old
 *
 * The consumer calls shouldFlush() after each Kafka consume loop iteration
 * to check if it's time to flush. If true, it calls flush() which:
 *   - Completes the S3 upload
 *   - Publishes metadata to ClickHouse
 *   - Commits Kafka offsets
 *   - Resets the batch
 */
export class SessionBatchManager {
    private batch: SessionBatchRecorder

    constructor(
        private readonly config: Config,
        private readonly storage: SessionBatchFileStorage,
        private readonly metadataStore: SessionMetadataStore,
        private readonly offsetManager: KafkaOffsetManager
    ) {
        this.batch = new SessionBatchRecorder(storage, metadataStore, offsetManager)
    }

    /**
     * Record a parsed message into the current batch.
     */
    public record(message: ParsedMessageData): void {
        this.batch.record(message)
    }

    /**
     * Check if the current batch should be flushed.
     * Returns true if:
     *   - Batch size exceeds maxBatchSizeBytes (100MB)
     *   - Batch age exceeds maxBatchAgeMs (10s)
     */
    public shouldFlush(): boolean {
        if (this.batch.size === 0) {
            return false
        }

        if (this.batch.size >= this.config.maxBatchSizeBytes) {
            console.log(
                `[BatchManager] Size threshold reached: ${(this.batch.size / 1024 / 1024).toFixed(1)}MB`
            )
            return true
        }

        const ageMs = DateTime.utc().diff(this.batch.createdAt).milliseconds
        if (ageMs >= this.config.maxBatchAgeMs) {
            console.log(`[BatchManager] Age threshold reached: ${ageMs}ms`)
            return true
        }

        return false
    }

    /**
     * Flush the current batch and prepare a new one.
     * This is the critical section — no messages are consumed during flush.
     */
    public async flush(): Promise<void> {
        const sessionCount = this.batch.sessionCount
        const size = this.batch.size

        console.log(
            `[BatchManager] Flushing batch: ${sessionCount} sessions, ` +
                `${(size / 1024 / 1024).toFixed(1)}MB`
        )

        const startTime = Date.now()
        const blocks = await this.batch.flush()
        const elapsed = Date.now() - startTime

        console.log(
            `[BatchManager] Flush complete: ${blocks.length} blocks in ${elapsed}ms`
        )
    }

    /**
     * Discard data for a revoked partition (called during Kafka rebalancing).
     */
    public discardPartition(partition: number): void {
        this.batch.discardPartition(partition)
    }
}
