<?php

declare(strict_types=1);

namespace Webpipe;

use Webpipe\Exception\PollTimeoutException;
use Webpipe\Exception\WebpipeException;
use Webpipe\Internal\HttpClient;

/**
 * Handle to a persistent headless Chromium session on the server.
 *
 * Server-side facts: sessions expire after 30 minutes idle; at most 2
 * concurrent running sessions per user. Always close() when done.
 */
final class BrowserSession
{
    public function __construct(
        private HttpClient $http,
        public readonly string $id,
        public string $status,
        public ?string $url = null,
        public ?string $sessionName = null,
        public ?string $expiresAt = null,
    ) {
    }

    private function base(): string
    {
        return "/api/v1/browser/{$this->id}";
    }

    /** Fetch the latest session state from the server. */
    public function refresh(): self
    {
        $data = $this->http->get($this->base());
        $this->status = (string) ($data['status'] ?? $this->status);
        $this->url = $data['url'] ?? $this->url;
        $this->sessionName = $data['session_name'] ?? $this->sessionName;
        $this->expiresAt = $data['expires_at'] ?? $this->expiresAt;
        return $this;
    }

    /** Block until the session is ready to accept commands. */
    public function waitUntilRunning(float $timeout = 60.0, float $pollInterval = 1.0): self
    {
        $deadline = microtime(true) + $timeout;
        while (true) {
            $this->refresh();
            if ($this->status === 'running') {
                return $this;
            }
            if (in_array($this->status, ['stopped', 'error', 'expired'], true)) {
                throw new WebpipeException(
                    "Browser session {$this->id} entered terminal status '{$this->status}'"
                );
            }
            if (microtime(true) >= $deadline) {
                throw new PollTimeoutException(
                    "Browser session {$this->id} not running after {$timeout}s"
                );
            }
            usleep((int) ($pollInterval * 1_000_000));
        }
    }

    /** Stop the session and release server resources. */
    public function close(): void
    {
        $this->http->delete($this->base());
        $this->status = 'stopped';
    }

    /** Navigate to a new URL (with anti-bot handling) and scrape it. */
    public function navigate(
        string $url,
        ?array $formats = null,
        ?bool $scrollToBottom = null,
        ?int $maxScrolls = null,
        ?int $scrollWait = null,
        ?int $scrollStep = null,
    ): Document {
        $body = ['url' => $url] + self::scrapeBody($formats, $scrollToBottom, $maxScrolls, $scrollWait, $scrollStep);
        $data = $this->http->post($this->base() . '/navigate', $body);
        return Document::fromArray($data['data'] ?? []);
    }

    /** Scrape the current page without navigating (faster than navigate). */
    public function scrape(
        ?array $formats = null,
        ?bool $scrollToBottom = null,
        ?int $maxScrolls = null,
        ?int $scrollWait = null,
        ?int $scrollStep = null,
    ): Document {
        $body = self::scrapeBody($formats, $scrollToBottom, $maxScrolls, $scrollWait, $scrollStep);
        $data = $this->http->post($this->base() . '/scrape', $body);
        return Document::fromArray($data['data'] ?? []);
    }

    /** Run a raw browser action. Returns the raw API payload. */
    public function action(string $type, array $params = []): array
    {
        return $this->http->post($this->base() . '/action', ['type' => $type] + $params);
    }

    public function click(string $selector, ?int $waitAfter = null): array
    {
        return $this->action('click', self::dropNull(['selector' => $selector, 'wait_after' => $waitAfter]));
    }

    public function fill(string $selector, string $value): array
    {
        return $this->action('fill', ['selector' => $selector, 'value' => $value]);
    }

    public function select(string $selector, string $value): array
    {
        return $this->action('select', ['selector' => $selector, 'value' => $value]);
    }

    public function press(string $key, ?string $selector = null, ?int $waitAfter = null): array
    {
        return $this->action('press', self::dropNull([
            'key' => $key, 'selector' => $selector, 'wait_after' => $waitAfter,
        ]));
    }

    public function scroll(string $direction = 'down', ?int $amount = null): array
    {
        return $this->action('scroll', self::dropNull(['direction' => $direction, 'amount' => $amount]));
    }

    public function wait(int $ms = 1000): array
    {
        return $this->action('wait', ['ms' => $ms]);
    }

    public function waitFor(string $selector, ?int $timeout = null): array
    {
        return $this->action('wait_for', self::dropNull(['selector' => $selector, 'timeout' => $timeout]));
    }

    /** Take a screenshot. The API returns a base64 PNG inside the payload. */
    public function screenshot(bool $fullPage = false): array
    {
        return $this->action('screenshot', ['full_page' => $fullPage]);
    }

    /** Evaluate a JavaScript expression in the page (value in payload's "result"). */
    public function evaluate(string $expression): array
    {
        return $this->action('evaluate', ['expression' => $expression]);
    }

    public function hover(string $selector): array
    {
        return $this->action('hover', ['selector' => $selector]);
    }

    public function check(string $selector): array
    {
        return $this->action('check', ['selector' => $selector]);
    }

    /** @return list<array{name:string, value:string, domain?:string, path?:string}> */
    public function getCookies(): array
    {
        $data = $this->http->get($this->base() . '/cookies');
        return $data['cookies'] ?? $data['data'] ?? [];
    }

    /** @param list<array{name:string, value:string, domain?:string, path?:string}> $cookies */
    public function setCookies(array $cookies): array
    {
        return $this->http->post($this->base() . '/cookies', ['op' => 'set', 'cookies' => $cookies]);
    }

    public function clearCookies(): array
    {
        return $this->http->post($this->base() . '/cookies', ['op' => 'clear']);
    }

    /** Persist cookies/storage under this session's sessionName. */
    public function saveSession(): array
    {
        return $this->http->post($this->base() . '/save_session');
    }

    /** @return array<string, mixed> */
    private static function scrapeBody(
        ?array $formats,
        ?bool $scrollToBottom,
        ?int $maxScrolls,
        ?int $scrollWait,
        ?int $scrollStep,
    ): array {
        return self::dropNull([
            'formats' => $formats,
            'scroll_to_bottom' => $scrollToBottom,
            'max_scrolls' => $maxScrolls,
            'scroll_wait' => $scrollWait,
            'scroll_step' => $scrollStep,
        ]);
    }

    /** @return array<string, mixed> */
    private static function dropNull(array $map): array
    {
        return array_filter($map, static fn ($v): bool => $v !== null);
    }
}
