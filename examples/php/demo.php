<?php
/**
 * WebPipe SDK quickstart demo (PHP ≥ 8.1).
 *
 * Setup:
 *   composer install            # links the local SDK via path repository
 *
 * API key (checked in this order — get one at https://webpipe.ai/api-keys.html):
 *   1. --api-key wc-...            flag
 *   2. WEBPIPE_API_KEY=wc-...      environment variable
 *   3. interactive prompt / pipe:  echo wc-... | php demo.php scrape
 *
 * Usage (every command has a built-in demo URL, or pass your own):
 *   php demo.php scrape [url] [--api-key wc-...]    # default https://example.com
 *   php demo.php map [url] [--api-key wc-...]       # default https://nginx.org
 *   php demo.php crawl [url] [--api-key wc-...]     # default https://quotes.toscrape.com
 *   php demo.php browser [--api-key wc-...]         # login-flow demo on quotes.toscrape.com
 */

declare(strict_types=1);

require __DIR__ . '/vendor/autoload.php';

use Webpipe\Exception\WebpipeException;
use Webpipe\WebpipeClient;

/** Resolve the API key: --api-key flag > env var > prompt/pipe. */
function resolveApiKey(?string $flag): string
{
    if ($flag !== null && $flag !== '') {
        return $flag;
    }
    $env = getenv('WEBPIPE_API_KEY');
    if ($env !== false && $env !== '') {
        return $env;
    }
    echo "API key not found in --api-key or WEBPIPE_API_KEY.\n";
    echo "Get one at https://webpipe.ai/api-keys.html\n";
    if (function_exists('stream_isatty') && stream_isatty(STDIN)) {
        echo 'Enter your API key (wc-...): ';
    }
    $line = fgets(STDIN);
    $key = $line === false ? '' : trim($line);
    if ($key !== '') {
        return $key;
    }
    fwrite(STDERR, "error: no API key provided\n");
    exit(1);
}

const DEFAULT_URLS = [
    'scrape' => 'https://example.com',
    'map' => 'https://nginx.org',
    'crawl' => 'https://quotes.toscrape.com',
];

function cmdScrape(WebpipeClient $client, string $url): void
{
    echo "→ Scraping {$url} ...\n";
    $doc = $client->scrape($url, formats: ['markdown', 'metadata', 'links']);
    echo '  title:  ', $doc->metadata['title'] ?? '', "\n";
    echo '  status: ', $doc->metadata['statusCode'] ?? '', "\n";
    echo '  links:  ', count($doc->links ?? []), "\n";
    echo "--- markdown (first 400 chars) ---\n";
    echo substr($doc->markdown ?? '', 0, 400), "\n";
}

function cmdMap(WebpipeClient $client, string $url): void
{
    echo "→ Mapping {$url} ...\n";
    $links = $client->map($url, limit: 50);
    echo '  found ', count($links), " URLs (limit 50):\n";
    foreach (array_slice($links, 0, 10) as $link) {
        echo '  - ', $link->url, "\n";
    }
    if (count($links) > 10) {
        echo '  ... and ', count($links) - 10, " more\n";
    }
}

function cmdCrawl(WebpipeClient $client, string $url): void
{
    echo "→ Crawling {$url} (limit 5, polling until done) ...\n";
    // PHP has no mainstream async runtime — the onProgress callback is the
    // idiomatic way to observe a long crawl (progress bars, logging).
    $job = $client->crawl(
        $url,
        limit: 5,
        scrapeOptions: ['formats' => ['markdown', 'metadata']],
        timeout: 300,
        onProgress: function ($status): void {
            echo "\r  progress: {$status->completed}/{$status->total} pages scraped ...";
        },
    );
    echo "\r  done:     {$job->completed}/{$job->total} pages          \n";
    foreach ($job->data as $page) {
        echo '  - ', $page->metadata['sourceURL'] ?? '?', ' | ', $page->metadata['title'] ?? '', "\n";
    }
}

function cmdBrowser(WebpipeClient $client): void
{
    $url = 'https://quotes.toscrape.com/login';
    echo "→ Browser login demo on {$url} (any credentials work) ...\n";
    $session = $client->browser->create(url: $url);
    try {
        $session->waitUntilRunning(timeout: 90);
        echo "  session running, filling the login form ...\n";
        $session->fill('input[name=username]', 'demo');
        $session->fill('input[name=password]', 'demo');
        $session->click('input[type=submit]', waitAfter: 2000);
        $doc = $session->scrape(formats: ['markdown']);
        $ok = str_contains($doc->markdown ?? '', 'Logout');
        echo $ok ? "  login succeeded ✓ (Logout link found)\n" : "  login — check output below\n";
        echo "--- markdown after login (first 300 chars) ---\n";
        echo substr($doc->markdown ?? '', 0, 300), "\n";
    } finally {
        $session->close();
    }
    echo "  session closed.\n";
}

// Parse args: positionals = <command> [url], plus --api-key <key>
$positionals = [];
$apiKeyFlag = null;
for ($i = 1; $i < count($argv); $i++) {
    if ($argv[$i] === '--api-key' && $i + 1 < count($argv)) {
        $apiKeyFlag = $argv[++$i];
    } else {
        $positionals[] = $argv[$i];
    }
}
$command = $positionals[0] ?? null;
if ($command === null || (!isset(DEFAULT_URLS[$command]) && $command !== 'browser')) {
    fwrite(STDERR, "usage: php demo.php <scrape|map|crawl|browser> [url] [--api-key wc-...]\n");
    exit(1);
}

try {
    $client = new WebpipeClient(apiKey: resolveApiKey($apiKeyFlag));
    if ($command === 'browser') {
        cmdBrowser($client);
    } else {
        $url = $positionals[1] ?? DEFAULT_URLS[$command];
        match ($command) {
            'scrape' => cmdScrape($client, $url),
            'map' => cmdMap($client, $url),
            'crawl' => cmdCrawl($client, $url),
        };
    }
} catch (WebpipeException | \InvalidArgumentException $e) {
    fwrite(STDERR, 'error: ' . $e->getMessage() . "\n");
    exit(1);
}
