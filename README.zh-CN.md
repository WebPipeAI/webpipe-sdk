# webpipe-sdk

[English](./README.md) | 简体中文

[WebPipe.ai](https://webpipe.ai/) 官方多语言 SDK —— 一次 API 调用，把任意网页变成干净、结构化的数据。

四大功能：Scrape（单页抓取）、Map（URL 发现）、Crawl（递归爬取）、Browser Session（持久化无头浏览器）。专为 AI 应用与数据管道设计。

## 各语言包

| 语言 | 包 | 状态 |
|---|---|---|
| Python | [`webpipe-sdk`](./packages/python)（`pip install webpipe-sdk`） | ✅ |
| TypeScript / JavaScript | [`webpipe-sdk`](./packages/js)（`npm install webpipe-sdk`） | ✅ |
| Go | [`webpipe-sdk`](./packages/go)（`go get github.com/webpipe-ai/webpipe-sdk/packages/go`） | ✅ |
| PHP | [`webpipe/webpipe-sdk`](./packages/php)（`composer require webpipe/webpipe-sdk`） | ✅ |
| Ruby | [`webpipe-sdk`](./packages/ruby)（`gem install webpipe-sdk`） | ✅ |
| Java | [`ai.webpipe:webpipe-sdk`](./packages/java)（Maven Central） | ✅ |
| Rust | [`webpipe-sdk`](./packages/rust)（`cargo add webpipe-sdk`） | ✅ |
| C# / .NET | [`Webpipe.Sdk`](./packages/dotnet)（`dotnet add package Webpipe.Sdk`） | ✅ |

## 快速上手

**Python**

```python
from webpipe import WebpipeClient

client = WebpipeClient(api_key="wc-...")  # 或设置 WEBPIPE_API_KEY 环境变量

doc = client.scrape("https://example.com", formats=["markdown", "links"])
print(doc.markdown)

job = client.crawl("https://example.com", limit=20)  # 自动轮询到完成
for page in job.data:
    print(page.metadata.source_url)
```

**TypeScript**

```ts
import { Webpipe } from "webpipe-sdk";

const client = new Webpipe({ apiKey: "wc-..." }); // 或设置 WEBPIPE_API_KEY

const doc = await client.scrape("https://example.com", { formats: ["markdown"] });
console.log(doc.markdown);

const session = await client.browser.create({ url: "https://example.com" });
await session.waitUntilRunning();
const page = await session.scrape({ formats: ["markdown"] });
await session.close();
```

**Go**

```go
import webpipe "github.com/webpipe-ai/webpipe-sdk/packages/go"

client, _ := webpipe.NewClient() // 读取 WEBPIPE_API_KEY

doc, _ := client.Scrape(ctx, "https://example.com", &webpipe.ScrapeOptions{
    Formats: []string{"markdown"},
})
fmt.Println(doc.Markdown)

job, _ := client.Crawl(ctx, "https://example.com",
    &webpipe.CrawlOptions{Limit: 20}, nil) // 自动轮询到完成
fmt.Println(len(job.Data), "pages")
```

**PHP**

```php
use Webpipe\WebpipeClient;

$client = new WebpipeClient(); // 读取 WEBPIPE_API_KEY

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

client = Webpipe::Client.new # 读取 WEBPIPE_API_KEY

doc = client.scrape("https://example.com", formats: ["markdown"])
puts doc.markdown

job = client.crawl("https://example.com", limit: 20) do |status|
  puts "进度: #{status.completed}/#{status.total}" # 每次轮询 yield 一次
end
```

**Java**

```java
import ai.webpipe.sdk.WebpipeClient;
import ai.webpipe.sdk.options.ScrapeOptions;

var client = WebpipeClient.create(); // 读取 WEBPIPE_API_KEY

var doc = client.scrape("https://example.com",
        new ScrapeOptions().formats(List.of("markdown")));
System.out.println(doc.markdown());

// 每个方法都有非阻塞的 *Async 变体（CompletableFuture）：
var future = client.crawlAsync("https://example.com",
        new CrawlOptions().limit(20), 2, 600); // CompletableFuture<CrawlJob>
```

**Rust**

```rust
use webpipe_sdk::{Client, ScrapeOptions};

let client = Client::from_env()?; // 读取 WEBPIPE_API_KEY

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

var client = new WebpipeClient(); // 读取 WEBPIPE_API_KEY

var doc = await client.ScrapeAsync("https://example.com",
    new ScrapeOptions { Formats = ["markdown"] });
Console.WriteLine(doc.Markdown);

var session = await client.Browser.CreateAsync(
    new CreateSessionOptions { Url = "https://example.com" });
await session.WaitUntilRunningAsync();
var page = await session.ScrapeAsync(new NavigateOptions { Formats = ["markdown"] });
await session.CloseAsync();
```

## 特性

- **Scrape** —— 任意 URL → Markdown / 清理版 HTML / 链接列表 / 元数据 / 结构化 JSON（支持 JSON Schema + `x-source` 字段映射）
- **Map** —— 发现网站全部 URL（robots.txt → sitemap → 页内链接）
- **Crawl** —— 异步任务递归爬取整站，SDK 自动轮询到完成
- **Browser Session** —— 持久化无头 Chromium：导航、点击、填表、截图、Cookie 注入、登录状态持久化
- 全面类型化、429/5xx 指数退避自动重试、精确的错误分层

## 示例

[`examples/`](./examples) 目录提供可直接运行的快速入门 demo（迷你 CLI，内置演示 URL，含完整浏览器登录流程）：

```bash
export WEBPIPE_API_KEY=wc-...
python examples/python/demo.py browser   # 或: node demo.mjs / go run . / php demo.php
                                         # ruby demo.rb / mvn exec:java / cargo run --example demo / dotnet run
```

## 文档

- API 文档：https://webpipe.ai/docs/
- 贡献者与 Agent 指南：[AGENTS.md](./AGENTS.md)
- 发布流程（维护者）：[docs/publishing.md](./docs/publishing.md)

## 许可证

MIT
