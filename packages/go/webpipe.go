// Package webpipe is the official Go SDK for WebPipe.ai — turn any webpage
// into clean, structured data (Scrape / Map / Crawl / Browser Session).
//
//	client, _ := webpipe.NewClient() // reads WEBPIPE_API_KEY
//	doc, _ := client.Scrape(ctx, "https://example.com", &webpipe.ScrapeOptions{
//		Formats: []string{"markdown"},
//	})
//	fmt.Println(doc.Markdown)
package webpipe

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"
)

// Version is the SDK version, sent in the User-Agent header.
const Version = "0.1.0"

// DefaultBaseURL is the production API endpoint.
const DefaultBaseURL = "https://api.web2json.ai"

const (
	envAPIKey = "WEBPIPE_API_KEY"
	envAPIURL = "WEBPIPE_API_URL"
)

// Client is the WebPipe API client. Create it with NewClient.
type Client struct {
	http    *httpClient
	Browser *BrowserResource
}

// ClientOption configures the client (functional options pattern).
type ClientOption func(*clientConfig)

type clientConfig struct {
	apiKey     string
	baseURL    string
	timeout    time.Duration
	maxRetries int
	httpClient *http.Client
}

// WithAPIKey sets the API key (wc-...). Defaults to WEBPIPE_API_KEY.
func WithAPIKey(key string) ClientOption {
	return func(c *clientConfig) { c.apiKey = key }
}

// WithBaseURL overrides the API base URL (self-hosted). Defaults to
// WEBPIPE_API_URL, then https://api.web2json.ai.
func WithBaseURL(url string) ClientOption {
	return func(c *clientConfig) { c.baseURL = url }
}

// WithTimeout sets the per-request timeout (default 60s; browser commands
// can be slow).
func WithTimeout(d time.Duration) ClientOption {
	return func(c *clientConfig) { c.timeout = d }
}

// WithMaxRetries sets retries for 429/5xx with exponential backoff (default 2).
func WithMaxRetries(n int) ClientOption {
	return func(c *clientConfig) { c.maxRetries = n }
}

// WithHTTPClient supplies a custom *http.Client (escape hatch for proxies,
// custom transports, ...). WithTimeout is ignored when set.
func WithHTTPClient(hc *http.Client) ClientOption {
	return func(c *clientConfig) { c.httpClient = hc }
}

// NewClient creates a WebPipe client. It returns an error if no API key is
// configured (option or WEBPIPE_API_KEY env var).
func NewClient(opts ...ClientOption) (*Client, error) {
	cfg := &clientConfig{
		apiKey:     os.Getenv(envAPIKey),
		baseURL:    DefaultBaseURL,
		timeout:    60 * time.Second,
		maxRetries: 2,
	}
	if v := os.Getenv(envAPIURL); v != "" {
		cfg.baseURL = v
	}
	for _, opt := range opts {
		opt(cfg)
	}
	if cfg.apiKey == "" {
		return nil, fmt.Errorf(
			"webpipe: missing API key — use WithAPIKey or set the %s environment "+
				"variable. Create one at https://webpipe.ai/api-keys.html", envAPIKey)
	}
	if cfg.httpClient == nil {
		cfg.httpClient = &http.Client{Timeout: cfg.timeout}
	}
	hc := &httpClient{
		baseURL:    strings.TrimRight(cfg.baseURL, "/"),
		apiKey:     cfg.apiKey,
		maxRetries: cfg.maxRetries,
		userAgent:  "webpipe-sdk-go/" + Version,
		client:     cfg.httpClient,
	}
	return &Client{http: hc, Browser: &BrowserResource{http: hc}}, nil
}

type scrapeRequest struct {
	URL string `json:"url"`
	ScrapeOptions
}

// Scrape scrapes a single page into markdown/html/links/metadata/json.
func (c *Client) Scrape(ctx context.Context, url string, opts *ScrapeOptions) (*Document, error) {
	body := scrapeRequest{URL: url}
	if opts != nil {
		body.ScrapeOptions = *opts
	}
	var res struct {
		Data *Document `json:"data"`
	}
	if err := c.http.do(ctx, http.MethodPost, "/api/v1/scrape", body, &res); err != nil {
		return nil, err
	}
	if res.Data == nil {
		res.Data = &Document{}
	}
	return res.Data, nil
}

type mapRequest struct {
	URL string `json:"url"`
	MapOptions
}

// Map discovers URLs of a website (robots.txt → sitemap → in-page links).
func (c *Client) Map(ctx context.Context, url string, opts *MapOptions) ([]MapLink, error) {
	body := mapRequest{URL: url}
	if opts != nil {
		body.MapOptions = *opts
	}
	var res struct {
		Links []MapLink `json:"links"`
	}
	if err := c.http.do(ctx, http.MethodPost, "/api/v1/map", body, &res); err != nil {
		return nil, err
	}
	return res.Links, nil
}

type crawlRequest struct {
	URL string `json:"url"`
	CrawlOptions
}

// StartCrawl submits an async crawl job and returns its handle immediately.
func (c *Client) StartCrawl(ctx context.Context, url string, opts *CrawlOptions) (*CrawlJobStart, error) {
	body := crawlRequest{URL: url}
	if opts != nil {
		body.CrawlOptions = *opts
	}
	var res CrawlJobStart
	if err := c.http.do(ctx, http.MethodPost, "/api/v1/crawl", body, &res); err != nil {
		return nil, err
	}
	return &res, nil
}

// GetCrawlStatus fetches the current status and partial results of a crawl job.
func (c *Client) GetCrawlStatus(ctx context.Context, jobID string) (*CrawlJob, error) {
	var res CrawlJob
	if err := c.http.do(ctx, http.MethodGet, "/api/v1/crawl/"+jobID, nil, &res); err != nil {
		return nil, err
	}
	return &res, nil
}

// Crawl submits a crawl job and polls until it completes.
//
// It returns *CrawlError if the job fails and *PollTimeoutError if
// wait.Timeout is exceeded. Passing a nil wait uses sane defaults
// (2s poll interval, no timeout).
func (c *Client) Crawl(ctx context.Context, url string, opts *CrawlOptions, wait *CrawlWaitOptions) (*CrawlJob, error) {
	w := CrawlWaitOptions{PollInterval: 2}
	if wait != nil {
		w = *wait
	}
	if w.PollInterval <= 0 {
		w.PollInterval = 2
	}
	job, err := c.StartCrawl(ctx, url, opts)
	if err != nil {
		return nil, err
	}
	var deadline time.Time
	if w.Timeout > 0 {
		deadline = time.Now().Add(time.Duration(w.Timeout * float64(time.Second)))
	}
	for {
		status, err := c.GetCrawlStatus(ctx, job.ID)
		if err != nil {
			return nil, err
		}
		switch status.Status {
		case CrawlStatusCompleted:
			return status, nil
		case CrawlStatusFailed:
			return nil, &CrawlError{JobID: job.ID, Errors: status.Errors}
		}
		if !deadline.IsZero() && time.Now().After(deadline) {
			return nil, &PollTimeoutError{What: "crawl job " + job.ID, Timeout: w.Timeout}
		}
		if !sleepCtx(ctx, time.Duration(w.PollInterval*float64(time.Second))) {
			return nil, ctx.Err()
		}
	}
}
