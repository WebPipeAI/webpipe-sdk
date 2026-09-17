package webpipe

// Field tags map 1:1 to the API's wire format (snake_case requests; the
// response mixes snake_case with camelCase outliers like sourceURL/rawHtml).
// Request bodies are marshaled from these structs directly — never
// transform keys recursively, json_schema holds user-defined field names.

// ScrapeOptions are the optional parameters of Scrape.
type ScrapeOptions struct {
	// Output formats: "markdown" (default), "html", "rawHtml", "links", "metadata", "json".
	Formats []string `json:"formats,omitempty"`
	// JSON Schema for structured extraction (requires "json" in Formats).
	JSONSchema map[string]any `json:"json_schema,omitempty"`
	// Use the server-side proxy pool.
	UseProxy *bool `json:"use_proxy,omitempty"`
	// Custom proxy URL, takes precedence over the proxy pool.
	Proxy string `json:"proxy,omitempty"`
	// Cookie header string, e.g. "session=abc; user=123".
	Cookie string `json:"cookie,omitempty"`
}

// Link is a link extracted from a page.
type Link struct {
	Text string `json:"text,omitempty"`
	Href string `json:"href,omitempty"`
}

// Metadata is page metadata. The API may add more fields (og:*, ...).
type Metadata struct {
	Title       string `json:"title,omitempty"`
	Description string `json:"description,omitempty"`
	SourceURL   string `json:"sourceURL,omitempty"`
	StatusCode  int    `json:"statusCode,omitempty"`
}

// Document is the result of scraping a single page
// (scrape / browser / crawl data item).
type Document struct {
	Markdown string    `json:"markdown,omitempty"`
	HTML     string    `json:"html,omitempty"`
	RawHTML  string    `json:"rawHtml,omitempty"`
	Links    []Link    `json:"links,omitempty"`
	Metadata *Metadata `json:"metadata,omitempty"`
	JSON     any       `json:"json,omitempty"`
}

// MapOptions are the optional parameters of Map.
type MapOptions struct {
	// Max URLs to return (default 5000, max 50000).
	Limit int `json:"limit,omitempty"`
	// Keyword filter over url/title/description, ranked by relevance.
	Search string `json:"search,omitempty"`
	// "include" (default) | "only" (sitemap only) | "exclude" (links only).
	Sitemap string `json:"sitemap,omitempty"`
	// Restrict to the same root domain (server default true). Pointer so
	// that an explicit false is distinguishable from unset.
	SameDomain *bool  `json:"same_domain,omitempty"`
	UseProxy   *bool  `json:"use_proxy,omitempty"`
	Proxy      string `json:"proxy,omitempty"`
	Cookie     string `json:"cookie,omitempty"`
}

// MapLink is a URL discovered by the Map endpoint.
type MapLink struct {
	URL         string `json:"url"`
	Title       string `json:"title,omitempty"`
	Description string `json:"description,omitempty"`
}

// CrawlOptions are the optional parameters of StartCrawl / Crawl.
type CrawlOptions struct {
	// Max pages to crawl (default 100, max 10000).
	Limit int `json:"limit,omitempty"`
	// Max link-discovery depth. 0 = start page only.
	MaxDiscoveryDepth int `json:"max_discovery_depth,omitempty"`
	// Regex whitelist for URL paths.
	IncludePaths []string `json:"include_paths,omitempty"`
	// Regex blacklist for URL paths.
	ExcludePaths       []string `json:"exclude_paths,omitempty"`
	AllowSubdomains    bool     `json:"allow_subdomains,omitempty"`
	AllowExternalLinks bool     `json:"allow_external_links,omitempty"`
	// Crawl the whole domain, not just the start URL's subtree.
	CrawlEntireDomain bool `json:"crawl_entire_domain,omitempty"`
	// Deduplicate URLs ignoring query params.
	IgnoreQueryParams bool `json:"ignore_query_params,omitempty"`
	// "include" (default) | "skip" | "only".
	Sitemap string `json:"sitemap,omitempty"`
	// Seconds between page requests.
	Delay float64 `json:"delay,omitempty"`
	// Concurrent requests, 1-10 (default 3).
	MaxConcurrency int `json:"max_concurrency,omitempty"`
	// Per-page scrape options.
	ScrapeOptions *ScrapeOptions `json:"scrape_options,omitempty"`
	UseProxy      *bool          `json:"use_proxy,omitempty"`
	Proxy         string         `json:"proxy,omitempty"`
	Cookie        string         `json:"cookie,omitempty"`
}

// CrawlJobStart is the handle returned when a crawl job is submitted.
type CrawlJobStart struct {
	ID  string `json:"id"`
	URL string `json:"url,omitempty"`
}

// Crawl job statuses.
const (
	CrawlStatusScraping  = "scraping"
	CrawlStatusCompleted = "completed"
	CrawlStatusFailed    = "failed"
)

// CrawlJob is the status and (partial or final) result of a crawl job.
// Data expires 24h after completion (see ExpiresAt).
type CrawlJob struct {
	Status    string     `json:"status"`
	Total     int        `json:"total"`
	Completed int        `json:"completed"`
	Data      []Document `json:"data"`
	Errors    []any      `json:"errors"`
	ExpiresAt string     `json:"expires_at,omitempty"`
}

// CrawlWaitOptions control how Crawl polls for completion.
type CrawlWaitOptions struct {
	// Seconds between status polls (default 2).
	PollInterval float64
	// Max seconds to wait before returning *PollTimeoutError (0 = no limit).
	Timeout float64
}

// CreateSessionOptions are the optional parameters of Browser.Create.
type CreateSessionOptions struct {
	// Navigate to this URL right after the browser starts.
	URL string `json:"url,omitempty"`
	// Initial cookie header string injected into the browser context.
	Cookie string `json:"cookie,omitempty"`
	Proxy  string `json:"proxy,omitempty"`
	// Named sessions share persisted login state (see SaveSession).
	SessionName string `json:"session_name,omitempty"`
}

// Browser session statuses.
const (
	SessionStatusStarting = "starting"
	SessionStatusRunning  = "running"
	SessionStatusStopped  = "stopped"
	SessionStatusError    = "error"
	SessionStatusExpired  = "expired"
)

// SessionInfo is the state of a browser session (GET /browser/{id}).
type SessionInfo struct {
	ID          string `json:"id"`
	Status      string `json:"status"`
	URL         string `json:"url,omitempty"`
	Title       string `json:"title,omitempty"`
	SessionName string `json:"session_name,omitempty"`
	ExpiresAt   string `json:"expires_at,omitempty"`
	LastCmdAt   string `json:"last_cmd_at,omitempty"`
}

// NavigateOptions are the optional parameters of BrowserSession.Navigate and
// BrowserSession.Scrape.
type NavigateOptions struct {
	Formats []string `json:"formats,omitempty"`
	// Scroll to the bottom to trigger lazy-loaded content.
	ScrollToBottom bool `json:"scroll_to_bottom,omitempty"`
	// Max scroll rounds (1-30, default 10).
	MaxScrolls int `json:"max_scrolls,omitempty"`
	// Wait after each scroll, ms (500-8000, default 2000).
	ScrollWait int `json:"scroll_wait,omitempty"`
	// Pixels per scroll (200-5000, default 1200).
	ScrollStep int `json:"scroll_step,omitempty"`
}

// BrowserAction is a raw browser action. Prefer the typed helpers on
// BrowserSession (Click, Fill, ...). Fields are sent verbatim.
type BrowserAction struct {
	Type       string `json:"type"`
	Selector   string `json:"selector,omitempty"`
	Value      string `json:"value,omitempty"`
	Key        string `json:"key,omitempty"`
	WaitAfter  int    `json:"wait_after,omitempty"`
	Direction  string `json:"direction,omitempty"`
	Amount     int    `json:"amount,omitempty"`
	Ms         int    `json:"ms,omitempty"`
	Timeout    int    `json:"timeout,omitempty"`
	FullPage   *bool  `json:"full_page,omitempty"`
	Expression string `json:"expression,omitempty"`
}

// Cookie is a browser cookie. Domain/Path are needed when injecting cookies.
type Cookie struct {
	Name   string `json:"name"`
	Value  string `json:"value"`
	Domain string `json:"domain,omitempty"`
	Path   string `json:"path,omitempty"`
}
