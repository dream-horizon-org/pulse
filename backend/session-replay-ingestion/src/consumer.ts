import Kafka, { CODES as ErrorCodes, Message, TopicPartition } from 'node-rdkafka'

import { Config } from './config'
import { KafkaMessageParser } from './kafka/message-parser'
import { KafkaOffsetManager } from './kafka/offset-manager'
import { KafkaProducer } from './kafka/producer'
import { S3SessionBatchFileStorage } from './sessions/s3-session-batch-writer'
import { SessionBatchManager } from './sessions/session-batch-manager'
import { SessionMetadataStore } from './sessions/session-metadata-store'

/**
 * Session Replay Ingestion Consumer
 *
 * Consumes recording events from Kafka, batches them in memory,
 * compresses with Snappy, uploads to S3, and publishes metadata
 * to ClickHouse via a Kafka topic.
 *
 * Lifecycle:
 *   1. Connect to Kafka consumer + producer
 *   2. Subscribe to session_recording_events topic
 *   3. Consume loop:
 *      a. Pull batch of messages from Kafka
 *      b. Parse + validate messages
 *      c. Record into in-memory batch (grouped by partition → session)
 *      d. If batch thresholds met (100MB or 10s):
 *         - Compress each session block with Snappy
 *         - Upload all blocks as single S3 file (multipart)
 *         - Publish block metadata to Kafka → ClickHouse
 *         - Commit Kafka consumer offsets
 *   4. On shutdown: flush remaining batch, disconnect
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
    private parser: KafkaMessageParser
    private stopping: boolean = false

    constructor(private readonly config: Config) {
        this.parser = new KafkaMessageParser()
    }

    public async start(): Promise<void> {
        console.log('[Consumer] Starting session replay ingestion consumer...')

        // Initialize Kafka producer (for metadata → ClickHouse)
        this.producer = new KafkaProducer(this.config.kafkaBrokers)
        await this.producer.connect()

        // Initialize S3 storage
        const s3Storage = new S3SessionBatchFileStorage(this.config)

        // Initialize metadata store
        const metadataStore = new SessionMetadataStore(this.producer, this.config.kafkaMetadataTopic)

        // Initialize Kafka consumer
        this.consumer = new Kafka.KafkaConsumer(
            {
                'group.id': this.config.kafkaGroupId,
                'metadata.broker.list': this.config.kafkaBrokers,
                'enable.auto.commit': true,
                'enable.auto.offset.store': false, // we control when offsets are stored
                'session.timeout.ms': 90000,
                'max.poll.interval.ms': 300000, // 5min max between polls
                'fetch.min.bytes': 1,
                'fetch.wait.max.ms': 500,
            } as any,
            {
                'auto.offset.reset': 'earliest',
            }
        )

        // Create offset manager that uses consumer.offsetsStore()
        const offsetManager = new KafkaOffsetManager(
            (offsets) => {
                if (this.consumer) {
                    this.consumer.offsetsStore(offsets)
                }
            },
            this.config.kafkaTopic
        )

        // Create batch manager
        this.batchManager = new SessionBatchManager(
            this.config,
            s3Storage,
            metadataStore,
            offsetManager
        )

        // Handle rebalancing
        this.consumer.on('rebalance', (err: any, assignments: TopicPartition[]) => {
            if (err.code === ErrorCodes.ERRORS.ERR__ASSIGN_PARTITIONS) {
                const partitions = assignments.map((a) => a.partition)
                console.log(`[Consumer] Partitions assigned: ${partitions.join(', ')}`)
            } else if (err.code === ErrorCodes.ERRORS.ERR__REVOKE_PARTITIONS) {
                const partitions = assignments.map((a) => a.partition)
                console.log(`[Consumer] Partitions revoked: ${partitions.join(', ')}`)
                for (const partition of partitions) {
                    this.batchManager?.discardPartition(partition)
                }
            }
        })

        this.consumer.on('event.error', (err: any) => {
            console.error('[Consumer] Kafka error:', err)
        })

        // Connect and subscribe
        await this.connectConsumer()
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

        // Flush remaining data
        if (this.batchManager) {
            try {
                await this.batchManager.flush()
            } catch (error) {
                console.error('[Consumer] Error flushing on shutdown:', error)
            }
        }

        if (this.consumer) {
            this.consumer.disconnect()
        }
        if (this.producer) {
            await this.producer.disconnect()
        }

        console.log('[Consumer] Stopped')
    }

    /**
     * Main consume loop.
     *
     * Each iteration:
     *   1. Pull up to fetchBatchSize messages from Kafka
     *   2. Parse and validate
     *   3. Record into batch
     *   4. Check if batch should be flushed
     *
     * The `await consumeBatch()` call implicitly pauses consumption
     * during flush — no explicit "stop consuming" needed. Node.js
     * single-threaded event loop ensures sequential processing.
     */
    private async consumeLoop(): Promise<void> {
        while (!this.stopping) {
            try {
                // Pull messages from Kafka
                const messages = await this.consumeBatch(this.config.fetchBatchSize)

                if (messages.length > 0) {
                    await this.handleBatch(messages)
                }

                // Check flush thresholds
                if (this.batchManager!.shouldFlush()) {
                    await this.batchManager!.flush()
                }
            } catch (error) {
                console.error('[Consumer] Fatal error in consume loop:', error)
                // Crash the process — Kubernetes will restart, Kafka replays
                process.exit(1)
            }
        }
    }

    /**
     * Process a batch of raw Kafka messages:
     *   - Parse JSON + decompress
     *   - Validate schema and timestamps
     *   - Extract project_id from Kafka headers
     *   - Record into the current batch
     */
    private async handleBatch(messages: Message[]): Promise<void> {
        // Adapt node-rdkafka Message to our parser's expected format
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

        for (const message of parsed) {
            this.batchManager!.record(message)
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
            this.consumer!.on('ready', () => {
                console.log('[Consumer] Kafka consumer connected')
                resolve()
            })
            this.consumer!.on('event.error', (err: any) => {
                reject(err)
            })
            this.consumer!.connect()
        })
    }
}
