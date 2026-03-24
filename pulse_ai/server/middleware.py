"""
Authentication middleware for the Pulse AI server.

Ensures every non-health request carries a valid Bearer token (JWT).
Health path is skipped; all other requests require a non-empty Bearer token.
"""

from __future__ import annotations

import logging

from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.responses import Response

logger = logging.getLogger(__name__)

BEARER_PREFIX = "Bearer "
HEALTH_PATH = "/health"

HTTP_UNAUTHORIZED = 401


class AuthMiddleware(BaseHTTPMiddleware):
    """Validates that requests have a non-empty Bearer (JWT) token."""

    async def dispatch(
        self, request: Request, call_next: RequestResponseEndpoint,
    ) -> Response:
        if request.url.path == HEALTH_PATH:
            return await call_next(request)

        auth_header = request.headers.get("authorization", "")
        has_no_bearer = not auth_header.startswith(BEARER_PREFIX)
        if has_no_bearer:
            return JSONResponse(
                status_code=HTTP_UNAUTHORIZED,
                content={"error": "Missing or invalid Authorization header"},
            )

        token = auth_header[len(BEARER_PREFIX) :].strip()
        is_token_empty = not token
        if is_token_empty:
            return JSONResponse(
                status_code=HTTP_UNAUTHORIZED,
                content={"error": "Empty authorization token"},
            )

        return await call_next(request)
