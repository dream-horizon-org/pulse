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
        })
    }

    public async connect(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.producer.on('ready', () => {
                console.log('[KafkaProducer] Connected')
                resolve()
            })
            this.producer.on('event.error', (err) => {
                console.error('[KafkaProducer] Error:', err)
                reject(err)
            })
            this.producer.connect()
        })
    }

    public produce(topic: string, key: string, value: string): Promise<void> {
        return new Promise((resolve, reject) => {
            this.producer.produce(
                topic,
                null, // partition (null = use key-based partitioning)
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

    public flush(): Promise<void> {
        return new Promise((resolve) => {
            this.producer.flush(5000, () => resolve())
        })
    }

    public async disconnect(): Promise<void> {
        return new Promise((resolve) => {
            this.producer.disconnect(() => {
                console.log('[KafkaProducer] Disconnected')
                resolve()
            })
        })
    }
}
