import { SessionBlockMetadata } from "./session-block-metadata";
import { KafkaProducer } from "../kafka/producer";
/**
 * Publishes session block metadata to a Kafka topic for ingestion into ClickHouse.
 *
 * Each block metadata event corresponds to one Snappy-compressed session
 * block that was written to S3. ClickHouse's AggregatingMergeTree merges
 * multiple blocks for the same session.
 *
 * Kafka message format matches the ClickHouse Kafka engine table schema:
 *   - JSONEachRow format
 *   - Keyed by SessionId
 */
export declare class SessionMetadataStore {
    private readonly producer;
    private readonly kafkaTopic;
    constructor(producer: KafkaProducer, kafkaTopic: string);
    storeSessionBlocks(blocks: SessionBlockMetadata[]): Promise<void>;
}
