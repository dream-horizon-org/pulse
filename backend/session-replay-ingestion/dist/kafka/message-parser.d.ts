import { ParsedMessageData } from './types';
interface RawKafkaMessage {
    value: Buffer | null;
    timestamp: number;
    partition: number;
    topic: string;
    size: number;
    offset: number;
    headers?: Array<{
        [key: string]: Buffer;
    }>;
}
/**
 * Parses raw Kafka messages into structured ParsedMessageData.
 * - Decompresses gzip if needed
 * - Validates JSON structure
 * - Extracts session_id, snapshot_items, user_id
 * - Reads project_id from Kafka headers
 * - Filters out events with invalid timestamps
 */
export declare class KafkaMessageParser {
    private readonly dropCounts;
    parseBatch(messages: RawKafkaMessage[]): Promise<ParsedMessageData[]>;
    private parseMessage;
    private drop;
    /**
     * Returns and resets the drop counters. Useful for periodic logging.
     */
    getAndResetDropCounts(): Map<string, number>;
    private isGzipped;
}
export {};
