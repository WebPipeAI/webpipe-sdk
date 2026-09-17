<?php

declare(strict_types=1);

namespace Webpipe;

/** A URL discovered by the Map endpoint. */
final class MapLink
{
    public function __construct(
        public readonly string $url,
        public readonly ?string $title = null,
        public readonly ?string $description = null,
    ) {
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data): self
    {
        return new self(
            url: (string) ($data['url'] ?? ''),
            title: $data['title'] ?? null,
            description: $data['description'] ?? null,
        );
    }
}
