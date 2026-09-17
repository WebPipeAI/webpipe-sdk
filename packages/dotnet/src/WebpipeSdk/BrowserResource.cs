using Webpipe.Sdk.Internal;
using Webpipe.Sdk.Models;
using Webpipe.Sdk.Options;

namespace Webpipe.Sdk;

/// <summary>Factory for browser sessions, exposed as <c>client.Browser</c>.</summary>
public sealed class BrowserResource
{
    private readonly HttpClientWrapper _http;

    internal BrowserResource(HttpClientWrapper http)
    {
        _http = http;
    }

    /// <summary>
    /// Create a session. It starts asynchronously — call
    /// <see cref="BrowserSession.WaitUntilRunningAsync"/> before sending commands
    /// for a controlled experience.
    /// </summary>
    public async Task<BrowserSession> CreateAsync(
        CreateSessionOptions? options = null, CancellationToken ct = default)
    {
        var res = await _http.PostAsync("/api/v1/browser", options ?? new CreateSessionOptions(), ct)
            .ConfigureAwait(false);
        var info = Json.Deserialize<SessionInfo>(res)
            ?? throw new WebpipeException("Invalid session create response");
        return new BrowserSession(_http, info);
    }
}
