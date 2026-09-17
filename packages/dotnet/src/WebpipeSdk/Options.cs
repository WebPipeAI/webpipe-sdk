using System.Text.Json.Serialization;

namespace Webpipe.Sdk.Options;

// Options use camelCase C# properties mapped to snake_case wire names via
// JsonPropertyName — explicit, never recursive. jsonSchema dictionary keys
// pass through untouched. Null properties are omitted from the request body.

/// <summary>Optional parameters of <c>ScrapeAsync</c>.</summary>
public class ScrapeOptions
{
    /// <summary>Output formats: "markdown" (default), "html", "rawHtml", "links", "metadata", "json".</summary>
    [JsonPropertyName("formats"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<string>? Formats { get; set; }

    /// <summary>JSON Schema for structured extraction (requires "json" in Formats).</summary>
    [JsonPropertyName("json_schema"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Dictionary<string, object>? JsonSchema { get; set; }

    /// <summary>Use the server-side proxy pool.</summary>
    [JsonPropertyName("use_proxy"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? UseProxy { get; set; }

    /// <summary>Custom proxy URL, takes precedence over the proxy pool.</summary>
    [JsonPropertyName("proxy"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Proxy { get; set; }

    /// <summary>Cookie header string, e.g. "session=abc; user=123".</summary>
    [JsonPropertyName("cookie"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Cookie { get; set; }
}

/// <summary>Optional parameters of <c>MapAsync</c>.</summary>
public class MapOptions
{
    /// <summary>Max URLs to return (default 5000, max 50000).</summary>
    [JsonPropertyName("limit"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public int? Limit { get; set; }

    /// <summary>Keyword filter over url/title/description, ranked by relevance.</summary>
    [JsonPropertyName("search"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Search { get; set; }

    /// <summary>"include" (default) | "only" (sitemap only) | "exclude" (links only).</summary>
    [JsonPropertyName("sitemap"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Sitemap { get; set; }

    /// <summary>Restrict to the same root domain (server default true).</summary>
    [JsonPropertyName("same_domain"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? SameDomain { get; set; }

    [JsonPropertyName("use_proxy"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? UseProxy { get; set; }

    [JsonPropertyName("proxy"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Proxy { get; set; }

    [JsonPropertyName("cookie"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Cookie { get; set; }
}

/// <summary>Optional parameters of <c>StartCrawlAsync</c> / <c>CrawlAsync</c>.</summary>
public class CrawlOptions
{
    /// <summary>Max pages to crawl (default 100, max 10000).</summary>
    [JsonPropertyName("limit"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public int? Limit { get; set; }

    /// <summary>Max link-discovery depth. 0 = start page only.</summary>
    [JsonPropertyName("max_discovery_depth"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public int? MaxDiscoveryDepth { get; set; }

    /// <summary>Regex whitelist for URL paths.</summary>
    [JsonPropertyName("include_paths"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<string>? IncludePaths { get; set; }

    /// <summary>Regex blacklist for URL paths.</summary>
    [JsonPropertyName("exclude_paths"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<string>? ExcludePaths { get; set; }

    [JsonPropertyName("allow_subdomains"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? AllowSubdomains { get; set; }

    [JsonPropertyName("allow_external_links"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? AllowExternalLinks { get; set; }

    /// <summary>Crawl the whole domain, not just the start URL's subtree.</summary>
    [JsonPropertyName("crawl_entire_domain"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? CrawlEntireDomain { get; set; }

    /// <summary>Deduplicate URLs ignoring query params.</summary>
    [JsonPropertyName("ignore_query_params"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? IgnoreQueryParams { get; set; }

    /// <summary>"include" (default) | "skip" | "only".</summary>
    [JsonPropertyName("sitemap"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Sitemap { get; set; }

    /// <summary>Seconds between page requests.</summary>
    [JsonPropertyName("delay"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public double? Delay { get; set; }

    /// <summary>Concurrent requests, 1-10 (default 3).</summary>
    [JsonPropertyName("max_concurrency"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public int? MaxConcurrency { get; set; }

    /// <summary>Per-page scrape options.</summary>
    [JsonPropertyName("scrape_options"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public ScrapeOptions? ScrapeOptions { get; set; }

    [JsonPropertyName("use_proxy"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? UseProxy { get; set; }

    [JsonPropertyName("proxy"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Proxy { get; set; }

    [JsonPropertyName("cookie"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Cookie { get; set; }
}

/// <summary>Optional parameters of <c>BrowserResource.CreateAsync</c>.</summary>
public class CreateSessionOptions
{
    /// <summary>Navigate to this URL right after the browser starts.</summary>
    [JsonPropertyName("url"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Url { get; set; }

    /// <summary>Initial cookie header string injected into the browser context.</summary>
    [JsonPropertyName("cookie"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Cookie { get; set; }

    [JsonPropertyName("proxy"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Proxy { get; set; }

    /// <summary>Named sessions share persisted login state (see SaveSessionAsync).</summary>
    [JsonPropertyName("session_name"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? SessionName { get; set; }
}

/// <summary>Optional parameters of <c>BrowserSession.NavigateAsync</c> and
/// <c>BrowserSession.ScrapeAsync</c>.</summary>
public class NavigateOptions
{
    [JsonPropertyName("formats"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<string>? Formats { get; set; }

    /// <summary>Scroll to the bottom to trigger lazy-loaded content.</summary>
    [JsonPropertyName("scroll_to_bottom"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? ScrollToBottom { get; set; }

    /// <summary>Max scroll rounds (1-30, default 10).</summary>
    [JsonPropertyName("max_scrolls"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public int? MaxScrolls { get; set; }

    /// <summary>Wait after each scroll, ms (500-8000, default 2000).</summary>
    [JsonPropertyName("scroll_wait"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public int? ScrollWait { get; set; }

    /// <summary>Pixels per scroll (200-5000, default 1200).</summary>
    [JsonPropertyName("scroll_step"), JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public int? ScrollStep { get; set; }
}
