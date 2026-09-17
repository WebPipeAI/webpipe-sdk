namespace Webpipe.Sdk;

/// <summary>Base class for all webpipe-sdk exceptions.</summary>
public class WebpipeException : Exception
{
    public WebpipeException(string message) : base(message) { }
    public WebpipeException(string message, Exception inner) : base(message, inner) { }
}

/// <summary>
/// An error response returned by the WebPipe API. Always carries the server's
/// original error message and the HTTP status code.
/// </summary>
public class ApiException : WebpipeException
{
    public int StatusCode { get; }
    public object? Body { get; }

    public ApiException(string message, int statusCode, object? body = null)
        : base($"[{statusCode}] {message}")
    {
        StatusCode = statusCode;
        Body = body;
    }

    /// <summary>Map an HTTP status code to the most specific exception subclass.</summary>
    public static ApiException ForStatus(int statusCode, string message, object? body = null) =>
        statusCode switch
        {
            400 => new BadRequestException(message, statusCode, body),
            401 => new AuthenticationException(message, statusCode, body),
            403 => new ForbiddenException(message, statusCode, body),
            404 => new NotFoundException(message, statusCode, body),
            429 => new RateLimitException(message, statusCode, body),
            >= 500 => new ServerException(message, statusCode, body),
            _ => new ApiException(message, statusCode, body),
        };
}

/// <summary>400 — invalid parameters (missing url, bad format, ...).</summary>
public class BadRequestException : ApiException
{
    public BadRequestException(string message, int statusCode, object? body = null) : base(message, statusCode, body) { }
}

/// <summary>401 — API key missing, invalid or deleted.</summary>
public class AuthenticationException : ApiException
{
    public AuthenticationException(string message, int statusCode, object? body = null) : base(message, statusCode, body) { }
}

/// <summary>403 — accessing a resource owned by another user.</summary>
public class ForbiddenException : ApiException
{
    public ForbiddenException(string message, int statusCode, object? body = null) : base(message, statusCode, body) { }
}

/// <summary>404 — job/session does not exist or has expired.</summary>
public class NotFoundException : ApiException
{
    public NotFoundException(string message, int statusCode, object? body = null) : base(message, statusCode, body) { }
}

/// <summary>429 — rate limit exceeded, or too many concurrent browser sessions.</summary>
public class RateLimitException : ApiException
{
    public RateLimitException(string message, int statusCode, object? body = null) : base(message, statusCode, body) { }
}

/// <summary>5xx — scrape timeout, target site unreachable, ...</summary>
public class ServerException : ApiException
{
    public ServerException(string message, int statusCode, object? body = null) : base(message, statusCode, body) { }
}

/// <summary>A crawl job reached status "failed".</summary>
public class CrawlException : WebpipeException
{
    public CrawlException(string message) : base(message) { }
}

/// <summary>Polling did not finish within the allotted timeout.</summary>
public class PollTimeoutException : WebpipeException
{
    public PollTimeoutException(string message) : base(message) { }
}
