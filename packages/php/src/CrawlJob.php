<?php

declare(strict_types=1);

namespace Webpipe;

/**
 * Status and (partial or final) results of a crawl job.
 * Results expire 24h after completion (see $expiresAt).
 */
final class CrawlJob
{
    public const STATUS_SCRAPING = 'scraping';
    public const STATUS_COMPLETED = 'completed';
    public const STATUS_FAILED = 'failed';

    /**
     * @param list<Document> $data
     * @param list<mixed> $errors
     */
    public function __construct(
        public readonly string $status,
        public readonly int $total = 0,
        public readonly int $completed = 0,
        public readonly array $data = [],
        public readonly array $errors = [],
        public readonly ?string $expiresAt = null,
    ) {
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data): self
    {
        $docs = [];
        foreach (($data['data'] ?? []) as $item) {
            if (is_array($item)) {
                $docs[] = Document::fromArray($item);
            }
        }

        return new self(
            status: (string) ($data['status'] ?? ''),
            total: (int) ($data['total'] ?? 0),
            completed: (int) ($data['completed'] ?? 0),
            data: $docs,
            errors: is_array($data['errors'] ?? null) ? $data['errors'] : [],
            expiresAt: $data['expires_at'] ?? null,
        );
    }
}
