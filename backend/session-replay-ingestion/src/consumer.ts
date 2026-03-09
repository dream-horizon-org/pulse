import { S3Client } from '@aws-sdk/client-s3'
import Kafka, { CODES as ErrorCodes, Message, TopicPartition } from 'node-rdkafka'

import { Config } from './config'
import { KafkaMessageParser } from './kafka/message-parser'
import { KafkaOffsetManager } from './kafka/offset-manager'
import { KafkaProducer } from './kafka/producer'
import { ParsedMessageData } from './kafka/types'
import { S3SessionBatchFileStorage } from './sessions/s3-session-batch-writer'
import { SessionBatchManager } from './sessions/session-batch-manager'
import { SessionMetadataStore } from './sessions/session-metadata-store'

/**
 * Session Replay Ingestion Consumer
 *
 * Consumes recording events from Kafka, batches them in memory,
 * compresses with zstd, uploads to S3, and publishes metadata
 * to ClickHouse via a Kafka topic.
 *
 * Lifecycle:
 *   1. Connect to Kafka consumer + producer
 *   2. Check S3 storage health
 *   3. Subscribe to session_recording_events topic
 *   4. Consume loop:
 *      a. Pull batch of messages from Kafka
 *      b. Parse + validate messages
 *      c. Record into in-memory batch (grouped by partition -> session)
 *      d. If batch thresholds met (100MB or 10s):
 *         - Compress each session block with zstd
 *         - Upload all blocks as single S3 file (streaming)
 *         - Publish block metadata to Kafka -> ClickHouse
 *         - Commit Kafka consumer offsets
 *   5. On shutdown: flush remaining batch, disconnect
 *
 * Rebalancing:
 *   When partitions are revoked, we discard in-memory data for those
 *   partitions. The new owner will replay from the last committed offset.
 *
 * Failure:
 *   If flush fails (S3 error, Kafka produce error), the process crashes.
 *   Kubernetes restarts it, and Kafka replays from the last committed
 *   offset (at-least-once delivery). Duplicate blocks are harmless
 *   because ClickHouse's AggregatingMergeTree handles them.
 */
export class SessionReplayConsumer {
    private consumer: Kafka.KafkaConsumer | null = null
    private producer: KafkaProducer | null = null
    private batchManager: SessionBatchManager | null = null
    private fileStorage: S3SessionBatchFileStorage | null = null
    private readonly parser: KafkaMessageParser
    private stopping: boolean = false

    constructor(private readonly config: Config) {
        this.parser = new KafkaMessageParser()
    }

    public async start(): Promise<void> {
        console.log('[Consumer] Starting session replay ingestion consumer...')

        // Initialize Kafka producer (for metadata -> ClickHouse)
        this.producer = new KafkaProducer(this.config.kafkaBrokers)
        await this.producer.connect()

        // Initialize S3 storage
        const s3Client = new S3Client({
            endpoint: this.config.s3Endpoint,
            region: this.config.s3Region,
            forcePathStyle: true,
            credentials:
                this.config.s3AccessKeyId && this.config.s3SecretAccessKey
                    ? {
                          accessKeyId: this.config.s3AccessKeyId,
                          secretAccessKey: this.config.s3SecretAccessKey,
                      }
                    : undefined,
        })

        this.fileStorage = new S3SessionBatchFileStorage(
            s3Client,
            this.config.s3Bucket,
            this.config.s3Prefix,
            this.config.s3TimeoutMs
        )

        // Check S3 health before starting consumer
        const s3Healthy = await this.fileStorage.checkHealth()
        if (!s3Healthy) {
            throw new Error(`S3 health check failed for bucket ${this.config.s3Bucket}`)
        }
        console.log('[Consumer] S3 storage health check passed')

        // Initialize metadata store
        const metadataStore = new SessionMetadataStore(this.producer, this.config.kafkaMetadataTopic)

        // Initialize Kafka consumer
        this.consumer = new Kafka.KafkaConsumer(
            {
                'group.id': this.config.kafkaGroupId,
                'metadata.broker.list': this.config.kafkaBrokers,
                'enable.auto.commit': true,
                'enable.auto.offset.store': false,
                'session.timeout.ms': 90000,
                'max.poll.interval.ms': 300000,
                'fetch.min.bytes': 1,
                'fetch.wait.max.ms': 500,
            } as any,
            {
                'auto.offset.reset': 'earliest',
            }
        )

        // Create offset manager
        const offsetManager = new KafkaOffsetManager(
            (offsets) => {
                if (this.consumer) {
                    this.consumer.offsetsStore(offsets)
                }
            },
            this.config.kafkaTopic
        )

        // Create batch manager
        this.batchManager = new SessionBatchManager({
            maxBatchSizeBytes: this.config.maxBatchSizeBytes,
            maxBatchAgeMs: this.config.maxBatchAgeMs,
            offsetManager,
            fileStorage: this.fileStorage,
            metadataStore,
        })

        // Handle rebalancing
        this.consumer.on('rebalance', (err: any, assignments: TopicPartition[]) => {
            if (err.code === ErrorCodes.ERRORS.ERR__ASSIGN_PARTITIONS) {
                const partitions = assignments.map((a) => a.partition)
                console.log(`[Consumer] Partitions assigned: ${partitions.join(', ')}`)
            } else if (err.code === ErrorCodes.ERRORS.ERR__REVOKE_PARTITIONS) {
                const partitions = assignments.map((a) => a.partition)
                console.log(`[Consumer] Partitions revoked: ${partitions.join(', ')}`)
                this.batchManager?.discardPartitions(partitions)
            }
        })

        this.consumer.on('event.error', (err: any) => {
            console.error('[Consumer] Kafka error:', err)
        })

        // Connect and subscribe
        await this.connectConsumer()
        this.consumer.setDefaultConsumeTimeout(500)
        this.consumer.subscribe([this.config.kafkaTopic])

        console.log(`[Consumer] Subscribed to ${this.config.kafkaTopic}`)
        console.log(
            `[Consumer] Batch thresholds: ` +
                `${(this.config.maxBatchSizeBytes / 1024 / 1024).toFixed(0)}MB size, ` +
                `${this.config.maxBatchAgeMs}ms age`
        )

        // Start consume loop
        await this.consumeLoop()
    }

    public async stop(): Promise<void> {
        console.log('[Consumer] Stopping...')
        this.stopping = true

        if (this.batchManager) {
            try {
                await this.batchManager.flush()
            } catch (error) {
                console.error('[Consumer] Error flushing on shutdown:', error)
            }
        }

        if (this.consumer) {
            await new Promise<void>((resolve) => {
                this.consumer!.disconnect(() => resolve())
            })
        }
        if (this.producer) {
            await this.producer.disconnect()
        }

        console.log('[Consumer] Stopped')
    }

    private async consumeLoop(): Promise<void> {
        while (!this.stopping) {
            try {
                const messages = await this.consumeBatch(this.config.fetchBatchSize)

                if (messages.length > 0) {
                    await this.handleBatch(messages)
                }

                if (this.batchManager!.shouldFlush()) {
                    await this.batchManager!.flush()
                }
            } catch (error) {
                console.error('[Consumer] Fatal error in consume loop:', error)
                process.exit(1)
            }
        }
    }

    /**
     * Parse a batch of raw Kafka messages and record into the current batch.
     */
    private async handleBatch(messages: Message[]): Promise<void> {
        const rawMessages = messages.map((m) => ({
            value: m.value ?? null,
            timestamp: m.timestamp ?? Date.now(),
            partition: m.partition,
            topic: m.topic,
            size: m.size,
            offset: m.offset,
            headers: m.headers as Array<{ [key: string]: Buffer }> | undefined,
        }))

        const parsed = await this.parser.parseBatch(rawMessages)

        await this.processMessages(parsed)
    }

    /**
     * Record parsed messages into the current batch.
     * Yields the event loop every YIELD_INTERVAL messages to prevent starvation
     * (allows heartbeats, timers, and I/O callbacks to fire between messages).
     */
    private async processMessages(parsedMessages: ParsedMessageData[]): Promise<void> {
        const YIELD_INTERVAL = 100
        const batch = this.batchManager!.getCurrentBatch()
        for (let i = 0; i < parsedMessages.length; i++) {
            batch.record(parsedMessages[i])
            if (i > 0 && i % YIELD_INTERVAL === 0) {
                await new Promise<void>((resolve) => setImmediate(resolve))
            }
        }
    }

    private consumeBatch(batchSize: number): Promise<Message[]> {
        return new Promise((resolve, reject) => {
            this.consumer!.consume(batchSize, (err: any, messages: Message[]) => {
                if (err) {
                    reject(err)
                } else {
                    resolve(messages)
                }
            })
        })
    }

    private connectConsumer(): Promise<void> {
        return new Promise((resolve, reject) => {
            const onError = (err: any) => {
                this.consumer!.removeListener('event.error', onError)
                reject(err)
            }
            this.consumer!.on('ready', () => {
                this.consumer!.removeListener('event.error', onError)
                console.log('[Consumer] Kafka consumer connected')
                resolve()
            })
            this.consumer!.on('event.error', onError)
            this.consumer!.connect()
        })
    }
}
