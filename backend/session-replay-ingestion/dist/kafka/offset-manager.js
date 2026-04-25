"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.KafkaOffsetManager = void 0;
/**
 * Tracks the highest processed Kafka offset per partition.
 * On flush, commits these offsets so the consumer group makes progress.
 *
 * Uses manual offset storage (enable.auto.offset.store=false) with
 * auto-commit (enable.auto.commit=true) — we control *what* gets
 * committed, librdkafka controls *when*.
 */
class KafkaOffsetManager {
    constructor(commitOffsets, topic) {
        this.commitOffsets = commitOffsets;
        this.topic = topic;
        this.partitionOffsets = new Map();
    }
    trackOffset({ partition, offset }) {
        this.partitionOffsets.set(partition, offset + 1);
    }
    discardPartition(partition) {
        this.partitionOffsets.delete(partition);
    }
    async commit() {
        const offsets = [];
        for (const [partition, offset] of this.partitionOffsets.entries()) {
            offsets.push({ topic: this.topic, partition, offset });
        }
        if (offsets.length > 0) {
            console.log(`[OffsetManager] Committing offsets for ${offsets.length} partitions`);
            await this.commitOffsets(offsets);
            this.partitionOffsets.clear();
        }
    }
}
exports.KafkaOffsetManager = KafkaOffsetManager;
//# sourceMappingURL=offset-manager.js.map