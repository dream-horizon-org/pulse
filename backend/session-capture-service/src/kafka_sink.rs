use rdkafka::config::ClientConfig;
use rdkafka::message::OwnedHeaders;
use rdkafka::producer::{FutureProducer, FutureRecord};
use rdkafka::util::Timeout;
use std::time::Duration;

use crate::config::Config;
use crate::error::CaptureError;

/// Kafka producer that sends recording events to a topic, partitioned by session_id.
pub struct KafkaSink {
    producer: FutureProducer,
    topic: String,
}

impl KafkaSink {
    pub fn new(config: &Config) -> Self {
        let producer: FutureProducer = ClientConfig::new()
            .set("bootstrap.servers", &config.kafka_brokers)
            .set("message.timeout.ms", "10000")
            .set("linger.ms", "5")
            .set("compression.type", "snappy")
            .set("queue.buffering.max.messages", "100000")
            .create()
            .expect("Failed to create Kafka producer");

        Self {
            producer,
            topic: config.kafka_topic.clone(),
        }
    }

    /// Send a recording event to Kafka.
    /// - key: session_id (determines partition)
    /// - payload: JSON-serialized CapturedEvent
    /// - project_id: sent as a Kafka header for downstream consumers
    pub async fn send(
        &self,
        key: &str,
        payload: &str,
        project_id: &str,
    ) -> Result<(), CaptureError> {
        let headers = OwnedHeaders::new().insert(rdkafka::message::Header {
            key: "project_id",
            value: Some(project_id.as_bytes()),
        });

        let record = FutureRecord::to(&self.topic)
            .key(key)
            .payload(payload)
            .headers(headers);

        self.producer
            .send(record, Timeout::After(Duration::from_secs(10)))
            .await
            .map_err(|(err, _)| {
                tracing::error!("Kafka produce error: {err}");
                CaptureError::KafkaError(err.to_string())
            })?;

        Ok(())
    }
}
