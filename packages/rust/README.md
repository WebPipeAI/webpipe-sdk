# webpipe-sdk (Rust)

Official Rust SDK for [WebPipe.ai](https://webpipe.ai/) — turn any webpage into clean, structured data.

## Install

```toml
[dependencies]
webpipe-sdk = "0.1.0"
tokio = { version = "1", features = ["macros", "rt-multi-thread"] }
```

Async-first, built on `reqwest` (rustls) + `serde` + `thiserror`.

## Quick Start

```rust
use webpipe_sdk::{Client, ScrapeOptions};

#[tokio::main]
async fn main() -> Result<(), webpipe_sdk::WebpipeError> {
    let client = Client::from_env()?; // WEBPIPE_API_KEY, or Client::new("wc-...")?

    // Scrape a page
    let doc = client
        .scrape("https://example.com", Some(&ScrapeOptions {
            formats: Some(vec!["markdown".into(), "metadata".into()]),
            ..Default::default()
        }))
        .await?;
    println!("{:?}", doc.markdown);
    Ok(())
}
```

### Crawl a whole site (polls automatically)

```rust
use std::time::Duration;
use webpipe_sdk::CrawlOptions;

let job = client
    .crawl(
        "https://docs.example.com",
        Some(&CrawlOptions {
            limit: Some(50),
            scrape_options: Some(ScrapeOptions {
                formats: Some(vec!["markdown".into()]),
                ..Default::default()
            }),
            ..Default::default()
        }),
        Duration::from_secs(2),
        Some(Duration::from_secs(600)),
    )
    .await?;
for page in &job.data {
    println!("{:?}", page.metadata);
}
```

Need control? `client.start_crawl(url, opts).await?` returns immediately; poll with `client.get_crawl_status(&job.id).await?`.

### Browser session (JS-rendered / logged-in pages)

```rust
use webpipe_sdk::CreateSessionOptions;
use std::time::Duration;

let mut session = client
    .browser()
    .create(Some(&CreateSessionOptions {
        url: Some("https://example.com/login".into()),
        ..Default::default()
    }))
    .await?;

session
    .wait_until_running(Duration::from_secs(1), Duration::from_secs(60))
    .await?;
session.fill("input[name=email]", "you@example.com").await?;
session.fill("input[name=password]", "secret").await?;
session.press("Enter", None, Some(3000)).await?;

let doc = session
    .navigate("https://example.com/dashboard", None)
    .await?;
println!("{:?}", doc.markdown);
session.save_session().await?; // reuse login state next time via session_name
session.close().await?;
```

### Map (discover all URLs)

```rust
let links = client
    .map("https://example.com", Some(&webpipe_sdk::MapOptions {
        limit: Some(100),
        ..Default::default()
    }))
    .await?;
for link in links {
    println!("{} {:?}", link.url, link.title);
}
```

## Errors

```rust
use webpipe_sdk::WebpipeError;

match client.scrape("https://example.com", None).await {
    Err(WebpipeError::Authentication(msg)) => { /* bad API key */ }
    Err(WebpipeError::RateLimit(msg)) => { /* 429 */ }
    Err(WebpipeError::Api { status, message }) => { /* other API errors */ }
    other => { /* ... */ }
}
```

`429` and `5xx` responses are retried automatically with exponential backoff (`max_retries(2)` by default).

## Configuration

| Builder | Env var | Default |
|---|---|---|
| `Client::new(key)` | `WEBPIPE_API_KEY` | — (required) |
| `base_url(...)` | `WEBPIPE_API_URL` | `https://api.web2json.ai` |
| `timeout(Duration)` | — | 60s |
| `max_retries(u32)` | — | 2 |

## Development

```bash
cargo test
```
