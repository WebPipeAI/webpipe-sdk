<?php

declare(strict_types=1);

namespace Webpipe;

use Webpipe\Internal\HttpClient;

/** Factory for browser sessions, exposed as $client->browser. */
final class BrowserResource
{
    public function __construct(private HttpClient $http)
    {
    }

    /**
     * Create a session. It starts asynchronously — call waitUntilRunning()
     * before sending commands for a controlled experience.
     */
    public function create(
        ?string $url = null,
        ?string $cookie = null,
        ?string $proxy = null,
        ?string $sessionName = null,
    ): BrowserSession {
        $body = array_filter(
            ['url' => $url, 'cookie' => $cookie, 'proxy' => $proxy, 'session_name' => $sessionName],
            static fn ($v): bool => $v !== null,
        );
        $data = $this->http->post('/api/v1/browser', $body);
        return new BrowserSession(
            $this->http,
            id: (string) ($data['id'] ?? ''),
            status: (string) ($data['status'] ?? 'starting'),
            url: $data['url'] ?? null,
            sessionName: $data['session_name'] ?? null,
            expiresAt: $data['expires_at'] ?? null,
        );
    }
}
