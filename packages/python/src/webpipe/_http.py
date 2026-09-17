"""Thin httpx wrapper: auth headers, retries with backoff, error mapping.

Private module — never import from outside the package.
"""

import asyncio
import time
from typing import Any, Dict, Optional

import httpx

from .errors import (
    APIError,
    AuthenticationError,
    BadRequestError,
    ForbiddenError,
    NotFoundError,
    RateLimitError,
    ServerError,
)

_STATUS_TO_ERROR = {
    400: BadRequestError,
    401: AuthenticationError,
    403: ForbiddenError,
    404: NotFoundError,
    429: RateLimitError,
}

_RETRYABLE_STATUSES = frozenset({429, 500, 502, 503, 504})


def _backoff(response: Optional[httpx.Response], attempt: int) -> float:
    """Seconds to wait before retry ``attempt`` (1-based)."""
    if response is not None:
        retry_after = response.headers.get("Retry-After")
        if retry_after:
            try:
                return max(0.0, float(retry_after))
            except ValueError:
                pass
    return 0.5 * (2**attempt)


def _raise_for_status(response: httpx.Response) -> None:
    if response.status_code < 400:
        return
    message = response.text
    body: Optional[Any] = None
    try:
        body = response.json()
        if isinstance(body, dict) and body.get("error"):
            message = str(body["error"])
    except ValueError:
        pass
    if response.status_code >= 500:
        error_cls = ServerError
    else:
        error_cls = _STATUS_TO_ERROR.get(response.status_code, APIError)
    raise error_cls(message, response.status_code, body)


def _decode(response: httpx.Response) -> Dict[str, Any]:
    if response.status_code == 204 or not response.content:
        return {}
    payload = response.json()
    return payload if isinstance(payload, dict) else {"data": payload}


def _headers(api_key: str, user_agent: str) -> Dict[str, str]:
    return {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "User-Agent": user_agent,
    }


class HttpClient:
    """Synchronous HTTP client with retries."""

    def __init__(
        self,
        base_url: str,
        api_key: str,
        timeout: float,
        max_retries: int,
        user_agent: str,
    ) -> None:
        self._max_retries = max_retries
        self._client = httpx.Client(
            base_url=base_url,
            headers=_headers(api_key, user_agent),
            timeout=timeout,
        )

    def request(
        self, method: str, path: str, json: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        attempt = 0
        while True:
            response: Optional[httpx.Response] = None
            try:
                response = self._client.request(method, path, json=json)
            except httpx.TransportError:
                if attempt >= self._max_retries:
                    raise
                attempt += 1
                time.sleep(_backoff(None, attempt))
                continue
            if response.status_code in _RETRYABLE_STATUSES and attempt < self._max_retries:
                attempt += 1
                time.sleep(_backoff(response, attempt))
                continue
            _raise_for_status(response)
            return _decode(response)

    def get(self, path: str) -> Dict[str, Any]:
        return self.request("GET", path)

    def post(self, path: str, json: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        return self.request("POST", path, json)

    def delete(self, path: str) -> Dict[str, Any]:
        return self.request("DELETE", path)

    def close(self) -> None:
        self._client.close()

    def __enter__(self) -> "HttpClient":
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()


class AsyncHttpClient:
    """Asynchronous HTTP client with retries."""

    def __init__(
        self,
        base_url: str,
        api_key: str,
        timeout: float,
        max_retries: int,
        user_agent: str,
    ) -> None:
        self._max_retries = max_retries
        self._client = httpx.AsyncClient(
            base_url=base_url,
            headers=_headers(api_key, user_agent),
            timeout=timeout,
        )

    async def request(
        self, method: str, path: str, json: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        attempt = 0
        while True:
            response: Optional[httpx.Response] = None
            try:
                response = await self._client.request(method, path, json=json)
            except httpx.TransportError:
                if attempt >= self._max_retries:
                    raise
                attempt += 1
                await asyncio.sleep(_backoff(None, attempt))
                continue
            if response.status_code in _RETRYABLE_STATUSES and attempt < self._max_retries:
                attempt += 1
                await asyncio.sleep(_backoff(response, attempt))
                continue
            _raise_for_status(response)
            return _decode(response)

    async def get(self, path: str) -> Dict[str, Any]:
        return await self.request("GET", path)

    async def post(self, path: str, json: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        return await self.request("POST", path, json)

    async def delete(self, path: str) -> Dict[str, Any]:
        return await self.request("DELETE", path)

    async def aclose(self) -> None:
        await self._client.aclose()

    async def __aenter__(self) -> "AsyncHttpClient":
        return self

    async def __aexit__(self, *exc: object) -> None:
        await self.aclose()
