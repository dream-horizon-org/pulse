import { DateTime } from 'luxon'

import { KafkaOffsetManager } from '../kafka/offset-manager'
import { ParsedMessageData } from '../kafka/types'
import { SessionBatchFileStorage } from './session-batch-file-storage'
import { SessionBlockMetadata } from './session-block-metadata'
import { SessionMetadataStore } from './session-metadata-store'
import { SnappySessionRecorder } from './snappy-session-recorder'

/**
 * Manages a batch of session recordings grouped by Kafka partition and session.
 *
 * Structure:
 *   partitionSessions: Map<partition, Map<"projectId$sessionId", SnappySessionRecorder>>
 *
 * Each SnappySessionRecorder buffers events for a single session.
 * When flush() is called:
 *   1. Compress each session → Snappy block
 *   2. Write all blocks to a single S3 batch file (byte-range addressing)
 *   3. Publish block metadata to Kafka (→ ClickHouse)
 *   4. Commit Kafka consumer offsets
 *   5. Clear all in-memory state
 */
export class SessionBatchRecorder {
    private partitionSessions: Map<number, Map<string, SessionInfo>> = new Map()
    private partitionSizes: Map<number, number> = new Map()
    private _size: number = 0
    private _createdAt: DateTime = DateTime.utc()

    constructor(
        private readonly storage: SessionBatchFileStorage,
        private readonly metadataStore: SessionMetadataStore,
        private readonly offsetManager: KafkaOffsetManager
    ) {}

    get size(): number {
        return this._size
    }

    get createdAt(): DateTime {
        return this._createdAt
    }

    get sessionCount(): number {
        let count = 0
        for (const sessions of this.partitionSessions.values()) {
            count += sessions.size
        }
        return count
    }

    /**
     * Record a parsed message into the batch.
     * Events are grouped by partition → projectId$sessionId.
     */
    public record(message: ParsedMessageData): void {
        const { partition } = message.metadata
        const sessionKey = `${message.project_id}$${message.session_id}`

        let sessions = this.partitionSessions.get(partition)
        if (!sessions) {
            sessions = new Map()
            this.partitionSessions.set(partition, sessions)
        }

        let sessionInfo = sessions.get(sessionKey)
        if (!sessionInfo) {
            sessionInfo = {
                recorder: new SnappySessionRecorder(),
                projectId: message.project_id,
                sessionId: message.session_id,
                userId: message.user_id,
                snapshotSource: message.snapshot_source,
            }
            sessions.set(sessionKey, sessionInfo)
        }

        const addedSize = sessionInfo.recorder.recordEvents(message.events, message.eventsRange)

        this._size += addedSize
        this.partitionSizes.set(
            partition,
            (this.partitionSizes.get(partition) || 0) + addedSize
        )

        // Track offset for this partition
        this.offsetManager.trackOffset({
            partition: message.metadata.partition,
            offset: message.metadata.offset,
        })
    }

    /**
     * Flush the entire batch:
     *   1. Write all session blocks to S3
     *   2. Publish block metadata to Kafka (→ ClickHouse)
     *   3. Commit Kafka offsets
     *
     * If any step fails, the process crashes. On restart, Kafka replays
     * from the last committed offset (at-least-once delivery).
     */
    public async flush(): Promise<SessionBlockMetadata[]> {
        const allMetadata: SessionBlockMetadata[] = []
        const writer = this.storage.newBatch()

        // Step 1: Compress each session and write to S3
        for (const sessions of this.partitionSessions.values()) {
            for (const [_key, sessionInfo] of sessions) {
                if (sessionInfo.recorder.isEmpty) {
                    continue
                }

                const endResult = await sessionInfo.recorder.end()
                const writeResult = await writer.writeSession({
                    buffer: endResult.buffer,
                    sessionId: sessionInfo.sessionId,
                    projectId: sessionInfo.projectId,
                })

                allMetadata.push({
                    sessionId: sessionInfo.sessionId,
                    projectId: sessionInfo.projectId,
                    userId: sessionInfo.userId,
                    startDateTime: endResult.startDateTime,
                    endDateTime: endResult.endDateTime,
                    blockUrl: writeResult.url,
                    snapshotSource: sessionInfo.snapshotSource,
                })
            }
        }

        // Step 2: Finalize S3 multipart upload
        await writer.finish()
        console.log(`[BatchRecorder] S3 upload complete: ${allMetadata.length} blocks`)

        // Step 3: Publish block metadata to Kafka → ClickHouse
        await this.metadataStore.storeSessionBlocks(allMetadata)

        // Step 4: Commit Kafka offsets (marks messages as processed)
        this.offsetManager.commit()

        // Step 5: Clear in-memory state
        this.clear()

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

    private clear(): void {
        this.partitionSessions.clear()
        this.partitionSizes.clear()
        this._size = 0
        this._createdAt = DateTime.utc()
    }
}

interface SessionInfo {
    recorder: SnappySessionRecorder
    projectId: string
    sessionId: string
    userId: string
    snapshotSource: string | null
}
