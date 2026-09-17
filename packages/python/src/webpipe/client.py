"""WebPipe API clients: :class:`WebpipeClient` (sync) and :class:`AsyncWebpipeClient`."""

import asyncio
import os
import time
from typing import Any, Dict, List, Literal, Optional, Sequence

from ._http import AsyncHttpClient, HttpClient
from ._version import __version__
from .browser import AsyncBrowserResource, BrowserResource
from .errors import CrawlError, PollTimeoutError
from .types import CrawlJob, CrawlJobStart, Document, MapLink

DEFAULT_BASE_URL = "https://api.web2json.ai"
ENV_API_KEY = "WEBPIPE_API_KEY"
ENV_API_URL = "WEBPIPE_API_URL"

SitemapPolicy = Literal["include", "only", "exclude", "skip"]

_USER_AGENT = f"webpipe-sdk-python/{__version__}"


def _drop_none(mapping: Dict[str, Any]) -> Dict[str, Any]:
    return {k: v for k, v in mapping.items() if v is not None}


def _resolve_config(api_key: Optional[str], base_url: Optional[str]) -> "tuple[str, str]":
    key = api_key or os.environ.get(ENV_API_KEY)
    if not key:
        raise ValueError(
            f"Missing API key. Pass api_key=... or set the {ENV_API_KEY} environment "
            "variable. Create one at https://webpipe.ai/api-keys.html"
        )
    url = (base_url or os.environ.get(ENV_API_URL) or DEFAULT_BASE_URL).rstrip("/")
    return key, url


def _scrape_body(
    url: str,
    formats: Optional[Sequence[str]],
    json_schema: Optional[Dict[str, Any]],
    use_proxy: Optional[bool],
    proxy: Optional[str],
    cookie: Optional[str],
) -> Dict[str, Any]:
    # NOTE: keys are sent to the API verbatim (snake_case). Never transform
    # recursively — json_schema.properties holds user-defined field names.
    return _drop_none(
        {
            "url": url,
            "formats": list(formats) if formats is not None else None,
            "json_schema": json_schema,
            "use_proxy": use_proxy,
            "proxy": proxy,
            "cookie": cookie,
        }
    )


def _crawl_body(
    url: str,
    limit: Optional[int],
    max_discovery_depth: Optional[int],
    include_paths: Optional[Sequence[str]],
    exclude_paths: Optional[Sequence[str]],
    allow_subdomains: Optional[bool],
    allow_external_links: Optional[bool],
    crawl_entire_domain: Optional[bool],
    ignore_query_params: Optional[bool],
    sitemap: Optional[str],
    delay: Optional[float],
    max_concurrency: Optional[int],
    scrape_options: Optional[Dict[str, Any]],
    use_proxy: Optional[bool],
    proxy: Optional[str],
    cookie: Optional[str],
) -> Dict[str, Any]:
    return _drop_none(
        {
            "url": url,
            "limit": limit,
            "max_discovery_depth": max_discovery_depth,
            "include_paths": list(include_paths) if include_paths is not None else None,
            "exclude_paths": list(exclude_paths) if exclude_paths is not None else None,
            "allow_subdomains": allow_subdomains,
            "allow_external_links": allow_external_links,
            "crawl_entire_domain": crawl_entire_domain,
            "ignore_query_params": ignore_query_params,
            "sitemap": sitemap,
            "delay": delay,
            "max_concurrency": max_concurrency,
            "scrape_options": scrape_options,
            "use_proxy": use_proxy,
            "proxy": proxy,
            "cookie": cookie,
        }
    )


class WebpipeClient:
    """Synchronous WebPipe API client.

    >>> client = WebpipeClient()  # reads WEBPIPE_API_KEY
    >>> doc = client.scrape("https://example.com", formats=["markdown"])
    >>> print(doc.markdown)
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        timeout: float = 60.0,
        max_retries: int = 2,
    ) -> None:
        key, url = _resolve_config(api_key, base_url)
        self._http = HttpClient(url, key, timeout, max_retries, _USER_AGENT)
        self.browser = BrowserResource(self._http)

    # -- Scrape -------------------------------------------------------------

    def scrape(
        self,
        url: str,
        *,
        formats: Optional[Sequence[str]] = None,
        json_schema: Optional[Dict[str, Any]] = None,
        use_proxy: Optional[bool] = None,
        proxy: Optional[str] = None,
        cookie: Optional[str] = None,
    ) -> Document:
        """Scrape a single page into markdown/html/links/metadata/json."""
        data = self._http.post(
            "/api/v1/scrape", _scrape_body(url, formats, json_schema, use_proxy, proxy, cookie)
        )
        return Document.model_validate(data.get("data") or {})

    # -- Map ----------------------------------------------------------------

    def map(
        self,
        url: str,
        *,
        limit: Optional[int] = None,
        search: Optional[str] = None,
        sitemap: Optional[SitemapPolicy] = None,
        same_domain: Optional[bool] = None,
        use_proxy: Optional[bool] = None,
        proxy: Optional[str] = None,
        cookie: Optional[str] = None,
    ) -> List[MapLink]:
        """Discover URLs of a website (robots.txt → sitemap → in-page links)."""
        body = _drop_none(
            {
                "url": url,
                "limit": limit,
                "search": search,
                "sitemap": sitemap,
                "same_domain": same_domain,
                "use_proxy": use_proxy,
                "proxy": proxy,
                "cookie": cookie,
            }
        )
        data = self._http.post("/api/v1/map", body)
        return [MapLink.model_validate(link) for link in data.get("links") or []]

    # -- Crawl --------------------------------------------------------------

    def start_crawl(
        self,
        url: str,
        *,
        limit: Optional[int] = None,
        max_discovery_depth: Optional[int] = None,
        include_paths: Optional[Sequence[str]] = None,
        exclude_paths: Optional[Sequence[str]] = None,
        allow_subdomains: Optional[bool] = None,
        allow_external_links: Optional[bool] = None,
        crawl_entire_domain: Optional[bool] = None,
        ignore_query_params: Optional[bool] = None,
        sitemap: Optional[SitemapPolicy] = None,
        delay: Optional[float] = None,
        max_concurrency: Optional[int] = None,
        scrape_options: Optional[Dict[str, Any]] = None,
        use_proxy: Optional[bool] = None,
        proxy: Optional[str] = None,
        cookie: Optional[str] = None,
    ) -> CrawlJobStart:
        """Submit an async crawl job and return its handle immediately."""
        body = _crawl_body(
            url,
            limit,
            max_discovery_depth,
            include_paths,
            exclude_paths,
            allow_subdomains,
            allow_external_links,
            crawl_entire_domain,
            ignore_query_params,
            sitemap,
            delay,
            max_concurrency,
            scrape_options,
            use_proxy,
            proxy,
            cookie,
        )
        data = self._http.post("/api/v1/crawl", body)
        return CrawlJobStart(id=data["id"], url=data.get("url"))

    def get_crawl_status(self, job_id: str) -> CrawlJob:
        """Fetch the current status and partial results of a crawl job."""
        return CrawlJob.model_validate(self._http.get(f"/api/v1/crawl/{job_id}"))

    def crawl(
        self,
        url: str,
        *,
        poll_interval: float = 2.0,
        timeout: Optional[float] = None,
        **options: Any,
    ) -> CrawlJob:
        """Submit a crawl job and poll until it completes.

        Raises :class:`CrawlError` if the job fails, :class:`PollTimeoutError`
        (a ``TimeoutError``) if ``timeout`` is exceeded. ``**options`` are the
        same keyword arguments as :meth:`start_crawl`.
        """
        job = self.start_crawl(url, **options)
        deadline = None if timeout is None else time.monotonic() + timeout
        while True:
            status = self.get_crawl_status(job.id)
            if status.status == "completed":
                return status
            if status.status == "failed":
                raise CrawlError(f"Crawl job {job.id} failed: {status.errors}")
            if deadline is not None and time.monotonic() >= deadline:
                raise PollTimeoutError(f"Crawl job {job.id} did not complete within {timeout}s")
            time.sleep(poll_interval)

    # -- lifecycle ----------------------------------------------------------

    def close(self) -> None:
        self._http.close()

    def __enter__(self) -> "WebpipeClient":
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()


class AsyncWebpipeClient:
    """Asynchronous WebPipe API client. Mirrors :class:`WebpipeClient`."""

    def __init__(
        self,
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        timeout: float = 60.0,
        max_retries: int = 2,
    ) -> None:
        key, url = _resolve_config(api_key, base_url)
        self._http = AsyncHttpClient(url, key, timeout, max_retries, _USER_AGENT)
        self.browser = AsyncBrowserResource(self._http)

    async def scrape(
        self,
        url: str,
        *,
        formats: Optional[Sequence[str]] = None,
        json_schema: Optional[Dict[str, Any]] = None,
        use_proxy: Optional[bool] = None,
        proxy: Optional[str] = None,
        cookie: Optional[str] = None,
    ) -> Document:
        data = await self._http.post(
            "/api/v1/scrape", _scrape_body(url, formats, json_schema, use_proxy, proxy, cookie)
        )
        return Document.model_validate(data.get("data") or {})

    async def map(
        self,
        url: str,
        *,
        limit: Optional[int] = None,
        search: Optional[str] = None,
        sitemap: Optional[SitemapPolicy] = None,
        same_domain: Optional[bool] = None,
        use_proxy: Optional[bool] = None,
        proxy: Optional[str] = None,
        cookie: Optional[str] = None,
    ) -> List[MapLink]:
        body = _drop_none(
            {
                "url": url,
                "limit": limit,
                "search": search,
                "sitemap": sitemap,
                "same_domain": same_domain,
                "use_proxy": use_proxy,
                "proxy": proxy,
                "cookie": cookie,
            }
        )
        data = await self._http.post("/api/v1/map", body)
        return [MapLink.model_validate(link) for link in data.get("links") or []]

    async def start_crawl(
        self,
        url: str,
        *,
        limit: Optional[int] = None,
        max_discovery_depth: Optional[int] = None,
        include_paths: Optional[Sequence[str]] = None,
        exclude_paths: Optional[Sequence[str]] = None,
        allow_subdomains: Optional[bool] = None,
        allow_external_links: Optional[bool] = None,
        crawl_entire_domain: Optional[bool] = None,
        ignore_query_params: Optional[bool] = None,
        sitemap: Optional[SitemapPolicy] = None,
        delay: Optional[float] = None,
        max_concurrency: Optional[int] = None,
        scrape_options: Optional[Dict[str, Any]] = None,
        use_proxy: Optional[bool] = None,
        proxy: Optional[str] = None,
        cookie: Optional[str] = None,
    ) -> CrawlJobStart:
        body = _crawl_body(
            url,
            limit,
            max_discovery_depth,
            include_paths,
            exclude_paths,
            allow_subdomains,
            allow_external_links,
            crawl_entire_domain,
            ignore_query_params,
            sitemap,
            delay,
            max_concurrency,
            scrape_options,
            use_proxy,
            proxy,
            cookie,
        )
        data = await self._http.post("/api/v1/crawl", body)
        return CrawlJobStart(id=data["id"], url=data.get("url"))

    async def get_crawl_status(self, job_id: str) -> CrawlJob:
        return CrawlJob.model_validate(await self._http.get(f"/api/v1/crawl/{job_id}"))

    async def crawl(
        self,
        url: str,
        *,
        poll_interval: float = 2.0,
        timeout: Optional[float] = None,
        **options: Any,
    ) -> CrawlJob:
        job = await self.start_crawl(url, **options)
        deadline = None if timeout is None else time.monotonic() + timeout
        while True:
            status = await self.get_crawl_status(job.id)
            if status.status == "completed":
                return status
            if status.status == "failed":
                raise CrawlError(f"Crawl job {job.id} failed: {status.errors}")
            if deadline is not None and time.monotonic() >= deadline:
                raise PollTimeoutError(f"Crawl job {job.id} did not complete within {timeout}s")
            await asyncio.sleep(poll_interval)

    async def aclose(self) -> None:
        await self._http.aclose()

    async def __aenter__(self) -> "AsyncWebpipeClient":
        return self

    async def __aexit__(self, *exc: object) -> None:
        await self.aclose()
