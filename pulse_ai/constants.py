import os

APP_NAME = "pulse_ai"

DEFAULT_MODEL = "gemini-2.5-flash"
AGENT_MODEL = os.getenv("AGENT_MODEL", DEFAULT_MODEL)

PLANNER_AGENT_NAME = "PlannerAgent"
EXECUTOR_AGENT_NAME = "ExecutorAgent"
SUMMARY_AGENT_NAME = "SummaryAgent"
REPORT_AGENT_NAME = "ReportAgent"
PIPELINE_AGENT_NAME = "PulseAIPipeline"

CORE_ANALYSIS_AGENT_NAME = "CoreAnalysis"
DEPENDENT_ANALYSIS_AGENT_NAME = "DependentAnalysis"

DEFAULT_CORS_ORIGINS = [
    "http://localhost:3000",
    "http://127.0.0.1:3000",
    "http://localhost:3001",
    "http://127.0.0.1:3001",
]

DEFAULT_TITLE = "New conversation"
TITLE_MAX_LENGTH = 60

AGENT_MODEL_ENV_KEY = 'AGENT_MODEL'

# Agent names

# Pulse backend connection
PULSE_BASE_URL_ENV_KEY = 'PULSE_BASE_URL'
DEFAULT_PULSE_BASE_URL = 'http://localhost:8080'

# Authentication
PULSE_ACCESS_TOKEN_ENV_KEY = 'PULSE_ACCESS_TOKEN'
PULSE_REFRESH_TOKEN_ENV_KEY = 'PULSE_REFRESH_TOKEN'

# User identity (required for write operations)
PULSE_USER_EMAIL_ENV_KEY = 'PULSE_USER_EMAIL'
