use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::{Duration, Instant};

use axum::body::Body;
use axum::extract::{MatchedPath, Request};
use axum::http::StatusCode;
use axum::middleware::Next;
use axum::response::IntoResponse;
use axum::routing::Router;

static ACTIVE_CONNECTIONS: AtomicUsize = AtomicUsize::new(0);

struct ConnectionGuard;

impl Drop for ConnectionGuard {
    fn drop(&mut self) {
        let connections = ACTIVE_CONNECTIONS
            .fetch_sub(1, Ordering::Relaxed)
            .saturating_sub(1);
        metrics::gauge!("capture_active_connections").set(connections as f64);
    }
}

pub async fn track_metrics(req: Request<Body>, next: Next) -> impl IntoResponse {
    let start = Instant::now();

    let path = if let Some(matched_path) = req.extensions().get::<MatchedPath>() {
        matched_path.as_str().to_owned()
    } else {
        req.uri().path().to_owned()
    };

    let method = req.method().clone();

    let connections = ACTIVE_CONNECTIONS.fetch_add(1, Ordering::Relaxed) + 1;
    metrics::gauge!("capture_active_connections").set(connections as f64);
    let _guard = ConnectionGuard;

    let response = next.run(req).await;

    let latency = start.elapsed().as_secs_f64();
    let status = response.status().as_u16().to_string();

    let labels = [
        ("method", method.to_string()),
        ("path", path),
        ("status", status),
    ];

    metrics::counter!("http_requests_total", &labels).increment(1);
    metrics::histogram!("http_requests_duration_seconds", &labels).record(latency);

    response
}

pub fn apply_request_timeout<S>(
    router: Router<S>,
    request_timeout_seconds: Option<u64>,
) -> Router<S>
where
    S: Clone + Send + Sync + 'static,
{
    if let Some(timeout_secs) = request_timeout_seconds {
        let timeout_duration = Duration::from_secs(timeout_secs);
        tracing::info!(
            "Applying request timeout middleware with duration: {:?}",
            timeout_duration
        );

        return router.layer(axum::middleware::from_fn(
            move |req: axum::extract::Request, next: axum::middleware::Next| async move {
                let start = Instant::now();
                let method = req.method().to_string();
                let path = req.uri().path().to_string();
                let client_ip = req
                    .headers()
                    .get("X-Forwarded-For")
                    .and_then(|v| v.to_str().ok())
                    .and_then(|s| s.split(',').next())
                    .map_or_else(|| "UNKNOWN".to_string(), |s| s.trim().to_string());
                let content_length = req
                    .headers()
                    .get("Content-Length")
                    .and_then(|v| v.to_str().ok())
                    .map_or_else(|| "UNKNOWN".to_string(), |s| s.to_string());
                let user_agent = req
                    .headers()
                    .get("User-Agent")
                    .and_then(|v| v.to_str().ok())
                    .map_or_else(|| "UNKNOWN".to_string(), |s| s.to_string());

                match tokio::time::timeout(timeout_duration, next.run(req)).await {
                    Ok(response) => {
                        let mut tags = vec![
                            ("method", method.clone()),
                            ("path", path.clone()),
                            ("status", response.status().as_u16().to_string()),
                        ];
                        let elapsed = start.elapsed();
                        if elapsed.as_secs() > timeout_secs {
                            tags.push(("threshold", "exceeded".to_string()));
                        } else {
                            tags.push(("threshold", "respected".to_string()));
                        }
                        metrics::counter!("middleware_pass_total", &tags).increment(1);
                        response
                    }
                    Err(_) => {
                        let elapsed = start.elapsed();
                        let tags = vec![
                            ("method", method.clone()),
                            ("path", path.clone()),
                        ];
                        metrics::counter!("middleware_request_timed_out_total", &tags).increment(1);

                        tracing::warn!(
                            method = method,
                            path = path,
                            client_ip = client_ip,
                            content_length = content_length,
                            user_agent = user_agent,
                            timeout_threshold_seconds = timeout_secs,
                            elapsed_seconds = elapsed.as_secs_f64(),
                            "Request timed out"
                        );

                        (StatusCode::GATEWAY_TIMEOUT, "Request timeout").into_response()
                    }
                }
            },
        ));
    }

    tracing::info!("No request timeout middleware applied");
    router
}
