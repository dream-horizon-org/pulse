import { S3Client } from '@aws-sdk/client-s3';
import { SessionBatchFileStorage, SessionBatchFileWriter } from './session-batch-file-storage';
export declare class S3SessionBatchFileStorage implements SessionBatchFileStorage {
    private readonly bucket;
    private readonly prefix;
    private readonly timeout;
    private readonly s3;
    constructor(s3Client: S3Client, bucket: string, prefix: string, timeout?: number);
    newBatch(projectId: string): SessionBatchFileWriter;
    checkHealth(): Promise<boolean>;
}
