"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SessionBatchRecorder = void 0;
const crypto_1 = require("crypto");
const snappy_session_recorder_1 = require("./snappy-session-recorder");
/**
 * Manages a batch of session recordings grouped by Kafka partition and session.
 *
 * Structure:
 *   partitionSessions: Map<partition, Map<"projectId$sessionId", SnappySessionRecorder>>
 *
 * Each SnappySessionRecorder buffers events for a single session.
 * When flush() is called:
 *   1. Compress each session with Snappy
 *   2. Write all blocks to a single S3 batch file (byte-range addressing)
 *   3. Publish block metadata to Kafka (-> ClickHouse)
 *   4. Commit Kafka consumer offsets
 *   5. Clear all in-memory state
 */
class SessionBatchRecorder {
    constructor(offsetManager, storage, metadataStore) {
        this.offsetManager = offsetManager;
        this.storage = storage;
        this.metadataStore = metadataStore;
        this.partitionSessions = new Map();
        this.partitionSizes = new Map();
        this._size = 0;
        this.batchId = (0, crypto_1.randomUUID)();
        console.log(`[BatchRecorder] Created batch ${this.batchId}`);
    }
    get size() {
        return this._size;
    }
    /**
     * Record a parsed message into the batch.
     * Events are grouped by partition -> projectId$sessionId.
     */
    record(message) {
        const { partition } = message.metadata;
        const sessionKey = `${message.project_id}$${message.session_id}`;
        let sessions = this.partitionSessions.get(partition);
        if (!sessions) {
            sessions = new Map();
            this.partitionSessions.set(partition, sessions);
            this.partitionSizes.set(partition, 0);
        }
        let recorder = sessions.get(sessionKey);
        if (!recorder) {
            recorder = new snappy_session_recorder_1.SnappySessionRecorder(message.session_id, message.project_id, this.batchId);
            sessions.set(sessionKey, recorder);
        }
        const bytesWritten = recorder.recordMessage(message);
        this._size += bytesWritten;
        this.partitionSizes.set(partition, (this.partitionSizes.get(partition) || 0) + bytesWritten);
        this.offsetManager.trackOffset({
            partition: message.metadata.partition,
            offset: message.metadata.offset,
        });
    }
    /**
     * Flush the entire batch:
     *   1. Write all session blocks to S3
     *   2. Publish block metadata to Kafka (-> ClickHouse)
     *   3. Commit Kafka offsets
     *
     * If any step fails, the process crashes. On restart, Kafka replays
     * from the last committed offset (at-least-once delivery).
     */
    async flush() {
        if (this.partitionSessions.size === 0) {
            await this.offsetManager.commit();
            console.log("[BatchRecorder] Flushed (no sessions)");
            return [];
        }
        const allMetadata = [];
        // Group non-empty recorders by projectId so each project gets its own S3 file.
        const projectRecorders = new Map();
        for (const sessions of this.partitionSessions.values()) {
            for (const recorder of sessions.values()) {
                if (recorder.isEmpty)
                    continue;
                const list = projectRecorders.get(recorder.projectId) ?? [];
                list.push(recorder);
                projectRecorders.set(recorder.projectId, list);
            }
        }
        // One writer per project → path: {prefix}/{projectId}/{date}/{timestamp}-{suffix}
        const writers = new Map([...projectRecorders.keys()].map((projectId) => [
            projectId,
            this.storage.newBatch(projectId),
        ]));
        for (const [projectId, recorders] of projectRecorders) {
            const writer = writers.get(projectId);
            for (const recorder of recorders) {
                const endResult = await recorder.end();
                const writeResult = await writer.writeSession({
                    buffer: endResult.buffer,
                    sessionId: recorder.sessionId,
                    projectId: recorder.projectId,
                });
                allMetadata.push({
                    sessionId: recorder.sessionId,
                    projectId: recorder.projectId,
                    userId: recorder.userId,
                    batchId: recorder.batchId,
                    startDateTime: endResult.startDateTime,
                    endDateTime: endResult.endDateTime,
                    blockUrl: writeResult.url,
                    snapshotSource: endResult.snapshotSource,
                });
            }
        }
        await Promise.all([...writers.values()].map((w) => w.finish()));
        console.log(`[BatchRecorder] S3 upload complete: ${allMetadata.length} blocks`);
        await this.metadataStore.storeSessionBlocks(allMetadata);
        await this.offsetManager.commit();
        this.partitionSessions.clear();
        this.partitionSizes.clear();
        this._size = 0;
        return allMetadata;
    }
    /**
     * Discard all data for a revoked Kafka partition.
     * Called during consumer rebalancing to prevent committing offsets
     * for partitions we no longer own.
     */
    discardPartition(partition) {
        const partitionSize = this.partitionSizes.get(partition) || 0;
        this._size -= partitionSize;
        this.partitionSizes.delete(partition);
        this.partitionSessions.delete(partition);
        this.offsetManager.discardPartition(partition);
        console.log(`[BatchRecorder] Discarded partition ${partition} (freed ${partitionSize} bytes)`);
    }
}
exports.SessionBatchRecorder = SessionBatchRecorder;
//# sourceMappingURL=session-batch-recorder.js.map