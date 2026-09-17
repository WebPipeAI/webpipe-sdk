// WebPipe SDK quickstart demo (.NET ≥ 8).
//
// API key (checked in this order — get one at https://webpipe.ai/api-keys.html):
//   1. --api-key wc-...            flag
//   2. WEBPIPE_API_KEY=wc-...      environment variable
//   3. interactive prompt / pipe:  echo wc-... | dotnet run -- scrape
//
// Usage (every command has a built-in demo URL, or pass your own):
//   dotnet run -- scrape [url] [--api-key wc-...]    # default https://example.com
//   dotnet run -- map [url] [--api-key wc-...]       # default https://nginx.org
//   dotnet run -- crawl [url] [--api-key wc-...]     # default https://quotes.toscrape.com
//   dotnet run -- browser [--api-key wc-...]         # login-flow demo

using Webpipe.Sdk;
using Webpipe.Sdk.Options;

var defaultUrls = new Dictionary<string, string>
{
    ["scrape"] = "https://example.com",
    ["map"] = "https://nginx.org",
    ["crawl"] = "https://quotes.toscrape.com",
};

// Parse args: positionals = <command> [url], plus --api-key <key>
var positionals = new List<string>();
string? apiKeyFlag = null;
for (var i = 0; i < args.Length; i++)
{
    if (args[i] == "--api-key" && i + 1 < args.Length)
        apiKeyFlag = args[++i];
    else
        positionals.Add(args[i]);
}
if (positionals.Count == 0 || (!defaultUrls.ContainsKey(positionals[0]) && positionals[0] != "browser"))
{
    Console.Error.WriteLine("usage: <scrape|map|crawl|browser> [url] [--api-key wc-...]");
    return 1;
}
var command = positionals[0];

var client = new WebpipeClient(apiKey: ResolveApiKey(apiKeyFlag));

try
{
    switch (command)
    {
        case "scrape":
        {
            var url = positionals.ElementAtOrDefault(1) ?? defaultUrls["scrape"];
            Console.WriteLine($"→ Scraping {url} ...");
            var doc = await client.ScrapeAsync(url,
                new ScrapeOptions { Formats = ["markdown", "metadata", "links"] });
            Console.WriteLine($"  title:  {doc.Metadata?.GetValueOrDefault("title")}");
            Console.WriteLine($"  status: {doc.Metadata?.GetValueOrDefault("statusCode")}");
            Console.WriteLine($"  links:  {doc.Links?.Count ?? 0}");
            Console.WriteLine("--- markdown (first 400 chars) ---");
            Console.WriteLine(doc.Markdown?[..Math.Min(400, doc.Markdown.Length)]);
            break;
        }
        case "map":
        {
            var url = positionals.ElementAtOrDefault(1) ?? defaultUrls["map"];
            Console.WriteLine($"→ Mapping {url} ...");
            var links = await client.MapAsync(url, new MapOptions { Limit = 50 });
            Console.WriteLine($"  found {links.Count} URLs (limit 50):");
            foreach (var l in links.Take(10))
                Console.WriteLine($"  - {l.Url}");
            if (links.Count > 10)
                Console.WriteLine($"  ... and {links.Count - 10} more");
            break;
        }
        case "crawl":
        {
            var url = positionals.ElementAtOrDefault(1) ?? defaultUrls["crawl"];
            Console.WriteLine($"→ Crawling {url} (limit 5, polling until done) ...");
            var job = await client.CrawlAsync(url,
                new CrawlOptions
                {
                    Limit = 5,
                    ScrapeOptions = new ScrapeOptions { Formats = ["markdown", "metadata"] },
                },
                pollInterval: 2, timeout: 300);
            Console.WriteLine($"  done: {job.Completed}/{job.Total} pages");
            foreach (var p in job.Data)
                Console.WriteLine($"  - {p.Metadata?.GetValueOrDefault("sourceURL")} | {p.Metadata?.GetValueOrDefault("title")}");
            break;
        }
        case "browser":
        {
            const string url = "https://quotes.toscrape.com/login";
            Console.WriteLine($"→ Browser login demo on {url} (any credentials work) ...");
            var session = await client.Browser.CreateAsync(new CreateSessionOptions { Url = url });
            try
            {
                await session.WaitUntilRunningAsync(timeoutSeconds: 90, pollIntervalSeconds: 2);
                Console.WriteLine("  session running, filling the login form ...");
                await session.FillAsync("input[name=username]", "demo");
                await session.FillAsync("input[name=password]", "demo");
                await session.ClickAsync("input[type=submit]", waitAfterMs: 2000);
                var doc = await session.ScrapeAsync(new NavigateOptions { Formats = ["markdown"] });
                var ok = doc.Markdown?.Contains("Logout") == true;
                Console.WriteLine(ok
                    ? "  login succeeded ✓ (Logout link found)"
                    : "  login — check output below");
                Console.WriteLine("--- markdown after login (first 300 chars) ---");
                Console.WriteLine(doc.Markdown?[..Math.Min(300, doc.Markdown.Length)]);
            }
            finally
            {
                await session.CloseAsync();
            }
            Console.WriteLine("  session closed.");
            break;
        }
    }
    return 0;
}
catch (WebpipeException e)
{
    Console.Error.WriteLine($"error: {e.Message}");
    return 1;
}

static string ResolveApiKey(string? flag)
{
    if (!string.IsNullOrEmpty(flag))
        return flag;
    var env = Environment.GetEnvironmentVariable("WEBPIPE_API_KEY");
    if (!string.IsNullOrEmpty(env))
        return env;
    Console.WriteLine("API key not found in --api-key or WEBPIPE_API_KEY.");
    Console.WriteLine("Get one at https://webpipe.ai/api-keys.html");
    if (!Console.IsInputRedirected)
        Console.Write("Enter your API key (wc-...): ");
    var key = Console.ReadLine()?.Trim();
    if (!string.IsNullOrEmpty(key))
        return key;
    Console.Error.WriteLine("error: no API key provided");
    Environment.Exit(1);
    return "";
}
