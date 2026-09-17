using System.Text.Json.Nodes;
using Webpipe.Sdk.Internal;
using Webpipe.Sdk.Models;
using Webpipe.Sdk.Options;

namespace Webpipe.Sdk;

/// <summary>
/// Handle to a persistent headless Chromium session on the server.
///
/// Server-side facts: sessions expire after 30 minutes idle; at most 2
/// concurrent running sessions per user. Always <see cref="CloseAsync"/> when done.
/// </summary>
public sealed class BrowserSession
{
    private readonly HttpClientWrapper _http;

    /// <summary>Session identifier.</summary>
    public string Id { get; }
    /// <summary>Last known session status (see <see cref="SessionInfo.StatusRunning"/> etc).</summary>
    public string Status { get; private set; }
    /// <summary>The page the browser is currently on.</summary>
    public string? Url { get; private set; }
    /// <summary>Named-session identifier (for persisted login state).</summary>
    public string? SessionName { get; private set; }
    /// <summary>When the session expires.</summary>
    public string? ExpiresAt { get; private set; }

    internal BrowserSession(HttpClientWrapper http, SessionInfo info)
    {
        _http = http;
        Id = info.Id;
        Status = info.Status;
        Url = info.Url;
        SessionName = info.SessionName;
        ExpiresAt = info.ExpiresAt;
    }

    private string Base() => $"/api/v1/browser/{Id}";

    private void Apply(SessionInfo info)
    {
        Status = info.Status;
        Url = info.Url;
        SessionName = info.SessionName;
        ExpiresAt = info.ExpiresAt;
    }

    /// <summary>Fetch the latest session state from the server.</summary>
    public async Task<BrowserSession> RefreshAsync(CancellationToken ct = default)
    {
        var res = await _http.GetAsync(Base(), ct).ConfigureAwait(false);
        Apply(Json.Deserialize<SessionInfo>(res)
            ?? throw new WebpipeException("Invalid session info response"));
        return this;
    }

    /// <summary>Block until the session is ready to accept commands.</summary>
    /// <param name="timeoutSeconds">Max seconds to wait (default 60).</param>
    /// <param name="pollIntervalSeconds">Seconds between polls (default 1).</param>
    public async Task<BrowserSession> WaitUntilRunningAsync(
        double timeoutSeconds = 60, double pollIntervalSeconds = 1, CancellationToken ct = default)
    {
        var deadline = DateTimeOffset.UtcNow.AddSeconds(timeoutSeconds);
        while (true)
        {
            await RefreshAsync(ct).ConfigureAwait(false);
            if (Status == SessionInfo.StatusRunning)
                return this;
            if (Status is SessionInfo.StatusStopped or SessionInfo.StatusError or SessionInfo.StatusExpired)
                throw new WebpipeException(
                    $"Browser session {Id} entered terminal status '{Status}'");
            if (DateTimeOffset.UtcNow >= deadline)
                throw new PollTimeoutException(
                    $"Browser session {Id} not running after {timeoutSeconds}s");
            await Task.Delay(TimeSpan.FromSeconds(pollIntervalSeconds), ct).ConfigureAwait(false);
        }
    }

    /// <summary>Stop the session and release server resources.</summary>
    public async Task CloseAsync(CancellationToken ct = default)
    {
        await _http.DeleteAsync(Base(), ct).ConfigureAwait(false);
        Status = SessionInfo.StatusStopped;
    }

    /// <summary>Navigate to a new URL (with anti-bot handling) and scrape it.</summary>
    public async Task<Document> NavigateAsync(
        string url, NavigateOptions? options = null, CancellationToken ct = default)
    {
        var body = ScrapeBody(options);
        body["url"] = url;
        var res = await _http.PostAsync(Base() + "/navigate", body, ct).ConfigureAwait(false);
        return Json.Deserialize<Document>(res?["data"]) ?? new Document(null, null, null, null, null, null);
    }

    /// <summary>Scrape the current page without navigating (faster than navigate).</summary>
    public async Task<Document> ScrapeAsync(NavigateOptions? options = null, CancellationToken ct = default)
    {
        var res = await _http.PostAsync(Base() + "/scrape", ScrapeBody(options), ct).ConfigureAwait(false);
        return Json.Deserialize<Document>(res?["data"]) ?? new Document(null, null, null, null, null, null);
    }

    /// <summary>Run a raw browser action. <paramref name="params"/> must be
    /// snake_case already; returns the raw API payload.</summary>
    public async Task<JsonNode?> ActionAsync(
        string type, Dictionary<string, object?>? @params = null, CancellationToken ct = default)
    {
        var body = new Dictionary<string, object?> { ["type"] = type };
        if (@params is not null)
        {
            foreach (var (key, value) in @params)
                body[key] = value;
        }
        return await _http.PostAsync(Base() + "/action", body, ct).ConfigureAwait(false);
    }

    /// <summary>Click an element. waitAfterMs: extra ms to wait afterwards.</summary>
    public Task<JsonNode?> ClickAsync(string selector, int? waitAfterMs = null, CancellationToken ct = default) =>
        ActionAsync("click", DropNull(new() { ["selector"] = selector, ["wait_after"] = waitAfterMs }), ct);

    /// <summary>Type into an input.</summary>
    public Task<JsonNode?> FillAsync(string selector, string value, CancellationToken ct = default) =>
        ActionAsync("fill", new() { ["selector"] = selector, ["value"] = value }, ct);

    /// <summary>Pick a dropdown option by value or label.</summary>
    public Task<JsonNode?> SelectAsync(string selector, string value, CancellationToken ct = default) =>
        ActionAsync("select", new() { ["selector"] = selector, ["value"] = value }, ct);

    /// <summary>Send a keyboard key (e.g. "Enter"), optionally focusing a selector first.</summary>
    public Task<JsonNode?> PressAsync(
        string key, string? selector = null, int? waitAfterMs = null, CancellationToken ct = default) =>
        ActionAsync("press", DropNull(new()
        {
            ["key"] = key, ["selector"] = selector, ["wait_after"] = waitAfterMs,
        }), ct);

    /// <summary>Scroll the page. direction: "down" (default) or "up"; amountPx in px.</summary>
    public Task<JsonNode?> ScrollAsync(string direction = "down", int? amountPx = null, CancellationToken ct = default) =>
        ActionAsync("scroll", DropNull(new() { ["direction"] = direction, ["amount"] = amountPx }), ct);

    /// <summary>Wait for the given milliseconds (max 30000).</summary>
    public Task<JsonNode?> WaitAsync(int ms = 1000, CancellationToken ct = default) =>
        ActionAsync("wait", new() { ["ms"] = ms }, ct);

    /// <summary>Wait until a selector appears. timeoutMs default 10000.</summary>
    public Task<JsonNode?> WaitForAsync(string selector, int? timeoutMs = null, CancellationToken ct = default) =>
        ActionAsync("wait_for", DropNull(new() { ["selector"] = selector, ["timeout"] = timeoutMs }), ct);

    /// <summary>Take a screenshot. The API returns a base64 PNG inside the payload.</summary>
    public Task<JsonNode?> ScreenshotAsync(bool fullPage = false, CancellationToken ct = default) =>
        ActionAsync("screenshot", new() { ["full_page"] = fullPage }, ct);

    /// <summary>Evaluate a JavaScript expression in the page (value in payload's "result").</summary>
    public Task<JsonNode?> EvaluateAsync(string expression, CancellationToken ct = default) =>
        ActionAsync("evaluate", new() { ["expression"] = expression }, ct);

    /// <summary>Hover over an element.</summary>
    public Task<JsonNode?> HoverAsync(string selector, CancellationToken ct = default) =>
        ActionAsync("hover", new() { ["selector"] = selector }, ct);

    /// <summary>Tick a checkbox.</summary>
    public Task<JsonNode?> CheckAsync(string selector, CancellationToken ct = default) =>
        ActionAsync("check", new() { ["selector"] = selector }, ct);

    /// <summary>Read the browser's current cookies.</summary>
    public async Task<IReadOnlyList<Cookie>> GetCookiesAsync(CancellationToken ct = default)
    {
        var res = await _http.GetAsync(Base() + "/cookies", ct).ConfigureAwait(false);
        var node = res?["cookies"] ?? res?["data"];
        return Json.Deserialize<List<Cookie>>(node) ?? [];
    }

    /// <summary>Inject cookies into the browser context.</summary>
    public Task<JsonNode?> SetCookiesAsync(IReadOnlyList<Cookie> cookies, CancellationToken ct = default) =>
        _http.PostAsync(Base() + "/cookies", new Dictionary<string, object?>
        {
            ["op"] = "set", ["cookies"] = cookies,
        }, ct);

    /// <summary>Remove all cookies from the browser context.</summary>
    public Task<JsonNode?> ClearCookiesAsync(CancellationToken ct = default) =>
        _http.PostAsync(Base() + "/cookies", new Dictionary<string, object?> { ["op"] = "clear" }, ct);

    /// <summary>Persist cookies/storage under this session's sessionName.</summary>
    public Task<JsonNode?> SaveSessionAsync(CancellationToken ct = default) =>
        _http.PostAsync(Base() + "/save_session", null, ct);

    private static Dictionary<string, object?> ScrapeBody(NavigateOptions? options)
    {
        var body = new Dictionary<string, object?>();
        if (options is null)
            return body;
        if (options.Formats is not null) body["formats"] = options.Formats;
        if (options.ScrollToBottom is not null) body["scroll_to_bottom"] = options.ScrollToBottom;
        if (options.MaxScrolls is not null) body["max_scrolls"] = options.MaxScrolls;
        if (options.ScrollWait is not null) body["scroll_wait"] = options.ScrollWait;
        if (options.ScrollStep is not null) body["scroll_step"] = options.ScrollStep;
        return body;
    }

    private static Dictionary<string, object?> DropNull(Dictionary<string, object?> map)
    {
        var result = new Dictionary<string, object?>();
        foreach (var (key, value) in map)
        {
            if (value is not null)
                result[key] = value;
        }
        return result;
    }
}
