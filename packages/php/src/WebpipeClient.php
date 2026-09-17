<?php

declare(strict_types=1);

namespace Webpipe;

use Webpipe\Exception\CrawlException;
use Webpipe\Exception\PollTimeoutException;
use Webpipe\Internal\HttpClient;

/**
 * The WebPipe API client.
 *
 * $client = new WebpipeClient(); // reads WEBPIPE_API_KEY
 * $doc = $client->scrape('https://example.com', formats: ['markdown']);
 * echo $doc->markdown;
 */
final class WebpipeClient
{
    public const VERSION = '0.1.0';
    public const DEFAULT_BASE_URL = 'https://api.web2json.ai';

    private HttpClient $http;
    public readonly BrowserResource $browser;

    public function __construct(
        ?string $apiKey = null,
        ?string $baseUrl = null,
        float $timeout = 60.0,
        int $maxRetries = 2,
        ?HttpClient $httpClient = null,
    ) {
        $apiKey ??= getenv('WEBPIPE_API_KEY') ?: null;
        if ($apiKey === null || $apiKey === '') {
            throw new \InvalidArgumentException(
                'Missing API key. Pass $apiKey or set the WEBPIPE_API_KEY environment '
                . 'variable. Create one at https://webpipe.ai/api-keys.html'
            );
        }
        $baseUrl ??= getenv('WEBPIPE_API_URL') ?: self::DEFAULT_BASE_URL;
        $this->http = $httpClient ?? HttpClient::create(
            rtrim($baseUrl, '/'),
            $apiKey,
            $timeout,
            $maxRetries,
            'webpipe-sdk-php/' . self::VERSION,
        );
        $this->browser = new BrowserResource($this->http);
    }

    // ----------------------------------------------------------------- Scrape

    /** Scrape a single page into markdown/html/links/metadata/json. */
    public function scrape(
        string $url,
        ?array $formats = null,
        ?array $jsonSchema = null,
        ?bool $useProxy = null,
        ?string $proxy = null,
        ?string $cookie = null,
    ): Document {
        $data = $this->http->post('/api/v1/scrape', self::dropNull([
            'url' => $url,
            'formats' => $formats,
            'json_schema' => $jsonSchema,
            'use_proxy' => $useProxy,
            'proxy' => $proxy,
            'cookie' => $cookie,
        ]));
        return Document::fromArray($data['data'] ?? []);
    }

    // -------------------------------------------------------------------- Map

    /**
     * Discover URLs of a website (robots.txt → sitemap → in-page links).
     *
     * @return list<MapLink>
     */
    public function map(
        string $url,
        ?int $limit = null,
        ?string $search = null,
        ?string $sitemap = null,
        ?bool $sameDomain = null,
        ?bool $useProxy = null,
        ?string $proxy = null,
        ?string $cookie = null,
    ): array {
        $data = $this->http->post('/api/v1/map', self::dropNull([
            'url' => $url,
            'limit' => $limit,
            'search' => $search,
            'sitemap' => $sitemap,
            'same_domain' => $sameDomain,
            'use_proxy' => $useProxy,
            'proxy' => $proxy,
            'cookie' => $cookie,
        ]));
        return array_values(array_map(
            fn (array $l): MapLink => MapLink::fromArray($l),
            array_filter($data['links'] ?? [], 'is_array'),
        ));
    }

    // ------------------------------------------------------------------ Crawl

    /**
     * Submit an async crawl job and return its handle immediately.
     *
     * @param list<string>|null $includePaths Regex whitelist for URL paths
     * @param list<string>|null $excludePaths Regex blacklist for URL paths
     * @param array<string, mixed>|null $scrapeOptions Per-page scrape options
     *        (formats, json_schema, ...; sent through untouched)
     */
    public function startCrawl(
        string $url,
        ?int $limit = null,
        ?int $maxDiscoveryDepth = null,
        ?array $includePaths = null,
        ?array $excludePaths = null,
        ?bool $allowSubdomains = null,
        ?bool $allowExternalLinks = null,
        ?bool $crawlEntireDomain = null,
        ?bool $ignoreQueryParams = null,
        ?string $sitemap = null,
        ?float $delay = null,
        ?int $maxConcurrency = null,
        ?array $scrapeOptions = null,
        ?bool $useProxy = null,
        ?string $proxy = null,
        ?string $cookie = null,
    ): CrawlJobStart {
        $data = $this->http->post('/api/v1/crawl', self::dropNull([
            'url' => $url,
            'limit' => $limit,
            'max_discovery_depth' => $maxDiscoveryDepth,
            'include_paths' => $includePaths,
            'exclude_paths' => $excludePaths,
            'allow_subdomains' => $allowSubdomains,
            'allow_external_links' => $allowExternalLinks,
            'crawl_entire_domain' => $crawlEntireDomain,
            'ignore_query_params' => $ignoreQueryParams,
            'sitemap' => $sitemap,
            'delay' => $delay,
            'max_concurrency' => $maxConcurrency,
            'scrape_options' => $scrapeOptions,
            'use_proxy' => $useProxy,
            'proxy' => $proxy,
            'cookie' => $cookie,
        ]));
        return new CrawlJobStart(id: (string) ($data['id'] ?? ''), url: $data['url'] ?? null);
    }

    /** Fetch the current status and partial results of a crawl job. */
    public function getCrawlStatus(string $jobId): CrawlJob
    {
        return CrawlJob::fromArray($this->http->get("/api/v1/crawl/{$jobId}"));
    }

    /**
     * Submit a crawl job and poll until it completes.
     *
     * Accepts the same named arguments as startCrawl(), plus polling controls.
     *
     * @param callable(CrawlJob): void|null $onProgress Called after each poll
     *        with the current job state — use it for progress bars/logging.
     *        This is the idiomatic way to observe a long crawl in PHP
     *        (which has no mainstream async runtime).
     *
     * @throws CrawlException If the job fails
     * @throws PollTimeoutException If $timeout (seconds) is exceeded
     */
    public function crawl(
        string $url,
        float $pollInterval = 2.0,
        ?float $timeout = null,
        ?callable $onProgress = null,
        mixed ...$options,
    ): CrawlJob {
        $job = $this->startCrawl($url, ...$options);
        $deadline = $timeout === null ? null : microtime(true) + $timeout;
        while (true) {
            $status = $this->getCrawlStatus($job->id);
            if ($status->status === CrawlJob::STATUS_COMPLETED) {
                return $status;
            }
            if ($status->status === CrawlJob::STATUS_FAILED) {
                throw new CrawlException(
                    "Crawl job {$job->id} failed: " . json_encode($status->errors)
                );
            }
            if ($onProgress !== null) {
                $onProgress($status);
            }
            if ($deadline !== null && microtime(true) >= $deadline) {
                throw new PollTimeoutException(
                    "Crawl job {$job->id} did not complete within {$timeout}s"
                );
            }
            usleep((int) ($pollInterval * 1_000_000));
        }
    }

    /** @return array<string, mixed> */
    private static function dropNull(array $map): array
    {
        return array_filter($map, static fn ($v): bool => $v !== null);
    }
}
