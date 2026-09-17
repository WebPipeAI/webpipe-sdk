using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace Webpipe.Sdk.Internal;

/// <summary>
/// Thin HttpClient wrapper: auth headers, retries with backoff, error mapping.
/// <strong>Internal — never use from outside the SDK.</strong>
/// </summary>
internal sealed class HttpClientWrapper
{
    private static readonly int[] RetryableStatuses = [429, 500, 502, 503, 504];

    private readonly System.Net.Http.HttpClient _client;
    private readonly int _maxRetries;

    public HttpClientWrapper(
        string baseUrl,
        string apiKey,
        TimeSpan timeout,
        int maxRetries,
        string userAgent,
        System.Net.Http.HttpClient? inner = null)
    {
        _maxRetries = maxRetries;
        _client = inner ?? new System.Net.Http.HttpClient();
        _client.BaseAddress = new Uri(baseUrl.TrimEnd('/') + "/");
        _client.Timeout = timeout;
        _client.DefaultRequestHeaders.Add("Authorization", $"Bearer {apiKey}");
        _client.DefaultRequestHeaders.Add("User-Agent", userAgent);
    }

    public Task<JsonNode?> GetAsync(string path, CancellationToken ct = default) =>
        RequestAsync(HttpMethod.Get, path, null, ct);

    public Task<JsonNode?> PostAsync(string path, object? body = null, CancellationToken ct = default) =>
        RequestAsync(HttpMethod.Post, path, body, ct);

    public Task<JsonNode?> DeleteAsync(string path, CancellationToken ct = default) =>
        RequestAsync(HttpMethod.Delete, path, null, ct);

    public async Task<JsonNode?> RequestAsync(HttpMethod method, string path, object? body, CancellationToken ct)
    {
        var attempt = 0;
        while (true)
        {
            HttpResponseMessage response;
            try
            {
                using var request = new HttpRequestMessage(method, path);
                if (body is not null)
                    request.Content = JsonContent.Create(body);
                response = await _client.SendAsync(request, ct).ConfigureAwait(false);
            }
            catch (HttpRequestException) when (attempt < _maxRetries)
            {
                attempt++;
                await Task.Delay(Backoff(null, attempt), ct).ConfigureAwait(false);
                continue;
            }
            catch (TaskCanceledException) when (!ct.IsCancellationRequested && attempt < _maxRetries)
            {
                // per-request timeout (not caller cancellation)
                attempt++;
                await Task.Delay(Backoff(null, attempt), ct).ConfigureAwait(false);
                continue;
            }

            var status = (int)response.StatusCode;
            if (RetryableStatuses.Contains(status) && attempt < _maxRetries)
            {
                attempt++;
                var retryAfter = response.Headers.RetryAfter?.Delta?.TotalSeconds;
                await Task.Delay(Backoff(retryAfter, attempt), ct).ConfigureAwait(false);
                continue;
            }

            var raw = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
            JsonNode? payload = null;
            if (!string.IsNullOrEmpty(raw))
            {
                try { payload = JsonNode.Parse(raw); }
                catch (JsonException) { /* non-JSON body */ }
            }

            if (status >= 400)
            {
                var message = payload?["error"]?.GetValue<string>() ?? raw;
                throw ApiException.ForStatus(status, message, payload);
            }
            return payload;
        }
    }

    private static TimeSpan Backoff(double? retryAfterSeconds, int attempt) =>
        retryAfterSeconds is >= 0
            ? TimeSpan.FromSeconds(retryAfterSeconds.Value)
            : TimeSpan.FromSeconds(0.5 * Math.Pow(2, attempt));
}
