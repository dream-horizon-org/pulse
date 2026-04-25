"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SessionMetadataStore = void 0;
const crypto_1 = require("crypto");
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
class SessionMetadataStore {
    constructor(producer, kafkaTopic) {
        this.producer = producer;
        this.kafkaTopic = kafkaTopic;
        console.log(`[MetadataStore] Created, topic=${kafkaTopic}`);
    }
    async storeSessionBlocks(blocks) {
        if (blocks.length === 0)
            return;
        console.log(`[MetadataStore] Storing ${blocks.length} blocks`);
        const chTimestampFormat = "yyyy-MM-dd HH:mm:ss.SSS000";
        const messages = blocks.map((metadata) => ({
            key: metadata.sessionId,
            value: JSON.stringify({
                uuid: (0, crypto_1.randomUUID)(),
                SessionId: metadata.sessionId,
                ProjectId: metadata.projectId,
                UserId: metadata.userId,
                batch_id: metadata.batchId,
                FirstTimestamp: metadata.startDateTime
                    .toUTC()
                    .toFormat(chTimestampFormat),
                LastTimestamp: metadata.endDateTime.toUTC().toFormat(chTimestampFormat),
                BlockUrl: metadata.blockUrl,
                SnapshotSource: metadata.snapshotSource ?? "",
            }),
        }));
        this.producer.queueMessages(this.kafkaTopic, messages);
        await this.producer.flush();
        console.log(`[MetadataStore] Published ${blocks.length} block metadata events to ${this.kafkaTopic}`);
    }
}
exports.SessionMetadataStore = SessionMetadataStore;
//# sourceMappingURL=session-metadata-store.js.map