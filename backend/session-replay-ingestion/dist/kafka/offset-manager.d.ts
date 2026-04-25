import { PartitionOffset } from '../types';
interface TopicPartitionOffset {
    topic: string;
    partition: number;
    offset: number;
}
type CommitOffsetsCallback = (offsets: TopicPartitionOffset[]) => void | Promise<void>;
/**
 * Tracks the highest processed Kafka offset per partition.
 * On flush, commits these offsets so the consumer group makes progress.
 *
 * Uses manual offset storage (enable.auto.offset.store=false) with
 * auto-commit (enable.auto.commit=true) — we control *what* gets
 * committed, librdkafka controls *when*.
 */
export declare class KafkaOffsetManager {
    private readonly commitOffsets;
    private readonly topic;
    private partitionOffsets;
    constructor(commitOffsets: CommitOffsetsCallback, topic: string);
    trackOffset({ partition, offset }: PartitionOffset): void;
    discardPartition(partition: number): void;
    commit(): Promise<void>;
}
export {};
