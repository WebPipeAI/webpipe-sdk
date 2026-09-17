//! Thin reqwest wrapper: auth headers, retries with backoff, error mapping.
//! Private module.

use std::time::Duration;

use reqwest::header::{HeaderMap, HeaderValue, AUTHORIZATION, CONTENT_TYPE, USER_AGENT};
use reqwest::Method;
use serde::Serialize;

use crate::error::{map_status, Result, WebpipeError};

const RETRYABLE_STATUSES: [u16; 5] = [429, 500, 502, 503, 504];

pub(crate) struct HttpClient {
    client: reqwest::Client,
    base_url: String,
    max_retries: u32,
}

impl HttpClient {
    pub fn new(
        base_url: &str,
        api_key: &str,
        timeout: Duration,
        max_retries: u32,
        user_agent: &str,
    ) -> Result<Self> {
        let mut headers = HeaderMap::new();
        let mut auth = HeaderValue::from_str(&format!("Bearer {api_key}"))
            .map_err(|e| WebpipeError::Config(format!("invalid API key header: {e}")))?;
        auth.set_sensitive(true);
        headers.insert(AUTHORIZATION, auth);
        headers.insert(USER_AGENT, HeaderValue::from_str(user_agent).expect("valid user agent"));
        headers.insert(CONTENT_TYPE, HeaderValue::from_static("application/json"));

        let client = reqwest::Client::builder()
            .default_headers(headers)
            .timeout(timeout)
            .build()?;
        Ok(Self {
            client,
            base_url: base_url.trim_end_matches('/').to_string(),
            max_retries,
        })
    }

    pub async fn get(&self, path: &str) -> Result<serde_json::Value> {
        self.request::<()>(Method::GET, path, None).await
    }

    pub async fn post<T: Serialize + ?Sized>(&self, path: &str, body: &T) -> Result<serde_json::Value> {
        self.request(Method::POST, path, Some(body)).await
    }

    pub async fn delete(&self, path: &str) -> Result<serde_json::Value> {
        self.request::<()>(Method::DELETE, path, None).await
    }

    async fn request<T: Serialize + ?Sized>(
        &self,
        method: Method,
        path: &str,
        body: Option<&T>,
    ) -> Result<serde_json::Value> {
        let url = format!("{}{path}", self.base_url);
        let mut attempt = 0u32;
        loop {
            let mut req = self.client.request(method.clone(), &url);
            if let Some(b) = body {
                req = req.json(b);
            }
            let res = match req.send().await {
                Ok(r) => r,
                Err(e) => {
                    let retryable = e.is_connect() || e.is_timeout() || e.is_request();
                    if retryable && attempt < self.max_retries {
                        attempt += 1;
                        sleep(backoff(None, attempt)).await;
                        continue;
                    }
                    return Err(e.into());
                }
            };

            let status = res.status().as_u16();
            if RETRYABLE_STATUSES.contains(&status) && attempt < self.max_retries {
                attempt += 1;
                let retry_after = res
                    .headers()
                    .get("Retry-After")
                    .and_then(|v| v.to_str().ok())
                    .and_then(|v| v.parse::<f64>().ok());
                sleep(backoff(retry_after, attempt)).await;
                continue;
            }

            let text = res.text().await.unwrap_or_default();
            let payload: serde_json::Value =
                serde_json::from_str(&text).unwrap_or(serde_json::Value::Null);
            if status >= 400 {
                let message = payload
                    .get("error")
                    .and_then(|e| e.as_str())
                    .map(str::to_owned)
                    .unwrap_or(text);
                return Err(map_status(status, message));
            }
            return Ok(payload);
        }
    }
}

fn backoff(retry_after: Option<f64>, attempt: u32) -> Duration {
    match retry_after {
        Some(seconds) if seconds >= 0.0 => Duration::from_secs_f64(seconds),
        _ => Duration::from_secs_f64(0.5 * 2f64.powi(attempt as i32)),
    }
}

async fn sleep(d: Duration) {
    tokio::time::sleep(d).await;
}
