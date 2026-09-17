package ai.webpipe.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Status and (partial or final) results of a crawl job.
 * Results expire 24h after completion (see {@code expiresAt}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrawlJob(
        String status,
        int total,
        int completed,
        List<Document> data,
        List<Object> errors,
        @JsonProperty("expires_at") String expiresAt) {

    public static final String STATUS_SCRAPING = "scraping";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
}
