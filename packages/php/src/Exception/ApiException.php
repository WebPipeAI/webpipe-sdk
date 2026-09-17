<?php

declare(strict_types=1);

namespace Webpipe\Exception;

/**
 * An error response returned by the WebPipe API. Always carries the server's
 * original error message and the HTTP status code.
 */
class ApiException extends WebpipeException
{
    public function __construct(
        string $message,
        public readonly int $statusCode,
        public readonly mixed $body = null,
    ) {
        parent::__construct("[{$statusCode}] {$message}", $statusCode);
    }

    /** Map an HTTP status code to the most specific exception subclass. */
    public static function forStatus(int $statusCode, string $message, mixed $body = null): self
    {
        return match (true) {
            $statusCode === 400 => new BadRequestException($message, $statusCode, $body),
            $statusCode === 401 => new AuthenticationException($message, $statusCode, $body),
            $statusCode === 403 => new ForbiddenException($message, $statusCode, $body),
            $statusCode === 404 => new NotFoundException($message, $statusCode, $body),
            $statusCode === 429 => new RateLimitException($message, $statusCode, $body),
            $statusCode >= 500 => new ServerException($message, $statusCode, $body),
            default => new self($message, $statusCode, $body),
        };
    }
}
