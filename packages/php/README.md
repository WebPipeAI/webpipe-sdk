# webpipe-sdk (PHP)

Official PHP SDK for [WebPipe.ai](https://webpipe.ai/) — turn any webpage into clean, structured data.

## Install

```bash
composer require webpipe/webpipe-sdk
```

Requires PHP ≥ 8.1. Single dependency: Guzzle 7.

## Quick Start

```php
<?php
require 'vendor/autoload.php';

use Webpipe\WebpipeClient;

$client = new WebpipeClient(); // reads WEBPIPE_API_KEY, or new WebpipeClient(apiKey: 'wc-...')

// Scrape a page
$doc = $client->scrape('https://example.com', formats: ['markdown', 'metadata']);
echo $doc->markdown;
echo $doc->metadata['title'];
```

### Crawl a whole site (polls automatically)

```php
$job = $client->crawl(
    'https://docs.example.com',
    limit: 50,
    scrapeOptions: ['formats' => ['markdown']],
    timeout: 600,
);
foreach ($job->data as $page) {
    echo $page->metadata['sourceURL'], ' ', $page->metadata['title'], PHP_EOL;
}
```

Need control? `$job = $client->startCrawl($url, limit: 50)` returns immediately; poll with `$client->getCrawlStatus($job->id)`.

### Browser session (JS-rendered / logged-in pages)

```php
$session = $client->browser->create(url: 'https://example.com/login');
try {
    $session->waitUntilRunning();
    $session->fill('input[name=email]', 'you@example.com');
    $session->fill('input[name=password]', 'secret');
    $session->press('Enter', waitAfter: 3000);

    $doc = $session->navigate('https://example.com/dashboard', formats: ['markdown']);
    echo $doc->markdown;
    $session->saveSession(); // reuse login state next time via sessionName
} finally {
    $session->close();
}
```

### Map (discover all URLs)

```php
$links = $client->map('https://example.com', limit: 100, search: 'blog');
foreach ($links as $link) {
    echo $link->url, ' ', $link->title, PHP_EOL;
}
```

## Errors

```php
use Webpipe\Exception\{AuthenticationException, RateLimitException, WebpipeException};

try {
    $client->scrape('https://example.com');
} catch (AuthenticationException $e) {
    // bad API key
} catch (RateLimitException $e) {
    // $e->statusCode === 429; $e->getMessage() keeps the server's error text
} catch (WebpipeException $e) {
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
composer install
composer test
```
