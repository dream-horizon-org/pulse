import os

APP_NAME = "pulse_ai"

DEFAULT_MODEL = "gemini-2.5-flash"
AGENT_MODEL = os.getenv("AGENT_MODEL", DEFAULT_MODEL)
PULSE_SERVER_BASE_URL = os.getenv("PULSE_SERVER_BASE_URL", "http://localhost:8080")

PLANNER_AGENT_NAME = "PlannerAgent"
EXECUTOR_AGENT_NAME = "ExecutorAgent"
SUMMARY_AGENT_NAME = "SummaryAgent"
REPORT_AGENT_NAME = "ReportAgent"
PIPELINE_AGENT_NAME = "PulseAIPipeline"
RCA_ANALYZER_AGENT_NAME = "RcaAnalyzerAgent"
RCA_REPORT_AGENT_NAME = "RcaReportAgent"
RCA_PIPELINE_AGENT_NAME = "RcaPipeline"

CORE_ANALYSIS_AGENT_NAME = "CoreAnalysis"
DEPENDENT_ANALYSIS_AGENT_NAME = "DependentAnalysis"

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
