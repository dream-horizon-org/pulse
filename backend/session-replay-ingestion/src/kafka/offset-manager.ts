import { PartitionOffset } from '../types'

interface TopicPartitionOffset {
    topic: string
    partition: number
    offset: number
}

type CommitOffsetsCallback = (offsets: TopicPartitionOffset[]) => void | Promise<void>

/**
 * Tracks the highest processed Kafka offset per partition.
 * On flush, commits these offsets so the consumer group makes progress.
 *
 * Uses manual offset storage (enable.auto.offset.store=false) with
 * auto-commit (enable.auto.commit=true) — we control *what* gets
 * committed, librdkafka controls *when*.
 */
export class KafkaOffsetManager {
    private partitionOffsets: Map<number, number> = new Map()

    constructor(
        private readonly commitOffsets: CommitOffsetsCallback,
        private readonly topic: string
    ) {}

    public trackOffset({ partition, offset }: PartitionOffset): void {
        this.partitionOffsets.set(partition, offset + 1)
    }

    public discardPartition(partition: number): void {
        this.partitionOffsets.delete(partition)
    }

    public async commit(): Promise<void> {
        const offsets: TopicPartitionOffset[] = []

        for (const [partition, offset] of this.partitionOffsets.entries()) {
            offsets.push({ topic: this.topic, partition, offset })
        }

        if (offsets.length > 0) {
            console.log(`[OffsetManager] Committing offsets for ${offsets.length} partitions`)
            await this.commitOffsets(offsets)
            this.partitionOffsets.clear()
        }
    }
}
