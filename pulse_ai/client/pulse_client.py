"""HTTP client for pulse-server with forwarded user JWT and project context."""

from __future__ import annotations

import logging
from typing import Any

import httpx

logger = logging.getLogger(__name__)

from pulse_ai.constants import (
    BACKEND_REQUEST_TIMEOUT_SECONDS,
    PULSE_TOOL_SESSION_MISSING_BEARER,
    PULSE_TOOL_SESSION_MISSING_PROJECT,
    get_pulse_base_url,
)


class PulseClient:
    """Async HTTP client for Pulse backend API calls.

    Use ``async with PulseClient(...) as client`` (or call ``aclose()``) so the
    underlying ``httpx.AsyncClient`` is closed after use.
    """

    def __init__(
        self,
        authorization_header: str,
        project_id: str,
    ) -> None:
        """Initialize the client.

        Args:
            authorization_header: Full ``Authorization`` header (e.g. ``Bearer <jwt>``).
            project_id: Sent as ``X-Project-ID`` (required for project-scoped APIs).
        """
        base_url = get_pulse_base_url()
        self.authorization_header = authorization_header
        self.project_id = project_id
        self._client = httpx.AsyncClient(
            base_url=base_url,
            timeout=float(BACKEND_REQUEST_TIMEOUT_SECONDS),
        )
        self._closed = False

    async def __aenter__(self) -> PulseClient:
        return self

    async def __aexit__(
        self,
        exc_type: type[BaseException] | None,
        exc_val: BaseException | None,
        exc_tb: Any,
    ) -> None:
        await self.aclose()

    def _build_headers(self) -> dict[str, str]:
        """Build request headers.

        Only sets Authorization when the value is non-empty to avoid
        "Illegal header value b'Bearer '" from httpx.
        """
        auth = None
        if self.authorization_header and self.authorization_header.strip():
            auth = self.authorization_header.strip()

        headers = {"Content-Type": "application/json"}
        if auth:
            headers["Authorization"] = auth
        if self.project_id and self.project_id.strip():
            headers["X-Project-ID"] = self.project_id.strip()
        return headers

    async def request(
        self,
        method: str,
        path: str,
        **kwargs,
    ) -> httpx.Response | dict:
        """Make an HTTP request.

        Returns httpx.Response on success/HTTP errors, or a dict on
        network/timeout errors.
        """
        # Tools call pulse_tool_session_auth_error first; this catches direct client misuse.
        missing_session_auth = not (
            self.authorization_header and self.authorization_header.strip()
        )
        if missing_session_auth:
            return {
                "status": "error",
                "message": PULSE_TOOL_SESSION_MISSING_BEARER,
            }
        missing_session_project = not (self.project_id and self.project_id.strip())
        if missing_session_project:
            return {
                "status": "error",
                "message": PULSE_TOOL_SESSION_MISSING_PROJECT,
            }

        try:
            return await self._do_request(method, path, **kwargs)

        except httpx.ConnectError as exc:
            logger.error(f"Connection error: {exc}")
            return {"status": "error", "message": f"Connection error: {exc}"}
        except (httpx.ReadTimeout, httpx.WriteTimeout, httpx.PoolTimeout) as exc:
            logger.error(f"Request timed out: {exc}")
            return {"status": "error", "message": f"Request timed out: {exc}"}
        except httpx.HTTPError as exc:
            logger.error(f"HTTP error: {exc}")
            return {"status": "error", "message": f"HTTP error: {exc}"}

    async def aclose(self) -> None:
        """Close the underlying HTTP client."""
        is_already_closed = self._closed
        if is_already_closed:
            return
        self._closed = True
        await self._client.aclose()

    async def _do_request(
        self,
        method: str,
        path: str,
        **kwargs,
    ) -> httpx.Response:
        """Execute a single HTTP request."""
        headers = self._build_headers()
        return await self._client.request(method, path, headers=headers, **kwargs)
