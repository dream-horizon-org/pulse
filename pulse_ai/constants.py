import os

APP_NAME = "pulse_ai"

DEFAULT_MODEL = "gemini-2.5-flash"
AGENT_MODEL_ENV_KEY = "AGENT_MODEL"
AGENT_MODEL = os.getenv(AGENT_MODEL_ENV_KEY, DEFAULT_MODEL)
PULSE_SERVER_BASE_URL = os.getenv("PULSE_SERVER_BASE_URL", "http://localhost:8080")

REPORT_AGENT_NAME = "ReportAgent"
PIPELINE_AGENT_NAME = "PulseAIPipeline"
RCA_AGENT_NAME = "RcaAgent"
SCREEN_RCA_NARRATIVE_AGENT_NAME = "ScreenRcaNarrativeAgent"
SCREEN_RCA_V2_AGENT_NAME = "ScreenRcaV2Agent"

CORE_ANALYSIS_AGENT_NAME = "CoreAnalysis"
DEPENDENT_ANALYSIS_AGENT_NAME = "DependentAnalysis"
EM_AGENT_NAME = "EMAgent"

# Timeouts are intentionally centralized so request and pipeline limits are visible.
BACKEND_REQUEST_TIMEOUT_SECONDS = 75  # Must exceed pulse-server RCA timeout (60s) + buffer
RCA_PIPELINE_TIMEOUT_SECONDS = 300

DEFAULT_CORS_ORIGINS = [
    "http://localhost:3000",
    "http://127.0.0.1:3000",
    "http://localhost:3001",
    "http://127.0.0.1:3001",
]

DEFAULT_TITLE = "New conversation"
TITLE_MAX_LENGTH = 60

# Pulse backend connection
PULSE_BASE_URL_ENV_KEY = "PULSE_BASE_URL"
DEFAULT_PULSE_BASE_URL = "http://localhost:8080"


def get_pulse_base_url() -> str:
    """Pulse-server base URL from ``PULSE_BASE_URL``, or :data:`DEFAULT_PULSE_BASE_URL` if unset/empty."""
    configured = os.getenv(PULSE_BASE_URL_ENV_KEY, "").strip()
    return configured or DEFAULT_PULSE_BASE_URL


# Root-cause tabular fetch from pulse-server (server/root_cause_fetch.py)
ROOT_CAUSE_FETCH_DATE_QUERY_PARAM = "date"
ROOT_CAUSE_FETCH_PATH_TEMPLATE = "/v1/interactions/{interaction}/root-cause"
# HTTP statuses returned to clients when mapping upstream/timeout failures
HTTP_TIMEOUT_GATEWAY = 504
HTTP_BAD_GATEWAY = 502

# pulse_ai tools: user/session-scoped backend calls
PULSE_TOOL_SESSION_MISSING_CONTEXT = (
    "Chat session is missing tool context; cannot call the Pulse API."
)
PULSE_TOOL_SESSION_MISSING_BEARER = (
    "Authorization is missing from this chat session. Sign in again or start a new chat."
)
PULSE_TOOL_SESSION_MISSING_PROJECT = (
    "Project context is missing from this chat session. Select a project and try again."
)

# pulse_ai_session_scope VARCHAR lengths. UNIQUE(app_name, user_id, session_id) must fit
# MySQL's 3072-byte utf8mb4 index limit (4 bytes/char → sum of these three ≤ 768).
SESSION_SCOPE_APP_NAME_LEN = 128
SESSION_SCOPE_USER_ID_LEN = 320
SESSION_SCOPE_SESSION_ID_LEN = 256
SESSION_SCOPE_PROJECT_ID_LEN = 256

# Synthetic ADK user: RCA is one-shot (fresh session_id per request) and does not decode JWT here.
# Keeps ephemeral RCA sessions separate from real chat users in the shared session_service.
USER_ID_RCA = "rca_report_service"
USER_ID_SCREEN_RCA = "screen_rca_narrative_service"
USER_ID_SCREEN_RCA_V2 = "screen_rca_v2_service"
# Authentication
PULSE_ACCESS_TOKEN_ENV_KEY = 'PULSE_ACCESS_TOKEN'
PULSE_REFRESH_TOKEN_ENV_KEY = 'PULSE_REFRESH_TOKEN'

PULSE_USER_EMAIL_ENV_KEY = "PULSE_USER_EMAIL"
