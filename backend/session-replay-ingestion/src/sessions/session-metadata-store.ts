import { randomUUID } from 'crypto'

import { SessionBlockMetadata } from './session-block-metadata'
import { KafkaProducer } from '../kafka/producer'

/**
 * Publishes session block metadata to a Kafka topic for ingestion into ClickHouse.
 *
 * Each block metadata event corresponds to one zstd-compressed session
 * block that was written to S3. ClickHouse's AggregatingMergeTree merges
 * multiple blocks for the same session.
 *
 * Kafka message format matches the ClickHouse Kafka engine table schema:
 *   - JSONEachRow format
 *   - Keyed by session_id
 */
export class SessionMetadataStore {
    constructor(
        private readonly producer: KafkaProducer,
        private readonly kafkaTopic: string
    ) {
        console.log(`[MetadataStore] Created, topic=${kafkaTopic}`)
    }

    public async storeSessionBlocks(blocks: SessionBlockMetadata[]): Promise<void> {
        if (blocks.length === 0) return

        console.log(`[MetadataStore] Storing ${blocks.length} blocks`)

        const chTimestampFormat = 'yyyy-MM-dd HH:mm:ss.SSS000'

        const messages = blocks.map((metadata) => ({
            key: metadata.sessionId,
            value: JSON.stringify({
                uuid: randomUUID(),
                session_id: metadata.sessionId,
                project_id: metadata.projectId,
                user_id: metadata.userId,
                batch_id: metadata.batchId,
                first_timestamp: metadata.startDateTime.toUTC().toFormat(chTimestampFormat),
                last_timestamp: metadata.endDateTime.toUTC().toFormat(chTimestampFormat),
                block_url: metadata.blockUrl,
                snapshot_source: metadata.snapshotSource ?? '',
            }),
        }))

        this.producer.queueMessages(this.kafkaTopic, messages)
        await this.producer.flush()

        console.log(`[MetadataStore] Published ${blocks.length} block metadata events to ${this.kafkaTopic}`)
    }
}
