# webpipe-sdk

English | [简体中文](./README.zh-CN.md)

Official SDKs for [WebPipe.ai](https://webpipe.ai/) — turn any webpage into clean, structured data with one API call.

Scrape, Map, Crawl, and persistent Browser Sessions. Designed for AI apps and data pipelines.

## Packages

| Language | Package | Status |
|---|---|---|
| Python | [`webpipe-sdk`](./packages/python) (`pip install webpipe-sdk`) | ✅ |
| TypeScript / JavaScript | [`webpipe-sdk`](./packages/js) (`npm install webpipe-sdk`) | ✅ |
| Go | [`webpipe-sdk`](./packages/go) (`go get github.com/webpipe-ai/webpipe-sdk/packages/go`) | ✅ |
| PHP | [`webpipe/webpipe-sdk`](./packages/php) (`composer require webpipe/webpipe-sdk`) | ✅ |
| Ruby | [`webpipe-sdk`](./packages/ruby) (`gem install webpipe-sdk`) | ✅ |
| Java | [`ai.webpipe:webpipe-sdk`](./packages/java) (Maven Central) | ✅ |
| Rust | [`webpipe-sdk`](./packages/rust) (`cargo add webpipe-sdk`) | ✅ |
| C# / .NET | [`Webpipe.Sdk`](./packages/dotnet) (`dotnet add package Webpipe.Sdk`) | ✅ |

## Quick Start

**Python**

```python
from webpipe import WebpipeClient

client = WebpipeClient(api_key="wc-...")  # or set WEBPIPE_API_KEY

doc = client.scrape("https://example.com", formats=["markdown", "links"])
print(doc.markdown)

job = client.crawl("https://example.com", limit=20)  # polls until done
for page in job.data:
    print(page.metadata.source_url)
```

**TypeScript**

```ts
import { Webpipe } from "webpipe-sdk";

const client = new Webpipe({ apiKey: "wc-..." }); // or set WEBPIPE_API_KEY

const doc = await client.scrape("https://example.com", { formats: ["markdown"] });
console.log(doc.markdown);

const session = await client.browser.create({ url: "https://example.com" });
await session.waitUntilRunning();
const page = await session.scrape({ formats: ["markdown"] });
await session.close();
```

**Go**

```go
import webpipe "github.com/WebPipeAI/webpipe-sdk/packages/go"

client, _ := webpipe.NewClient() // reads WEBPIPE_API_KEY

doc, _ := client.Scrape(ctx, "https://example.com", &webpipe.ScrapeOptions{
    Formats: []string{"markdown"},
})
fmt.Println(doc.Markdown)

job, _ := client.Crawl(ctx, "https://example.com",
    &webpipe.CrawlOptions{Limit: 20}, nil) // polls until done
fmt.Println(len(job.Data), "pages")
```

**PHP**

```php
use Webpipe\WebpipeClient;

$client = new WebpipeClient(); // reads WEBPIPE_API_KEY

$doc = $client->scrape('https://example.com', formats: ['markdown']);
echo $doc->markdown;

$session = $client->browser->create(url: 'https://example.com');
$session->waitUntilRunning();
$page = $session->scrape(formats: ['markdown']);
$session->close();
```

**Ruby**

```ruby
require "webpipe"

client = Webpipe::Client.new # reads WEBPIPE_API_KEY

doc = client.scrape("https://example.com", formats: ["markdown"])
puts doc.markdown

job = client.crawl("https://example.com", limit: 20) do |status|
  puts "progress: #{status.completed}/#{status.total}" # yields after each poll
end
```

**Java**

```java
import ai.webpipe.sdk.WebpipeClient;
import ai.webpipe.sdk.options.ScrapeOptions;

var client = WebpipeClient.create(); // reads WEBPIPE_API_KEY

var doc = client.scrape("https://example.com",
        new ScrapeOptions().formats(List.of("markdown")));
System.out.println(doc.markdown());

// Every method also has a non-blocking *Async variant:
var future = client.crawlAsync("https://example.com",
        new CrawlOptions().limit(20), 2, 600); // CompletableFuture<CrawlJob>
```

**Rust**

```rust
use webpipe_sdk::{Client, ScrapeOptions};

let client = Client::from_env()?; // reads WEBPIPE_API_KEY

let doc = client
    .scrape("https://example.com", Some(&ScrapeOptions {
        formats: Some(vec!["markdown".into()]),
        ..Default::default()
    }))
    .await?;
println!("{:?}", doc.markdown);
```

**C# / .NET**

```csharp
using Webpipe.Sdk;
using Webpipe.Sdk.Options;

var client = new WebpipeClient(); // reads WEBPIPE_API_KEY

var doc = await client.ScrapeAsync("https://example.com",
    new ScrapeOptions { Formats = ["markdown"] });
Console.WriteLine(doc.Markdown);

var session = await client.Browser.CreateAsync(
    new CreateSessionOptions { Url = "https://example.com" });
await session.WaitUntilRunningAsync();
var page = await session.ScrapeAsync(new NavigateOptions { Formats = ["markdown"] });
await session.CloseAsync();
```

## Features

- **Scrape** — any URL → Markdown / cleaned HTML / links / metadata / structured JSON (with JSON Schema + `x-source` field mapping)
- **Map** — discover every URL on a site (robots.txt → sitemap → in-page links)
- **Crawl** — recursive whole-site crawling as an async job; SDK polls for you
- **Browser Session** — persistent headless Chromium: navigate, click, fill forms, screenshots, cookie injection, saved login states
- Typed everywhere, automatic retries with backoff, precise error classes

## Examples

Runnable quickstart demos (mini CLIs with built-in demo URLs, including a full
browser login flow) live in [`examples/`](./examples):

```bash
export WEBPIPE_API_KEY=wc-...
python examples/python/demo.py browser   # or: node demo.mjs / go run . / php demo.php
                                         # ruby demo.rb / mvn exec:java / cargo run --example demo / dotnet run
```

## Documentation

- API docs: https://webpipe.ai/docs/
- Repository guide for contributors & agents: [AGENTS.md](./AGENTS.md)

## License

MIT
