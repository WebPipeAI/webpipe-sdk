"""Exception hierarchy for the WebPipe SDK."""

from typing import Any, Optional


class WebpipeError(Exception):
    """Base class for all webpipe-sdk errors."""


class APIError(WebpipeError):
    """An error response returned by the WebPipe API.

    Always carries the server's original ``error`` message and the HTTP status code.
    """

    def __init__(self, message: str, status_code: int, body: Optional[Any] = None) -> None:
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.body = body

    def __str__(self) -> str:
        return f"[{self.status_code}] {self.message}"


class BadRequestError(APIError):
    """400 — invalid parameters (missing url, bad format, ...)."""


class AuthenticationError(APIError):
    """401 — API key missing, invalid or deleted."""


class ForbiddenError(APIError):
    """403 — accessing a resource owned by another user."""


class NotFoundError(APIError):
    """404 — job/session does not exist or has expired."""


class RateLimitError(APIError):
    """429 — rate limit exceeded, or too many concurrent browser sessions."""


class ServerError(APIError):
    """5xx — scrape timeout, target site unreachable, ..."""


class CrawlError(WebpipeError):
    """A crawl job reached status ``failed``."""


class PollTimeoutError(WebpipeError, TimeoutError):
    """Polling did not finish within the allotted timeout.

    Subclasses the builtin :class:`TimeoutError` so generic ``except TimeoutError``
    handlers also catch it.
    """
