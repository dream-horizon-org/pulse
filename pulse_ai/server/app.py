"""
FastAPI application factory, middleware, and shared services.
"""

from __future__ import annotations

import logging
import os
from typing import Any

from dotenv import load_dotenv
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from google.adk.runners import Runner
from pydantic import BaseModel

from pulse_ai.agent import root_agent
from pulse_ai.agents.rca import rca_agent
from pulse_ai.agents.screen_rca import screen_rca_narrative_agent, screen_rca_v2_agent
from pulse_ai.constants import APP_NAME, DEFAULT_CORS_ORIGINS
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

logger = logging.getLogger(__name__)


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


@app.exception_handler(RequestValidationError)
async def request_validation_exception_handler(
    request: Request, exc: RequestValidationError,
) -> JSONResponse:
    logger.warning(
        "Request validation failed for %s %s: %s",
        request.method,
        request.url.path,
        exc.errors(),
    )
    return JSONResponse(status_code=422, content={"detail": exc.errors()})


app.add_middleware(AuthMiddleware)
app.add_middleware(
    CORSMiddleware,
    allow_origins=_get_cors_origins(),
    allow_methods=["*"],
    allow_headers=["*"],
)

session_service = _create_session_service()
session_scope_store = create_session_scope_store(os.getenv("SESSION_DB_URL"))

runner = Runner(
    agent=root_agent,
    app_name=APP_NAME,
    session_service=session_service,
)

rca_runner = Runner(
    agent=rca_agent,
    app_name=APP_NAME,
    session_service=session_service,
    auto_create_session=True,
)

screen_rca_runner = Runner(
    agent=screen_rca_narrative_agent,
    app_name=APP_NAME,
    session_service=session_service,
    auto_create_session=True,
)

screen_rca_v2_runner = Runner(
    agent=screen_rca_v2_agent,
    app_name=APP_NAME,
    session_service=session_service,
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
