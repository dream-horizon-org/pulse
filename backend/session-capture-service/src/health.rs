use std::collections::HashMap;
use std::ops::Add;
use std::sync::atomic::{AtomicU8, Ordering};
use std::sync::{Arc, RwLock};
use std::time::Duration;

use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use tokio::sync::mpsc;

// ---------------------------------------------------------------------------
// Shutdown state machine for graceful rollout/termination.
//
// During rolling deployments, the pre-stop hook creates
// `/tmp/shutdown` to signal the pod is being terminated. The readiness probe
// detects this and transitions to `Prestop`, returning 503 to stop receiving
// new traffic while existing requests complete.
// ---------------------------------------------------------------------------

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
#[repr(u8)]
pub enum ShutdownStatus {
    Unknown = 0,
    Running = 1,
    Prestop = 2,
    Terminating = 3,
    Completed = 4,
}

impl ShutdownStatus {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Unknown => "unknown",
            Self::Running => "running",
            Self::Prestop => "prestop",
            Self::Terminating => "terminating",
            Self::Completed => "completed",
        }
    }
}

impl From<u8> for ShutdownStatus {
    fn from(v: u8) -> Self {
        match v {
            1 => Self::Running,
            2 => Self::Prestop,
            3 => Self::Terminating,
            4 => Self::Completed,
            _ => Self::Unknown,
        }
    }
}

static SHUTDOWN_STATUS: AtomicU8 = AtomicU8::new(ShutdownStatus::Running as u8);

pub fn set_shutdown_status(status: ShutdownStatus) {
    SHUTDOWN_STATUS.store(status as u8, Ordering::Relaxed);
}

pub fn get_shutdown_status() -> ShutdownStatus {
    SHUTDOWN_STATUS.load(Ordering::Relaxed).into()
}

pub async fn readiness_handler() -> StatusCode {
    let shutdown_status = get_shutdown_status();
    let is_running_or_unknown =
        shutdown_status == ShutdownStatus::Running || shutdown_status == ShutdownStatus::Unknown;

    if is_running_or_unknown && std::path::Path::new("/tmp/shutdown").exists() {
        set_shutdown_status(ShutdownStatus::Prestop);
        tracing::info!("Shutdown file detected, transitioning to PRESTOP status");
    }

    if is_running_or_unknown {
        StatusCode::OK
    } else {
        StatusCode::SERVICE_UNAVAILABLE
    }
}

// ---------------------------------------------------------------------------
// Component health registry
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Eq, PartialEq)]
#[allow(dead_code)]
pub enum ComponentStatus {
    Starting,
    HealthyUntil(time::OffsetDateTime),
    Unhealthy,
    Stalled,
}

impl ComponentStatus {
    #[allow(dead_code)]
    pub fn is_healthy(&self) -> bool {
        match self {
            ComponentStatus::HealthyUntil(until) => until.gt(&time::OffsetDateTime::now_utc()),
            _ => false,
        }
    }
}

#[derive(Default, Debug)]
pub struct HealthStatus {
    pub healthy: bool,
    pub components: HashMap<String, ComponentStatus>,
}

impl IntoResponse for HealthStatus {
    fn into_response(self) -> Response {
        let body = format!("{self:?}");
        match self.healthy {
            true => (StatusCode::OK, body),
            false => (StatusCode::INTERNAL_SERVER_ERROR, body),
        }
        .into_response()
    }
}

struct HealthMessage {
    component: String,
    status: ComponentStatus,
}

#[derive(Clone)]
pub struct HealthHandle {
    component: String,
    deadline: Duration,
    sender: mpsc::Sender<HealthMessage>,
}

impl HealthHandle {
    pub async fn report_healthy(&self) {
        self.report_status(ComponentStatus::HealthyUntil(
            time::OffsetDateTime::now_utc().add(self.deadline),
        ))
        .await
    }

    async fn report_status(&self, status: ComponentStatus) {
        let message = HealthMessage {
            component: self.component.clone(),
            status,
        };
        if let Err(err) = self.sender.send(message).await {
            tracing::warn!("failed to report health status: {}", err);
        }
    }

    pub fn report_healthy_blocking(&self) {
        let status = ComponentStatus::HealthyUntil(
            time::OffsetDateTime::now_utc().add(self.deadline),
        );
        let message = HealthMessage {
            component: self.component.clone(),
            status,
        };
        if let Ok(h) = tokio::runtime::Handle::try_current() {
            let m = self.clone();
            h.spawn(async move { m.report_status(message.status).await });
        } else if let Err(err) = self.sender.blocking_send(message) {
            tracing::warn!("failed to report health status: {}", err);
        }
    }
}

#[derive(Clone)]
pub struct HealthRegistry {
    name: String,
    components: Arc<RwLock<HashMap<String, ComponentStatus>>>,
    sender: mpsc::Sender<HealthMessage>,
}

impl HealthRegistry {
    pub fn new(name: &str) -> Self {
        let (tx, mut rx) = mpsc::channel::<HealthMessage>(16);
        let registry = Self {
            name: name.to_owned(),
            components: Default::default(),
            sender: tx,
        };

        let components = registry.components.clone();
        tokio::spawn(async move {
            while let Some(message) = rx.recv().await {
                if let Ok(mut map) = components.write() {
                    _ = map.insert(message.component, message.status);
                } else {
                    tracing::warn!("poisoned HealthRegistry mutex");
                }
            }
        });

        registry
    }

    pub async fn register(&self, component: String, deadline: Duration) -> HealthHandle {
        let handle = HealthHandle {
            component,
            deadline,
            sender: self.sender.clone(),
        };
        handle.report_status(ComponentStatus::Starting).await;
        handle
    }

    pub fn get_status(&self) -> HealthStatus {
        let components = self
            .components
            .read()
            .expect("poisoned HealthRegistry mutex");

        let mut result = HealthStatus {
            healthy: !components.is_empty(),
            components: Default::default(),
        };
        let now = time::OffsetDateTime::now_utc();

        for (name, status) in components.iter() {
            match status {
                ComponentStatus::HealthyUntil(until) => {
                    if until.gt(&now) {
                        result.components.insert(name.clone(), status.clone());
                    } else {
                        result.healthy = false;
                        result
                            .components
                            .insert(name.clone(), ComponentStatus::Stalled);
                    }
                }
                _ => {
                    result.healthy = false;
                    result.components.insert(name.clone(), status.clone());
                }
            }
        }

        match result.healthy {
            true => tracing::debug!("{} health check ok", self.name),
            false => tracing::warn!("{} health check failed: {:?}", self.name, result.components),
        }
        result
    }
}
