using System.Net;
using System.Text.Json.Nodes;
using Webpipe.Sdk.Models;
using Xunit;

namespace WebpipeSdk.Tests;

public class BrowserSessionTests
{
    [Fact]
    public async Task SessionLifecycle()
    {
        var mock = new MockHttpHandler()
            .Enqueue(HttpStatusCode.OK, new { success = true, id = "sess-1", status = "starting" })
            .Enqueue(HttpStatusCode.OK, new { success = true, id = "sess-1", status = "starting" })
            .Enqueue(HttpStatusCode.OK, new { success = true, id = "sess-1", status = "running" })
            .Enqueue(HttpStatusCode.OK, new { success = true, data = new { markdown = "# Hi" } })
            .Enqueue(HttpStatusCode.OK, new { success = true, ok = true })
            .Enqueue(HttpStatusCode.OK, new { success = true });
        var client = MockClientFactory.Create(mock);

        var session = await client.Browser.CreateAsync(
            new Webpipe.Sdk.Options.CreateSessionOptions { Url = "https://example.com", SessionName = "demo" });
        Assert.Equal("sess-1", session.Id);
        Assert.Equal("starting", session.Status);
        Assert.Equal("demo", JsonNode.Parse(mock.Requests[0].body!)!["session_name"]!.GetValue<string>());

        await session.WaitUntilRunningAsync(timeoutSeconds: 5, pollIntervalSeconds: 0.01);
        Assert.Equal("running", session.Status);

        var doc = await session.ScrapeAsync(new Webpipe.Sdk.Options.NavigateOptions { Formats = ["markdown"] });
        Assert.Equal("# Hi", doc.Markdown);

        await session.ClickAsync("button.more", waitAfterMs: 500);
        var actionBody = JsonNode.Parse(mock.LastRequest.body!)!;
        Assert.Equal("click", actionBody["type"]!.GetValue<string>());
        Assert.Equal("button.more", actionBody["selector"]!.GetValue<string>());
        Assert.Equal(500, actionBody["wait_after"]!.GetValue<int>());

        await session.CloseAsync();
        Assert.Equal("stopped", session.Status);
        Assert.Equal("DELETE", mock.LastRequest.method.Method);
    }

    [Fact]
    public async Task Cookies()
    {
        var mock = new MockHttpHandler()
            .Enqueue(HttpStatusCode.OK, new { success = true, id = "sess-2", status = "running" })
            .Enqueue(HttpStatusCode.OK, new
            {
                success = true,
                cookies = new[] { new { name = "session", value = "abc" } },
            })
            .Enqueue(HttpStatusCode.OK, new { success = true })
            .Enqueue(HttpStatusCode.OK, new { success = true });
        var client = MockClientFactory.Create(mock);

        var session = await client.Browser.CreateAsync();
        var cookies = await session.GetCookiesAsync();
        Assert.Equal("session", cookies[0].Name);

        await session.SetCookiesAsync([new Webpipe.Sdk.Models.Cookie("x", "1", "example.com", "/")]);
        Assert.Equal("set", JsonNode.Parse(mock.LastRequest.body!)!["op"]!.GetValue<string>());

        await session.ClearCookiesAsync();
        Assert.Equal("clear", JsonNode.Parse(mock.LastRequest.body!)!["op"]!.GetValue<string>());
    }
}
