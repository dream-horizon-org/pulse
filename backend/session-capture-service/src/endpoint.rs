use axum::body::Bytes;
use axum::extract::State;
use axum::http::{HeaderMap, StatusCode};
use axum::Json;
use flate2::read::GzDecoder;
use serde_json::{json, Value};
use std::io::Read;
use std::sync::Arc;
use uuid::Uuid;

use crate::error::CaptureError;
use crate::kafka_sink::KafkaSink;
use crate::recording::{self, RecordingPayload};

/// POST /s/ — Capture recording events from mobile SDK
///
/// Flow:
///   1. Decompress gzip if Content-Encoding header present
///   2. Parse JSON → RecordingPayload (single or array of events)
///   3. Validate: project_id, session_id, user_id, snapshot_data
///   4. Serialize to CapturedEvent JSON
///   5. Produce to Kafka (partitioned by session_id, project_id in header)
///   6. Return 200 OK
pub async fn capture(
    State(kafka): State<Arc<KafkaSink>>,
    headers: HeaderMap,
    body: Bytes,
) -> Result<Json<Value>, CaptureError> {
    // Step 1: Decompress if gzip
    let payload_str = if is_gzipped(&body) || has_gzip_header(&headers) {
        let mut decoder = GzDecoder::new(&body[..]);
        let mut s = String::new();
        decoder
            .read_to_string(&mut s)
            .map_err(|e| CaptureError::DecompressionError(e.to_string()))?;
        s
    } else {
        String::from_utf8(body.to_vec())
            .map_err(|e| CaptureError::InvalidPayload(e.to_string()))?
    };

    // Step 2: Parse JSON
    let payload: RecordingPayload = serde_json::from_str(&payload_str)
        .map_err(|e| CaptureError::InvalidPayload(e.to_string()))?;
    let events = payload.into_vec();

    // Step 3: Validate and extract
    let validated = recording::validate_and_extract(events)?;

    // Step 4: Serialize for Kafka
    // The inner `data` field is a JSON string so the Node.js consumer
    // can parse it in the same way.
    let inner_data = json!({
        "event": "snapshot_items",
        "properties": {
            "user_id": validated.user_id,
            "session_id": validated.session_id,
            "snapshot_items": validated.snapshot_items,
            "snapshot_source": validated.snapshot_source,
            "lib": validated.snapshot_library,
        }
    });

    let kafka_message = json!({
        "uuid": Uuid::now_v7().to_string(),
        "user_id": validated.user_id,
        "data": inner_data.to_string(),
        "event": "snapshot_items",
    });

    // Step 5: Produce to Kafka
    kafka
        .send(
            &validated.session_id,
            &kafka_message.to_string(),
            &validated.project_id,
        )
        .await?;

    tracing::debug!(
        session_id = %validated.session_id,
        project_id = %validated.project_id,
        items = validated.snapshot_items.len(),
        "Recording event captured"
    );

    Ok(Json(json!({"status": 1})))
}

/// GET /healthcheck
pub async fn healthcheck() -> (StatusCode, &'static str) {
    (StatusCode::OK, "ok")
}

fn is_gzipped(bytes: &[u8]) -> bool {
    bytes.len() >= 2 && bytes[0] == 0x1f && bytes[1] == 0x8b
}

fn has_gzip_header(headers: &HeaderMap) -> bool {
    headers
        .get("content-encoding")
        .and_then(|v| v.to_str().ok())
        .map(|v| v.contains("gzip"))
        .unwrap_or(false)
}
