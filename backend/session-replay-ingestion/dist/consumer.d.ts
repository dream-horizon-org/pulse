import { Config } from "./config";
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
export declare class SessionReplayConsumer {
    private readonly config;
    private consumer;
    private producer;
    private batchManager;
    private fileStorage;
    private readonly parser;
    private stopping;
    private stopResolve;
    private assignedPartitions;
    constructor(config: Config);
    start(): Promise<void>;
    stop(): Promise<void>;
    private normalizeHeaders;
}
