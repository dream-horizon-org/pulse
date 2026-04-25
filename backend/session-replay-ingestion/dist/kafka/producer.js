"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.KafkaProducer = void 0;
class KafkaProducer {
    constructor(kafka) {
        this.pending = [];
        this.producer = kafka.producer({ idempotent: true });
    }
    async connect() {
        await this.producer.connect();
        console.log('[KafkaProducer] Connected');
    }
    queueMessages(topic, messages) {
        this.pending.push({ topic, messages });
    }
    async flush() {
        if (this.pending.length === 0)
            return;
        const batches = this.pending.splice(0);
        await Promise.all(batches.map(({ topic, messages }) => this.producer.send({
            topic,
            messages: messages.map((m) => ({ key: m.key, value: m.value })),
        })));
    }
    async disconnect() {
        await this.producer.disconnect();
        console.log('[KafkaProducer] Disconnected');
    }
}
exports.KafkaProducer = KafkaProducer;
//# sourceMappingURL=producer.js.map