import Kafka from 'node-rdkafka'

/**
 * Simple Kafka producer wrapper for producing session metadata
 * to the ClickHouse ingestion topic.
 */
export class KafkaProducer {
    private producer: Kafka.HighLevelProducer

    constructor(brokers: string) {
        this.producer = new Kafka.HighLevelProducer({
            'metadata.broker.list': brokers,
            'linger.ms': 5,
            'batch.num.messages': 1000,
            'compression.type': 'snappy',
            'enable.idempotence': true,
        })
    }

    public async connect(): Promise<void> {
        this.producer.on('event.error', (err) => {
            console.error('[KafkaProducer] Error:', err)
        })

        return new Promise((resolve, reject) => {
            this.producer.connect(undefined, (error, data) => {
                if (error) {
                    reject(error)
                } else {
                    console.log('[KafkaProducer] Connected', { brokers: data?.brokers })
                    resolve()
                }
            })
        })
    }

    public produce(topic: string, key: string, value: string): Promise<void> {
        return new Promise((resolve, reject) => {
            this.producer.produce(
                topic,
                null,
                Buffer.from(value),
                key,
                Date.now(),
                (err) => {
                    if (err) reject(err)
                    else resolve()
                }
            )
        })
    }

    /**
     * Queue multiple messages into the producer's internal buffer without
     * awaiting each one individually. All messages are buffered synchronously
     * via node-rdkafka's internal queue, then a single flush() sends them.
     * This avoids N sequential async round-trips.
     */
    public queueMessages(topic: string, messages: Array<{ key: string; value: string }>): void {
        for (const msg of messages) {
            this.producer.produce(
                topic,
                null,
                Buffer.from(msg.value),
                msg.key,
                Date.now(),
                (err) => {
                    if (err) console.error('[KafkaProducer] Queue error:', err)
                }
            )
        }
    }

    public flush(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.producer.flush(10000, (err) => {
                if (err) {
                    reject(err)
                } else {
                    resolve()
                }
            })
        })
    }

    public async disconnect(): Promise<void> {
        await this.flush()
        return new Promise((resolve, reject) => {
            this.producer.disconnect((err) => {
                if (err) {
                    console.error('[KafkaProducer] Disconnect error:', err)
                    reject(err)
                } else {
                    console.log('[KafkaProducer] Disconnected')
                    resolve()
                }
            })
        })
    }
}
