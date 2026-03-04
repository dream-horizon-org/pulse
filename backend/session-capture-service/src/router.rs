use axum::extract::DefaultBodyLimit;
use axum::routing::{get, post};
use axum::Router;
use std::sync::Arc;
use tower_http::cors::{Any, CorsLayer};

use crate::endpoint;
use crate::kafka_sink::KafkaSink;

pub fn create_router(kafka: Arc<KafkaSink>) -> Router {
    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers(Any);

    Router::new()
        .route("/s", post(endpoint::capture))
        .route("/s/", post(endpoint::capture))
        .route("/healthcheck", get(endpoint::healthcheck))
        .layer(DefaultBodyLimit::max(25 * 1024 * 1024)) // 25MB
        .layer(cors)
        .with_state(kafka)
}
