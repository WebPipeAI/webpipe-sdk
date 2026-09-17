# webpipe-sdk (.NET)

Official .NET SDK for [WebPipe.ai](https://webpipe.ai/) — turn any webpage into clean, structured data.

## Install

```bash
dotnet add package Webpipe.Sdk
```

Requires .NET 8+. **Zero runtime dependencies** — `HttpClient` + `System.Text.Json` from the framework.

## Quick Start

```csharp
using Webpipe.Sdk;
using Webpipe.Sdk.Options;

var client = new WebpipeClient(); // reads WEBPIPE_API_KEY, or new WebpipeClient(apiKey: "wc-...")

// Scrape a page
var doc = await client.ScrapeAsync("https://example.com",
    new ScrapeOptions { Formats = ["markdown", "metadata"] });
Console.WriteLine(doc.Markdown);
Console.WriteLine(doc.Metadata?["title"]);
```

### Crawl a whole site (polls automatically)

```csharp
var job = await client.CrawlAsync("https://docs.example.com",
    new CrawlOptions
    {
        Limit = 50,
        ScrapeOptions = new ScrapeOptions { Formats = ["markdown"] },
    },
    pollInterval: 2, timeout: 600);
foreach (var page in job.Data)
    Console.WriteLine($"{page.Metadata?["sourceURL"]} {page.Metadata?["title"]}");
```

Need control? `var job = await client.StartCrawlAsync(url, opts)` returns immediately; poll with `client.GetCrawlStatusAsync(job.Id)`.

### Browser session (JS-rendered / logged-in pages)

```csharp
var session = await client.Browser.CreateAsync(
    new CreateSessionOptions { Url = "https://example.com/login" });
try
{
    await session.WaitUntilRunningAsync();
    await session.FillAsync("input[name=email]", "you@example.com");
    await session.FillAsync("input[name=password]", "secret");
    await session.PressAsync("Enter", waitAfterMs: 3000);

    var doc = await session.NavigateAsync("https://example.com/dashboard",
        new NavigateOptions { Formats = ["markdown"] });
    Console.WriteLine(doc.Markdown);
    await session.SaveSessionAsync(); // reuse login state via SessionName
}
finally
{
    await session.CloseAsync();
}
```

### Map (discover all URLs)

```csharp
var links = await client.MapAsync("https://example.com",
    new MapOptions { Limit = 100, Search = "blog" });
foreach (var link in links)
    Console.WriteLine($"{link.Url} {link.Title}");
```

## Errors

```csharp
try
{
    await client.ScrapeAsync("https://example.com");
}
catch (AuthenticationException)
{
    // bad API key
}
catch (RateLimitException e)
{
    // e.StatusCode == 429; e.Message keeps the server's error text
}
catch (WebpipeException)
{
    // anything else
}
```

`429` and `5xx` responses are retried automatically with exponential backoff (`maxRetries: 2` by default).

## Configuration

| Param | Env var | Default |
|---|---|---|
| `apiKey` | `WEBPIPE_API_KEY` | — (required) |
| `baseUrl` | `WEBPIPE_API_URL` | `https://api.web2json.ai` |
| `timeout` | — | 60s |
| `maxRetries` | — | 2 |

## Development

```bash
dotnet test tests/WebpipeSdk.Tests
```
