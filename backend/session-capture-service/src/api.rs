use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::{Deserialize, Serialize};
use serde_json::json;

#[derive(Debug, PartialEq, Eq, Deserialize, Serialize)]
pub enum CaptureResponseCode {
    Ok = 1,
}

#[derive(Debug, PartialEq, Eq, Deserialize, Serialize)]
pub struct CaptureResponse {
    pub status: CaptureResponseCode,
}

impl IntoResponse for CaptureResponse {
    fn into_response(self) -> Response {
        (StatusCode::OK, Json(self)).into_response()
    }
}

#[derive(Debug)]
pub enum CaptureError {
    EmptyBatch,
    MissingProjectId,
    MissingSessionId,
    InvalidSessionId,
    MissingSnapshotData,
    InvalidPayload(String),
    DecompressionError(String),
    PayloadTooBig(String),
    BodyReadTimeout,
    RequestDecodingError(String),
    EventTooBig(String),
    RetryableSinkError,
}

impl std::fmt::Display for CaptureError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::EmptyBatch => write!(f, "empty batch"),
            Self::MissingProjectId => write!(f, "missing project_id"),
            Self::MissingSessionId => write!(f, "missing session_id"),
            Self::InvalidSessionId => write!(f, "invalid session_id"),
            Self::MissingSnapshotData => write!(f, "missing snapshot_data"),
            Self::InvalidPayload(e) => write!(f, "invalid payload: {e}"),
            Self::DecompressionError(e) => write!(f, "decompression failed: {e}"),
            Self::PayloadTooBig(e) => write!(f, "payload too big: {e}"),
            Self::BodyReadTimeout => write!(f, "body read timeout"),
            Self::RequestDecodingError(e) => write!(f, "request decoding error: {e}"),
            Self::EventTooBig(e) => write!(f, "event too big: {e}"),
            Self::RetryableSinkError => write!(f, "retryable sink error"),
        }
    }
}

impl IntoResponse for CaptureError {
    fn into_response(self) -> Response {
        let (status, message) = match &self {
            CaptureError::EmptyBatch => (StatusCode::BAD_REQUEST, "Empty batch"),
            CaptureError::MissingProjectId => (StatusCode::BAD_REQUEST, "Missing project_id"),
            CaptureError::MissingSessionId => (StatusCode::BAD_REQUEST, "Missing session_id"),
            CaptureError::InvalidSessionId => (StatusCode::BAD_REQUEST, "Invalid session_id"),
            CaptureError::MissingSnapshotData => (StatusCode::BAD_REQUEST, "Missing snapshot_data"),
            CaptureError::InvalidPayload(_) => (StatusCode::BAD_REQUEST, "Invalid payload"),
            CaptureError::DecompressionError(_) => {
                (StatusCode::BAD_REQUEST, "Decompression failed")
            }
            CaptureError::PayloadTooBig(_) => {
                (StatusCode::PAYLOAD_TOO_LARGE, "Payload too large")
            }
            CaptureError::BodyReadTimeout => {
                (StatusCode::REQUEST_TIMEOUT, "Body read timeout")
            }
            CaptureError::RequestDecodingError(_) => {
                (StatusCode::BAD_REQUEST, "Request decoding error")
            }
            CaptureError::EventTooBig(_) => {
                (StatusCode::PAYLOAD_TOO_LARGE, "Event too large for sink")
            }
            CaptureError::RetryableSinkError => {
                (StatusCode::SERVICE_UNAVAILABLE, "Service temporarily unavailable")
            }
        };

        tracing::warn!("Capture error: {self}");
        (status, Json(json!({"status": 0, "error": message}))).into_response()
    }
}
