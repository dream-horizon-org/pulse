use std::sync::Arc;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

mod config;
mod endpoint;
mod error;
mod kafka_sink;
mod recording;
mod router;

#[tokio::main]
async fn main() {
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "pulse_session_capture=info".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    let config = config::Config::from_env();
    let addr = format!("0.0.0.0:{}", config.port);

    tracing::info!("Connecting to Kafka at {}", config.kafka_brokers);
    let kafka = Arc::new(kafka_sink::KafkaSink::new(&config));

    let app = router::create_router(kafka);

    tracing::info!("Pulse session capture service listening on {}", addr);
    let listener = tokio::net::TcpListener::bind(&addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}
