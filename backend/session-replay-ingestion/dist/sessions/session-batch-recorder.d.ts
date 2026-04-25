import { KafkaOffsetManager } from "../kafka/offset-manager";
import { ParsedMessageData } from "../kafka/types";
import { SessionBatchFileStorage } from "./session-batch-file-storage";
import { SessionBlockMetadata } from "./session-block-metadata";
import { SessionMetadataStore } from "./session-metadata-store";
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
export declare class SessionBatchRecorder {
    private readonly offsetManager;
    private readonly storage;
    private readonly metadataStore;
    private readonly partitionSessions;
    private readonly partitionSizes;
    private _size;
    private readonly batchId;
    constructor(offsetManager: KafkaOffsetManager, storage: SessionBatchFileStorage, metadataStore: SessionMetadataStore);
    get size(): number;
    /**
     * Record a parsed message into the batch.
     * Events are grouped by partition -> projectId$sessionId.
     */
    record(message: ParsedMessageData): void;
    /**
     * Flush the entire batch:
     *   1. Write all session blocks to S3
     *   2. Publish block metadata to Kafka (-> ClickHouse)
     *   3. Commit Kafka offsets
     *
     * If any step fails, the process crashes. On restart, Kafka replays
     * from the last committed offset (at-least-once delivery).
     */
    flush(): Promise<SessionBlockMetadata[]>;
    /**
     * Discard all data for a revoked Kafka partition.
     * Called during consumer rebalancing to prevent committing offsets
     * for partitions we no longer own.
     */
    discardPartition(partition: number): void;
}
