# webpipe-sdk (TypeScript / JavaScript)

Official TypeScript/JavaScript SDK for [WebPipe.ai](https://webpipe.ai/) — turn any webpage into clean, structured data.

## Install

```bash
npm install webpipe-sdk
```

Requires Node.js ≥ 18. **Zero runtime dependencies** — uses native `fetch`. Ships ESM + CJS + type definitions.

## Quick Start

```ts
import { Webpipe } from "webpipe-sdk";

const client = new Webpipe(); // reads WEBPIPE_API_KEY env var, or pass { apiKey: "wc-..." }
```

### Scrape a page

```ts
const doc = await client.scrape("https://example.com", {
  formats: ["markdown", "links", "metadata"],
});
console.log(doc.markdown);
console.log(doc.metadata?.title);
```

Structured JSON extraction with a schema:

```ts
const doc = await client.scrape("https://go.dev/doc/effective_go", {
  formats: ["json"],
  jsonSchema: {
    type: "object",
    properties: {
      title: { type: "string" },
      sections: { type: "array", "x-source": "headings" },
    },
  },
});
console.log(doc.json);
```

### Crawl a whole site (polls automatically)

```ts
const job = await client.crawl("https://docs.example.com", {
  limit: 50,
  scrapeOptions: { formats: ["markdown"] },
});
for (const page of job.data) {
  console.log(page.metadata?.sourceURL, page.metadata?.title);
}
```

Need control? `const job = await client.startCrawl(url)` returns immediately; poll with `client.getCrawlStatus(job.id)`.

### Browser session (JS-rendered / logged-in pages)

```ts
const session = await client.browser.create({ url: "https://example.com/login" });
try {
  await session.waitUntilRunning();
  await session.fill("input[name=email]", "you@example.com");
  await session.fill("input[name=password]", "secret");
  await session.press("Enter", { waitAfter: 3000 });

  const doc = await session.navigate("https://example.com/dashboard", {
    formats: ["markdown"],
  });
  console.log(doc.markdown);
  await session.saveSession(); // reuse login state next time via sessionName
} finally {
  await session.close();
}
```

### Map (discover all URLs)

```ts
const links = await client.map("https://example.com", { limit: 100, search: "blog" });
for (const link of links) console.log(link.url, link.title);
```

## Errors

```ts
import { AuthenticationError, RateLimitError, WebpipeError } from "webpipe-sdk";

try {
  await client.scrape("https://example.com");
} catch (err) {
  if (err instanceof AuthenticationError) {
    // bad API key
  } else if (err instanceof RateLimitError) {
    // err.statusCode === 429; err.message keeps the server's error text
  } else if (err instanceof WebpipeError) {
    // anything else from the SDK
  }
}
```

`429` and `5xx` responses are retried automatically with exponential backoff (`maxRetries: 2` by default).

## Configuration

| Option | Env var | Default |
|---|---|---|
| `apiKey` | `WEBPIPE_API_KEY` | — (required) |
| `baseUrl` | `WEBPIPE_API_URL` | `https://api.web2json.ai` |
| `timeout` | — | 60s |
| `maxRetries` | — | 2 |

## Development

```bash
npm install
npm run build && npm run typecheck && npm test
```
