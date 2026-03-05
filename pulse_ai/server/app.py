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
from google.adk.artifacts import InMemoryArtifactService
from google.adk.runners import Runner
from pydantic import BaseModel

from pulse_ai.agent import root_agent
from pulse_ai.constants import APP_NAME, DEFAULT_CORS_ORIGINS

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
    if db_url:
        from google.adk.sessions import DatabaseSessionService
        return DatabaseSessionService(db_url=db_url)
    from google.adk.sessions import InMemorySessionService
    return InMemorySessionService()


# ── App & shared instances ───────────────────────────────────────────────────

app = FastAPI(title="Pulse AI Agent Server")

app.add_middleware(
    CORSMiddleware,
    allow_origins=_get_cors_origins(),
    allow_methods=["*"],
    allow_headers=["*"],
)

session_service = _create_session_service()
artifact_service = InMemoryArtifactService()

runner = Runner(
    agent=root_agent,
    app_name=APP_NAME,
    session_service=session_service,
    artifact_service=artifact_service,
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
