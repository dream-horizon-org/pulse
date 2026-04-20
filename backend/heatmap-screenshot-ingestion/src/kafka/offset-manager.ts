/**
 * Mirror of `backend/session-replay-ingestion/src/kafka/offset-manager.ts`.
 */
interface TopicPartitionOffset {
  topic: string;
  partition: number;
  offset: number;
}

type CommitOffsetsCallback = (
  offsets: TopicPartitionOffset[],
) => void | Promise<void>;

export class KafkaOffsetManager {
  private partitionOffsets = new Map<number, number>();

  constructor(
    private readonly commitOffsets: CommitOffsetsCallback,
    private readonly topic: string,
  ) {}

  public trackOffset(partition: number, offset: number): void {
    this.partitionOffsets.set(partition, offset + 1);
  }

  public async commit(): Promise<void> {
    const offsets: TopicPartitionOffset[] = [];
    for (const [partition, offset] of this.partitionOffsets.entries()) {
      offsets.push({ topic: this.topic, partition, offset });
    }
    if (offsets.length > 0) {
      console.log(
        `[OffsetManager] Committing offsets for ${offsets.length} partition(s)`,
      );
      await this.commitOffsets(offsets);
      this.partitionOffsets.clear();
    }
  }
}
