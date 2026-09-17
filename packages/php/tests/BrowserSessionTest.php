<?php

declare(strict_types=1);

namespace Webpipe\Tests;

use GuzzleHttp\Client as GuzzleClient;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Middleware;
use GuzzleHttp\Psr7\Response;
use PHPUnit\Framework\TestCase;
use Webpipe\Internal\HttpClient;
use Webpipe\WebpipeClient;

final class BrowserSessionTest extends TestCase
{
    private array $history = [];

    private function mockClient(array $responses): WebpipeClient
    {
        $mock = new MockHandler($responses);
        $stack = HandlerStack::create($mock);
        $stack->push(Middleware::history($this->history));
        $guzzle = new GuzzleClient(['handler' => $stack, 'http_errors' => false]);
        $http = HttpClient::create(
            'https://api.web2json.ai',
            'wc-test-key',
            60.0,
            0,
            'webpipe-sdk-php/' . WebpipeClient::VERSION,
            $guzzle,
        );
        return new WebpipeClient(apiKey: 'wc-test-key', httpClient: $http);
    }

    private static function json(array $body, int $status = 200): Response
    {
        return new Response($status, ['Content-Type' => 'application/json'], (string) json_encode($body));
    }

    public function testSessionLifecycle(): void
    {
        $client = $this->mockClient([
            // create
            self::json(['success' => true, 'id' => 'sess-1', 'status' => 'starting']),
            // refresh: starting then running
            self::json(['success' => true, 'id' => 'sess-1', 'status' => 'starting']),
            self::json(['success' => true, 'id' => 'sess-1', 'status' => 'running']),
            // scrape current page
            self::json(['success' => true, 'data' => ['markdown' => '# Hi']]),
            // action
            self::json(['success' => true, 'ok' => true]),
            // delete
            self::json(['success' => true]),
        ]);

        $session = $client->browser->create(url: 'https://example.com', sessionName: 'demo');
        self::assertSame('sess-1', $session->id);
        self::assertSame('starting', $session->status);

        $createBody = json_decode((string) $this->history[0]['request']->getBody(), true);
        self::assertSame('demo', $createBody['session_name']);

        $session->waitUntilRunning(timeout: 5, pollInterval: 0.01);
        self::assertSame('running', $session->status);

        $doc = $session->scrape(formats: ['markdown']);
        self::assertSame('# Hi', $doc->markdown);

        $session->click('button.more', waitAfter: 500);
        $actionBody = json_decode((string) $this->history[4]['request']->getBody(), true);
        self::assertSame(
            ['type' => 'click', 'selector' => 'button.more', 'wait_after' => 500],
            $actionBody,
        );

        $session->close();
        self::assertSame('stopped', $session->status);
        self::assertSame('DELETE', $this->history[5]['request']->getMethod());
    }

    public function testCookies(): void
    {
        $client = $this->mockClient([
            self::json(['success' => true, 'id' => 'sess-2', 'status' => 'running']),
            self::json(['success' => true, 'cookies' => [['name' => 'session', 'value' => 'abc']]]),
            self::json(['success' => true]),
            self::json(['success' => true]),
        ]);

        $session = $client->browser->create();
        $cookies = $session->getCookies();
        self::assertSame('session', $cookies[0]['name']);

        $session->setCookies([['name' => 'x', 'value' => '1', 'domain' => 'example.com']]);
        self::assertSame('set', json_decode((string) $this->history[2]['request']->getBody(), true)['op']);

        $session->clearCookies();
        self::assertSame(['op' => 'clear'], json_decode((string) $this->history[3]['request']->getBody(), true));
    }
}
