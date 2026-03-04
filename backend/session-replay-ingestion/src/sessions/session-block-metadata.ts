import { DateTime } from 'luxon'

/**
 * Metadata for a single session block, written to ClickHouse via Kafka.
 * Each block is a Snappy-compressed JSONL blob stored in an S3 batch file.
 */
export interface SessionBlockMetadata {
    sessionId: string
    projectId: string
    userId: string
    startDateTime: DateTime
    endDateTime: DateTime
    blockUrl: string // s3://bucket/key?range=bytes=start-end
    size: number // compressed block size in bytes
    snapshotSource: string | null
    snapshotLibrary: string | null
}
