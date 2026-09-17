using System.Text.Json.Nodes;
using Webpipe.Sdk.Internal;
using Webpipe.Sdk.Models;
using Webpipe.Sdk.Options;

namespace Webpipe.Sdk;

/// <summary>
/// The WebPipe API client.
/// <code>
/// var client = new WebpipeClient(); // reads WEBPIPE_API_KEY
/// var doc = await client.ScrapeAsync("https://example.com",
///     new ScrapeOptions { Formats = ["markdown"] });
/// Console.WriteLine(doc.Markdown);
/// </code>
/// </summary>
public sealed class WebpipeClient
{
    /// <summary>SDK version, sent in the User-Agent header.</summary>
    public const string Version = "0.1.0";
    /// <summary>Production API endpoint.</summary>
    public const string DefaultBaseUrl = "https://api.web2json.ai";

    private readonly HttpClientWrapper _http;

    /// <summary>Browser session factory.</summary>
    public BrowserResource Browser { get; }

    /// <summary>
    /// Create a client.
    /// </summary>
    /// <param name="apiKey">API key (wc-...). Falls back to WEBPIPE_API_KEY.</param>
    /// <param name="baseUrl">Falls back to WEBPIPE_API_URL, then production.</param>
    /// <param name="timeout">Per-request timeout (default 60s; browser commands can be slow).</param>
    /// <param name="maxRetries">Retries for 429/5xx with exponential backoff (default 2).</param>
    /// <param name="httpClient">Optional custom HttpClient (escape hatch / tests).</param>
    public WebpipeClient(
        string? apiKey = null,
        string? baseUrl = null,
        TimeSpan? timeout = null,
        int maxRetries = 2,
        System.Net.Http.HttpClient? httpClient = null)
    {
        apiKey ??= Environment.GetEnvironmentVariable("WEBPIPE_API_KEY");
        if (string.IsNullOrEmpty(apiKey))
        {
            throw new ArgumentException(
                "Missing API key. Pass apiKey or set the WEBPIPE_API_KEY environment " +
                "variable. Create one at https://webpipe.ai/api-keys.html");
        }
        baseUrl ??= Environment.GetEnvironmentVariable("WEBPIPE_API_URL") ?? DefaultBaseUrl;
        _http = new HttpClientWrapper(
            baseUrl, apiKey, timeout ?? TimeSpan.FromSeconds(60), maxRetries,
            $"webpipe-sdk-dotnet/{Version}", httpClient);
        Browser = new BrowserResource(_http);
    }

    // ------------------------------------------------------------------ Scrape

    /// <summary>Scrape a single page into markdown/html/links/metadata/json.</summary>
    public async Task<Document> ScrapeAsync(
        string url, ScrapeOptions? options = null, CancellationToken ct = default)
    {
        var body = BodyWithUrl(url, options);
        var res = await _http.PostAsync("/api/v1/scrape", body, ct).ConfigureAwait(false);
        return Json.Deserialize<Document>(res?["data"]) ?? new Document(null, null, null, null, null, null);
    }

    // --------------------------------------------------------------------- Map

    /// <summary>Discover URLs of a website (robots.txt → sitemap → in-page links).</summary>
    public async Task<IReadOnlyList<MapLink>> MapAsync(
        string url, MapOptions? options = null, CancellationToken ct = default)
    {
        var body = BodyWithUrl(url, options);
        var res = await _http.PostAsync("/api/v1/map", body, ct).ConfigureAwait(false);
        return Json.Deserialize<List<MapLink>>(res?["links"]) ?? [];
    }

    // ------------------------------------------------------------------- Crawl

    /// <summary>Submit an async crawl job and return its handle immediately.</summary>
    public async Task<CrawlJobStart> StartCrawlAsync(
        string url, CrawlOptions? options = null, CancellationToken ct = default)
    {
        var body = BodyWithUrl(url, options);
        var res = await _http.PostAsync("/api/v1/crawl", body, ct).ConfigureAwait(false);
        return Json.Deserialize<CrawlJobStart>(res)
            ?? throw new WebpipeException("Invalid crawl start response");
    }

    /// <summary>Fetch the current status and partial results of a crawl job.</summary>
    public async Task<CrawlJob> GetCrawlStatusAsync(string jobId, CancellationToken ct = default)
    {
        var res = await _http.GetAsync($"/api/v1/crawl/{jobId}", ct).ConfigureAwait(false);
        return Json.Deserialize<CrawlJob>(res)
            ?? throw new WebpipeException("Invalid crawl status response");
    }

    /// <summary>
    /// Submit a crawl job and poll until it completes.
    /// </summary>
    /// <param name="pollInterval">Seconds between status polls (default 2).</param>
    /// <param name="timeout">Max seconds to wait (null = no limit).</param>
    /// <exception cref="CrawlException">The job failed.</exception>
    /// <exception cref="PollTimeoutException">The timeout was exceeded.</exception>
    public async Task<CrawlJob> CrawlAsync(
        string url,
        CrawlOptions? options = null,
        double pollInterval = 2,
        double? timeout = null,
        CancellationToken ct = default)
    {
        var job = await StartCrawlAsync(url, options, ct).ConfigureAwait(false);
        var deadline = timeout is null
            ? DateTimeOffset.MaxValue
            : DateTimeOffset.UtcNow.AddSeconds(timeout.Value);
        while (true)
        {
            var status = await GetCrawlStatusAsync(job.Id, ct).ConfigureAwait(false);
            if (status.Status == CrawlJob.StatusCompleted)
                return status;
            if (status.Status == CrawlJob.StatusFailed)
                throw new CrawlException(
                    $"Crawl job {job.Id} failed: {System.Text.Json.JsonSerializer.Serialize(status.Errors)}");
            if (DateTimeOffset.UtcNow >= deadline)
                throw new PollTimeoutException(
                    $"Crawl job {job.Id} did not complete within {timeout}s");
            await Task.Delay(TimeSpan.FromSeconds(pollInterval), ct).ConfigureAwait(false);
        }
    }

    internal static Dictionary<string, object?> BodyWithUrl(string url, object? options)
    {
        var body = new Dictionary<string, object?> { ["url"] = url };
        if (options is null)
            return body;
        // Flatten options into the body via their JsonPropertyName mappings.
        var node = System.Text.Json.JsonSerializer.SerializeToNode(options, options.GetType());
        if (node is JsonObject obj)
        {
            foreach (var (key, value) in obj)
                body[key] = value;
        }
        return body;
    }
}
