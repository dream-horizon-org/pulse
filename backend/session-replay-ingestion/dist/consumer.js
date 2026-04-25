"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SessionReplayConsumer = void 0;
const client_s3_1 = require("@aws-sdk/client-s3");
const kafkajs_1 = require("kafkajs");
const message_parser_1 = require("./kafka/message-parser");
const offset_manager_1 = require("./kafka/offset-manager");
const producer_1 = require("./kafka/producer");
const s3_session_batch_writer_1 = require("./sessions/s3-session-batch-writer");
const session_batch_manager_1 = require("./sessions/session-batch-manager");
const session_metadata_store_1 = require("./sessions/session-metadata-store");
/**
 * Session Replay Ingestion Consumer
 *
 * Consumes recording events from Kafka, batches them in memory,
 * compresses with Snappy, uploads to S3, and publishes metadata
 * to ClickHouse via a Kafka topic.
 *
 * Lifecycle:
 *   1. Connect to Kafka consumer + producer
 *   2. Check S3 storage health
 *   3. Subscribe to session_recording_events topic
 *   4. KafkaJS run loop (eachBatch):
 *      a. Process each message in the fetched batch
 *      b. Record into in-memory batch (grouped by partition -> session)
 *      c. If batch thresholds met (100MB or 10s):
 *         - Compress each session block with Snappy
 *         - Upload all blocks as single S3 file (streaming)
 *         - Publish block metadata to Kafka -> ClickHouse
 *         - Commit Kafka consumer offsets
 *   5. On shutdown: flush remaining batch, disconnect
 *
 * Rebalancing:
 *   When a rebalance starts, we discard all in-memory data.
 *   The new owner will replay from the last committed offset.
 *
 * Failure:
 *   If flush fails (S3 error, Kafka produce error), the process crashes.
 *   Kubernetes restarts it, and Kafka replays from the last committed
 *   offset (at-least-once delivery). Duplicate blocks are harmless
 *   because ClickHouse's AggregatingMergeTree handles them.
 */
class SessionReplayConsumer {
    constructor(config) {
        this.config = config;
        this.consumer = null;
        this.producer = null;
        this.batchManager = null;
        this.fileStorage = null;
        this.stopping = false;
        this.stopResolve = null;
        this.assignedPartitions = [];
        this.parser = new message_parser_1.KafkaMessageParser();
    }
    async start() {
        console.log("[Consumer] Starting session replay ingestion consumer...");
        const kafka = new kafkajs_1.Kafka({
            clientId: "pulse-session-replay-ingestion",
            brokers: this.config.kafkaBrokers.split(","),
        });
        this.producer = new producer_1.KafkaProducer(kafka);
        await this.producer.connect();
        const s3Client = new client_s3_1.S3Client({
            endpoint: this.config.s3Endpoint,
            region: this.config.s3Region,
            forcePathStyle: true,
            credentials: this.config.s3AccessKeyId && this.config.s3SecretAccessKey
                ? {
                    accessKeyId: this.config.s3AccessKeyId,
                    secretAccessKey: this.config.s3SecretAccessKey,
                }
                : undefined,
        });
        this.fileStorage = new s3_session_batch_writer_1.S3SessionBatchFileStorage(s3Client, this.config.s3Bucket, this.config.s3Prefix, this.config.s3TimeoutMs);
        const s3Healthy = await this.fileStorage.checkHealth();
        if (!s3Healthy) {
            throw new Error(`S3 health check failed for bucket ${this.config.s3Bucket}`);
        }
        console.log("[Consumer] S3 storage health check passed");
        const metadataStore = new session_metadata_store_1.SessionMetadataStore(this.producer, this.config.kafkaMetadataTopic);
        this.consumer = kafka.consumer({
            groupId: this.config.kafkaGroupId,
            sessionTimeout: 90000,
            heartbeatInterval: 3000,
            maxWaitTimeInMs: 500,
            minBytes: 1,
        });
        // Commit offsets via KafkaJS manual commit after each flush
        const offsetManager = new offset_manager_1.KafkaOffsetManager(async (offsets) => {
            if (this.consumer) {
                await this.consumer.commitOffsets(offsets.map((o) => ({
                    topic: o.topic,
                    partition: o.partition,
                    offset: String(o.offset),
                })));
            }
        }, this.config.kafkaTopic);
        this.batchManager = new session_batch_manager_1.SessionBatchManager({
            maxBatchSizeBytes: this.config.maxBatchSizeBytes,
            maxBatchAgeMs: this.config.maxBatchAgeMs,
            offsetManager,
            fileStorage: this.fileStorage,
            metadataStore,
        });
        await this.consumer.connect();
        // Before rebalance: discard all in-memory data so the new owner
        // replays from the last committed offset
        this.consumer.on(this.consumer.events.REBALANCING, () => {
            console.log("[Consumer] Rebalancing — discarding in-memory batch");
            if (this.batchManager && this.assignedPartitions.length > 0) {
                this.batchManager.discardPartitions(this.assignedPartitions);
                this.assignedPartitions = [];
            }
        });
        this.consumer.on(this.consumer.events.GROUP_JOIN, (event) => {
            const assignment = event.payload.memberAssignment;
            this.assignedPartitions = Object.values(assignment).flat();
            console.log(`[Consumer] Partitions assigned: ${this.assignedPartitions.join(", ")}`);
        });
        await this.consumer.subscribe({
            topics: [this.config.kafkaTopic],
            fromBeginning: true,
        });
        console.log(`[Consumer] Subscribed to ${this.config.kafkaTopic}`);
        console.log(`[Consumer] Batch thresholds: ` +
            `${(this.config.maxBatchSizeBytes / 1024 / 1024).toFixed(0)}MB size, ` +
            `${this.config.maxBatchAgeMs}ms age`);
        // consumer.run() is non-blocking — block start() via a Promise resolved on stop()
        await new Promise((resolve, reject) => {
            this.stopResolve = resolve;
            this.consumer.run({
                autoCommit: false,
                eachBatch: async ({ batch, resolveOffset, heartbeat, isRunning, isStale, }) => {
                    try {
                        for (const message of batch.messages) {
                            if (this.stopping || !isRunning() || isStale())
                                break;
                            const raw = {
                                value: message.value,
                                timestamp: message.timestamp
                                    ? parseInt(message.timestamp)
                                    : Date.now(),
                                partition: batch.partition,
                                topic: batch.topic,
                                size: message.value?.length ?? 0,
                                offset: parseInt(message.offset),
                                headers: message.headers
                                    ? this.normalizeHeaders(message.headers)
                                    : undefined,
                            };
                            const parsed = await this.parser.parseBatch([raw]);
                            const currentBatch = this.batchManager.getCurrentBatch();
                            for (const p of parsed) {
                                currentBatch.record(p);
                            }
                            resolveOffset(message.offset);
                            await heartbeat();
                        }
                        if (!this.stopping && this.batchManager.shouldFlush()) {
                            await this.batchManager.flush();
                        }
                    }
                    catch (error) {
                        console.error("[Consumer] Fatal error in eachBatch:", error);
                        reject(error);
                        process.exit(1);
                    }
                },
            }).catch((err) => {
                if (!this.stopping)
                    reject(err);
                else
                    resolve();
            });
        });
    }
    async stop() {
        console.log("[Consumer] Stopping...");
        this.stopping = true;
        // Disconnect waits for the current eachBatch to finish, then stops the runner.
        // eachBatch checks this.stopping and breaks out without flushing, so there
        // is no concurrent flush after this point.
        if (this.consumer) {
            await this.consumer.disconnect();
            this.consumer = null;
        }
        // Final flush — S3 upload may succeed; offset commit is skipped because
        // this.consumer is now null (at-least-once: replayed on restart).
        if (this.batchManager) {
            try {
                await this.batchManager.flush();
            }
            catch (error) {
                console.error("[Consumer] Error flushing on shutdown:", error);
            }
        }
        if (this.producer) {
            await this.producer.disconnect();
        }
        this.stopResolve?.();
        console.log("[Consumer] Stopped");
    }
    normalizeHeaders(headers) {
        return Object.entries(headers).map(([key, value]) => ({
            [key]: Buffer.isBuffer(value)
                ? value
                : Buffer.from(Array.isArray(value) ? String(value[0] ?? "") : String(value ?? "")),
        }));
    }
}
exports.SessionReplayConsumer = SessionReplayConsumer;
//# sourceMappingURL=consumer.js.map