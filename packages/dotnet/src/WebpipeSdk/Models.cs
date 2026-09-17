using System.Text.Json.Serialization;

namespace Webpipe.Sdk.Models;

/// <summary>A link extracted from a page.</summary>
public record Link(
    [property: JsonPropertyName("text")] string? Text,
    [property: JsonPropertyName("href")] string? Href);

/// <summary>
/// Result of scraping a single page (scrape / browser / crawl data item).
/// <paramref name="Metadata"/> is a raw dictionary — the API adds extra keys
/// (og:*, sourceURL, statusCode, ...).
/// </summary>
public record Document(
    [property: JsonPropertyName("markdown")] string? Markdown,
    [property: JsonPropertyName("html")] string? Html,
    [property: JsonPropertyName("rawHtml")] string? RawHtml,
    [property: JsonPropertyName("links")] IReadOnlyList<Link>? Links,
    [property: JsonPropertyName("metadata")] IReadOnlyDictionary<string, object>? Metadata,
    [property: JsonPropertyName("json")] object? Json);

/// <summary>A URL discovered by the Map endpoint.</summary>
public record MapLink(
    [property: JsonPropertyName("url")] string Url,
    [property: JsonPropertyName("title")] string? Title,
    [property: JsonPropertyName("description")] string? Description);

/// <summary>Handle returned when a crawl job is submitted.</summary>
public record CrawlJobStart(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("url")] string? Url);

/// <summary>
/// Status and (partial or final) results of a crawl job.
/// Results expire 24h after completion (see <paramref name="ExpiresAt"/>).
/// </summary>
public record CrawlJob(
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("total")] int Total,
    [property: JsonPropertyName("completed")] int Completed,
    [property: JsonPropertyName("data")] IReadOnlyList<Document> Data,
    [property: JsonPropertyName("errors")] IReadOnlyList<object> Errors,
    [property: JsonPropertyName("expires_at")] string? ExpiresAt)
{
    public const string StatusScraping = "scraping";
    public const string StatusCompleted = "completed";
    public const string StatusFailed = "failed";
}

/// <summary>State of a browser session (GET /browser/{id}).</summary>
public record SessionInfo(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("url")] string? Url,
    [property: JsonPropertyName("title")] string? Title,
    [property: JsonPropertyName("session_name")] string? SessionName,
    [property: JsonPropertyName("expires_at")] string? ExpiresAt)
{
    public const string StatusStarting = "starting";
    public const string StatusRunning = "running";
    public const string StatusStopped = "stopped";
    public const string StatusError = "error";
    public const string StatusExpired = "expired";
}

/// <summary>A browser cookie. Domain/Path are needed when injecting cookies.</summary>
public record Cookie(
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("value")] string Value,
    [property: JsonPropertyName("domain")] string? Domain = null,
    [property: JsonPropertyName("path")] string? Path = null);
