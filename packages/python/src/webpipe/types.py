"""Pydantic models for WebPipe API payloads.

The API uses snake_case in requests but a mix of snake_case and camelCase in
responses (``sourceURL``, ``statusCode``, ``rawHtml``). Aliases below map the
camelCase outliers onto pythonic attribute names.
"""

import warnings
from typing import Any, Dict, List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field


class Link(BaseModel):
    """A link extracted from a page."""

    text: Optional[str] = None
    href: Optional[str] = None


class Metadata(BaseModel):
    """Page metadata. Unknown fields (``og:*``, ``twitter:*``, ...) are preserved."""

    model_config = ConfigDict(extra="allow", populate_by_name=True)

    title: Optional[str] = None
    description: Optional[str] = None
    source_url: Optional[str] = Field(None, alias="sourceURL")
    status_code: Optional[int] = Field(None, alias="statusCode")


with warnings.catch_warnings():
    # pydantic warns that field "json" shadows the deprecated BaseModel.json()
    # method. We keep the name for API parity (the response key IS "json").
    warnings.simplefilter("ignore")

    class Document(BaseModel):
        """Result of scraping a single page (scrape / browser / crawl data item)."""

        model_config = ConfigDict(extra="allow", populate_by_name=True)

        markdown: Optional[str] = None
        html: Optional[str] = None
        raw_html: Optional[str] = Field(None, alias="rawHtml")
        links: Optional[List[Link]] = None
        metadata: Optional[Metadata] = None
        json: Optional[Any] = None


class MapLink(BaseModel):
    """A URL discovered by the Map endpoint."""

    url: str
    title: Optional[str] = None
    description: Optional[str] = None


CrawlStatus = Literal["scraping", "completed", "failed"]


class CrawlJobStart(BaseModel):
    """Handle returned when a crawl job is submitted."""

    id: str
    url: Optional[str] = None


class CrawlJob(BaseModel):
    """Status and (partial or final) results of a crawl job.

    Results are only kept by the server for 24 hours (see ``expires_at``).
    """

    model_config = ConfigDict(populate_by_name=True)

    status: CrawlStatus
    total: int = 0
    completed: int = 0
    data: List[Document] = Field(default_factory=list)
    errors: List[Any] = Field(default_factory=list)
    expires_at: Optional[str] = None


SessionStatus = Literal["starting", "running", "stopped", "error", "expired"]


class SessionHistoryEntry(BaseModel):
    """One command in a browser session's history."""

    model_config = ConfigDict(extra="allow")

    cmd: Optional[str] = None
    type: Optional[str] = None
    url: Optional[str] = None
    ok: Optional[bool] = None


class SessionInfo(BaseModel):
    """State of a browser session (GET /browser/{id})."""

    model_config = ConfigDict(extra="allow", populate_by_name=True)

    id: str
    status: SessionStatus
    url: Optional[str] = None
    title: Optional[str] = None
    session_name: Optional[str] = None
    expires_at: Optional[str] = None
    last_cmd_at: Optional[str] = None
    history: List[SessionHistoryEntry] = Field(default_factory=list)
    last_data: Optional[Dict[str, Any]] = None


class Cookie(BaseModel):
    """A browser cookie. ``domain``/``path`` are needed when injecting cookies."""

    model_config = ConfigDict(extra="allow")

    name: str
    value: str
    domain: Optional[str] = None
    path: Optional[str] = None
