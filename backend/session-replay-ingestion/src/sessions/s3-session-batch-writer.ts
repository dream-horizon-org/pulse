import { CompleteMultipartUploadCommandOutput, HeadBucketCommand, S3Client } from '@aws-sdk/client-s3'
import { Upload } from '@aws-sdk/lib-storage'
import { randomBytes } from 'crypto'
import { PassThrough } from 'stream'

import {
    SessionBatchFileStorage,
    SessionBatchFileWriter,
    WriteSessionData,
    WriteSessionResult,
} from './session-batch-file-storage'

class S3SessionBatchFileWriter implements SessionBatchFileWriter {
    private stream: PassThrough
    private uploadPromise: Promise<CompleteMultipartUploadCommandOutput>
    private key: string
    private currentOffset = 0
    private timeoutId: NodeJS.Timeout | null = null
    private error: Error | null = null
    private rejectCallbacks: ((error: Error) => void)[] = []
    private uploadStartTime: number

    constructor(
        private readonly s3: S3Client,
        private readonly bucket: string,
        private readonly prefix: string,
        private readonly timeout: number
    ) {
        this.stream = new PassThrough()
        this.key = this.generateKey()
        this.uploadStartTime = Date.now()

        console.log(`[S3BatchWriter] Opening stream for ${this.key}`)

        const upload = new Upload({
            client: this.s3,
            params: {
                Bucket: this.bucket,
                Key: this.key,
                Body: this.stream,
                ContentType: 'application/octet-stream',
            },
        })

        this.stream.on('error', (error) => {
            console.error(`[S3BatchWriter] Stream error for ${this.key}:`, error)
            this.handleError(error)
        })

        this.timeoutId = setTimeout(() => {
            this.handleError(new Error(`S3 upload timed out after ${this.timeout}ms`))
            this.stream.destroy()
        }, this.timeout)

        this.uploadPromise = upload.done().catch((error) => {
            console.error(`[S3BatchWriter] Upload error for ${this.key}:`, error)
            this.handleError(error)
            throw error
        })
    }

    private handleError(error: Error): void {
        if (!this.error) {
            this.error = error
            this.rejectCallbacks.forEach((reject) => reject(error))
            this.rejectCallbacks = []
            if (this.timeoutId) {
                clearTimeout(this.timeoutId)
                this.timeoutId = null
            }
        }
    }

    /**
     * Wraps an async operation so that any background error (stream error, timeout,
     * upload failure) immediately rejects the caller's promise. This prevents silent
     * data loss where writeSession() resolves but the upload has already failed.
     */
    private async withErrorBarrier<T>(operation: () => Promise<T>): Promise<T> {
        return new Promise<T>((resolve, reject) => {
            if (this.error) {
                reject(this.error)
                return
            }

            this.rejectCallbacks.push(reject)

            operation()
                .then((result) => {
                    if (!this.error) {
                        this.rejectCallbacks = this.rejectCallbacks.filter((cb) => cb !== reject)
                        resolve(result)
                    }
                })
                .catch((error) => {
                    console.error(`[S3BatchWriter] Operation error for ${this.key}:`, error)
                    this.handleError(error)
                })
        })
    }

    public async writeSession(sessionData: WriteSessionData): Promise<WriteSessionResult> {
        return await this.withErrorBarrier(async () => {
            const buffer = sessionData.buffer
            const startOffset = this.currentOffset

            const canWriteMore = this.stream.write(buffer)
            if (!canWriteMore) {
                await new Promise<void>((resolve) => {
                    this.stream.once('drain', resolve)
                })
            }

            this.currentOffset += buffer.length

            return {
                bytesWritten: buffer.length,
                url: `s3://${this.bucket}/${this.key}?range=bytes=${startOffset}-${this.currentOffset - 1}`,
                retentionPeriodDays: null,
            }
        })
    }

    public async finish(): Promise<void> {
        return await this.withErrorBarrier(async () => {
            try {
                this.stream.end()
                await this.uploadPromise
                if (this.timeoutId) {
                    clearTimeout(this.timeoutId)
                    this.timeoutId = null
                }

                const uploadDuration = (Date.now() - this.uploadStartTime) / 1000
                console.log(
                    `[S3BatchWriter] Upload complete: ${this.key} ` +
                        `(${this.currentOffset} bytes in ${uploadDuration.toFixed(1)}s)`
                )
            } catch (error) {
                console.error(`[S3BatchWriter] Upload finalization error for ${this.key}:`, error)
                throw error
            }
        })
    }

    private generateKey(): string {
        const now = new Date()
        const datePrefix = now.toISOString().slice(0, 10) // yyyy-MM-dd
        const timestamp = now.getTime()
        const suffix = randomBytes(8).toString('hex')
        return `${this.prefix}/${datePrefix}/${timestamp}-${suffix}`
    }
}

export class S3SessionBatchFileStorage implements SessionBatchFileStorage {
    private readonly s3: S3Client

    constructor(
        s3Client: S3Client,
        private readonly bucket: string,
        private readonly prefix: string,
        private readonly timeout: number = 30000
    ) {
        this.s3 = s3Client
        console.log(`[S3Storage] Created storage: bucket=${bucket}, prefix=${prefix}`)
    }

    public newBatch(): SessionBatchFileWriter {
        return new S3SessionBatchFileWriter(this.s3, this.bucket, this.prefix, this.timeout)
    }

    public async checkHealth(): Promise<boolean> {
        try {
            const command = new HeadBucketCommand({ Bucket: this.bucket })
            await this.s3.send(command)
            return true
        } catch (error) {
            console.error(`[S3Storage] Health check failed for bucket ${this.bucket}:`, error)
            return false
        }
    }
}
