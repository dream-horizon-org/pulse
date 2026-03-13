pub struct Config {
    pub port: u16,
    pub kafka: KafkaConfig,
    pub request_timeout_seconds: Option<u64>,
    pub body_chunk_read_timeout_seconds: Option<u64>,
    pub body_read_chunk_size_kb: usize,
}

pub struct KafkaConfig {
    pub kafka_hosts: String,
    pub kafka_topic: String,
    pub kafka_producer_linger_ms: u32,
    pub kafka_producer_queue_mib: u32,
    pub kafka_message_timeout_ms: u32,
    pub kafka_producer_message_max_bytes: u32,
    pub kafka_compression_codec: String,
    pub kafka_producer_max_retries: u32,
    pub kafka_producer_acks: String,
    pub kafka_metadata_max_age_ms: u32,
    pub kafka_topic_metadata_refresh_interval_ms: u32,
    pub kafka_socket_timeout_ms: u32,
    pub kafka_tls: bool,
    pub kafka_client_id: String,
}

fn env_or<T: std::str::FromStr>(key: &str, default: T) -> T {
    std::env::var(key)
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(default)
}

impl Config {
    pub fn from_env() -> Self {
        Self {
            port: env_or("PORT", 3400),
            kafka: KafkaConfig::from_env(),
            request_timeout_seconds: std::env::var("REQUEST_TIMEOUT_SECONDS")
                .ok()
                .and_then(|v| v.parse().ok()),
            body_chunk_read_timeout_seconds: std::env::var("BODY_CHUNK_READ_TIMEOUT_SECONDS")
                .ok()
                .and_then(|v| v.parse().ok()),
            body_read_chunk_size_kb: env_or("BODY_READ_CHUNK_SIZE_KB", 256),
        }
    }
}

impl KafkaConfig {
    pub fn from_env() -> Self {
        Self {
            kafka_hosts: std::env::var("KAFKA_BROKERS")
                .unwrap_or_else(|_| "localhost:9092".into()),
            kafka_topic: std::env::var("KAFKA_TOPIC")
                .unwrap_or_else(|_| "session_recording_events".into()),
            kafka_producer_linger_ms: env_or("KAFKA_PRODUCER_LINGER_MS", 20),
            kafka_producer_queue_mib: env_or("KAFKA_PRODUCER_QUEUE_MIB", 50),
            kafka_message_timeout_ms: env_or("KAFKA_MESSAGE_TIMEOUT_MS", 20000),
            kafka_producer_message_max_bytes: env_or("KAFKA_PRODUCER_MESSAGE_MAX_BYTES", 26214400),
            kafka_compression_codec: std::env::var("KAFKA_COMPRESSION_CODEC")
                .unwrap_or_else(|_| "snappy".into()),
            kafka_producer_max_retries: env_or("KAFKA_PRODUCER_MAX_RETRIES", 2),
            kafka_producer_acks: std::env::var("KAFKA_PRODUCER_ACKS")
                .unwrap_or_else(|_| "all".into()),
            kafka_metadata_max_age_ms: env_or("KAFKA_METADATA_MAX_AGE_MS", 60000),
            kafka_topic_metadata_refresh_interval_ms: env_or(
                "KAFKA_TOPIC_METADATA_REFRESH_INTERVAL_MS",
                20000,
            ),
            kafka_socket_timeout_ms: env_or("KAFKA_SOCKET_TIMEOUT_MS", 60000),
            kafka_tls: env_or("KAFKA_TLS", false),
            kafka_client_id: std::env::var("KAFKA_CLIENT_ID").unwrap_or_default(),
        }
    }
}
