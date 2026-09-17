"""Tests for the sync client. All HTTP traffic is mocked via respx."""

import httpx
import pytest
import respx

from webpipe import (
    AuthenticationError,
    CrawlError,
    NotFoundError,
    WebpipeClient,
)

BASE = "https://api.web2json.ai"


@pytest.fixture
def client() -> WebpipeClient:
    return WebpipeClient(api_key="wc-test-key", max_retries=0)


# -- construction -----------------------------------------------------------


def test_missing_api_key_raises(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("WEBPIPE_API_KEY", raising=False)
    with pytest.raises(ValueError, match="WEBPIPE_API_KEY"):
        WebpipeClient()


def test_api_key_from_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("WEBPIPE_API_KEY", "wc-env-key")
    WebpipeClient()  # must not raise


# -- scrape -----------------------------------------------------------------


@respx.mock
def test_scrape_success(client: WebpipeClient) -> None:
    route = respx.post(f"{BASE}/api/v1/scrape").mock(
        return_value=httpx.Response(
            200,
            json={
                "success": True,
                "data": {
                    "markdown": "# Example",
                    "metadata": {
                        "title": "Example Domain",
                        "sourceURL": "https://example.com",
                        "statusCode": 200,
                    },
                },
            },
        )
    )

    doc = client.scrape("https://example.com", formats=["markdown", "metadata"])

    assert doc.markdown == "# Example"
    assert doc.metadata is not None
    assert doc.metadata.title == "Example Domain"
    assert doc.metadata.source_url == "https://example.com"
    assert doc.metadata.status_code == 200

    sent = route.calls.last.request
    assert sent.headers["Authorization"] == "Bearer wc-test-key"
    assert sent.headers["User-Agent"].startswith("webpipe-sdk-python/")

    import json as _json

    assert _json.loads(sent.content) == {
        "url": "https://example.com",
        "formats": ["markdown", "metadata"],
    }


@respx.mock
def test_scrape_401_raises_authentication_error(client: WebpipeClient) -> None:
    respx.post(f"{BASE}/api/v1/scrape").mock(
        return_value=httpx.Response(
            401, json={"success": False, "error": "未授权：请提供有效的 API Key"}
        )
    )
    with pytest.raises(AuthenticationError) as exc_info:
        client.scrape("https://example.com")
    assert exc_info.value.status_code == 401
    assert "未授权" in str(exc_info.value)


@respx.mock
def test_scrape_retries_on_429() -> None:
    retry_client = WebpipeClient(api_key="wc-test-key", max_retries=1)
    route = respx.post(f"{BASE}/api/v1/scrape").mock(
        side_effect=[
            httpx.Response(429, json={"success": False, "error": "rate limited"}),
            httpx.Response(200, json={"success": True, "data": {"markdown": "ok"}}),
        ]
    )
    doc = retry_client.scrape("https://example.com")
    assert doc.markdown == "ok"
    assert route.call_count == 2


# -- map --------------------------------------------------------------------


@respx.mock
def test_map_returns_links(client: WebpipeClient) -> None:
    respx.post(f"{BASE}/api/v1/map").mock(
        return_value=httpx.Response(
            200,
            json={
                "success": True,
                "links": [
                    {"url": "https://example.com/about", "title": "About", "description": ""},
                    {"url": "https://example.com/blog", "title": None, "description": None},
                ],
            },
        )
    )
    links = client.map("https://example.com", limit=100)
    assert len(links) == 2
    assert links[0].url == "https://example.com/about"
    assert links[0].title == "About"


# -- crawl ------------------------------------------------------------------


@respx.mock
def test_crawl_polls_until_completed(client: WebpipeClient) -> None:
    respx.post(f"{BASE}/api/v1/crawl").mock(
        return_value=httpx.Response(200, json={"success": True, "id": "job-1"})
    )
    status_route = respx.get(f"{BASE}/api/v1/crawl/job-1").mock(
        side_effect=[
            httpx.Response(
                200,
                json={
                    "success": True,
                    "status": "scraping",
                    "total": 2,
                    "completed": 1,
                    "data": [],
                    "errors": [],
                },
            ),
            httpx.Response(
                200,
                json={
                    "success": True,
                    "status": "completed",
                    "total": 2,
                    "completed": 2,
                    "data": [
                        {"markdown": "# A", "metadata": {"sourceURL": "https://x/a"}},
                        {"markdown": "# B", "metadata": {"sourceURL": "https://x/b"}},
                    ],
                    "errors": [],
                    "expires_at": "2026-07-28T00:00:00+00:00",
                },
            ),
        ]
    )

    job = client.crawl("https://x", limit=2, poll_interval=0.01)

    assert job.status == "completed"
    assert len(job.data) == 2
    assert job.data[1].markdown == "# B"
    assert status_route.call_count == 2


@respx.mock
def test_crawl_failed_raises(client: WebpipeClient) -> None:
    respx.post(f"{BASE}/api/v1/crawl").mock(
        return_value=httpx.Response(200, json={"success": True, "id": "job-2"})
    )
    respx.get(f"{BASE}/api/v1/crawl/job-2").mock(
        return_value=httpx.Response(
            200,
            json={"success": True, "status": "failed", "errors": ["boom"]},
        )
    )
    with pytest.raises(CrawlError):
        client.crawl("https://x", poll_interval=0.01)


@respx.mock
def test_crawl_timeout(client: WebpipeClient) -> None:
    respx.post(f"{BASE}/api/v1/crawl").mock(
        return_value=httpx.Response(200, json={"success": True, "id": "job-3"})
    )
    respx.get(f"{BASE}/api/v1/crawl/job-3").mock(
        return_value=httpx.Response(
            200, json={"success": True, "status": "scraping", "data": [], "errors": []}
        )
    )
    with pytest.raises(TimeoutError):
        client.crawl("https://x", poll_interval=0.01, timeout=0.05)


@respx.mock
def test_get_crawl_status_404(client: WebpipeClient) -> None:
    respx.get(f"{BASE}/api/v1/crawl/gone").mock(
        return_value=httpx.Response(404, json={"success": False, "error": "not found"})
    )
    with pytest.raises(NotFoundError):
        client.get_crawl_status("gone")
