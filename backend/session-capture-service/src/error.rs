use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde_json::json;

#[derive(Debug)]
pub enum CaptureError {
    EmptyBatch,
    MissingProjectId,
    MissingSessionId,
    InvalidSessionId,
    MissingUserId,
    MissingSnapshotData,
    InvalidPayload(String),
    DecompressionError(String),
    KafkaError(String),
}

impl std::fmt::Display for CaptureError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::EmptyBatch => write!(f, "empty batch"),
            Self::MissingProjectId => write!(f, "missing project_id"),
            Self::MissingSessionId => write!(f, "missing session_id"),
            Self::InvalidSessionId => write!(f, "invalid session_id"),
            Self::MissingUserId => write!(f, "missing user_id"),
            Self::MissingSnapshotData => write!(f, "missing snapshot_data"),
            Self::InvalidPayload(e) => write!(f, "invalid payload: {e}"),
            Self::DecompressionError(e) => write!(f, "decompression failed: {e}"),
            Self::KafkaError(e) => write!(f, "kafka error: {e}"),
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
            CaptureError::MissingUserId => (StatusCode::BAD_REQUEST, "Missing user_id"),
            CaptureError::MissingSnapshotData => (StatusCode::BAD_REQUEST, "Missing snapshot_data"),
            CaptureError::InvalidPayload(_) => (StatusCode::BAD_REQUEST, "Invalid payload"),
            CaptureError::DecompressionError(_) => {
                (StatusCode::BAD_REQUEST, "Decompression failed")
            }
            CaptureError::KafkaError(_) => {
                (StatusCode::INTERNAL_SERVER_ERROR, "Internal server error")
            }
        };

        tracing::warn!("Capture error: {self}");
        (status, Json(json!({"status": 0, "error": message}))).into_response()
    }
}
