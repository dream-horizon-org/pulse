export interface Config {
    kafkaBrokers: string;
    kafkaTopic: string;
    kafkaGroupId: string;
    kafkaMetadataTopic: string;
    s3Endpoint: string;
    s3Region: string;
    s3Bucket: string;
    s3Prefix: string;
    s3AccessKeyId?: string;
    s3SecretAccessKey?: string;
    maxBatchSizeBytes: number;
    maxBatchAgeMs: number;
    s3TimeoutMs: number;
    fetchBatchSize: number;
}
export declare function loadConfig(): Config;
