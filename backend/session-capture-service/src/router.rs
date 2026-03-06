use std::future::ready;
use std::sync::Arc;
use std::time::Duration;

use axum::extract::DefaultBodyLimit;
use axum::http::{Method, StatusCode};
use axum::routing::get;
use axum::routing::post;
use axum::Router;
use metrics_exporter_prometheus::PrometheusHandle;
use tower_http::cors::{Any, CorsLayer};
use tower_http::trace::TraceLayer;

use crate::endpoint;
use crate::health::HealthRegistry;
use crate::metrics_middleware::{apply_request_timeout, track_metrics};
use crate::sinks;

pub const RECORDING_BODY_SIZE: usize = 25 * 1024 * 1024; // 25MB

#[derive(Clone)]
pub struct State {
    pub sink: Arc<dyn sinks::Event>,
    pub body_chunk_read_timeout: Option<Duration>,
    pub body_read_chunk_size_kb: usize,
}

pub fn create_router(
    state: State,
    request_timeout_seconds: Option<u64>,
    liveness: HealthRegistry,
    metrics_handle: PrometheusHandle,
) -> Router {
    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods([Method::POST, Method::OPTIONS])
        .allow_headers(Any);

    let mut router = Router::new()
        .route("/s", post(endpoint::capture))
        .route("/s/", post(endpoint::capture))
        .layer(DefaultBodyLimit::max(RECORDING_BODY_SIZE));

    router = router.route("/healthcheck", get(endpoint::healthcheck));
    router = router.route(
        "/_liveness",
        get(move || ready(liveness.get_status())),
    );
    router = router.route(
        "/_readiness",
        get(readiness_handler),
    );
    router = router.route(
        "/metrics",
        get(move || ready(metrics_handle.render())),
    );

    router = apply_request_timeout(router, request_timeout_seconds);

    router
        .layer(axum::middleware::from_fn(track_metrics))
        .layer(TraceLayer::new_for_http())
        .layer(cors)
        .with_state(state)
}

async fn readiness_handler() -> StatusCode {
    if std::path::Path::new("/tmp/shutdown").exists() {
        StatusCode::SERVICE_UNAVAILABLE
    } else {
        StatusCode::OK
    }
}
