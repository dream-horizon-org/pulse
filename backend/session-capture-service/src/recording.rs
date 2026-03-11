use serde::Deserialize;
use serde_json::Value;

use crate::error::CaptureError;

/// Raw recording event from the mobile SDK.
/// Supports both root-level and properties-level field extraction.
#[derive(Debug, Deserialize)]
pub struct RawRecording {
    #[allow(dead_code)]
    pub event: Option<String>,

    pub user_id: Option<Value>,

    pub project_id: Option<String>,

    #[allow(dead_code)]
    pub timestamp: Option<String>,

    #[serde(default)]
    pub properties: RecordingProperties,
}

#[derive(Debug, Default, Deserialize)]
pub struct RecordingProperties {
    pub session_id: Option<Value>,

    pub snapshot_data: Option<Value>,

    pub snapshot_source: Option<Value>,

    pub lib: Option<String>,

    /// Fallback for user_id if not at root level
    pub user_id: Option<Value>,

    /// Fallback for project_id if not at root level
    pub project_id: Option<String>,
}

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

/// Validated and extracted recording data ready for Kafka
pub struct ValidatedRecording {
    pub project_id: String,
    pub session_id: String,
    pub user_id: String,
    pub snapshot_source: String,
    pub snapshot_library: String,
    pub snapshot_items: Vec<Value>,
}

impl RawRecording {
    fn extract_user_id(&self) -> Option<String> {
        let value = match &self.user_id {
            None | Some(Value::Null) => match &self.properties.user_id {
                None | Some(Value::Null) => return None,
                Some(id) => id,
            },
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

    fn extract_project_id(&self) -> Option<String> {
        self.project_id
            .clone()
            .or_else(|| self.properties.project_id.clone())
    }
}

/// Validates a batch of recording events and extracts the fields needed for Kafka.
/// - project_id, session_id, user_id must be present
/// - session_id must be alphanumeric + dash, ≤70 chars
/// - snapshot_data must be a JSON array or object
pub fn validate_and_extract(
    mut events: Vec<RawRecording>,
) -> Result<ValidatedRecording, CaptureError> {
    if events.is_empty() {
        return Err(CaptureError::EmptyBatch);
    }

    let first = &mut events[0];

    let project_id = first
        .extract_project_id()
        .ok_or(CaptureError::MissingProjectId)?;

    let user_id = first
        .extract_user_id()
        .ok_or(CaptureError::MissingUserId)?;

    let session_id = first
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
    let session_id = session_id_str.to_string();

    let snapshot_source = first
        .properties
        .snapshot_source
        .take()
        .and_then(|v| v.as_str().map(|s| s.to_string()))
        .unwrap_or_else(|| "mobile".to_string());

    let snapshot_library = first
        .properties
        .lib
        .take()
        .unwrap_or_else(|| "unknown".to_string());

    // Collect snapshot_data from all events into a single array
    let mut snapshot_items: Vec<Value> = Vec::new();
    for event in &mut events {
        let data = event
            .properties
            .snapshot_data
            .take()
            .ok_or(CaptureError::MissingSnapshotData)?;

        match data {
            Value::Array(mut arr) => snapshot_items.append(&mut arr),
            Value::Object(obj) => snapshot_items.push(Value::Object(obj)),
            _ => return Err(CaptureError::MissingSnapshotData),
        }
    }

    Ok(ValidatedRecording {
        project_id,
        session_id,
        user_id,
        snapshot_source,
        snapshot_library,
        snapshot_items,
    })
}
