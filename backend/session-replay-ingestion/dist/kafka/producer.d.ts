import { Kafka } from 'kafkajs';
export declare class KafkaProducer {
    private producer;
    private pending;
    constructor(kafka: Kafka);
    connect(): Promise<void>;
    queueMessages(topic: string, messages: Array<{
        key: string;
        value: string;
    }>): void;
    flush(): Promise<void>;
    disconnect(): Promise<void>;
}
