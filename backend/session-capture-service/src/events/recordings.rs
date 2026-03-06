use std::sync::Arc;

use serde::Deserialize;
use serde_json::{json, Value};
use tracing::{instrument, Span};
use uuid::Uuid;

use crate::api::CaptureError;
use crate::sinks;

/// Raw recording event from the mobile SDK.
#[derive(Debug, Deserialize)]
pub struct RawRecording {
    #[allow(dead_code)]
    pub event: Option<String>,

    pub user_id: Option<Value>,

    pub project_id: Option<String>,

    #[serde(default)]
    pub properties: RecordingProperties,
}

#[derive(Debug, Default, Deserialize)]
pub struct RecordingProperties {
    pub session_id: Option<Value>,

    pub snapshot_data: Option<Value>,

    pub snapshot_source: Option<Value>,
}

impl RawRecording {
    fn extract_user_id(&self) -> Option<String> {
        let value = match &self.user_id {
            None | Some(Value::Null) => return None,
            Some(id) => id,
        };

        let s = value
            .as_str()
            .map(|s| s.to_owned())
            .unwrap_or_else(|| value.to_string());

        if s.is_empty() {
            None
        } else {
            Some(s.chars().take(200).collect())
        }
    }
}

/// Process recording (session replay) events with ownership-based extraction.
///
/// This function:
/// 1. Validates the batch (non-empty, required fields present)
/// 2. Extracts metadata from the first event using `.take()` (no clones)
/// 3. Collects snapshot_data from all events
/// 4. Serializes to Kafka format using `serialize_snapshot_data_sync`
/// 5. Sends to the sink (Kafka)
#[instrument(skip_all, fields(events = events.len(), session_id, project_id))]
pub async fn process_replay_events(
    sink: Arc<dyn sinks::Event>,
    events: Vec<RawRecording>,
) -> Result<(), CaptureError> {
    // Extract metadata from first event by consuming the vec via iterator (no clones!)
    // We split off the first event to extract metadata, then iterate over the rest
    let mut events_iter = events.into_iter();
    let mut first_event = events_iter.next().ok_or(CaptureError::EmptyBatch)?;

    let project_id = first_event
        .project_id
        .take()
        .ok_or(CaptureError::MissingProjectId)?;

    let user_id = first_event
        .extract_user_id()
        .unwrap_or_default();

    // Take session_id by ownership (no clone!)
    let session_id = first_event
        .properties
        .session_id
        .take()
        .ok_or(CaptureError::MissingSessionId)?;

    let session_id_str = session_id
        .as_str()
        .ok_or(CaptureError::InvalidSessionId)?;

    if session_id_str.len() > 70
        || !session_id_str
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '-')
    {
        return Err(CaptureError::InvalidSessionId);
    }
    Span::current().record("session_id", session_id_str);
    Span::current().record("project_id", &project_id);

    let session_id = session_id_str.to_string();

    // Take snapshot_source by ownership (no clone!)
    let snapshot_source = first_event
        .properties
        .snapshot_source
        .take()
        .and_then(|v| v.as_str().map(|s| s.to_string()))
        .unwrap_or_else(|| "mobile".to_string());

    // Collect snapshot_data from all events by taking ownership (no clone!)
    // Process first event's snapshot_data, then remaining events separately
    let mut snapshot_items: Vec<Value> = Vec::new();

    let Some(snapshot_data) = first_event.properties.snapshot_data.take() else {
        return Err(CaptureError::MissingSnapshotData);
    };
    match snapshot_data {
        Value::Array(mut arr) => snapshot_items.append(&mut arr),
        Value::Object(obj) => snapshot_items.push(Value::Object(obj)),
        _ => return Err(CaptureError::MissingSnapshotData),
    }

    // Process remaining events' snapshot_data
    for mut event in events_iter {
        let Some(snapshot_data) = event.properties.snapshot_data.take() else {
            return Err(CaptureError::MissingSnapshotData);
        };
        match snapshot_data {
            Value::Array(mut arr) => snapshot_items.append(&mut arr),
            Value::Object(obj) => snapshot_items.push(Value::Object(obj)),
            _ => return Err(CaptureError::MissingSnapshotData),
        }
    }

    // Serialize synchronously -- benchmarked to be faster than spawn_blocking
    // under high concurrency (50-100+ requests)
    let serialized_data = serialize_snapshot_data_sync(
        &user_id,
        &session_id,
        &snapshot_source,
        &snapshot_items,
    );

    let kafka_message = json!({
        "uuid": Uuid::now_v7().to_string(),
        "user_id": user_id,
        "data": serialized_data,
        "event": "snapshot_items",
    });

    sink.send(
        &session_id,
        &kafka_message.to_string(),
        &project_id,
    )
    .await?;

    tracing::debug!(
        session_id = %session_id,
        project_id = %project_id,
        items = snapshot_items.len(),
        "Recording event captured"
    );

    Ok(())
}

/// Synchronously serialize snapshot data to JSON string.
/// Separated as its own function following PostHog's pattern --
/// keeps serialization logic isolated and testable.
pub fn serialize_snapshot_data_sync(
    user_id: &str,
    session_id: &str,
    snapshot_source: &str,
    snapshot_items: &[Value],
) -> String {
    json!({
        "event": "snapshot_items",
        "properties": {
            "user_id": user_id,
            "session_id": session_id,
            "snapshot_items": snapshot_items,
            "snapshot_source": snapshot_source,
        }
    })
    .to_string()
}
