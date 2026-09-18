# webpipe-sdk (Go)

Official Go SDK for [WebPipe.ai](https://webpipe.ai/) — turn any webpage into clean, structured data.

## Install

```bash
go get github.com/WebPipeAI/webpipe-sdk/packages/go
```

**Zero dependencies** — standard library only. Requires Go ≥ 1.22.

## Quick Start

```go
package main

import (
    "context"
    "fmt"
    "log"

    webpipe "github.com/WebPipeAI/webpipe-sdk/packages/go"
)

func main() {
    client, err := webpipe.NewClient() // reads WEBPIPE_API_KEY, or webpipe.WithAPIKey("wc-...")
    if err != nil {
        log.Fatal(err)
    }
    ctx := context.Background()

    // Scrape a page
    doc, err := client.Scrape(ctx, "https://example.com", &webpipe.ScrapeOptions{
        Formats: []string{"markdown", "metadata"},
    })
    if err != nil {
        log.Fatal(err)
    }
    fmt.Println(doc.Markdown)
}
```

### Crawl a whole site (polls automatically)

```go
job, err := client.Crawl(ctx, "https://docs.example.com",
    &webpipe.CrawlOptions{
        Limit:         50,
        ScrapeOptions: &webpipe.ScrapeOptions{Formats: []string{"markdown"}},
    },
    &webpipe.CrawlWaitOptions{PollInterval: 2, Timeout: 600},
)
if err != nil {
    log.Fatal(err)
}
for _, page := range job.Data {
    fmt.Println(page.Metadata.SourceURL, page.Metadata.Title)
}
```

Need control? `job, _ := client.StartCrawl(ctx, url, opts)` returns immediately; poll with `client.GetCrawlStatus(ctx, job.ID)`.

### Browser session (JS-rendered / logged-in pages)

```go
session, err := client.Browser.Create(ctx, &webpipe.CreateSessionOptions{
    URL: "https://example.com/login",
})
if err != nil {
    log.Fatal(err)
}
defer session.Close(ctx)

if err := session.WaitUntilRunning(ctx, 1, 60); err != nil {
    log.Fatal(err)
}
session.Fill(ctx, "input[name=email]", "you@example.com")
session.Fill(ctx, "input[name=password]", "secret")
session.Press(ctx, "Enter", "", 3000)

doc, err := session.Navigate(ctx, "https://example.com/dashboard", &webpipe.NavigateOptions{
    Formats: []string{"markdown"},
})
fmt.Println(doc.Markdown)
session.SaveSession(ctx) // reuse login state next time via SessionName
```

### Map (discover all URLs)

```go
links, err := client.Map(ctx, "https://example.com", &webpipe.MapOptions{Limit: 100})
for _, l := range links {
    fmt.Println(l.URL, l.Title)
}
```

## Errors

Sentinel categories + `errors.Is`, details via `errors.As`:

```go
doc, err := client.Scrape(ctx, url, nil)
switch {
case errors.Is(err, webpipe.ErrAuthentication):
    // bad API key
case errors.Is(err, webpipe.ErrRateLimit):
    // 429
}

var apiErr *webpipe.APIError
if errors.As(err, &apiErr) {
    fmt.Println(apiErr.StatusCode, apiErr.Message) // server's original error text
}
```

`429` and `5xx` are retried automatically with exponential backoff (`WithMaxRetries(2)` by default).

## Configuration

| Option | Env var | Default |
|---|---|---|
| `WithAPIKey` | `WEBPIPE_API_KEY` | — (required) |
| `WithBaseURL` | `WEBPIPE_API_URL` | `https://api.web2json.ai` |
| `WithTimeout` | — | 60s |
| `WithMaxRetries` | — | 2 |
| `WithHTTPClient` | — | stdlib default |

## Development

```bash
go build ./... && go vet ./... && go test ./...
```
