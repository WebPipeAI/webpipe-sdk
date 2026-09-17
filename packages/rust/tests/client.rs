use std::time::Duration;

use serde_json::json;
use webpipe_sdk::{Client, MapOptions, ScrapeOptions, WebpipeError};
use wiremock::matchers::{method, path};
use wiremock::{Mock, MockServer, ResponseTemplate};

fn client(server: &MockServer, max_retries: u32) -> Client {
    Client::builder("wc-test-key")
        .base_url(server.uri())
        .max_retries(max_retries)
        .timeout(Duration::from_secs(5))
        .build()
        .unwrap()
}

#[tokio::test]
async fn missing_api_key() {
    let err = Client::new("").err().unwrap();
    assert!(matches!(err, WebpipeError::Config(_)));
}

#[tokio::test]
async fn scrape_success() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/v1/scrape"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "success": true,
            "data": {
                "markdown": "# Example",
                "metadata": {"title": "Example Domain", "sourceURL": "https://example.com", "statusCode": 200}
            }
        })))
        .expect(1)
        .mount(&server)
        .await;

    let doc = client(&server, 0)
        .scrape(
            "https://example.com",
            Some(&ScrapeOptions {
                formats: Some(vec!["markdown".into(), "metadata".into()]),
                ..Default::default()
            }),
        )
        .await
        .unwrap();

    assert_eq!(doc.markdown.as_deref(), Some("# Example"));
    assert_eq!(
        doc.metadata.unwrap()["sourceURL"].as_str().unwrap(),
        "https://example.com"
    );
}

#[tokio::test]
async fn scrape_401_maps_to_authentication() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/v1/scrape"))
        .respond_with(ResponseTemplate::new(401).set_body_json(json!({
            "success": false, "error": "invalid key"
        })))
        .mount(&server)
        .await;

    let err = client(&server, 0).scrape("https://example.com", None).await.err().unwrap();
    match err {
        WebpipeError::Authentication(msg) => assert!(msg.contains("invalid key")),
        other => panic!("expected Authentication, got {other:?}"),
    }
}

#[tokio::test]
async fn retries_on_429_then_succeeds() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/v1/scrape"))
        .respond_with(ResponseTemplate::new(429).set_body_json(json!({"error": "slow down"})))
        .up_to_n_times(1)
        .expect(1)
        .mount(&server)
        .await;
    Mock::given(method("POST"))
        .and(path("/api/v1/scrape"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "success": true, "data": {"markdown": "ok"}
        })))
        .expect(1)
        .mount(&server)
        .await;

    let doc = client(&server, 1).scrape("https://example.com", None).await.unwrap();
    assert_eq!(doc.markdown.as_deref(), Some("ok"));
}

#[tokio::test]
async fn map_returns_links() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/v1/map"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "success": true,
            "links": [
                {"url": "https://example.com/about", "title": "About"},
                {"url": "https://example.com/blog"}
            ]
        })))
        .mount(&server)
        .await;

    let links = client(&server, 0)
        .map(
            "https://example.com",
            Some(&MapOptions {
                limit: Some(100),
                same_domain: Some(false),
                ..Default::default()
            }),
        )
        .await
        .unwrap();
    assert_eq!(links.len(), 2);
    assert_eq!(links[0].url, "https://example.com/about");
}

#[tokio::test]
async fn crawl_polls_until_completed() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/v1/crawl"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({"success": true, "id": "job-1"})))
        .expect(1)
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .and(path("/api/v1/crawl/job-1"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "success": true, "status": "scraping", "total": 2, "completed": 1
        })))
        .up_to_n_times(1)
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .and(path("/api/v1/crawl/job-1"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "success": true, "status": "completed", "total": 2, "completed": 2,
            "data": [{"markdown": "# A"}, {"markdown": "# B"}],
            "errors": [], "expires_at": "2026-07-28T00:00:00+00:00"
        })))
        .mount(&server)
        .await;

    let job = client(&server, 0)
        .crawl("https://x", None, Duration::from_millis(10), Some(Duration::from_secs(10)))
        .await
        .unwrap();
    assert_eq!(job.status, "completed");
    assert_eq!(job.data.len(), 2);
    assert_eq!(job.expires_at.as_deref(), Some("2026-07-28T00:00:00+00:00"));
}

#[tokio::test]
async fn crawl_failed() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/v1/crawl"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({"success": true, "id": "job-2"})))
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .and(path("/api/v1/crawl/job-2"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "success": true, "status": "failed", "errors": ["boom"]
        })))
        .mount(&server)
        .await;

    let err = client(&server, 0)
        .crawl("https://x", None, Duration::from_millis(10), Some(Duration::from_secs(5)))
        .await
        .err()
        .unwrap();
    assert!(matches!(err, WebpipeError::Crawl(_)));
}

#[tokio::test]
async fn crawl_status_404() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/api/v1/crawl/gone"))
        .respond_with(ResponseTemplate::new(404).set_body_json(json!({"success": false, "error": "gone"})))
        .mount(&server)
        .await;

    let err = client(&server, 0).get_crawl_status("gone").await.err().unwrap();
    assert!(matches!(err, WebpipeError::NotFound(_)));
}

#[tokio::test]
async fn browser_lifecycle() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/v1/browser"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "success": true, "id": "sess-1", "status": "starting"
        })))
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .and(path("/api/v1/browser/sess-1"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "success": true, "id": "sess-1", "status": "starting"
        })))
        .up_to_n_times(1)
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .and(path("/api/v1/browser/sess-1"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "success": true, "id": "sess-1", "status": "running"
        })))
        .mount(&server)
        .await;
    Mock::given(method("POST"))
        .and(path("/api/v1/browser/sess-1/scrape"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "success": true, "data": {"markdown": "# Hi"}
        })))
        .mount(&server)
        .await;
    Mock::given(method("POST"))
        .and(path("/api/v1/browser/sess-1/action"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({"success": true, "ok": true})))
        .mount(&server)
        .await;
    Mock::given(method("DELETE"))
        .and(path("/api/v1/browser/sess-1"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({"success": true})))
        .mount(&server)
        .await;

    let c = client(&server, 0);
    let mut session = c.browser().create(None).await.unwrap();
    assert_eq!(session.status(), "starting");

    session
        .wait_until_running(Duration::from_millis(10), Duration::from_secs(5))
        .await
        .unwrap();
    assert_eq!(session.status(), "running");

    let doc = session.scrape(None).await.unwrap();
    assert_eq!(doc.markdown.as_deref(), Some("# Hi"));

    session.click("button.more", Some(500)).await.unwrap();
    session.close().await.unwrap();
    assert_eq!(session.status(), "stopped");
}
