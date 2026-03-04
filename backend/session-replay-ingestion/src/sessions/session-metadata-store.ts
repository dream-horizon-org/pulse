import { SessionBlockMetadata } from './session-block-metadata'
import { KafkaProducer } from '../kafka/producer'

/**
 * Buffers session block metadata and publishes it to a Kafka topic
 * for ingestion into ClickHouse.
 *
 * Each block metadata event corresponds to one Snappy-compressed session
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
    ) {}

    public async storeSessionBlocks(blocks: SessionBlockMetadata[]): Promise<void> {
        if (blocks.length === 0) return

        for (const metadata of blocks) {
            // ClickHouse DateTime64 with JSONEachRow expects 'YYYY-MM-DD HH:MM:SS.ffffff' format
            const chTimestampFormat = 'yyyy-MM-dd HH:mm:ss.SSS000'
            const event = {
                session_id: metadata.sessionId,
                project_id: metadata.projectId,
                user_id: metadata.userId,
                first_timestamp: metadata.startDateTime.toUTC().toFormat(chTimestampFormat),
                last_timestamp: metadata.endDateTime.toUTC().toFormat(chTimestampFormat),
                block_url: metadata.blockUrl,
                snapshot_source: metadata.snapshotSource ?? '',
            }

            await this.producer.produce(
                this.kafkaTopic,
                metadata.sessionId,
                JSON.stringify(event)
            )
        }

        await this.producer.flush()

        console.log(
            `[MetadataStore] Published ${blocks.length} block metadata events to ${this.kafkaTopic}`
        )
    }
}
