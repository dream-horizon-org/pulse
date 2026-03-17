"""HTTP client for the Pulse backend with auth headers and 401-retry."""

import os

import httpx

from pulse_ai.constants import (
    DEFAULT_PULSE_BASE_URL,
    PULSE_ACCESS_TOKEN_ENV_KEY,
    PULSE_BASE_URL_ENV_KEY,
    PULSE_REFRESH_TOKEN_ENV_KEY,
)


class PulseClient:
    """Async HTTP client for Pulse backend API calls.

    Reads auth tokens from env vars (dev) or can be overridden for prod.
    Automatically retries on 401 by refreshing the access token.
    """

    def __init__(
        self,
        access_token: str | None = None,
        refresh_token: str | None = None,
    ):
        base_url = os.getenv(PULSE_BASE_URL_ENV_KEY, DEFAULT_PULSE_BASE_URL)
        self.access_token = access_token or os.getenv(PULSE_ACCESS_TOKEN_ENV_KEY, "")
        self.refresh_token = refresh_token or os.getenv(PULSE_REFRESH_TOKEN_ENV_KEY, "")
        self._client = httpx.AsyncClient(base_url=base_url, timeout=30.0)

    def _build_headers(self) -> dict[str, str]:
        """Build request headers with current access token."""
        headers = {
            "Authorization": f"Bearer {self.access_token}",
            "Content-Type": "application/json",
        }
        return headers

    async def request(
        self,
        method: str,
        path: str,
        **kwargs,
    ) -> httpx.Response | dict:
        """Make an HTTP request with automatic 401-retry.

        Returns httpx.Response on success/HTTP errors, or a dict on
        network/timeout errors.
        """
        try:
            response = await self._do_request(method, path, **kwargs)

            if response.status_code == 401 and self.refresh_token:
                refreshed = await self._refresh_access_token()
                if refreshed:
                    response = await self._do_request(method, path, **kwargs)

            return response

        except httpx.ConnectError as exc:
            return {"status": "error", "message": f"Connection error: {exc}"}
        except (httpx.ReadTimeout, httpx.WriteTimeout, httpx.PoolTimeout) as exc:
            return {"status": "error", "message": f"Request timed out: {exc}"}
        except httpx.HTTPError as exc:
            return {"status": "error", "message": f"HTTP error: {exc}"}

    async def _do_request(
        self,
        method: str,
        path: str,
        **kwargs,
    ) -> httpx.Response:
        """Execute a single HTTP request."""
        headers = self._build_headers()
        return await self._client.request(method, path, headers=headers, **kwargs)

    async def _refresh_access_token(self) -> bool:
        """Refresh the access token using the refresh token.

        Returns True if refresh succeeded, False otherwise.
        """
        try:
            resp = await self._client.post(
                "/v1/auth/token/refresh",
                json={"refreshToken": self.refresh_token},
                headers={"Content-Type": "application/json"},
            )
            if resp.status_code != 200:
                return False

            data = resp.json().get("data", {})
            self.access_token = data.get("accessToken", self.access_token)
            self.refresh_token = data.get("refreshToken", self.refresh_token)
            return True

        except httpx.HTTPError:
            return False
