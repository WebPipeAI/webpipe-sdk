"""webpipe-sdk — the official Python SDK for WebPipe.ai.

>>> from webpipe import WebpipeClient
>>> client = WebpipeClient(api_key="wc-...")
>>> doc = client.scrape("https://example.com", formats=["markdown"])
>>> print(doc.markdown)
"""

from ._version import __version__
from .browser import AsyncBrowserSession, BrowserSession
from .client import AsyncWebpipeClient, WebpipeClient
from .errors import (
    APIError,
    AuthenticationError,
    BadRequestError,
    CrawlError,
    ForbiddenError,
    NotFoundError,
    PollTimeoutError,
    RateLimitError,
    ServerError,
    WebpipeError,
)
from .types import (
    Cookie,
    CrawlJob,
    CrawlJobStart,
    Document,
    Link,
    MapLink,
    Metadata,
    SessionInfo,
)

__all__ = [
    "__version__",
    # clients
    "WebpipeClient",
    "AsyncWebpipeClient",
    # browser
    "BrowserSession",
    "AsyncBrowserSession",
    # types
    "Cookie",
    "CrawlJob",
    "CrawlJobStart",
    "Document",
    "Link",
    "MapLink",
    "Metadata",
    "SessionInfo",
    # errors
    "WebpipeError",
    "APIError",
    "AuthenticationError",
    "BadRequestError",
    "CrawlError",
    "ForbiddenError",
    "NotFoundError",
    "PollTimeoutError",
    "RateLimitError",
    "ServerError",
]
