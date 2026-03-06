import { randomUUID } from 'crypto'

import { KafkaOffsetManager } from '../kafka/offset-manager'
import { ParsedMessageData } from '../kafka/types'
import { SessionBatchFileStorage } from './session-batch-file-storage'
import { SessionBlockMetadata } from './session-block-metadata'
import { SessionMetadataStore } from './session-metadata-store'
import { ZstdSessionRecorder } from './zstd-session-recorder'

/**
 * Manages a batch of session recordings grouped by Kafka partition and session.
 *
 * Structure:
 *   partitionSessions: Map<partition, Map<"projectId$sessionId", ZstdSessionRecorder>>
 *
 * Each ZstdSessionRecorder buffers events for a single session.
 * When flush() is called:
 *   1. Compress each session with zstd
 *   2. Write all blocks to a single S3 batch file (byte-range addressing)
 *   3. Publish block metadata to Kafka (-> ClickHouse)
 *   4. Commit Kafka consumer offsets
 *   5. Clear all in-memory state
 */
export class SessionBatchRecorder {
    private readonly partitionSessions = new Map<number, Map<string, ZstdSessionRecorder>>()
    private readonly partitionSizes = new Map<number, number>()
    private _size: number = 0
    private readonly batchId: string

    constructor(
        private readonly offsetManager: KafkaOffsetManager,
        private readonly storage: SessionBatchFileStorage,
        private readonly metadataStore: SessionMetadataStore
    ) {
        this.batchId = randomUUID()
        console.log(`[BatchRecorder] Created batch ${this.batchId}`)
    }

    get size(): number {
        return this._size
    }

    /**
     * Record a parsed message into the batch.
     * Events are grouped by partition -> projectId$sessionId.
     */
    public record(message: ParsedMessageData): void {
        const { partition } = message.metadata
        const sessionKey = `${message.project_id}$${message.session_id}`

        let sessions = this.partitionSessions.get(partition)
        if (!sessions) {
            sessions = new Map()
            this.partitionSessions.set(partition, sessions)
            this.partitionSizes.set(partition, 0)
        }

        let recorder = sessions.get(sessionKey)
        if (!recorder) {
            recorder = new ZstdSessionRecorder(message.session_id, message.project_id, this.batchId)
            sessions.set(sessionKey, recorder)
        }

        const bytesWritten = recorder.recordMessage(message)

        this._size += bytesWritten
        this.partitionSizes.set(partition, (this.partitionSizes.get(partition) || 0) + bytesWritten)

        this.offsetManager.trackOffset({
            partition: message.metadata.partition,
            offset: message.metadata.offset,
        })
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
    public async flush(): Promise<SessionBlockMetadata[]> {
        if (this.partitionSessions.size === 0) {
            await this.offsetManager.commit()
            console.log('[BatchRecorder] Flushed (no sessions)')
            return []
        }

        const allMetadata: SessionBlockMetadata[] = []
        const writer = this.storage.newBatch()

        for (const sessions of this.partitionSessions.values()) {
            for (const recorder of sessions.values()) {
                if (recorder.isEmpty) {
                    continue
                }

                const endResult = await recorder.end()
                const writeResult = await writer.writeSession({
                    buffer: endResult.buffer,
                    sessionId: recorder.sessionId,
                    projectId: recorder.projectId,
                })

                allMetadata.push({
                    sessionId: recorder.sessionId,
                    projectId: recorder.projectId,
                    userId: recorder.userId,
                    batchId: recorder.batchId,
                    startDateTime: endResult.startDateTime,
                    endDateTime: endResult.endDateTime,
                    blockUrl: writeResult.url,
                    snapshotSource: endResult.snapshotSource,
                })
            }
        }

        await writer.finish()
        console.log(`[BatchRecorder] S3 upload complete: ${allMetadata.length} blocks`)

        await this.metadataStore.storeSessionBlocks(allMetadata)

        this.offsetManager.commit()

        this.partitionSessions.clear()
        this.partitionSizes.clear()
        this._size = 0

        return allMetadata
    }

    /**
     * Discard all data for a revoked Kafka partition.
     * Called during consumer rebalancing to prevent committing offsets
     * for partitions we no longer own.
     */
    public discardPartition(partition: number): void {
        const partitionSize = this.partitionSizes.get(partition) || 0
        this._size -= partitionSize
        this.partitionSizes.delete(partition)
        this.partitionSessions.delete(partition)
        this.offsetManager.discardPartition(partition)
        console.log(`[BatchRecorder] Discarded partition ${partition} (freed ${partitionSize} bytes)`)
    }
}
