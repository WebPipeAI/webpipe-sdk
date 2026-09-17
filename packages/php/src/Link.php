<?php

declare(strict_types=1);

namespace Webpipe;

/** A link extracted from a page. */
final class Link
{
    public function __construct(
        public readonly ?string $text = null,
        public readonly ?string $href = null,
    ) {
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data): self
    {
        return new self(text: $data['text'] ?? null, href: $data['href'] ?? null);
    }
}
