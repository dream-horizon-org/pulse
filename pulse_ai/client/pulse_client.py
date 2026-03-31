"""HTTP client for the Pulse backend with auth headers."""

import logging
import os

import httpx

logger = logging.getLogger(__name__)

from pulse_ai.constants import DEFAULT_PULSE_BASE_URL, PULSE_BASE_URL_ENV_KEY


class PulseClient:
    """Async HTTP client for Pulse backend API calls.

    Pass ``access_token`` or ``authorization_header`` (e.g. from tool session state).
    Token refresh on 401 is not implemented here; callers may handle expiry upstream later.
    """

    def __init__(
        self,
        access_token: str | None = None,
        authorization_header: str | None = None,
        project_id: str | None = None,
    ):
        """Initialize the client.

        Args:
            access_token: Optional access token for Bearer auth.
            authorization_header: Optional full "Authorization" header from the request
                (e.g. "Bearer <token>"). When set, this is used for all requests instead
                of building from access_token. Used when tools receive auth via session state.
            project_id: Optional project ID. When set, sent as X-Project-ID on all requests
                so the backend can set ProjectContext (required for project-scoped endpoints).
        """
        base_url = os.getenv(PULSE_BASE_URL_ENV_KEY, DEFAULT_PULSE_BASE_URL)
        self.access_token = access_token or ""
        self.authorization_header = authorization_header
        self.project_id = project_id
        self._client = httpx.AsyncClient(base_url=base_url, timeout=30.0)

    def _build_headers(self) -> dict[str, str]:
        """Build request headers. Use request auth from session state when provided.

        Only sets Authorization when the value is non-empty to avoid
        "Illegal header value b'Bearer '" from httpx.
        """
        if self.authorization_header and self.authorization_header.strip():
            auth = self.authorization_header.strip()
        elif self.access_token and self.access_token.strip():
            auth = f"Bearer {self.access_token.strip()}"
        else:
            auth = None

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
        try:
            headers = self._build_headers()
            return await self._client.request(method, path, headers=headers, **kwargs)

        except httpx.ConnectError as exc:
            logger.error(f"Connection error: {exc}")
            return {"status": "error", "message": f"Connection error: {exc}"}
        except (httpx.ReadTimeout, httpx.WriteTimeout, httpx.PoolTimeout) as exc:
            logger.error(f"Request timed out: {exc}")
            return {"status": "error", "message": f"Request timed out: {exc}"}
        except httpx.HTTPError as exc:
            logger.error(f"HTTP error: {exc}")
            return {"status": "error", "message": f"HTTP error: {exc}"}
