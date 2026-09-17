<?php

declare(strict_types=1);

namespace Webpipe\Tests;

use GuzzleHttp\Client as GuzzleClient;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Middleware;
use GuzzleHttp\Psr7\Response;
use PHPUnit\Framework\TestCase;
use Webpipe\CrawlJob;
use Webpipe\Exception\AuthenticationException;
use Webpipe\Exception\CrawlException;
use Webpipe\Exception\NotFoundException;
use Webpipe\Exception\PollTimeoutException;
use Webpipe\Internal\HttpClient;
use Webpipe\WebpipeClient;

final class WebpipeClientTest extends TestCase
{
    /** @var list<array{request: mixed, response: mixed}> */
    private array $history = [];

    private function mockClient(array $responses, int $maxRetries = 0): WebpipeClient
    {
        $mock = new MockHandler($responses);
        $stack = HandlerStack::create($mock);
        $stack->push(Middleware::history($this->history));
        $guzzle = new GuzzleClient(['handler' => $stack, 'http_errors' => false]);
        $http = HttpClient::create(
            'https://api.web2json.ai',
            'wc-test-key',
            60.0,
            $maxRetries,
            'webpipe-sdk-php/' . WebpipeClient::VERSION,
            $guzzle,
        );
        return new WebpipeClient(apiKey: 'wc-test-key', httpClient: $http);
    }

    private static function json(array $body, int $status = 200): Response
    {
        return new Response($status, ['Content-Type' => 'application/json'], (string) json_encode($body));
    }

    public function testMissingApiKeyThrows(): void
    {
        putenv('WEBPIPE_API_KEY');
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessageMatches('/WEBPIPE_API_KEY/');
        new WebpipeClient();
    }

    public function testScrapeSuccess(): void
    {
        $client = $this->mockClient([
            self::json([
                'success' => true,
                'data' => [
                    'markdown' => '# Example',
                    'metadata' => ['title' => 'Example Domain', 'sourceURL' => 'https://example.com', 'statusCode' => 200],
                ],
            ]),
        ]);

        $doc = $client->scrape('https://example.com', formats: ['markdown', 'metadata']);

        self::assertSame('# Example', $doc->markdown);
        self::assertSame('Example Domain', $doc->metadata['title']);
        self::assertSame(200, $doc->metadata['statusCode']);

        $request = $this->history[0]['request'];
        self::assertSame('Bearer wc-test-key', $request->getHeaderLine('Authorization'));
        self::assertStringStartsWith('webpipe-sdk-php/', $request->getHeaderLine('User-Agent'));
        $body = json_decode((string) $request->getBody(), true);
        self::assertSame(['url' => 'https://example.com', 'formats' => ['markdown', 'metadata']], $body);
    }

    public function testScrape401(): void
    {
        $client = $this->mockClient([
            self::json(['success' => false, 'error' => 'invalid key'], 401),
        ]);
        try {
            $client->scrape('https://example.com');
            self::fail('expected AuthenticationException');
        } catch (AuthenticationException $e) {
            self::assertSame(401, $e->statusCode);
            self::assertStringContainsString('invalid key', $e->getMessage());
        }
    }

    public function testScrapeRetriesOn429(): void
    {
        $client = $this->mockClient([
            self::json(['success' => false, 'error' => 'slow down'], 429),
            self::json(['success' => true, 'data' => ['markdown' => 'ok']]),
        ], maxRetries: 1);

        $doc = $client->scrape('https://example.com');
        self::assertSame('ok', $doc->markdown);
        self::assertCount(2, $this->history);
    }

    public function testMapReturnsLinks(): void
    {
        $client = $this->mockClient([
            self::json([
                'success' => true,
                'links' => [
                    ['url' => 'https://example.com/about', 'title' => 'About'],
                    ['url' => 'https://example.com/blog'],
                ],
            ]),
        ]);
        $links = $client->map('https://example.com', limit: 100, sameDomain: false);
        self::assertCount(2, $links);
        self::assertSame('https://example.com/about', $links[0]->url);

        $body = json_decode((string) $this->history[0]['request']->getBody(), true);
        self::assertFalse($body['same_domain']);
        self::assertSame(100, $body['limit']);
    }

    public function testCrawlPollsUntilCompleted(): void
    {
        $client = $this->mockClient([
            self::json(['success' => true, 'id' => 'job-1']),
            self::json(['success' => true, 'status' => 'scraping', 'total' => 2, 'completed' => 1]),
            self::json([
                'success' => true, 'status' => 'completed', 'total' => 2, 'completed' => 2,
                'data' => [['markdown' => '# A'], ['markdown' => '# B']],
                'errors' => [],
                'expires_at' => '2026-07-28T00:00:00+00:00',
            ]),
        ]);

        $job = $client->crawl('https://x', limit: 2, pollInterval: 0.01);

        self::assertSame(CrawlJob::STATUS_COMPLETED, $job->status);
        self::assertCount(2, $job->data);
        self::assertSame('# B', $job->data[1]->markdown);
        self::assertSame('2026-07-28T00:00:00+00:00', $job->expiresAt);
    }

    public function testCrawlFailed(): void
    {
        $client = $this->mockClient([
            self::json(['success' => true, 'id' => 'job-2']),
            self::json(['success' => true, 'status' => 'failed', 'errors' => ['boom']]),
        ]);
        $this->expectException(CrawlException::class);
        $client->crawl('https://x', pollInterval: 0.01);
    }

    public function testCrawlTimeout(): void
    {
        // first response is the submit; subsequent GETs always "scraping"
        $mock = new MockHandler(array_merge(
            [self::json(['success' => true, 'id' => 'job-3'])],
            array_fill(0, 50, self::json(['success' => true, 'status' => 'scraping'])),
        ));
        $stack = HandlerStack::create($mock);
        $guzzle = new GuzzleClient(['handler' => $stack, 'http_errors' => false]);
        $http = HttpClient::create(
            'https://api.web2json.ai',
            'wc-test-key',
            60.0,
            0,
            'webpipe-sdk-php/' . WebpipeClient::VERSION,
            $guzzle,
        );
        $client = new WebpipeClient(apiKey: 'wc-test-key', httpClient: $http);

        $this->expectException(PollTimeoutException::class);
        $client->crawl('https://x', pollInterval: 0.01, timeout: 0.05);
    }

    public function testGetCrawlStatus404(): void
    {
        $client = $this->mockClient([
            self::json(['success' => false, 'error' => 'gone'], 404),
        ]);
        $this->expectException(NotFoundException::class);
        $client->getCrawlStatus('gone');
    }

    public function testCrawlProgressCallback(): void
    {
        $client = $this->mockClient([
            self::json(['success' => true, 'id' => 'job-9']),
            self::json(['success' => true, 'status' => 'scraping', 'total' => 3, 'completed' => 1]),
            self::json(['success' => true, 'status' => 'scraping', 'total' => 3, 'completed' => 2]),
            self::json(['success' => true, 'status' => 'completed', 'total' => 3, 'completed' => 3, 'data' => [], 'errors' => []]),
        ]);

        $progress = [];
        $job = $client->crawl(
            'https://x',
            limit: 3,
            pollInterval: 0.01,
            onProgress: function (CrawlJob $status) use (&$progress): void {
                $progress[] = "{$status->completed}/{$status->total}";
            },
        );

        self::assertSame(CrawlJob::STATUS_COMPLETED, $job->status);
        self::assertSame(['1/3', '2/3'], $progress);
    }
}
