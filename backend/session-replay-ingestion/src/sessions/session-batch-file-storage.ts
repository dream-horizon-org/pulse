/**
 * Interface for writing session batch files to a storage backend (S3/MinIO).
 *
 * A "batch writer" represents a single S3 object being built up from
 * multiple session blocks. Each block is written sequentially and gets
 * a byte-range URL for random access.
 */
export interface WriteSessionData {
    buffer: Buffer
    sessionId: string
    projectId: string
}

export interface WriteSessionResult {
    bytesWritten: number
    url: string // s3://bucket/key?range=bytes=start-end
}

export interface SessionBatchFileWriter {
    writeSession(data: WriteSessionData): Promise<WriteSessionResult>
    finish(): Promise<void>
}

export interface SessionBatchFileStorage {
    newBatch(): SessionBatchFileWriter
}
