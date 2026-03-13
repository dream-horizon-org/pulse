export interface Config {
    kafkaBrokers: string
    kafkaTopic: string
    kafkaGroupId: string
    kafkaMetadataTopic: string
    s3Endpoint: string
    s3Region: string
    s3Bucket: string
    s3Prefix: string
    s3AccessKeyId?: string
    s3SecretAccessKey?: string
    maxBatchSizeBytes: number
    maxBatchAgeMs: number
    s3TimeoutMs: number
    fetchBatchSize: number
}

export function loadConfig(): Config {
    return {
        kafkaBrokers: process.env.KAFKA_BROKERS || 'localhost:9092',
        kafkaTopic: process.env.KAFKA_TOPIC || 'session_recording_events',
        kafkaGroupId: process.env.KAFKA_GROUP_ID || 'session-replay-ingestion-v1',
        kafkaMetadataTopic: process.env.KAFKA_METADATA_TOPIC || 'clickhouse_session_replay_events',
        s3Endpoint: process.env.S3_ENDPOINT || 'http://localhost:9000',
        s3Region: process.env.S3_REGION || 'us-east-1',
        s3Bucket: process.env.S3_BUCKET || 'pulse-session-replay',
        s3Prefix: process.env.S3_PREFIX || 'session-recordings',
        s3AccessKeyId: process.env.S3_ACCESS_KEY_ID,
        s3SecretAccessKey: process.env.S3_SECRET_ACCESS_KEY,
        maxBatchSizeBytes: parseInt(process.env.MAX_BATCH_SIZE_KB || '102400') * 1024, // 100MB default
        maxBatchAgeMs: parseInt(process.env.MAX_BATCH_AGE_MS || '10000'), // 10s default
        s3TimeoutMs: parseInt(process.env.S3_TIMEOUT_MS || '30000'), // 30s default
        fetchBatchSize: parseInt(process.env.FETCH_BATCH_SIZE || '500'),
    }
}
