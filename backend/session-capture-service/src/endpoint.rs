use axum::body::Body;
use axum::debug_handler;
use axum::extract::{MatchedPath, State};
use axum::http::StatusCode;
use tracing::instrument;

use crate::api::{CaptureError, CaptureResponse, CaptureResponseCode};
use crate::events::recordings;
use crate::payload::handle_recording_payload;
use crate::router::{self, RECORDING_BODY_SIZE};

#[instrument(skip_all)]
#[debug_handler]
pub async fn capture(
    State(state): State<router::State>,
    path: MatchedPath,
    body: Body,
) -> Result<CaptureResponse, CaptureError> {
    let events = handle_recording_payload(
        body,
        RECORDING_BODY_SIZE,
        state.body_chunk_read_timeout,
        state.body_read_chunk_size_kb,
        path.as_str(),
    )
    .await?;

    recordings::process_replay_events(state.sink, events).await?;

    Ok(CaptureResponse {
        status: CaptureResponseCode::Ok,
    })
}

pub async fn healthcheck() -> (StatusCode, &'static str) {
    (StatusCode::OK, "ok")
}
