use std::time::Duration;

use serde::Deserialize;
use tracing::{instrument, warn, Span};

use crate::api::CaptureError;
use crate::extractors::extract_body_with_timeout;
use crate::events::recordings::RawRecording;
use crate::payload::decompress_payload;

/// Supports both single event and array of events in one request
#[derive(Debug, Deserialize)]
#[serde(untagged)]
pub enum RecordingPayload {
    Array(Vec<RawRecording>),
    One(Box<RawRecording>),
}

impl RecordingPayload {
    pub fn into_vec(self) -> Vec<RawRecording> {
        match self {
            RecordingPayload::Array(v) => v,
            RecordingPayload::One(e) => vec![*e],
        }
    }
}

/// Processes recording (session replay) payloads.
///
/// Handles the full payload pipeline: streaming body extraction with
/// per-chunk timeout, decompression, deserialization, and batch validation.
/// Returns the parsed events ready for `process_replay_events`.
#[instrument(skip_all, fields(batch_size))]
pub async fn handle_recording_payload(
    body: axum::body::Body,
    payload_size_limit: usize,
    chunk_timeout: Option<Duration>,
    chunk_size_kb: usize,
    path: &str,
) -> Result<Vec<RawRecording>, CaptureError> {
    let bytes = extract_body_with_timeout(
        body,
        payload_size_limit,
        chunk_timeout,
        chunk_size_kb,
        path,
    )
    .await?;

    let payload_str = decompress_payload(bytes, payload_size_limit)?;

    let payload: RecordingPayload = serde_json::from_str(&payload_str)
        .map_err(|e| CaptureError::InvalidPayload(e.to_string()))?;
    let events = payload.into_vec();

    if events.is_empty() {
        warn!("rejected empty recording batch");
        return Err(CaptureError::EmptyBatch);
    }

    Span::current().record("batch_size", events.len());
    metrics::counter!("capture_events_received_total").increment(events.len() as u64);

    Ok(events)
}
