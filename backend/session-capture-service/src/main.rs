use std::sync::Arc;
use std::time::Duration;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

mod api;
mod config;
mod endpoint;
mod events;
mod extractors;
mod health;
mod metrics_middleware;
mod payload;
mod prometheus;
mod router;
mod sinks;

#[tokio::main]
async fn main() {
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "pulse_session_capture=info".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    let metrics_handle = prometheus::setup_metrics_recorder();

    let config = config::Config::from_env();
    let addr = format!("0.0.0.0:{}", config.port);

    let liveness = health::HealthRegistry::new("liveness");
    let kafka_health = liveness
        .register("rdkafka".to_string(), Duration::from_secs(30))
        .await;

    let sink: Arc<dyn sinks::Event> = Arc::new(
        sinks::kafka::KafkaSink::new(&config.kafka, kafka_health)
            .expect("Failed to create Kafka producer"),
    );

    let state = router::State {
        sink,
        body_chunk_read_timeout: config
            .body_chunk_read_timeout_seconds
            .map(Duration::from_secs),
        body_read_chunk_size_kb: config.body_read_chunk_size_kb,
    };

    let app = router::create_router(
        state,
        config.request_timeout_seconds,
        liveness,
        metrics_handle,
    );

    tracing::info!("Pulse session capture service listening on {}", addr);
    let listener = tokio::net::TcpListener::bind(&addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}
