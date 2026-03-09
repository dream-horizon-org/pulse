use async_trait::async_trait;
use rdkafka::config::ClientConfig;
use rdkafka::message::OwnedHeaders;
use rdkafka::producer::{FutureProducer, FutureRecord, Producer};
use rdkafka::util::Timeout;
use std::time::Duration;
use tracing::instrument;

use crate::api::CaptureError;
use crate::config::KafkaConfig;
use crate::health::HealthHandle;
use crate::sinks;

pub struct KafkaContext {
    liveness: HealthHandle,
}

impl rdkafka::ClientContext for KafkaContext {
    fn stats(&self, stats: rdkafka::Statistics) {
        let brokers_up = stats.brokers.values().any(|broker| broker.state == "UP");
        if brokers_up {
            self.liveness.report_healthy_blocking();
        }

        metrics::gauge!("capture_kafka_callback_queue_depth").set(stats.replyq as f64);
        metrics::gauge!("capture_kafka_producer_queue_depth").set(stats.msg_cnt as f64);
        metrics::gauge!("capture_kafka_producer_queue_depth_limit").set(stats.msg_max as f64);
        metrics::gauge!("capture_kafka_producer_queue_bytes").set(stats.msg_size as f64);
        metrics::gauge!("capture_kafka_producer_queue_bytes_limit").set(stats.msg_size_max as f64);

        let total_brokers = stats.brokers.len();
        let up_brokers = stats
            .brokers
            .values()
            .filter(|broker| broker.state == "UP")
            .count();
        let down_brokers = total_brokers.saturating_sub(up_brokers);
        metrics::gauge!("capture_kafka_any_brokers_down").set(if down_brokers > 0 { 1.0 } else { 0.0 });

        for (topic, topic_stats) in &stats.topics {
            metrics::gauge!(
                "capture_kafka_produce_avg_batch_size_bytes",
                "topic" => topic.clone()
            )
            .set(topic_stats.batchsize.avg as f64);
            metrics::gauge!(
                "capture_kafka_produce_avg_batch_size_events",
                "topic" => topic.clone()
            )
            .set(topic_stats.batchcnt.avg as f64);
        }

        for (_, broker_stats) in &stats.brokers {
            let id_string = format!("{}", broker_stats.nodeid);
            metrics::gauge!(
                "capture_kafka_broker_connected",
                "broker" => id_string.clone()
            )
            .set(if broker_stats.state == "UP" { 1.0 } else { 0.0 });

            if let Some(ref rtt) = broker_stats.rtt {
                metrics::gauge!(
                    "capture_kafka_produce_rtt_latency_us",
                    "quantile" => "p50",
                    "broker" => id_string.clone()
                )
                .set(rtt.p50 as f64);
                metrics::gauge!(
                    "capture_kafka_produce_rtt_latency_us",
                    "quantile" => "p90",
                    "broker" => id_string.clone()
                )
                .set(rtt.p90 as f64);
                metrics::gauge!(
                    "capture_kafka_produce_rtt_latency_us",
                    "quantile" => "p95",
                    "broker" => id_string.clone()
                )
                .set(rtt.p95 as f64);
                metrics::gauge!(
                    "capture_kafka_produce_rtt_latency_us",
                    "quantile" => "p99",
                    "broker" => id_string.clone()
                )
                .set(rtt.p99 as f64);
            }

            metrics::gauge!(
                "capture_kafka_broker_requests_pending",
                "broker" => id_string.clone()
            )
            .set(broker_stats.outbuf_cnt as f64);
            metrics::gauge!(
                "capture_kafka_broker_responses_awaiting",
                "broker" => id_string.clone()
            )
            .set(broker_stats.waitresp_cnt as f64);

            metrics::counter!(
                "capture_kafka_broker_tx_errors_total",
                "broker" => id_string.clone()
            )
            .absolute(broker_stats.txerrs);

            metrics::counter!(
                "capture_kafka_broker_rx_errors_total",
                "broker" => id_string.clone()
            )
            .absolute(broker_stats.rxerrs);

            metrics::counter!(
                "capture_kafka_broker_request_timeouts",
                "broker" => id_string
            )
            .absolute(broker_stats.req_timeouts);
        }
    }
}

pub struct KafkaSink {
    producer: FutureProducer<KafkaContext>,
    topic: String,
}

impl KafkaSink {
    pub async fn new(config: &KafkaConfig, liveness: HealthHandle) -> anyhow::Result<Self> {
        tracing::info!("Connecting to Kafka brokers at {}", config.kafka_hosts);

        let mut client_config = ClientConfig::new();
        client_config
            .set("bootstrap.servers", &config.kafka_hosts)
            .set("statistics.interval.ms", "10000")
            .set("linger.ms", config.kafka_producer_linger_ms.to_string())
            .set(
                "message.timeout.ms",
                config.kafka_message_timeout_ms.to_string(),
            )
            .set(
                "message.max.bytes",
                config.kafka_producer_message_max_bytes.to_string(),
            )
            .set("compression.codec", &config.kafka_compression_codec)
            .set(
                "queue.buffering.max.kbytes",
                (config.kafka_producer_queue_mib * 1024).to_string(),
            )
            .set(
                "message.send.max.retries",
                config.kafka_producer_max_retries.to_string(),
            )
            .set("acks", &config.kafka_producer_acks)
            .set(
                "metadata.max.age.ms",
                config.kafka_metadata_max_age_ms.to_string(),
            )
            .set(
                "topic.metadata.refresh.interval.ms",
                config.kafka_topic_metadata_refresh_interval_ms.to_string(),
            )
            .set(
                "socket.timeout.ms",
                config.kafka_socket_timeout_ms.to_string(),
            );

        if !config.kafka_client_id.is_empty() {
            client_config.set("client.id", &config.kafka_client_id);
        }

        if config.kafka_tls {
            client_config
                .set("security.protocol", "ssl")
                .set("enable.ssl.certificate.verification", "false");
        }

        tracing::debug!("rdkafka configuration: {client_config:?}");

        let context = KafkaContext { liveness: liveness.clone() };
        let producer: FutureProducer<KafkaContext> = client_config.create_with_context(context)?;

        if producer
            .client()
            .fetch_metadata(
                Some("__consumer_offsets"),
                Timeout::After(Duration::from_secs(10)),
            )
            .is_ok()
        {
            liveness.report_healthy().await;
            tracing::info!("Connected to Kafka brokers");
        }

        Ok(Self {
            producer,
            topic: config.kafka_topic.clone(),
        })
    }
}

#[async_trait]
impl sinks::Event for KafkaSink {
    #[instrument(skip_all)]
    async fn send(
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

        match self
            .producer
            .send(record, Timeout::After(Duration::from_secs(10)))
            .await
        {
            Ok(_) => Ok(()),
            Err((err, _)) => {
                let err_string = err.to_string();
                if err_string.contains("Message size too large")
                    || err_string.contains("MSG_SIZE_TOO_LARGE")
                {
                    tracing::error!(
                        key = key,
                        payload_size = payload.len(),
                        "Kafka message too large"
                    );
                    Err(CaptureError::EventTooBig(format!(
                        "Event payload too large for Kafka: {} bytes",
                        payload.len()
                    )))
                } else {
                    tracing::error!("Kafka produce error: {err}");
                    Err(CaptureError::RetryableSinkError)
                }
            }
        }
    }
}
