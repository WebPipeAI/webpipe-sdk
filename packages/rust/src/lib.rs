//! webpipe-sdk — the official Rust SDK for WebPipe.ai.
//!
//! Turn any webpage into clean, structured data (Scrape / Map / Crawl /
//! Browser Session). Async-first, built on reqwest + serde.
//!
//! ```no_run
//! use webpipe_sdk::Client;
//!
//! #[tokio::main]
//! async fn main() -> Result<(), webpipe_sdk::WebpipeError> {
//!     let client = Client::from_env()?; // WEBPIPE_API_KEY
//!     let doc = client.scrape("https://example.com", None).await?;
//!     println!("{:?}", doc.markdown);
//!     Ok(())
//! }
//! ```

mod browser;
mod client;
mod error;
mod http;
mod types;

pub use browser::{BrowserResource, BrowserSession};
pub use client::{Client, ClientBuilder, DEFAULT_BASE_URL, ENV_API_KEY, ENV_API_URL, VERSION};
pub use error::{Result, WebpipeError};
pub use types::{
    crawl_status, session_status, Cookie, CrawlJob, CrawlJobStart, CrawlOptions,
    CreateSessionOptions, Document, Link, MapLink, MapOptions, NavigateOptions, ScrapeOptions,
    SessionInfo,
};
