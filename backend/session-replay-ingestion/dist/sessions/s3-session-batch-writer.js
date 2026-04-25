"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.S3SessionBatchFileStorage = void 0;
const client_s3_1 = require("@aws-sdk/client-s3");
const lib_storage_1 = require("@aws-sdk/lib-storage");
const crypto_1 = require("crypto");
const stream_1 = require("stream");
/**
 * S3 `Tagging` header: `project_id` + `date` (must match heatmap-screenshot-ingestion exactly).
 */
function buildIngestionS3ObjectTagging(projectId, dateUtcYyyyMmDd) {
    return `project_id=${encodeURIComponent(projectId)}&date=${encodeURIComponent(dateUtcYyyyMmDd)}`;
}
class S3SessionBatchFileWriter {
    constructor(s3, bucket, prefix, projectId, timeout) {
        this.s3 = s3;
        this.bucket = bucket;
        this.prefix = prefix;
        this.projectId = projectId;
        this.timeout = timeout;
        this.currentOffset = 0;
        this.timeoutId = null;
        this.error = null;
        this.rejectCallbacks = [];
        this.stream = new stream_1.PassThrough();
        const { key, dateUtcYyyyMmDd } = this.generateKeyAndDate();
        this.key = key;
        this.uploadStartTime = Date.now();
        console.log(`[S3BatchWriter] Opening stream for ${this.key}`);
        const upload = new lib_storage_1.Upload({
            client: this.s3,
            params: {
                Bucket: this.bucket,
                Key: this.key,
                Body: this.stream,
                ContentType: 'application/octet-stream',
                Tagging: buildIngestionS3ObjectTagging(this.projectId, dateUtcYyyyMmDd),
            },
        });
        this.stream.on('error', (error) => {
            console.error(`[S3BatchWriter] Stream error for ${this.key}:`, error);
            this.handleError(error);
        });
        this.timeoutId = setTimeout(() => {
            this.handleError(new Error(`S3 upload timed out after ${this.timeout}ms`));
            this.stream.destroy();
        }, this.timeout);
        this.uploadPromise = upload.done().catch((error) => {
            console.error(`[S3BatchWriter] Upload error for ${this.key}:`, error);
            this.handleError(error);
            throw error;
        });
    }
    handleError(error) {
        if (!this.error) {
            this.error = error;
            this.rejectCallbacks.forEach((reject) => reject(error));
            this.rejectCallbacks = [];
            if (this.timeoutId) {
                clearTimeout(this.timeoutId);
                this.timeoutId = null;
            }
        }
    }
    /**
     * Wraps an async operation so that any background error (stream error, timeout,
     * upload failure) immediately rejects the caller's promise. This prevents silent
     * data loss where writeSession() resolves but the upload has already failed.
     */
    async withErrorBarrier(operation) {
        return new Promise((resolve, reject) => {
            if (this.error) {
                reject(this.error);
                return;
            }
            this.rejectCallbacks.push(reject);
            operation()
                .then((result) => {
                if (!this.error) {
                    this.rejectCallbacks = this.rejectCallbacks.filter((cb) => cb !== reject);
                    resolve(result);
                }
            })
                .catch((error) => {
                console.error(`[S3BatchWriter] Operation error for ${this.key}:`, error);
                this.handleError(error);
            });
        });
    }
    async writeSession(sessionData) {
        return await this.withErrorBarrier(async () => {
            const buffer = sessionData.buffer;
            const startOffset = this.currentOffset;
            const canWriteMore = this.stream.write(buffer);
            if (!canWriteMore) {
                await new Promise((resolve) => {
                    this.stream.once('drain', resolve);
                });
            }
            this.currentOffset += buffer.length;
            return {
                bytesWritten: buffer.length,
                url: `s3://${this.bucket}/${this.key}?range=bytes=${startOffset}-${this.currentOffset - 1}`,
                retentionPeriodDays: null,
            };
        });
    }
    async finish() {
        return await this.withErrorBarrier(async () => {
            try {
                this.stream.end();
                await this.uploadPromise;
                if (this.timeoutId) {
                    clearTimeout(this.timeoutId);
                    this.timeoutId = null;
                }
                const uploadDuration = (Date.now() - this.uploadStartTime) / 1000;
                console.log(`[S3BatchWriter] Upload complete: ${this.key} ` +
                    `(${this.currentOffset} bytes in ${uploadDuration.toFixed(1)}s)`);
            }
            catch (error) {
                console.error(`[S3BatchWriter] Upload finalization error for ${this.key}:`, error);
                throw error;
            }
        });
    }
    generateKeyAndDate() {
        const now = new Date();
        const dateUtcYyyyMmDd = now.toISOString().slice(0, 10); // yyyy-MM-dd UTC
        const timestamp = now.getTime();
        const suffix = (0, crypto_1.randomBytes)(8).toString('hex');
        return {
            key: `${this.prefix}/${this.projectId}/${dateUtcYyyyMmDd}/${timestamp}-${suffix}`,
            dateUtcYyyyMmDd,
        };
    }
}
class S3SessionBatchFileStorage {
    constructor(s3Client, bucket, prefix, timeout = 30000) {
        this.bucket = bucket;
        this.prefix = prefix;
        this.timeout = timeout;
        this.s3 = s3Client;
        console.log(`[S3Storage] Created storage: bucket=${bucket}, prefix=${prefix}`);
    }
    newBatch(projectId) {
        return new S3SessionBatchFileWriter(this.s3, this.bucket, this.prefix, projectId, this.timeout);
    }
    async checkHealth() {
        try {
            const command = new client_s3_1.HeadBucketCommand({ Bucket: this.bucket });
            await this.s3.send(command);
            return true;
        }
        catch (error) {
            console.error(`[S3Storage] Health check failed for bucket ${this.bucket}:`, error);
            return false;
        }
    }
}
exports.S3SessionBatchFileStorage = S3SessionBatchFileStorage;
//# sourceMappingURL=s3-session-batch-writer.js.map