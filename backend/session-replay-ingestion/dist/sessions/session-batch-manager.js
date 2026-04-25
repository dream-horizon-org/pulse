"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SessionBatchManager = void 0;
const session_batch_recorder_1 = require("./session-batch-recorder");
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
class SessionBatchManager {
    constructor(config) {
        this.maxBatchSizeBytes = config.maxBatchSizeBytes;
        this.maxBatchAgeMs = config.maxBatchAgeMs;
        this.offsetManager = config.offsetManager;
        this.fileStorage = config.fileStorage;
        this.metadataStore = config.metadataStore;
        this.currentBatch = new session_batch_recorder_1.SessionBatchRecorder(this.offsetManager, this.fileStorage, this.metadataStore);
        this.lastFlushTime = Date.now();
    }
    /**
     * Returns the current batch for the consumer to record into.
     */
    getCurrentBatch() {
        return this.currentBatch;
    }
    /**
     * Flushes the current batch and replaces it with a new one.
     */
    async flush() {
        const size = this.currentBatch.size;
        console.log(`[BatchManager] Flushing batch: ${(size / 1024 / 1024).toFixed(1)}MB`);
        const startTime = Date.now();
        const blocks = await this.currentBatch.flush();
        const elapsed = Date.now() - startTime;
        console.log(`[BatchManager] Flush complete: ${blocks.length} blocks in ${elapsed}ms`);
        this.currentBatch = new session_batch_recorder_1.SessionBatchRecorder(this.offsetManager, this.fileStorage, this.metadataStore);
        this.lastFlushTime = Date.now();
    }
    /**
     * Checks if the current batch should be flushed based on:
     * - Size of the batch exceeding maxBatchSizeBytes
     * - Age of the batch exceeding maxBatchAgeMs
     */
    shouldFlush() {
        const batchSize = this.currentBatch.size;
        if (batchSize === 0) {
            return false;
        }
        if (batchSize >= this.maxBatchSizeBytes) {
            console.log(`[BatchManager] Size threshold reached: ${(batchSize / 1024 / 1024).toFixed(1)}MB`);
            return true;
        }
        const batchAge = Date.now() - this.lastFlushTime;
        if (batchAge >= this.maxBatchAgeMs) {
            console.log(`[BatchManager] Age threshold reached: ${batchAge}ms`);
            return true;
        }
        return false;
    }
    /**
     * Discard data for revoked partitions (called during Kafka rebalancing).
     */
    discardPartitions(partitions) {
        console.log(`[BatchManager] Discarding partitions: ${partitions.join(', ')}`);
        for (const partition of partitions) {
            this.currentBatch.discardPartition(partition);
        }
    }
}
exports.SessionBatchManager = SessionBatchManager;
//# sourceMappingURL=session-batch-manager.js.map