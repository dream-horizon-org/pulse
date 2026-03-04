import {
    S3Client,
    CreateMultipartUploadCommand,
    UploadPartCommand,
    CompleteMultipartUploadCommand,
    AbortMultipartUploadCommand,
    CompletedPart,
} from '@aws-sdk/client-s3'
import { randomUUID } from 'node:crypto'
import { DateTime } from 'luxon'

import { Config } from '../config'
import {
    SessionBatchFileStorage,
    SessionBatchFileWriter,
    WriteSessionData,
    WriteSessionResult,
} from './session-batch-file-storage'

// S3 minimum part size for multipart upload (5MB)
const MIN_PART_SIZE = 5 * 1024 * 1024

/**
 * Writes multiple session blocks into a single S3 object using multipart upload.
 *
 * Each session block is appended to an in-memory buffer. When the buffer exceeds
 * the minimum part size (5MB), it's flushed as a multipart upload part. At the end,
 * any remaining data is flushed as the final part.
 *
 * Byte-range addressing:
 *   - Each session block gets a URL like s3://bucket/key?range=bytes=start-end
 *   - The API uses these ranges to fetch individual blocks from the batch file
 *
 * File layout in S3:
 *   [SessionBlock_A_snappy][SessionBlock_B_snappy][SessionBlock_C_snappy]...
 */
export class S3SessionBatchFileStorage implements SessionBatchFileStorage {
    private s3Client: S3Client

    constructor(private readonly config: Config) {
        this.s3Client = new S3Client({
            endpoint: config.s3Endpoint,
            region: config.s3Region,
            forcePathStyle: true, // needed for MinIO
            credentials:
                config.s3AccessKeyId && config.s3SecretAccessKey
                    ? {
                          accessKeyId: config.s3AccessKeyId,
                          secretAccessKey: config.s3SecretAccessKey,
                      }
                    : undefined,
        })
    }

    newBatch(): SessionBatchFileWriter {
        const datePrefix = DateTime.utc().toFormat('yyyy/MM/dd/HH')
        const key = `${this.config.s3Prefix}/${datePrefix}/${randomUUID()}`
        return new S3BatchWriter(this.s3Client, this.config.s3Bucket, key)
    }
}

class S3BatchWriter implements SessionBatchFileWriter {
    private currentOffset: number = 0
    private buffer: Buffer = Buffer.alloc(0)
    private parts: CompletedPart[] = []
    private partNumber: number = 1
    private uploadId: string | undefined
    private initialized: boolean = false

    constructor(
        private readonly s3Client: S3Client,
        private readonly bucket: string,
        private readonly key: string
    ) {}

    async writeSession(data: WriteSessionData): Promise<WriteSessionResult> {
        if (!this.initialized) {
            await this.initMultipartUpload()
        }

        const startOffset = this.currentOffset
        this.buffer = Buffer.concat([this.buffer, data.buffer])
        this.currentOffset += data.buffer.length

        // Flush to S3 if buffer exceeds minimum part size
        if (this.buffer.length >= MIN_PART_SIZE) {
            await this.uploadPart(this.buffer)
            this.buffer = Buffer.alloc(0)
        }

        return {
            bytesWritten: data.buffer.length,
            url: `s3://${this.bucket}/${this.key}?range=bytes=${startOffset}-${this.currentOffset - 1}`,
        }
    }

    async finish(): Promise<void> {
        if (!this.initialized || !this.uploadId) {
            return
        }

        try {
            // Upload any remaining buffered data as the final part
            if (this.buffer.length > 0) {
                await this.uploadPart(this.buffer)
                this.buffer = Buffer.alloc(0)
            }

            await this.s3Client.send(
                new CompleteMultipartUploadCommand({
                    Bucket: this.bucket,
                    Key: this.key,
                    UploadId: this.uploadId,
                    MultipartUpload: { Parts: this.parts },
                })
            )

            console.log(
                `[S3BatchWriter] Completed upload: ${this.key} ` +
                    `(${this.parts.length} parts, ${this.currentOffset} bytes)`
            )
        } catch (error) {
            // Abort the multipart upload on failure
            await this.s3Client
                .send(
                    new AbortMultipartUploadCommand({
                        Bucket: this.bucket,
                        Key: this.key,
                        UploadId: this.uploadId,
                    })
                )
                .catch(() => {}) // best-effort abort

            throw error
        }
    }

    private async initMultipartUpload(): Promise<void> {
        const result = await this.s3Client.send(
            new CreateMultipartUploadCommand({
                Bucket: this.bucket,
                Key: this.key,
                ContentType: 'application/octet-stream',
            })
        )
        this.uploadId = result.UploadId
        this.initialized = true
    }

    private async uploadPart(data: Buffer): Promise<void> {
        const result = await this.s3Client.send(
            new UploadPartCommand({
                Bucket: this.bucket,
                Key: this.key,
                UploadId: this.uploadId,
                PartNumber: this.partNumber,
                Body: data,
            })
        )

        this.parts.push({
            ETag: result.ETag,
            PartNumber: this.partNumber,
        })
        this.partNumber++
    }
}
