//! The WebPipe API client.

use std::time::{Duration, Instant};

use serde::Serialize;

use crate::browser::BrowserResource;
use crate::error::{Result, WebpipeError};
use crate::http::HttpClient;
use crate::types::{
    CrawlJob, CrawlJobStart, CrawlOptions, Document, MapLink, MapOptions, ScrapeOptions,
};

pub const VERSION: &str = "0.1.0";
pub const DEFAULT_BASE_URL: &str = "https://api.web2json.ai";
pub const ENV_API_KEY: &str = "WEBPIPE_API_KEY";
pub const ENV_API_URL: &str = "WEBPIPE_API_URL";

/// The WebPipe API client (async).
///
/// ```no_run
/// # async fn demo() -> Result<(), webpipe_sdk::WebpipeError> {
/// let client = webpipe_sdk::Client::from_env()?;
/// let doc = client.scrape("https://example.com", None).await?;
/// println!("{:?}", doc.markdown);
/// # Ok(())
/// # }
/// ```
pub struct Client {
    http: std::sync::Arc<HttpClient>,
    browser: BrowserResource,
}

impl Client {
    /// Create a client with the given API key (`wc-...`).
    pub fn new(api_key: impl Into<String>) -> Result<Self> {
        ClientBuilder::new(api_key).build()
    }

    /// Create a client from environment variables (`WEBPIPE_API_KEY`, `WEBPIPE_API_URL`).
    pub fn from_env() -> Result<Self> {
        let key = std::env::var(ENV_API_KEY).map_err(|_| {
            WebpipeError::Config(format!(
                "missing API key — set {ENV_API_KEY} or use Client::new(). \
                 Create one at https://webpipe.ai/api-keys.html"
            ))
        })?;
        ClientBuilder::new(key)
            .base_url(std::env::var(ENV_API_URL).unwrap_or_else(|_| DEFAULT_BASE_URL.into()))
            .build()
    }

    /// Start configuring a client.
    pub fn builder(api_key: impl Into<String>) -> ClientBuilder {
        ClientBuilder::new(api_key)
    }

    /// Browser session factory.
    pub fn browser(&self) -> &BrowserResource {
        &self.browser
    }

    /// Scrape a single page into markdown/html/links/metadata/json.
    pub async fn scrape(&self, url: &str, options: Option<&ScrapeOptions>) -> Result<Document> {
        #[derive(Serialize)]
        struct Body<'a> {
            url: &'a str,
            #[serde(flatten)]
            options: Option<&'a ScrapeOptions>,
        }
        let res = self
            .http
            .post("/api/v1/scrape", &Body { url, options })
            .await?;
        Ok(serde_json::from_value(
            res.get("data").cloned().unwrap_or(serde_json::Value::Null),
        )?)
    }

    /// Discover URLs of a website (robots.txt → sitemap → in-page links).
    pub async fn map(&self, url: &str, options: Option<&MapOptions>) -> Result<Vec<MapLink>> {
        #[derive(Serialize)]
        struct Body<'a> {
            url: &'a str,
            #[serde(flatten)]
            options: Option<&'a MapOptions>,
        }
        let res = self.http.post("/api/v1/map", &Body { url, options }).await?;
        Ok(serde_json::from_value(
            res.get("links").cloned().unwrap_or(serde_json::Value::Null),
        )?)
    }

    /// Submit an async crawl job and return its handle immediately.
    pub async fn start_crawl(&self, url: &str, options: Option<&CrawlOptions>) -> Result<CrawlJobStart> {
        #[derive(Serialize)]
        struct Body<'a> {
            url: &'a str,
            #[serde(flatten)]
            options: Option<&'a CrawlOptions>,
        }
        let res = self
            .http
            .post("/api/v1/crawl", &Body { url, options })
            .await?;
        Ok(serde_json::from_value(res)?)
    }

    /// Fetch the current status and partial results of a crawl job.
    pub async fn get_crawl_status(&self, job_id: &str) -> Result<CrawlJob> {
        let res = self.http.get(&format!("/api/v1/crawl/{job_id}")).await?;
        Ok(serde_json::from_value(res)?)
    }

    /// Submit a crawl job and poll until it completes.
    ///
    /// Returns [`WebpipeError::Crawl`] if the job fails and
    /// [`WebpipeError::PollTimeout`] if `timeout` is exceeded.
    pub async fn crawl(
        &self,
        url: &str,
        options: Option<&CrawlOptions>,
        poll_interval: Duration,
        timeout: Option<Duration>,
    ) -> Result<CrawlJob> {
        let job = self.start_crawl(url, options).await?;
        let started = Instant::now();
        loop {
            let status = self.get_crawl_status(&job.id).await?;
            match status.status.as_str() {
                crate::types::crawl_status::COMPLETED => return Ok(status),
                crate::types::crawl_status::FAILED => {
                    return Err(WebpipeError::Crawl(format!(
                        "crawl job {} failed: {:?}",
                        job.id, status.errors
                    )));
                }
                _ => {}
            }
            if let Some(t) = timeout {
                if started.elapsed() >= t {
                    return Err(WebpipeError::PollTimeout(format!(
                        "crawl job {} did not complete within {}s",
                        job.id,
                        t.as_secs()
                    )));
                }
            }
            tokio::time::sleep(poll_interval).await;
        }
    }
}

/// Builder for [`Client`].
pub struct ClientBuilder {
    api_key: String,
    base_url: String,
    timeout: Duration,
    max_retries: u32,
}

impl ClientBuilder {
    pub fn new(api_key: impl Into<String>) -> Self {
        Self {
            api_key: api_key.into(),
            base_url: DEFAULT_BASE_URL.into(),
            timeout: Duration::from_secs(60),
            max_retries: 2,
        }
    }

    /// Override the API base URL (self-hosted).
    pub fn base_url(mut self, url: impl Into<String>) -> Self {
        self.base_url = url.into();
        self
    }

    /// Per-request timeout (default 60s; browser commands can be slow).
    pub fn timeout(mut self, timeout: Duration) -> Self {
        self.timeout = timeout;
        self
    }

    /// Retries for 429/5xx with exponential backoff (default 2).
    pub fn max_retries(mut self, max_retries: u32) -> Self {
        self.max_retries = max_retries;
        self
    }

    pub fn build(self) -> Result<Client> {
        if self.api_key.is_empty() {
            return Err(WebpipeError::Config(
                "missing API key — pass it to Client::new() or set WEBPIPE_API_KEY. \
                 Create one at https://webpipe.ai/api-keys.html"
                    .into(),
            ));
        }
        let http = std::sync::Arc::new(HttpClient::new(
            &self.base_url,
            &self.api_key,
            self.timeout,
            self.max_retries,
            &format!("webpipe-sdk-rust/{VERSION}"),
        )?);
        Ok(Client {
            browser: BrowserResource::new(std::sync::Arc::clone(&http)),
            http,
        })
    }
}
