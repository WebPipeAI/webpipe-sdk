package ai.webpipe.sdk.exceptions;

/**
 * An error response returned by the WebPipe API. Always carries the server's
 * original error message and the HTTP status code.
 */
public class ApiException extends WebpipeException {
    private final int statusCode;
    private final transient Object body;

    public ApiException(String message, int statusCode, Object body) {
        super("[" + statusCode + "] " + message);
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Object getBody() {
        return body;
    }

    /** Map an HTTP status code to the most specific exception subclass. */
    public static ApiException forStatus(int statusCode, String message, Object body) {
        return switch (statusCode) {
            case 400 -> new BadRequestException(message, statusCode, body);
            case 401 -> new AuthenticationException(message, statusCode, body);
            case 403 -> new ForbiddenException(message, statusCode, body);
            case 404 -> new NotFoundException(message, statusCode, body);
            case 429 -> new RateLimitException(message, statusCode, body);
            default -> statusCode >= 500
                    ? new ServerException(message, statusCode, body)
                    : new ApiException(message, statusCode, body);
        };
    }
}
