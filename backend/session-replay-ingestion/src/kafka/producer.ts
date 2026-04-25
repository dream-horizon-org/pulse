import { Kafka, Producer } from 'kafkajs'

export class KafkaProducer {
    private producer: Producer
    private pending: Array<{ topic: string; messages: Array<{ key: string; value: string }> }> = []

    constructor(kafka: Kafka) {
        this.producer = kafka.producer()
    }

    public async connect(): Promise<void> {
        await this.producer.connect()
        console.log('[KafkaProducer] Connected')
    }

    public queueMessages(topic: string, messages: Array<{ key: string; value: string }>): void {
        this.pending.push({ topic, messages })
    }

    public async flush(): Promise<void> {
        if (this.pending.length === 0) return
        const batches = this.pending.splice(0)
        await Promise.all(
            batches.map(({ topic, messages }) =>
                this.producer.send({
                    topic,
                    messages: messages.map((m) => ({ key: m.key, value: m.value })),
                })
            )
        )
    }

    public async disconnect(): Promise<void> {
        await this.producer.disconnect()
        console.log('[KafkaProducer] Disconnected')
    }
}
