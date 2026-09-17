<?php

declare(strict_types=1);

namespace Webpipe\Internal;

use GuzzleHttp\Client as GuzzleClient;
use GuzzleHttp\Exception\ConnectException;
use Webpipe\Exception\ApiException;

/**
 * Thin Guzzle wrapper: auth headers, retries with backoff, error mapping.
 * @internal Never use from outside the SDK.
 */
final class HttpClient
{
    private const RETRYABLE_STATUSES = [429, 500, 502, 503, 504];

    /**
     * @param array<string, string> $headers Default headers applied to every request.
     */
    public function __construct(
        private GuzzleClient $client,
        private int $maxRetries,
        private array $headers = [],
    ) {
    }

    public static function create(
        string $baseUrl,
        string $apiKey,
        float $timeout,
        int $maxRetries,
        string $userAgent,
        ?GuzzleClient $guzzle = null,
    ): self {
        $headers = [
            'Authorization' => "Bearer {$apiKey}",
            'Content-Type' => 'application/json',
            'User-Agent' => $userAgent,
        ];
        return new self(
            $guzzle ?? new GuzzleClient([
                'base_uri' => $baseUrl,
                'timeout' => $timeout,
                'http_errors' => false,
            ]),
            $maxRetries,
            $headers,
        );
    }

    /**
     * @param array<string, mixed>|null $body
     * @return array<string, mixed>
     */
    public function request(string $method, string $path, ?array $body = null): array
    {
        $attempt = 0;
        while (true) {
            try {
                $options = ['headers' => $this->headers];
                if ($body !== null) {
                    $options['json'] = $body;
                }
                $response = $this->client->request($method, $path, $options);
            } catch (ConnectException $e) {
                if ($attempt >= $this->maxRetries) {
                    throw $e;
                }
                $attempt++;
                $this->sleep($this->backoffSeconds(null, $attempt));
                continue;
            }

            $status = $response->getStatusCode();
            if (in_array($status, self::RETRYABLE_STATUSES, true) && $attempt < $this->maxRetries) {
                $attempt++;
                $this->sleep($this->backoffSeconds($response->getHeaderLine('Retry-After'), $attempt));
                continue;
            }

            $raw = (string) $response->getBody();
            $decoded = $raw === '' ? null : json_decode($raw, true);

            if ($status >= 400) {
                $message = is_array($decoded) && isset($decoded['error'])
                    ? (string) $decoded['error']
                    : $response->getReasonPhrase();
                throw ApiException::forStatus($status, $message, $decoded);
            }

            return is_array($decoded) ? $decoded : [];
        }
    }

    /** @param array<string, mixed>|null $body */
    public function get(string $path): array
    {
        return $this->request('GET', $path);
    }

    /** @param array<string, mixed>|null $body */
    public function post(string $path, ?array $body = null): array
    {
        return $this->request('POST', $path, $body);
    }

    public function delete(string $path): array
    {
        return $this->request('DELETE', $path);
    }

    private function backoffSeconds(?string $retryAfter, int $attempt): float
    {
        if ($retryAfter !== null && $retryAfter !== '' && is_numeric($retryAfter)) {
            return max(0.0, (float) $retryAfter);
        }
        return 0.5 * (2 ** $attempt);
    }

    private function sleep(float $seconds): void
    {
        usleep((int) ($seconds * 1_000_000));
    }
}
