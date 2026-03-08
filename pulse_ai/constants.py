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
