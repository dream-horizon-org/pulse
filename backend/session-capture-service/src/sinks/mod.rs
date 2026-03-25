pub mod kafka;

use async_trait::async_trait;

use crate::api::CaptureError;

#[async_trait]
pub trait Event: Send + Sync {
    async fn send(
        &self,
        key: &str,
        payload: &str,
        project_id: &str,
    ) -> Result<(), CaptureError>;
}
