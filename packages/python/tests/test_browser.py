"""Tests for the browser session resource. All HTTP traffic is mocked."""

import httpx
import pytest
import respx

from webpipe import WebpipeClient

BASE = "https://api.web2json.ai"


@pytest.fixture
def client() -> WebpipeClient:
    return WebpipeClient(api_key="wc-test-key", max_retries=0)


@respx.mock
def test_session_lifecycle(client: WebpipeClient) -> None:
    # create
    respx.post(f"{BASE}/api/v1/browser").mock(
        return_value=httpx.Response(
            200,
            json={
                "success": True,
                "id": "sess-1",
                "status": "starting",
                "url": "https://example.com",
                "session_name": "demo",
            },
        )
    )
    # status: starting -> running
    respx.get(f"{BASE}/api/v1/browser/sess-1").mock(
        side_effect=[
            httpx.Response(200, json={"success": True, "id": "sess-1", "status": "starting"}),
            httpx.Response(200, json={"success": True, "id": "sess-1", "status": "running"}),
        ]
    )
    # scrape current page
    scrape_route = respx.post(f"{BASE}/api/v1/browser/sess-1/scrape").mock(
        return_value=httpx.Response(
            200,
            json={
                "success": True,
                "data": {"markdown": "# Hi", "metadata": {"title": "Hi"}},
            },
        )
    )
    # action
    action_route = respx.post(f"{BASE}/api/v1/browser/sess-1/action").mock(
        return_value=httpx.Response(200, json={"success": True, "ok": True})
    )
    # delete
    delete_route = respx.delete(f"{BASE}/api/v1/browser/sess-1").mock(
        return_value=httpx.Response(200, json={"success": True})
    )

    session = client.browser.create(url="https://example.com", session_name="demo")
    assert session.id == "sess-1"
    assert session.status == "starting"

    session.wait_until_running(timeout=5, poll_interval=0.01)
    assert session.status == "running"

    doc = session.scrape(formats=["markdown"])
    assert doc.markdown == "# Hi"

    session.click("button.more", wait_after=500)
    import json as _json

    sent = _json.loads(action_route.calls.last.request.content)
    assert sent == {"type": "click", "selector": "button.more", "wait_after": 500}

    session.close()
    assert session.status == "stopped"
    assert delete_route.called
    assert scrape_route.called


@respx.mock
def test_context_manager_closes_session(client: WebpipeClient) -> None:
    respx.post(f"{BASE}/api/v1/browser").mock(
        return_value=httpx.Response(
            200, json={"success": True, "id": "sess-2", "status": "running"}
        )
    )
    delete_route = respx.delete(f"{BASE}/api/v1/browser/sess-2").mock(
        return_value=httpx.Response(200, json={"success": True})
    )

    with client.browser.create() as session:
        assert session.status == "running"
    assert session.status == "stopped"
    assert delete_route.called


@respx.mock
def test_cookies(client: WebpipeClient) -> None:
    respx.post(f"{BASE}/api/v1/browser").mock(
        return_value=httpx.Response(
            200, json={"success": True, "id": "sess-3", "status": "running"}
        )
    )
    respx.get(f"{BASE}/api/v1/browser/sess-3/cookies").mock(
        return_value=httpx.Response(
            200,
            json={
                "success": True,
                "cookies": [{"name": "session", "value": "abc", "domain": "example.com"}],
            },
        )
    )
    set_route = respx.post(f"{BASE}/api/v1/browser/sess-3/cookies").mock(
        return_value=httpx.Response(200, json={"success": True})
    )

    session = client.browser.create()
    cookies = session.get_cookies()
    assert cookies[0].name == "session"

    session.set_cookies([{"name": "x", "value": "1", "domain": "example.com", "path": "/"}])
    import json as _json

    sent = _json.loads(set_route.calls.last.request.content)
    assert sent["op"] == "set"
    assert sent["cookies"][0]["name"] == "x"

    session.clear_cookies()
    sent = _json.loads(set_route.calls.last.request.content)
    assert sent == {"op": "clear"}
