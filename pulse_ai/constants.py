"""Server-oriented constants (FastAPI, session store, RCA runner). Agent names live in ``pulse_ai.agents.settings``."""

APP_NAME = "pulse_ai"

DEFAULT_CORS_ORIGINS = [
    "http://localhost:3000",
    "http://127.0.0.1:3000",
    "http://localhost:3001",
    "http://127.0.0.1:3001",
]

RCA_PIPELINE_TIMEOUT_SECONDS = 300

DEFAULT_TITLE = "New conversation"
TITLE_MAX_LENGTH = 60

# Root-cause tabular fetch from pulse-server (server/root_cause_fetch.py)
ROOT_CAUSE_FETCH_DATE_QUERY_PARAM = "date"
ROOT_CAUSE_FETCH_PATH_TEMPLATE = "/v1/interactions/{interaction}/root-cause"
# HTTP statuses returned to clients when mapping upstream/timeout failures
HTTP_TIMEOUT_GATEWAY = 504
HTTP_BAD_GATEWAY = 502

# pulse_ai_session_scope VARCHAR lengths. UNIQUE(app_name, user_id, session_id) must fit
# MySQL's 3072-byte utf8mb4 index limit (4 bytes/char → sum of these three ≤ 768).
SESSION_SCOPE_APP_NAME_LEN = 128
SESSION_SCOPE_USER_ID_LEN = 320
SESSION_SCOPE_SESSION_ID_LEN = 256
SESSION_SCOPE_PROJECT_ID_LEN = 256

# Synthetic ADK user: RCA is one-shot (fresh session_id per request) and does not decode JWT here.
USER_ID_RCA = "rca_report_service"
# Authentication
PULSE_ACCESS_TOKEN_ENV_KEY = "PULSE_ACCESS_TOKEN"
PULSE_REFRESH_TOKEN_ENV_KEY = "PULSE_REFRESH_TOKEN"

PULSE_USER_EMAIL_ENV_KEY = "PULSE_USER_EMAIL"
