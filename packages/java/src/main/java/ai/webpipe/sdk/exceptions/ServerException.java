package ai.webpipe.sdk.exceptions;

/** 5xx — scrape timeout, target site unreachable, ... */
public class ServerException extends ApiException {
    public ServerException(String message, int statusCode, Object body) {
        super(message, statusCode, body);
    }
}
