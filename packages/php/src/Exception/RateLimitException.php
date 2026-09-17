<?php

declare(strict_types=1);

namespace Webpipe\Exception;

/** 429 — rate limit exceeded, or too many concurrent browser sessions. */
class RateLimitException extends ApiException
{
}
