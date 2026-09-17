<?php

declare(strict_types=1);

namespace Webpipe;

/** Handle returned when a crawl job is submitted. */
final class CrawlJobStart
{
    public function __construct(
        public readonly string $id,
        public readonly ?string $url = null,
    ) {
    }
}
