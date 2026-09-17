using System.Net;
using System.Text;
using System.Text.Json.Nodes;

namespace WebpipeSdk.Tests;

/// <summary>Queue-based mock HttpMessageHandler. Records requests for assertions.</summary>
internal sealed class MockHttpHandler : HttpMessageHandler
{
    private readonly Queue<(HttpStatusCode status, string body)> _responses = new();
    public List<(HttpMethod method, string path, string? body, string? auth, string? userAgent)> Requests { get; } = new();

    public MockHttpHandler Enqueue(HttpStatusCode status, object body)
    {
        _responses.Enqueue((status, System.Text.Json.JsonSerializer.Serialize(body)));
        return this;
    }

    public (HttpMethod method, string path, string? body, string? auth, string? userAgent) LastRequest =>
        Requests[^1];

    protected override async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request, CancellationToken cancellationToken)
    {
        string? body = request.Content is null
            ? null
            : await request.Content.ReadAsStringAsync(cancellationToken);
        Requests.Add((
            request.Method,
            request.RequestUri?.AbsolutePath ?? "",
            body,
            request.Headers.Authorization?.ToString(),
            request.Headers.UserAgent.ToString()));

        var (status, responseBody) = _responses.Count > 0
            ? _responses.Dequeue()
            : (HttpStatusCode.NotFound, "{\"error\":\"no mock queued\"}");
        return new HttpResponseMessage(status)
        {
            Content = new StringContent(responseBody, Encoding.UTF8, "application/json"),
        };
    }
}

internal static class MockClientFactory
{
    public static Webpipe.Sdk.WebpipeClient Create(MockHttpHandler handler, int maxRetries = 0) =>
        new("wc-test-key", "https://api.web2json.ai", maxRetries: maxRetries,
            httpClient: new System.Net.Http.HttpClient(handler));
}
