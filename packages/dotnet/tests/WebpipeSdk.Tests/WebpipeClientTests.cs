using System.Net;
using System.Text.Json.Nodes;
using Webpipe.Sdk;
using Webpipe.Sdk.Models;
using Webpipe.Sdk.Options;
using Xunit;

namespace WebpipeSdk.Tests;

public class WebpipeClientTests
{
    [Fact]
    public void MissingApiKeyThrows()
    {
        Environment.SetEnvironmentVariable("WEBPIPE_API_KEY", null);
        var ex = Assert.Throws<ArgumentException>(() => new WebpipeClient());
        Assert.Contains("WEBPIPE_API_KEY", ex.Message);
    }

    [Fact]
    public async Task ScrapeSuccess()
    {
        var mock = new MockHttpHandler()
            .Enqueue(HttpStatusCode.OK, new
            {
                success = true,
                data = new
                {
                    markdown = "# Example",
                    metadata = new { title = "Example Domain", sourceURL = "https://example.com", statusCode = 200 },
                },
            });
        var client = MockClientFactory.Create(mock);

        var doc = await client.ScrapeAsync("https://example.com",
            new ScrapeOptions { Formats = ["markdown", "metadata"] });

        Assert.Equal("# Example", doc.Markdown);
        Assert.Equal("Example Domain", doc.Metadata?["title"].ToString());

        var req = mock.LastRequest;
        Assert.Equal("Bearer wc-test-key", req.auth);
        Assert.StartsWith("webpipe-sdk-dotnet/", req.userAgent);
        var sent = JsonNode.Parse(req.body!)!;
        Assert.Equal("https://example.com", sent["url"]!.GetValue<string>());
        Assert.Equal("markdown", sent["formats"]![0]!.GetValue<string>());
        Assert.Null(sent["use_proxy"]);
    }

    [Fact]
    public async Task Scrape401MapsToAuthentication()
    {
        var mock = new MockHttpHandler()
            .Enqueue(HttpStatusCode.Unauthorized, new { success = false, error = "invalid key" });
        var client = MockClientFactory.Create(mock);

        var ex = await Assert.ThrowsAsync<AuthenticationException>(
            () => client.ScrapeAsync("https://example.com"));
        Assert.Equal(401, ex.StatusCode);
        Assert.Contains("invalid key", ex.Message);
    }

    [Fact]
    public async Task RetriesOn429ThenSucceeds()
    {
        var mock = new MockHttpHandler()
            .Enqueue((HttpStatusCode)429, new { success = false, error = "slow down" })
            .Enqueue(HttpStatusCode.OK, new { success = true, data = new { markdown = "ok" } });
        var client = MockClientFactory.Create(mock, maxRetries: 1);

        var doc = await client.ScrapeAsync("https://example.com");
        Assert.Equal("ok", doc.Markdown);
        Assert.Equal(2, mock.Requests.Count);
    }

    [Fact]
    public async Task MapReturnsLinks()
    {
        var mock = new MockHttpHandler()
            .Enqueue(HttpStatusCode.OK, new
            {
                success = true,
                links = new object[]
                {
                    new { url = "https://example.com/about", title = "About" },
                    new { url = "https://example.com/blog" },
                },
            });
        var client = MockClientFactory.Create(mock);

        var links = await client.MapAsync("https://example.com",
            new MapOptions { Limit = 100, SameDomain = false });

        Assert.Equal(2, links.Count);
        Assert.Equal("https://example.com/about", links[0].Url);
        var sent = JsonNode.Parse(mock.LastRequest.body!)!;
        Assert.False(sent["same_domain"]!.GetValue<bool>());
    }

    [Fact]
    public async Task CrawlPollsUntilCompleted()
    {
        var mock = new MockHttpHandler()
            .Enqueue(HttpStatusCode.OK, new { success = true, id = "job-1" })
            .Enqueue(HttpStatusCode.OK, new { success = true, status = "scraping", total = 2, completed = 1 })
            .Enqueue(HttpStatusCode.OK, new
            {
                success = true,
                status = "completed",
                total = 2,
                completed = 2,
                data = new object[] { new { markdown = "# A" }, new { markdown = "# B" } },
                errors = Array.Empty<object>(),
                expires_at = "2026-07-28T00:00:00+00:00",
            });
        var client = MockClientFactory.Create(mock);

        var job = await client.CrawlAsync("https://x",
            new CrawlOptions { Limit = 2 }, pollInterval: 0.01);

        Assert.Equal(CrawlJob.StatusCompleted, job.Status);
        Assert.Equal(2, job.Data.Count);
        Assert.Equal("# B", job.Data[1].Markdown);
        Assert.Equal("2026-07-28T00:00:00+00:00", job.ExpiresAt);
    }

    [Fact]
    public async Task CrawlFailed()
    {
        var mock = new MockHttpHandler()
            .Enqueue(HttpStatusCode.OK, new { success = true, id = "job-2" })
            .Enqueue(HttpStatusCode.OK, new { success = true, status = "failed", errors = new[] { "boom" } });
        var client = MockClientFactory.Create(mock);

        await Assert.ThrowsAsync<CrawlException>(
            () => client.CrawlAsync("https://x", pollInterval: 0.01));
    }

    [Fact]
    public async Task CrawlTimeout()
    {
        var mock = new MockHttpHandler()
            .Enqueue(HttpStatusCode.OK, new { success = true, id = "job-3" });
        for (var i = 0; i < 20; i++)
            mock.Enqueue(HttpStatusCode.OK, new { success = true, status = "scraping" });
        var client = MockClientFactory.Create(mock);

        await Assert.ThrowsAsync<PollTimeoutException>(
            () => client.CrawlAsync("https://x", pollInterval: 0.01, timeout: 0.05));
    }

    [Fact]
    public async Task CrawlStatus404()
    {
        var mock = new MockHttpHandler()
            .Enqueue(HttpStatusCode.NotFound, new { success = false, error = "gone" });
        var client = MockClientFactory.Create(mock);

        await Assert.ThrowsAsync<NotFoundException>(() => client.GetCrawlStatusAsync("gone"));
    }
}
