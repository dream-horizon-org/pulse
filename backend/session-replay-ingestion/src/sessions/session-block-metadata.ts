import { DateTime } from 'luxon'

/**
 * Metadata for a single session block, written to ClickHouse via Kafka.
 * Each block is a zstd-compressed JSONL blob stored in an S3 batch file.
 */
export interface SessionBlockMetadata {
    /** Unique identifier for the session */
    sessionId: string
    /** ID of the project that owns this session recording */
    projectId: string
    /** User ID of the session recording */
    userId: string
    /** ID of the batch this session block belongs to */
    batchId: string
    /** Timestamp of the first event in the session block */
    startDateTime: DateTime
    /** Timestamp of the last event in the session block */
    endDateTime: DateTime
    /** URL to the block data with byte range query parameter */
    blockUrl: string | null
    /** Source of the snapshot (web/mobile) */
    snapshotSource: string | null
}
