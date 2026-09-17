//! Request options and response models.
//!
//! Serde handles the snake_case wire format directly on the structs — never
//! transform keys recursively, `json_schema` holds user-defined field names.

use serde::{Deserialize, Serialize};

/// Optional parameters of [`crate::Client::scrape`].
#[derive(Debug, Clone, Default, Serialize)]
pub struct ScrapeOptions {
    /// Output formats: "markdown" (default), "html", "rawHtml", "links", "metadata", "json".
    #[serde(skip_serializing_if = "Option::is_none")]
    pub formats: Option<Vec<String>>,
    /// JSON Schema for structured extraction (requires "json" in formats).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub json_schema: Option<serde_json::Value>,
    /// Use the server-side proxy pool.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub use_proxy: Option<bool>,
    /// Custom proxy URL, takes precedence over the proxy pool.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub proxy: Option<String>,
    /// Cookie header string, e.g. "session=abc; user=123".
    #[serde(skip_serializing_if = "Option::is_none")]
    pub cookie: Option<String>,
}

/// A link extracted from a page.
#[derive(Debug, Clone, Deserialize)]
pub struct Link {
    pub text: Option<String>,
    pub href: Option<String>,
}

/// Result of scraping a single page (scrape / browser / crawl data item).
///
/// `metadata` is raw JSON — the API adds extra keys (og:*, sourceURL, ...).
#[derive(Debug, Clone, Deserialize)]
pub struct Document {
    pub markdown: Option<String>,
    pub html: Option<String>,
    #[serde(rename = "rawHtml")]
    pub raw_html: Option<String>,
    pub links: Option<Vec<Link>>,
    pub metadata: Option<serde_json::Value>,
    pub json: Option<serde_json::Value>,
}

/// Optional parameters of [`crate::Client::map`].
#[derive(Debug, Clone, Default, Serialize)]
pub struct MapOptions {
    /// Max URLs to return (default 5000, max 50000).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub limit: Option<u32>,
    /// Keyword filter over url/title/description, ranked by relevance.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub search: Option<String>,
    /// "include" (default) | "only" (sitemap only) | "exclude" (links only).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sitemap: Option<String>,
    /// Restrict to the same root domain (server default true).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub same_domain: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub use_proxy: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub proxy: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub cookie: Option<String>,
}

/// A URL discovered by the Map endpoint.
#[derive(Debug, Clone, Deserialize)]
pub struct MapLink {
    pub url: String,
    pub title: Option<String>,
    pub description: Option<String>,
}

/// Optional parameters of [`crate::Client::start_crawl`] / [`crate::Client::crawl`].
#[derive(Debug, Clone, Default, Serialize)]
pub struct CrawlOptions {
    /// Max pages to crawl (default 100, max 10000).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub limit: Option<u32>,
    /// Max link-discovery depth. 0 = start page only.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub max_discovery_depth: Option<u32>,
    /// Regex whitelist for URL paths.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub include_paths: Option<Vec<String>>,
    /// Regex blacklist for URL paths.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub exclude_paths: Option<Vec<String>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub allow_subdomains: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub allow_external_links: Option<bool>,
    /// Crawl the whole domain, not just the start URL's subtree.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub crawl_entire_domain: Option<bool>,
    /// Deduplicate URLs ignoring query params.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub ignore_query_params: Option<bool>,
    /// "include" (default) | "skip" | "only".
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sitemap: Option<String>,
    /// Seconds between page requests.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub delay: Option<f64>,
    /// Concurrent requests, 1-10 (default 3).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub max_concurrency: Option<u32>,
    /// Per-page scrape options.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub scrape_options: Option<ScrapeOptions>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub use_proxy: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub proxy: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub cookie: Option<String>,
}

/// Handle returned when a crawl job is submitted.
#[derive(Debug, Clone, Deserialize)]
pub struct CrawlJobStart {
    pub id: String,
    pub url: Option<String>,
}

/// Crawl job status values.
pub mod crawl_status {
    pub const SCRAPING: &str = "scraping";
    pub const COMPLETED: &str = "completed";
    pub const FAILED: &str = "failed";
}

/// Status and (partial or final) results of a crawl job.
/// Results expire 24h after completion (see `expires_at`).
#[derive(Debug, Clone, Deserialize)]
pub struct CrawlJob {
    pub status: String,
    #[serde(default)]
    pub total: u32,
    #[serde(default)]
    pub completed: u32,
    #[serde(default)]
    pub data: Vec<Document>,
    #[serde(default)]
    pub errors: Vec<serde_json::Value>,
    pub expires_at: Option<String>,
}

/// Optional parameters of [`crate::BrowserResource::create`].
#[derive(Debug, Clone, Default, Serialize)]
pub struct CreateSessionOptions {
    /// Navigate to this URL right after the browser starts.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub url: Option<String>,
    /// Initial cookie header string injected into the browser context.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub cookie: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub proxy: Option<String>,
    /// Named sessions share persisted login state (see `save_session`).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub session_name: Option<String>,
}

/// Browser session status values.
pub mod session_status {
    pub const STARTING: &str = "starting";
    pub const RUNNING: &str = "running";
    pub const STOPPED: &str = "stopped";
    pub const ERROR: &str = "error";
    pub const EXPIRED: &str = "expired";
}

/// State of a browser session (GET /browser/{id}).
#[derive(Debug, Clone, Deserialize)]
pub struct SessionInfo {
    pub id: String,
    pub status: String,
    pub url: Option<String>,
    pub title: Option<String>,
    pub session_name: Option<String>,
    pub expires_at: Option<String>,
}

/// Optional parameters of [`crate::BrowserSession::navigate`] and
/// [`crate::BrowserSession::scrape`].
#[derive(Debug, Clone, Default, Serialize)]
pub struct NavigateOptions {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub formats: Option<Vec<String>>,
    /// Scroll to the bottom to trigger lazy-loaded content.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub scroll_to_bottom: Option<bool>,
    /// Max scroll rounds (1-30, default 10).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub max_scrolls: Option<u32>,
    /// Wait after each scroll, ms (500-8000, default 2000).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub scroll_wait: Option<u32>,
    /// Pixels per scroll (200-5000, default 1200).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub scroll_step: Option<u32>,
}

/// A browser cookie. domain/path are needed when injecting cookies.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Cookie {
    pub name: String,
    pub value: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub domain: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub path: Option<String>,
}
