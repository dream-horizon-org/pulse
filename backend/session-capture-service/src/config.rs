pub struct Config {
    pub port: u16,
    pub kafka_brokers: String,
    pub kafka_topic: String,
    pub max_body_size: usize,
}

impl Config {
    pub fn from_env() -> Self {
        Self {
            port: std::env::var("PORT")
                .unwrap_or_else(|_| "3400".into())
                .parse()
                .expect("PORT must be a number"),
            kafka_brokers: std::env::var("KAFKA_BROKERS")
                .unwrap_or_else(|_| "localhost:9092".into()),
            kafka_topic: std::env::var("KAFKA_TOPIC")
                .unwrap_or_else(|_| "session_recording_events".into()),
            max_body_size: 25 * 1024 * 1024, // 25MB
        }
    }
}
