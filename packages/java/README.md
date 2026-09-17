# webpipe-sdk (Java)

Official Java SDK for [WebPipe.ai](https://webpipe.ai/) — turn any webpage into clean, structured data.

## Install

Maven:

```xml
<dependency>
    <groupId>ai.webpipe</groupId>
    <artifactId>webpipe-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'ai.webpipe:webpipe-sdk:0.1.0'
```

Requires Java ≥ 17. Single dependency: Jackson Databind. HTTP via `java.net.http` (JDK built-in).

## Quick Start

```java
import ai.webpipe.sdk.WebpipeClient;
import ai.webpipe.sdk.options.ScrapeOptions;
import java.util.List;

var client = WebpipeClient.create(); // reads WEBPIPE_API_KEY
// or WebpipeClient.builder().apiKey("wc-...").build();

// Scrape a page
var doc = client.scrape("https://example.com",
        new ScrapeOptions().formats(List.of("markdown", "metadata")));
System.out.println(doc.markdown());
System.out.println(doc.metadata().get("title"));
```

### Crawl a whole site (polls automatically)

```java
var job = client.crawl("https://docs.example.com",
        new CrawlOptions().limit(50)
            .scrapeOptions(new ScrapeOptions().formats(List.of("markdown"))),
        2,    // pollInterval seconds
        600); // timeout seconds
for (var page : job.data()) {
    System.out.println(page.metadata().get("sourceURL") + " " + page.metadata().get("title"));
}
```

Need control? `var job = client.startCrawl(url, opts)` returns immediately; poll with `client.getCrawlStatus(job.id())`.

### Browser session (JS-rendered / logged-in pages)

```java
var session = client.browser.create(new CreateSessionOptions().url("https://example.com/login"));
try {
    session.waitUntilRunning(1, 60);
    session.fill("input[name=email]", "you@example.com");
    session.fill("input[name=password]", "secret");
    session.press("Enter", null, 3000);

    var doc = session.navigate("https://example.com/dashboard",
            new NavigateOptions().formats(List.of("markdown")));
    System.out.println(doc.markdown());
    session.saveSession(); // reuse login state next time via sessionName
} finally {
    session.close();
}
```

### Map (discover all URLs)

```java
var links = client.map("https://example.com", new MapOptions().limit(100).search("blog"));
for (var link : links) {
    System.out.println(link.url() + " " + link.title());
}
```

## Errors

```java
try {
    client.scrape("https://example.com");
} catch (AuthenticationException e) {
    // bad API key; e.getStatusCode() == 401
} catch (RateLimitException e) {
    // 429; e.getMessage() keeps the server's error text
} catch (WebpipeException e) {
    // anything else
}
```

`429` and `5xx` responses are retried automatically with exponential backoff (`maxRetries(2)` by default).

## Configuration

| Builder | Env var | Default |
|---|---|---|
| `apiKey(...)` | `WEBPIPE_API_KEY` | — (required) |
| `baseUrl(...)` | `WEBPIPE_API_URL` | `https://api.web2json.ai` |
| `timeout(Duration)` | — | 60s |
| `maxRetries(int)` | — | 2 |

## Development

```bash
mvn test
```
