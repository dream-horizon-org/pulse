"""
Authentication and source-validation middleware for the Pulse AI server.

Ensures every non-health request carries a valid Bearer token and
originates from a trusted source (pulse-server or localhost).
"""

from __future__ import annotations

import logging
import os

from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.responses import Response

logger = logging.getLogger(__name__)

TRUSTED_SERVICE_KEY = os.getenv("TRUSTED_SERVICE_KEY", "")
BEARER_PREFIX = "Bearer "
HEALTH_PATH = "/health"
LOCALHOST_ADDRESSES = frozenset({"127.0.0.1", "::1", "localhost"})

HTTP_UNAUTHORIZED = 401
HTTP_FORBIDDEN = 403


class AuthMiddleware(BaseHTTPMiddleware):
    """Validates that requests have a JWT token and originate from a trusted source."""

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

        if TRUSTED_SERVICE_KEY:
            service_key = request.headers.get("x-pulse-service-key", "")
            client_host = request.client.host if request.client else ""
            is_trusted_key = service_key == TRUSTED_SERVICE_KEY
            is_localhost = client_host in LOCALHOST_ADDRESSES

            if not is_trusted_key and not is_localhost:
                logger.warning("Rejected request from untrusted source: %s", client_host)
                return JSONResponse(
                    status_code=HTTP_FORBIDDEN,
                    content={"error": "Request from untrusted source"},
                )

        return await call_next(request)
