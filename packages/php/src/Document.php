<?php

declare(strict_types=1);

namespace Webpipe;

/**
 * Result of scraping a single page (scrape / browser / crawl data item).
 */
final class Document
{
    /**
     * @param list<Link>|null $links
     * @param array<string, mixed>|null $metadata Page metadata (title, description,
     *                                            sourceURL, statusCode, og:*, ...)
     */
    public function __construct(
        public readonly ?string $markdown = null,
        public readonly ?string $html = null,
        public readonly ?string $rawHtml = null,
        public readonly ?array $links = null,
        public readonly ?array $metadata = null,
        public readonly mixed $json = null,
    ) {
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data): self
    {
        $links = null;
        if (isset($data['links']) && is_array($data['links'])) {
            $links = array_map(
                fn (array $l): Link => Link::fromArray($l),
                array_values(array_filter($data['links'], 'is_array')),
            );
        }

        return new self(
            markdown: $data['markdown'] ?? null,
            html: $data['html'] ?? null,
            rawHtml: $data['rawHtml'] ?? null,
            links: $links,
            metadata: $data['metadata'] ?? null,
            json: $data['json'] ?? null,
        );
    }
}
