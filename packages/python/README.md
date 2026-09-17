# webpipe-sdk (Python)

Official Python SDK for [WebPipe.ai](https://webpipe.ai/) — turn any webpage into clean, structured data.

## Install

```bash
pip install webpipe-sdk
```

Requires Python ≥ 3.9. Depends only on `httpx` + `pydantic`.

## Quick Start

```python
from webpipe import WebpipeClient

client = WebpipeClient()  # reads WEBPIPE_API_KEY env var, or pass api_key="wc-..."
```

### Scrape a page

```python
doc = client.scrape("https://example.com", formats=["markdown", "links", "metadata"])
print(doc.markdown)
print(doc.metadata.title)
```

Structured JSON extraction with a schema:

```python
doc = client.scrape(
    "https://go.dev/doc/effective_go",
    formats=["json"],
    json_schema={
        "type": "object",
        "properties": {
            "title": {"type": "string"},
            "sections": {"type": "array", "x-source": "headings"},
        },
    },
)
print(doc.json)
```

### Crawl a whole site (polls automatically)

```python
job = client.crawl(
    "https://docs.example.com",
    limit=50,
    scrape_options={"formats": ["markdown"]},
)
for page in job.data:
    print(page.metadata.source_url, page.metadata.title)
```

Need control? `job = client.start_crawl(url)` returns immediately; poll with `client.get_crawl_status(job.id)`.

### Browser session (JS-rendered / logged-in pages)

```python
with client.browser.create(url="https://example.com/login") as session:
    session.wait_until_running()
    session.fill("input[name=email]", "you@example.com")
    session.fill("input[name=password]", "secret")
    session.press("Enter", wait_after=3000)

    doc = session.navigate("https://example.com/dashboard", formats=["markdown"])
    print(doc.markdown)
    session.save_session()  # reuse login state next time via session_name
# session.close() is called automatically on exit
```

### Map (discover all URLs)

```python
for link in client.map("https://example.com", limit=100, search="blog"):
    print(link.url, link.title)
```

### Async

```python
import asyncio
from webpipe import AsyncWebpipeClient

async def main():
    async with AsyncWebpipeClient() as client:
        doc = await client.scrape("https://example.com", formats=["markdown"])
        print(doc.markdown)

asyncio.run(main())
```

## Errors

```python
from webpipe import AuthenticationError, RateLimitError, NotFoundError, WebpipeError

try:
    client.scrape("https://example.com")
except AuthenticationError:
    ...  # bad API key
except RateLimitError as e:
    ...  # e.status_code == 429, e.message keeps the server's error text
except WebpipeError:
    ...  # anything else
```

`429` and `5xx` responses are retried automatically with exponential backoff (`max_retries=2` by default).

## Configuration

| Param | Env var | Default |
|---|---|---|
| `api_key` | `WEBPIPE_API_KEY` | — (required) |
| `base_url` | `WEBPIPE_API_URL` | `https://api.web2json.ai` |
| `timeout` | — | 60s |
| `max_retries` | — | 2 |

## Development

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
pytest
```
