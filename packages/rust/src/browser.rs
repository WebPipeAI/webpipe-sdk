//! Browser session resource.
//!
//! A [`BrowserSession`] is a handle to a persistent headless Chromium on the
//! server. Server-side facts: sessions expire after 30 minutes idle; at most
//! 2 concurrent running sessions per user. Always [`BrowserSession::close`]
//! when done.

use std::sync::Arc;
use std::time::{Duration, Instant};

use serde::Serialize;

use crate::error::{Result, WebpipeError};
use crate::http::HttpClient;
use crate::types::{
    session_status, Cookie, CreateSessionOptions, Document, NavigateOptions, SessionInfo,
};

/// Factory for browser sessions, obtained via [`crate::Client::browser`].
pub struct BrowserResource {
    http: Arc<HttpClient>,
}

impl BrowserResource {
    pub(crate) fn new(http: Arc<HttpClient>) -> Self {
        Self { http }
    }

    /// Create a session. It starts asynchronously — call
    /// [`BrowserSession::wait_until_running`] before sending commands for a
    /// controlled experience.
    pub async fn create(&self, options: Option<&CreateSessionOptions>) -> Result<BrowserSession> {
        let res = self
            .http
            .post("/api/v1/browser", &options.unwrap_or(&CreateSessionOptions::default()))
            .await?;
        let info: SessionInfo = serde_json::from_value(res)?;
        Ok(BrowserSession {
            http: Arc::clone(&self.http),
            info,
        })
    }
}

/// Handle to a persistent browser session.
pub struct BrowserSession {
    http: Arc<HttpClient>,
    info: SessionInfo,
}

impl BrowserSession {
    /// Session identifier.
    pub fn id(&self) -> &str {
        &self.info.id
    }

    /// Last known session status (see [`crate::types::session_status`]).
    pub fn status(&self) -> &str {
        &self.info.status
    }

    fn base(&self) -> String {
        format!("/api/v1/browser/{}", self.info.id)
    }

    /// Fetch the latest session state from the server.
    pub async fn refresh(&mut self) -> Result<&mut Self> {
        let res = self.http.get(&self.base()).await?;
        self.info = serde_json::from_value(res)?;
        Ok(self)
    }

    /// Block until the session is ready to accept commands.
    pub async fn wait_until_running(
        &mut self,
        poll_interval: Duration,
        timeout: Duration,
    ) -> Result<&mut Self> {
        let started = Instant::now();
        loop {
            self.refresh().await?;
            match self.info.status.as_str() {
                s if s == session_status::RUNNING => return Ok(self),
                s if s == session_status::STOPPED
                    || s == session_status::ERROR
                    || s == session_status::EXPIRED =>
                {
                    return Err(WebpipeError::Config(format!(
                        "browser session {} entered terminal status '{s}'",
                        self.info.id
                    )));
                }
                _ => {}
            }
            if started.elapsed() >= timeout {
                return Err(WebpipeError::PollTimeout(format!(
                    "browser session {} not running after {}s",
                    self.info.id,
                    timeout.as_secs()
                )));
            }
            tokio::time::sleep(poll_interval).await;
        }
    }

    /// Stop the session and release server resources.
    pub async fn close(&mut self) -> Result<()> {
        self.http.delete(&self.base()).await?;
        self.info.status = session_status::STOPPED.into();
        Ok(())
    }

    /// Navigate to a new URL (with anti-bot handling) and scrape it.
    pub async fn navigate(&self, url: &str, options: Option<&NavigateOptions>) -> Result<Document> {
        #[derive(Serialize)]
        struct Body<'a> {
            url: &'a str,
            #[serde(flatten)]
            options: Option<&'a NavigateOptions>,
        }
        let res = self
            .http
            .post(&format!("{}/navigate", self.base()), &Body { url, options })
            .await?;
        Ok(serde_json::from_value(
            res.get("data").cloned().unwrap_or(serde_json::Value::Null),
        )?)
    }

    /// Scrape the current page without navigating (faster than navigate).
    pub async fn scrape(&self, options: Option<&NavigateOptions>) -> Result<Document> {
        let res = self
            .http
            .post(
                &format!("{}/scrape", self.base()),
                &options.unwrap_or(&NavigateOptions::default()),
            )
            .await?;
        Ok(serde_json::from_value(
            res.get("data").cloned().unwrap_or(serde_json::Value::Null),
        )?)
    }

    /// Run a raw browser action. `params` must already be snake_case;
    /// returns the raw API payload.
    pub async fn action(
        &self,
        action_type: &str,
        params: serde_json::Value,
    ) -> Result<serde_json::Value> {
        let mut body = serde_json::json!({ "type": action_type });
        if let (Some(a), Some(b)) = (body.as_object_mut(), params.as_object()) {
            for (k, v) in b {
                a.insert(k.clone(), v.clone());
            }
        }
        self.http.post(&format!("{}/action", self.base()), &body).await
    }

    /// Click an element. `wait_after`: extra ms to wait afterwards.
    pub async fn click(&self, selector: &str, wait_after: Option<u32>) -> Result<serde_json::Value> {
        let mut params = serde_json::json!({ "selector": selector });
        if let Some(ms) = wait_after {
            params["wait_after"] = ms.into();
        }
        self.action("click", params).await
    }

    /// Type into an input.
    pub async fn fill(&self, selector: &str, value: &str) -> Result<serde_json::Value> {
        self.action("fill", serde_json::json!({ "selector": selector, "value": value }))
            .await
    }

    /// Pick a dropdown option by value or label.
    pub async fn select(&self, selector: &str, value: &str) -> Result<serde_json::Value> {
        self.action("select", serde_json::json!({ "selector": selector, "value": value }))
            .await
    }

    /// Send a keyboard key (e.g. "Enter"), optionally focusing a selector first.
    pub async fn press(
        &self,
        key: &str,
        selector: Option<&str>,
        wait_after: Option<u32>,
    ) -> Result<serde_json::Value> {
        let mut params = serde_json::json!({ "key": key });
        if let Some(s) = selector {
            params["selector"] = s.into();
        }
        if let Some(ms) = wait_after {
            params["wait_after"] = ms.into();
        }
        self.action("press", params).await
    }

    /// Scroll the page. direction: "down" (default) or "up"; amount in px.
    pub async fn scroll(&self, direction: Option<&str>, amount: Option<u32>) -> Result<serde_json::Value> {
        self.action(
            "scroll",
            serde_json::json!({
                "direction": direction.unwrap_or("down"),
                "amount": amount,
            }),
        )
        .await
    }

    /// Wait for the given milliseconds (max 30000).
    pub async fn wait(&self, ms: u32) -> Result<serde_json::Value> {
        self.action("wait", serde_json::json!({ "ms": ms })).await
    }

    /// Wait until a selector appears. timeout in ms (default 10000).
    pub async fn wait_for(&self, selector: &str, timeout: Option<u32>) -> Result<serde_json::Value> {
        let mut params = serde_json::json!({ "selector": selector });
        if let Some(t) = timeout {
            params["timeout"] = t.into();
        }
        self.action("wait_for", params).await
    }

    /// Take a screenshot. The API returns a base64 PNG inside the payload.
    pub async fn screenshot(&self, full_page: bool) -> Result<serde_json::Value> {
        self.action("screenshot", serde_json::json!({ "full_page": full_page }))
            .await
    }

    /// Evaluate a JavaScript expression in the page (value in payload's "result").
    pub async fn evaluate(&self, expression: &str) -> Result<serde_json::Value> {
        self.action("evaluate", serde_json::json!({ "expression": expression }))
            .await
    }

    /// Hover over an element.
    pub async fn hover(&self, selector: &str) -> Result<serde_json::Value> {
        self.action("hover", serde_json::json!({ "selector": selector }))
            .await
    }

    /// Tick a checkbox.
    pub async fn check(&self, selector: &str) -> Result<serde_json::Value> {
        self.action("check", serde_json::json!({ "selector": selector }))
            .await
    }

    /// Read the browser's current cookies.
    pub async fn get_cookies(&self) -> Result<Vec<Cookie>> {
        let res = self.http.get(&format!("{}/cookies", self.base())).await?;
        let raw = res
            .get("cookies")
            .or_else(|| res.get("data"))
            .cloned()
            .unwrap_or(serde_json::Value::Null);
        Ok(serde_json::from_value(raw)?)
    }

    /// Inject cookies into the browser context.
    pub async fn set_cookies(&self, cookies: &[Cookie]) -> Result<serde_json::Value> {
        self.http
            .post(
                &format!("{}/cookies", self.base()),
                &serde_json::json!({ "op": "set", "cookies": cookies }),
            )
            .await
    }

    /// Remove all cookies from the browser context.
    pub async fn clear_cookies(&self) -> Result<serde_json::Value> {
        self.http
            .post(&format!("{}/cookies", self.base()), &serde_json::json!({ "op": "clear" }))
            .await
    }

    /// Persist cookies/storage under this session's `session_name`.
    pub async fn save_session(&self) -> Result<serde_json::Value> {
        self.http
            .post(&format!("{}/save_session", self.base()), &serde_json::json!({}))
            .await
    }
}
