"""Browser session resources (sync + async).

A :class:`BrowserSession` is a handle to a persistent headless Chromium on the
server. Create via ``client.browser.create(...)``, drive it with the methods
below, then ``close()`` it (or use it as a context manager).

Server-side facts to keep in mind:
- sessions expire after 30 minutes idle; at most 2 concurrent ``running`` per user;
- commands sent while the session is still ``starting`` are queued by the server
  (waits up to 60s), but prefer ``wait_until_running()`` for a controlled UX.
"""

import asyncio
import time
from typing import Any, Dict, List, Optional, Sequence, Union

from ._http import AsyncHttpClient, HttpClient
from .errors import PollTimeoutError, WebpipeError
from .types import Cookie, Document, SessionInfo

_TERMINAL_STATUSES = ("stopped", "error", "expired")


def _drop_none(mapping: Dict[str, Any]) -> Dict[str, Any]:
    return {k: v for k, v in mapping.items() if v is not None}


def _scrape_body(
    formats: Optional[Sequence[str]],
    scroll_to_bottom: Optional[bool],
    max_scrolls: Optional[int],
    scroll_wait: Optional[int],
    scroll_step: Optional[int],
) -> Dict[str, Any]:
    return _drop_none(
        {
            "formats": list(formats) if formats is not None else None,
            "scroll_to_bottom": scroll_to_bottom,
            "max_scrolls": max_scrolls,
            "scroll_wait": scroll_wait,
            "scroll_step": scroll_step,
        }
    )


def _cookies_payload(cookies: Sequence[Union[Cookie, Dict[str, Any]]]) -> List[Dict[str, Any]]:
    payload = []
    for c in cookies:
        payload.append(c.model_dump(exclude_none=True) if isinstance(c, Cookie) else dict(c))
    return payload


class BrowserSession:
    """Synchronous handle to a persistent browser session."""

    def __init__(self, http: HttpClient, info: SessionInfo) -> None:
        self._http = http
        self.id = info.id
        self.status = info.status
        self.url = info.url
        self.session_name = info.session_name
        self.expires_at = info.expires_at

    @property
    def _base(self) -> str:
        return f"/api/v1/browser/{self.id}"

    def _apply(self, info: SessionInfo) -> None:
        self.status = info.status
        self.url = info.url
        self.session_name = info.session_name
        self.expires_at = info.expires_at

    # -- lifecycle -----------------------------------------------------------

    def refresh(self) -> "BrowserSession":
        """Fetch the latest session state from the server."""
        self._apply(SessionInfo.model_validate(self._http.get(self._base)))
        return self

    def wait_until_running(
        self, *, timeout: float = 60.0, poll_interval: float = 1.0
    ) -> "BrowserSession":
        """Block until the session is ready to accept commands."""
        deadline = time.monotonic() + timeout
        while True:
            self.refresh()
            if self.status == "running":
                return self
            if self.status in _TERMINAL_STATUSES:
                raise WebpipeError(
                    f"Browser session {self.id} entered terminal status '{self.status}'"
                )
            if time.monotonic() >= deadline:
                raise PollTimeoutError(f"Browser session {self.id} not running after {timeout}s")
            time.sleep(poll_interval)

    def close(self) -> None:
        """Stop the session and release server resources."""
        self._http.delete(self._base)
        self.status = "stopped"

    # -- content -------------------------------------------------------------

    def navigate(
        self,
        url: str,
        *,
        formats: Optional[Sequence[str]] = None,
        scroll_to_bottom: Optional[bool] = None,
        max_scrolls: Optional[int] = None,
        scroll_wait: Optional[int] = None,
        scroll_step: Optional[int] = None,
    ) -> Document:
        """Navigate to a new URL (with anti-bot handling) and scrape it."""
        body = {"url": url}
        body.update(_scrape_body(formats, scroll_to_bottom, max_scrolls, scroll_wait, scroll_step))
        data = self._http.post(f"{self._base}/navigate", body)
        return Document.model_validate(data.get("data") or {})

    def scrape(
        self,
        *,
        formats: Optional[Sequence[str]] = None,
        scroll_to_bottom: Optional[bool] = None,
        max_scrolls: Optional[int] = None,
        scroll_wait: Optional[int] = None,
        scroll_step: Optional[int] = None,
    ) -> Document:
        """Scrape the current page without navigating (faster than ``navigate``)."""
        body = _scrape_body(formats, scroll_to_bottom, max_scrolls, scroll_wait, scroll_step)
        data = self._http.post(f"{self._base}/scrape", body)
        return Document.model_validate(data.get("data") or {})

    # -- actions -------------------------------------------------------------

    def action(self, type: str, **params: Any) -> Dict[str, Any]:
        """Run a raw browser action. Returns the raw API payload."""
        return self._http.post(f"{self._base}/action", {"type": type, **params})

    def click(self, selector: str, *, wait_after: Optional[int] = None) -> Dict[str, Any]:
        return self.action("click", **_drop_none({"selector": selector, "wait_after": wait_after}))

    def fill(self, selector: str, value: str) -> Dict[str, Any]:
        return self.action("fill", selector=selector, value=value)

    def select(self, selector: str, value: str) -> Dict[str, Any]:
        return self.action("select", selector=selector, value=value)

    def press(
        self,
        key: str,
        *,
        selector: Optional[str] = None,
        wait_after: Optional[int] = None,
    ) -> Dict[str, Any]:
        return self.action(
            "press", **_drop_none({"key": key, "selector": selector, "wait_after": wait_after})
        )

    def scroll(self, *, direction: str = "down", amount: Optional[int] = None) -> Dict[str, Any]:
        return self.action("scroll", **_drop_none({"direction": direction, "amount": amount}))

    def wait(self, ms: int = 1000) -> Dict[str, Any]:
        return self.action("wait", ms=ms)

    def wait_for(self, selector: str, *, timeout: Optional[int] = None) -> Dict[str, Any]:
        return self.action("wait_for", **_drop_none({"selector": selector, "timeout": timeout}))

    def screenshot(self, *, full_page: bool = False) -> Dict[str, Any]:
        """Take a screenshot. The API returns a base64 PNG inside the payload."""
        return self.action("screenshot", full_page=full_page)

    def evaluate(self, expression: str) -> Dict[str, Any]:
        """Evaluate a JavaScript expression in the page."""
        return self.action("evaluate", expression=expression)

    def hover(self, selector: str) -> Dict[str, Any]:
        return self.action("hover", selector=selector)

    def check(self, selector: str) -> Dict[str, Any]:
        return self.action("check", selector=selector)

    # -- cookies & persistence -------------------------------------------------

    def get_cookies(self) -> List[Cookie]:
        data = self._http.get(f"{self._base}/cookies")
        raw = data.get("cookies") or data.get("data") or []
        return [Cookie.model_validate(c) for c in raw]

    def set_cookies(self, cookies: Sequence[Union[Cookie, Dict[str, Any]]]) -> Dict[str, Any]:
        return self._http.post(
            f"{self._base}/cookies",
            {"op": "set", "cookies": _cookies_payload(cookies)},
        )

    def clear_cookies(self) -> Dict[str, Any]:
        return self._http.post(f"{self._base}/cookies", {"op": "clear"})

    def save_session(self) -> Dict[str, Any]:
        """Persist cookies/storage under this session's ``session_name``."""
        return self._http.post(f"{self._base}/save_session")

    # -- context manager -------------------------------------------------------

    def __enter__(self) -> "BrowserSession":
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()


class BrowserResource:
    """Factory for browser sessions, exposed as ``client.browser``."""

    def __init__(self, http: HttpClient) -> None:
        self._http = http

    def create(
        self,
        *,
        url: Optional[str] = None,
        cookie: Optional[str] = None,
        proxy: Optional[str] = None,
        session_name: Optional[str] = None,
    ) -> BrowserSession:
        """Create a session. It starts asynchronously — call ``wait_until_running()``."""
        body = _drop_none(
            {"url": url, "cookie": cookie, "proxy": proxy, "session_name": session_name}
        )
        info = SessionInfo.model_validate(self._http.post("/api/v1/browser", body))
        return BrowserSession(self._http, info)


class AsyncBrowserSession:
    """Asynchronous handle to a persistent browser session."""

    def __init__(self, http: AsyncHttpClient, info: SessionInfo) -> None:
        self._http = http
        self.id = info.id
        self.status = info.status
        self.url = info.url
        self.session_name = info.session_name
        self.expires_at = info.expires_at

    @property
    def _base(self) -> str:
        return f"/api/v1/browser/{self.id}"

    def _apply(self, info: SessionInfo) -> None:
        self.status = info.status
        self.url = info.url
        self.session_name = info.session_name
        self.expires_at = info.expires_at

    async def refresh(self) -> "AsyncBrowserSession":
        self._apply(SessionInfo.model_validate(await self._http.get(self._base)))
        return self

    async def wait_until_running(
        self, *, timeout: float = 60.0, poll_interval: float = 1.0
    ) -> "AsyncBrowserSession":
        deadline = time.monotonic() + timeout
        while True:
            await self.refresh()
            if self.status == "running":
                return self
            if self.status in _TERMINAL_STATUSES:
                raise WebpipeError(
                    f"Browser session {self.id} entered terminal status '{self.status}'"
                )
            if time.monotonic() >= deadline:
                raise PollTimeoutError(f"Browser session {self.id} not running after {timeout}s")
            await asyncio.sleep(poll_interval)

    async def close(self) -> None:
        await self._http.delete(self._base)
        self.status = "stopped"

    async def navigate(
        self,
        url: str,
        *,
        formats: Optional[Sequence[str]] = None,
        scroll_to_bottom: Optional[bool] = None,
        max_scrolls: Optional[int] = None,
        scroll_wait: Optional[int] = None,
        scroll_step: Optional[int] = None,
    ) -> Document:
        body = {"url": url}
        body.update(_scrape_body(formats, scroll_to_bottom, max_scrolls, scroll_wait, scroll_step))
        data = await self._http.post(f"{self._base}/navigate", body)
        return Document.model_validate(data.get("data") or {})

    async def scrape(
        self,
        *,
        formats: Optional[Sequence[str]] = None,
        scroll_to_bottom: Optional[bool] = None,
        max_scrolls: Optional[int] = None,
        scroll_wait: Optional[int] = None,
        scroll_step: Optional[int] = None,
    ) -> Document:
        body = _scrape_body(formats, scroll_to_bottom, max_scrolls, scroll_wait, scroll_step)
        data = await self._http.post(f"{self._base}/scrape", body)
        return Document.model_validate(data.get("data") or {})

    async def action(self, type: str, **params: Any) -> Dict[str, Any]:
        return await self._http.post(f"{self._base}/action", {"type": type, **params})

    async def click(self, selector: str, *, wait_after: Optional[int] = None) -> Dict[str, Any]:
        return await self.action(
            "click", **_drop_none({"selector": selector, "wait_after": wait_after})
        )

    async def fill(self, selector: str, value: str) -> Dict[str, Any]:
        return await self.action("fill", selector=selector, value=value)

    async def select(self, selector: str, value: str) -> Dict[str, Any]:
        return await self.action("select", selector=selector, value=value)

    async def press(
        self,
        key: str,
        *,
        selector: Optional[str] = None,
        wait_after: Optional[int] = None,
    ) -> Dict[str, Any]:
        return await self.action(
            "press", **_drop_none({"key": key, "selector": selector, "wait_after": wait_after})
        )

    async def scroll(
        self, *, direction: str = "down", amount: Optional[int] = None
    ) -> Dict[str, Any]:
        return await self.action("scroll", **_drop_none({"direction": direction, "amount": amount}))

    async def wait(self, ms: int = 1000) -> Dict[str, Any]:
        return await self.action("wait", ms=ms)

    async def wait_for(self, selector: str, *, timeout: Optional[int] = None) -> Dict[str, Any]:
        return await self.action(
            "wait_for", **_drop_none({"selector": selector, "timeout": timeout})
        )

    async def screenshot(self, *, full_page: bool = False) -> Dict[str, Any]:
        return await self.action("screenshot", full_page=full_page)

    async def evaluate(self, expression: str) -> Dict[str, Any]:
        return await self.action("evaluate", expression=expression)

    async def hover(self, selector: str) -> Dict[str, Any]:
        return await self.action("hover", selector=selector)

    async def check(self, selector: str) -> Dict[str, Any]:
        return await self.action("check", selector=selector)

    async def get_cookies(self) -> List[Cookie]:
        data = await self._http.get(f"{self._base}/cookies")
        raw = data.get("cookies") or data.get("data") or []
        return [Cookie.model_validate(c) for c in raw]

    async def set_cookies(self, cookies: Sequence[Union[Cookie, Dict[str, Any]]]) -> Dict[str, Any]:
        return await self._http.post(
            f"{self._base}/cookies",
            {"op": "set", "cookies": _cookies_payload(cookies)},
        )

    async def clear_cookies(self) -> Dict[str, Any]:
        return await self._http.post(f"{self._base}/cookies", {"op": "clear"})

    async def save_session(self) -> Dict[str, Any]:
        return await self._http.post(f"{self._base}/save_session")

    async def __aenter__(self) -> "AsyncBrowserSession":
        return self

    async def __aexit__(self, *exc: object) -> None:
        await self.close()


class AsyncBrowserResource:
    """Factory for async browser sessions, exposed as ``client.browser``."""

    def __init__(self, http: AsyncHttpClient) -> None:
        self._http = http

    async def create(
        self,
        *,
        url: Optional[str] = None,
        cookie: Optional[str] = None,
        proxy: Optional[str] = None,
        session_name: Optional[str] = None,
    ) -> AsyncBrowserSession:
        body = _drop_none(
            {"url": url, "cookie": cookie, "proxy": proxy, "session_name": session_name}
        )
        info = SessionInfo.model_validate(await self._http.post("/api/v1/browser", body))
        return AsyncBrowserSession(self._http, info)
