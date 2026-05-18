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


# HTTP statuses returned to clients when mapping upstream/timeout failures
HTTP_TIMEOUT_GATEWAY = 504
HTTP_BAD_GATEWAY = 502

# Async interaction RCA (pulse-server); see root_cause_payload_fetch.py
RCA_ASYNC_RCA_TYPE_INTERACTION = "INTERACTION"
RCA_REPORT_POST_PATH = "/v1/ai/rca/report"
RCA_REPORT_PEEK_PATH_PREFIX = "/v1/ai-rca/report"
RCA_JOB_GET_PATH_TEMPLATE = "/v1/ai-rca/job/{job_id}"
RCA_JOB_POLL_INTERVAL_SEC = 3.0

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
# Authentication
PULSE_ACCESS_TOKEN_ENV_KEY = 'PULSE_ACCESS_TOKEN'
PULSE_REFRESH_TOKEN_ENV_KEY = 'PULSE_REFRESH_TOKEN'

PULSE_USER_EMAIL_ENV_KEY = "PULSE_USER_EMAIL"

# ── Chat memory / compaction ─────────────────────────────────────────────────
# Target token budget per LLM request (history only — does not include the
# incoming user message for the current turn).
# Derived from: Gemini Flash latency sweet-spot at ~40K tokens.
TOKEN_BUDGET = 80_000

# Number of chars per token (Gemini heuristic: 1 token ≈ 4 chars for plain text).
CHARS_PER_TOKEN = 4

# JSON payloads (function_call args, function_response) tokenize more densely
# than prose — Gemini averages ~3 chars/token on structured JSON.
CHARS_PER_TOKEN_JSON = 3

# Tool responses older than this many turns are compacted into structured
# summaries.  The most recent K turns keep raw tool payloads for full fidelity
# on follow-up questions.
# Math: 5 raw turns × 3,500 tokens = 17,500; leaves ~16,300 tokens for
# compacted older turns at ~800 tokens each → supports ~25 total turns.
TOOL_AGE_THRESHOLD = 5

# Hard safety cap on the number of conversation turns kept in the LLM window.
# Prevents runaway growth if the token estimator underestimates (e.g., very
# large tool payloads).
MAX_WINDOW_SAFETY_CAP = 30

# Cost estimates used for documentation and tuning reference only.
# Not used at runtime — the token estimator operates on actual event content.
_FIXED_OVERHEAD_ESTIMATE = 6_200   # System prompts + tool definitions (tokens)
_RAW_TURN_COST_ESTIMATE = 3_500    # User text + assistant text + 2-3 tool responses
_COMPACTED_TURN_COST_ESTIMATE = 800  # After tool response compaction
