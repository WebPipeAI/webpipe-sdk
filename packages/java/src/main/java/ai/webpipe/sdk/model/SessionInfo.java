package ai.webpipe.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** State of a browser session (GET /browser/{id}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionInfo(
        String id,
        String status,
        String url,
        String title,
        @JsonProperty("session_name") String sessionName,
        @JsonProperty("expires_at") String expiresAt) {

    public static final String STATUS_STARTING = "starting";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_STOPPED = "stopped";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_EXPIRED = "expired";
}
