import os

APP_NAME = "pulse_ai"

DEFAULT_MODEL = "gemini-2.5-flash"
AGENT_MODEL = os.getenv("AGENT_MODEL", DEFAULT_MODEL)
PULSE_SERVER_BASE_URL = os.getenv("PULSE_SERVER_BASE_URL", "http://localhost:8080")

REPORT_AGENT_NAME = "ReportAgent"
PIPELINE_AGENT_NAME = "PulseAIPipeline"
RCA_ANALYZER_AGENT_NAME = "RcaAnalyzerAgent"
RCA_REPORT_AGENT_NAME = "RcaReportAgent"
RCA_PIPELINE_AGENT_NAME = "RcaPipeline"

CORE_ANALYSIS_AGENT_NAME = "CoreAnalysis"
DEPENDENT_ANALYSIS_AGENT_NAME = "DependentAnalysis"
EM_AGENT_NAME = "EMAgent"

# Timeouts are intentionally centralized so request and pipeline limits are visible.
BACKEND_REQUEST_TIMEOUT_SECONDS = 30
RCA_PIPELINE_TIMEOUT_SECONDS = 90

DEFAULT_CORS_ORIGINS = [
    "http://localhost:3000",
    "http://127.0.0.1:3000",
    "http://localhost:3001",
    "http://127.0.0.1:3001",
]

DEFAULT_TITLE = "New conversation"
TITLE_MAX_LENGTH = 60

# Pulse backend connection
PULSE_BASE_URL_ENV_KEY = 'PULSE_BASE_URL'
DEFAULT_PULSE_BASE_URL = 'http://localhost:8080'

# Authentication
PULSE_ACCESS_TOKEN_ENV_KEY = 'PULSE_ACCESS_TOKEN'
PULSE_REFRESH_TOKEN_ENV_KEY = 'PULSE_REFRESH_TOKEN'

# pulse_ai_session_scope VARCHAR lengths. UNIQUE(app_name, user_id, session_id) must fit
# MySQL's 3072-byte utf8mb4 index limit (4 bytes/char → sum of these three ≤ 768).
SESSION_SCOPE_APP_NAME_LEN = 128
SESSION_SCOPE_USER_ID_LEN = 320
SESSION_SCOPE_SESSION_ID_LEN = 256
SESSION_SCOPE_PROJECT_ID_LEN = 256
