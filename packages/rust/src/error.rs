//! Error types for the WebPipe SDK.

/// Errors returned by this SDK. API errors always carry the server's original
/// `error` message and the HTTP status code.
#[derive(Debug, thiserror::Error)]
pub enum WebpipeError {
    /// 400 — invalid parameters (missing url, bad format, ...).
    #[error("bad request (400): {0}")]
    BadRequest(String),
    /// 401 — API key missing, invalid or deleted.
    #[error("authentication failed (401): {0}")]
    Authentication(String),
    /// 403 — accessing a resource owned by another user.
    #[error("forbidden (403): {0}")]
    Forbidden(String),
    /// 404 — job/session does not exist or has expired.
    #[error("not found (404): {0}")]
    NotFound(String),
    /// 429 — rate limit exceeded, or too many concurrent browser sessions.
    #[error("rate limited (429): {0}")]
    RateLimit(String),
    /// 5xx — scrape timeout, target site unreachable, ...
    #[error("server error ({status}): {message}")]
    Server {
        /// HTTP status code.
        status: u16,
        /// Server's error message.
        message: String,
    },
    /// Any other non-2xx API response.
    #[error("api error ({status}): {message}")]
    Api {
        /// HTTP status code.
        status: u16,
        /// Server's error message.
        message: String,
    },
    /// A crawl job reached status `failed`.
    #[error("crawl job failed: {0}")]
    Crawl(String),
    /// Polling did not finish within the allotted timeout.
    #[error("polling timed out: {0}")]
    PollTimeout(String),
    /// Invalid client configuration (e.g. malformed API key).
    #[error("config error: {0}")]
    Config(String),
    /// The response body could not be decoded.
    #[error("failed to decode response: {0}")]
    Decode(String),
    /// Transport-level HTTP error.
    #[error("http error: {0}")]
    Http(#[from] reqwest::Error),
}

impl From<serde_json::Error> for WebpipeError {
    fn from(e: serde_json::Error) -> Self {
        WebpipeError::Decode(e.to_string())
    }
}

/// Convenience result alias.
pub type Result<T> = std::result::Result<T, WebpipeError>;

pub(crate) fn map_status(status: u16, message: String) -> WebpipeError {
    match status {
        400 => WebpipeError::BadRequest(message),
        401 => WebpipeError::Authentication(message),
        403 => WebpipeError::Forbidden(message),
        404 => WebpipeError::NotFound(message),
        429 => WebpipeError::RateLimit(message),
        s if s >= 500 => WebpipeError::Server {
            status: s,
            message,
        },
        s => WebpipeError::Api {
            status: s,
            message,
        },
    }
}
