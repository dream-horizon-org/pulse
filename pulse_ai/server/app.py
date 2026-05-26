"""
FastAPI application factory, middleware, and shared services.
"""

from __future__ import annotations

import logging
import os
from typing import Any

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from pydantic import BaseModel

from pulse_ai.agent import root_agent
from pulse_ai.agents.interaction_report.pipeline import interaction_report_pipeline
from pulse_ai.agents.rca import rca_agent
from pulse_ai.agents.screen_rca import screen_rca_narrative_agent
from pulse_ai.agents.session_rca import session_rca_narrative_agent
from pulse_ai.constants import APP_NAME, DEFAULT_CORS_ORIGINS
from pulse_ai.server.compacting_session_service import CompactingSessionService
from pulse_ai.server.middleware import AuthMiddleware
from pulse_ai.server.session_scope_store import (
    create_session_scope_store,
    to_async_sqlalchemy_url,
)

load_dotenv()

logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)


def _get_cors_origins() -> list[str]:
    env_origins = os.getenv("CORS_ALLOWED_ORIGINS")
    if env_origins:
        return [o.strip() for o in env_origins.split(",") if o.strip()]
    return DEFAULT_CORS_ORIGINS


def _create_session_service() -> Any:
    db_url = os.getenv("SESSION_DB_URL")
    if db_url and db_url.strip():
        from google.adk.sessions import DatabaseSessionService

        # ADK uses create_async_engine; plain sqlite:// uses sync pysqlite — use aiosqlite.
        async_url = to_async_sqlalchemy_url(db_url.strip())
        return DatabaseSessionService(db_url=async_url)
    from google.adk.sessions import InMemorySessionService
    return InMemorySessionService()


# ── App & shared instances ───────────────────────────────────────────────────

app = FastAPI(title="Pulse AI Agent Server")

app.add_middleware(AuthMiddleware)
app.add_middleware(
    CORSMiddleware,
    allow_origins=_get_cors_origins(),
    allow_methods=["*"],
    allow_headers=["*"],
)

# raw_session_service: used by route handlers that return full history to the
# frontend (GET /sessions/{user_id}/{session_id}).  Must NOT be compacted so
# users always see their complete chat history.
session_service = _create_session_service()
session_scope_store = create_session_scope_store(os.getenv("SESSION_DB_URL"))

# compacting_session_service: used exclusively by the Runner so the LLM only
# sees a compacted, token-budget-capped view of the session history.
_compacting_session_service = CompactingSessionService(inner=session_service)

runner = Runner(
    agent=root_agent,
    app_name=APP_NAME,
    session_service=_compacting_session_service,
)

_rca_session_service = InMemorySessionService()

rca_runner = Runner(
    agent=rca_agent,
    app_name=APP_NAME,
    session_service=_rca_session_service,  # RCA is one-shot — ephemeral, no DB needed
    auto_create_session=True,
)

screen_rca_runner = Runner(
    agent=screen_rca_narrative_agent,
    app_name=APP_NAME,
    session_service=session_service,
    auto_create_session=True,
)

session_rca_runner = Runner(
    agent=session_rca_narrative_agent,
    app_name=APP_NAME,
    session_service=session_service,
    auto_create_session=True,
)

_interaction_report_session_service = InMemorySessionService()

interaction_report_runner = Runner(
    agent=interaction_report_pipeline,
    app_name=APP_NAME,
    session_service=_interaction_report_session_service,
    auto_create_session=True,
)


# ── Request / response models ───────────────────────────────────────────────

class _MessagePart(BaseModel):
    text: str


class _NewMessage(BaseModel):
    parts: list[_MessagePart]


class RunSSERequest(BaseModel):
    app_name: str = APP_NAME
    user_id: str
    session_id: str
    new_message: _NewMessage
    streaming: bool = True
