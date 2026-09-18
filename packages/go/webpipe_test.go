package webpipe_test

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"

	webpipe "github.com/WebPipeAI/webpipe-sdk/packages/go"
)

// newTestClient builds a client pointed at the test server.
func newTestClient(t *testing.T, handler http.Handler) (*webpipe.Client, *httptest.Server) {
	t.Helper()
	srv := httptest.NewServer(handler)
	t.Cleanup(srv.Close)
	client, err := webpipe.NewClient(
		webpipe.WithAPIKey("wc-test-key"),
		webpipe.WithBaseURL(srv.URL),
		webpipe.WithMaxRetries(0),
	)
	if err != nil {
		t.Fatalf("NewClient: %v", err)
	}
	return client, srv
}

func writeJSON(t *testing.T, w http.ResponseWriter, status int, body any) {
	t.Helper()
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(body); err != nil {
		t.Fatalf("encode: %v", err)
	}
}

func TestMissingAPIKey(t *testing.T) {
	t.Setenv("WEBPIPE_API_KEY", "")
	_, err := webpipe.NewClient()
	if err == nil || !strings.Contains(err.Error(), "WEBPIPE_API_KEY") {
		t.Fatalf("expected missing-key error, got %v", err)
	}
}

func TestScrapeSuccess(t *testing.T) {
	var gotAuth, gotUA string
	var gotBody map[string]any
	client, _ := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAuth = r.Header.Get("Authorization")
		gotUA = r.Header.Get("User-Agent")
		_ = json.NewDecoder(r.Body).Decode(&gotBody)
		writeJSON(t, w, 200, map[string]any{
			"success": true,
			"data": map[string]any{
				"markdown": "# Example",
				"metadata": map[string]any{
					"title":      "Example Domain",
					"sourceURL":  "https://example.com",
					"statusCode": 200,
				},
			},
		})
	}))

	doc, err := client.Scrape(context.Background(), "https://example.com", &webpipe.ScrapeOptions{
		Formats: []string{"markdown", "metadata"},
	})
	if err != nil {
		t.Fatalf("Scrape: %v", err)
	}
	if doc.Markdown != "# Example" {
		t.Errorf("markdown = %q", doc.Markdown)
	}
	if doc.Metadata == nil || doc.Metadata.SourceURL != "https://example.com" || doc.Metadata.StatusCode != 200 {
		t.Errorf("metadata = %+v", doc.Metadata)
	}
	if gotAuth != "Bearer wc-test-key" {
		t.Errorf("Authorization = %q", gotAuth)
	}
	if !strings.HasPrefix(gotUA, "webpipe-sdk-go/") {
		t.Errorf("User-Agent = %q", gotUA)
	}
	if fmt.Sprint(gotBody["formats"]) != "[markdown metadata]" {
		t.Errorf("body formats = %v", gotBody["formats"])
	}
	if _, leaked := gotBody["use_proxy"]; leaked {
		t.Errorf("unset option leaked into body: %v", gotBody)
	}
}

func TestScrape401MapsToSentinel(t *testing.T) {
	client, _ := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(t, w, 401, map[string]any{"success": false, "error": "invalid key"})
	}))
	_, err := client.Scrape(context.Background(), "https://example.com", nil)
	if !errors.Is(err, webpipe.ErrAuthentication) {
		t.Fatalf("errors.Is(err, ErrAuthentication) = false for %v", err)
	}
	var apiErr *webpipe.APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("err is not *APIError: %T", err)
	}
	if apiErr.StatusCode != 401 || apiErr.Message != "invalid key" {
		t.Errorf("APIError = %+v", apiErr)
	}
}

func TestRetryOn429(t *testing.T) {
	var calls atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if calls.Add(1) == 1 {
			writeJSON(t, w, 429, map[string]any{"success": false, "error": "slow down"})
			return
		}
		writeJSON(t, w, 200, map[string]any{"success": true, "data": map[string]any{"markdown": "ok"}})
	}))
	defer srv.Close()
	client, err := webpipe.NewClient(
		webpipe.WithAPIKey("wc-test-key"),
		webpipe.WithBaseURL(srv.URL),
		webpipe.WithMaxRetries(1),
	)
	if err != nil {
		t.Fatal(err)
	}
	doc, err := client.Scrape(context.Background(), "https://example.com", nil)
	if err != nil {
		t.Fatalf("Scrape: %v", err)
	}
	if doc.Markdown != "ok" || calls.Load() != 2 {
		t.Errorf("markdown=%q calls=%d", doc.Markdown, calls.Load())
	}
}

func TestMapReturnsLinks(t *testing.T) {
	var gotBody map[string]any
	client, _ := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewDecoder(r.Body).Decode(&gotBody)
		writeJSON(t, w, 200, map[string]any{
			"success": true,
			"links": []map[string]any{
				{"url": "https://example.com/about", "title": "About"},
				{"url": "https://example.com/blog"},
			},
		})
	}))
	sameDomain := false
	links, err := client.Map(context.Background(), "https://example.com", &webpipe.MapOptions{
		Limit:      100,
		SameDomain: &sameDomain,
	})
	if err != nil {
		t.Fatalf("Map: %v", err)
	}
	if len(links) != 2 || links[0].URL != "https://example.com/about" {
		t.Errorf("links = %+v", links)
	}
	if v, ok := gotBody["same_domain"].(bool); !ok || v {
		t.Errorf("same_domain should be explicit false, body = %v", gotBody)
	}
}

func TestCrawlPollsUntilCompleted(t *testing.T) {
	var statusCalls atomic.Int32
	mux := http.NewServeMux()
	mux.HandleFunc("POST /api/v1/crawl", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(t, w, 200, map[string]any{"success": true, "id": "job-1"})
	})
	mux.HandleFunc("GET /api/v1/crawl/job-1", func(w http.ResponseWriter, r *http.Request) {
		if statusCalls.Add(1) == 1 {
			writeJSON(t, w, 200, map[string]any{"success": true, "status": "scraping", "total": 2, "completed": 1})
			return
		}
		writeJSON(t, w, 200, map[string]any{
			"success": true, "status": "completed", "total": 2, "completed": 2,
			"data":   []map[string]any{{"markdown": "# A"}, {"markdown": "# B"}},
			"errors": []any{}, "expires_at": "2026-07-28T00:00:00+00:00",
		})
	})
	client, _ := newTestClient(t, mux)

	job, err := client.Crawl(context.Background(), "https://x",
		&webpipe.CrawlOptions{Limit: 2},
		&webpipe.CrawlWaitOptions{PollInterval: 0.01})
	if err != nil {
		t.Fatalf("Crawl: %v", err)
	}
	if job.Status != webpipe.CrawlStatusCompleted || len(job.Data) != 2 {
		t.Errorf("job = %+v", job)
	}
	if job.ExpiresAt != "2026-07-28T00:00:00+00:00" {
		t.Errorf("expiresAt = %q", job.ExpiresAt)
	}
}

func TestCrawlFailed(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /api/v1/crawl", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(t, w, 200, map[string]any{"success": true, "id": "job-2"})
	})
	mux.HandleFunc("GET /api/v1/crawl/job-2", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(t, w, 200, map[string]any{"success": true, "status": "failed", "errors": []any{"boom"}})
	})
	client, _ := newTestClient(t, mux)

	_, err := client.Crawl(context.Background(), "https://x", nil, &webpipe.CrawlWaitOptions{PollInterval: 0.01})
	var crawlErr *webpipe.CrawlError
	if !errors.As(err, &crawlErr) {
		t.Fatalf("expected *CrawlError, got %T: %v", err, err)
	}
}

func TestGetCrawlStatus404(t *testing.T) {
	client, _ := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(t, w, 404, map[string]any{"success": false, "error": "gone"})
	}))
	_, err := client.GetCrawlStatus(context.Background(), "gone")
	if !errors.Is(err, webpipe.ErrNotFound) {
		t.Fatalf("expected ErrNotFound, got %v", err)
	}
}

func TestBrowserLifecycle(t *testing.T) {
	var pollCount atomic.Int32
	var lastActionBody map[string]any
	mux := http.NewServeMux()
	mux.HandleFunc("POST /api/v1/browser", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(t, w, 200, map[string]any{"success": true, "id": "sess-1", "status": "starting"})
	})
	mux.HandleFunc("GET /api/v1/browser/sess-1", func(w http.ResponseWriter, r *http.Request) {
		status := "starting"
		if pollCount.Add(1) > 1 {
			status = "running"
		}
		writeJSON(t, w, 200, map[string]any{"success": true, "id": "sess-1", "status": status})
	})
	mux.HandleFunc("POST /api/v1/browser/sess-1/scrape", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(t, w, 200, map[string]any{"success": true, "data": map[string]any{"markdown": "# Hi"}})
	})
	mux.HandleFunc("POST /api/v1/browser/sess-1/action", func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewDecoder(r.Body).Decode(&lastActionBody)
		writeJSON(t, w, 200, map[string]any{"success": true, "ok": true})
	})
	mux.HandleFunc("DELETE /api/v1/browser/sess-1", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(t, w, 200, map[string]any{"success": true})
	})
	client, _ := newTestClient(t, mux)
	ctx := context.Background()

	session, err := client.Browser.Create(ctx, &webpipe.CreateSessionOptions{URL: "https://example.com"})
	if err != nil {
		t.Fatalf("Create: %v", err)
	}
	if session.Status() != "starting" {
		t.Errorf("status = %q", session.Status())
	}
	if err := session.WaitUntilRunning(ctx, 0.01, 5); err != nil {
		t.Fatalf("WaitUntilRunning: %v", err)
	}
	doc, err := session.Scrape(ctx, &webpipe.NavigateOptions{Formats: []string{"markdown"}})
	if err != nil || doc.Markdown != "# Hi" {
		t.Fatalf("Scrape: doc=%+v err=%v", doc, err)
	}
	if _, err := session.Click(ctx, "button.more", 500); err != nil {
		t.Fatalf("Click: %v", err)
	}
	if lastActionBody["type"] != "click" || lastActionBody["selector"] != "button.more" {
		t.Errorf("action body = %v", lastActionBody)
	}
	if n := int(lastActionBody["wait_after"].(float64)); n != 500 {
		t.Errorf("wait_after = %v", lastActionBody["wait_after"])
	}
	if err := session.Close(ctx); err != nil {
		t.Fatalf("Close: %v", err)
	}
	if session.Status() != "stopped" {
		t.Errorf("status after close = %q", session.Status())
	}
}
