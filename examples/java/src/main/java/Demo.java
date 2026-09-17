import ai.webpipe.sdk.WebpipeClient;
import ai.webpipe.sdk.exceptions.WebpipeException;
import ai.webpipe.sdk.options.CreateSessionOptions;
import ai.webpipe.sdk.options.CrawlOptions;
import ai.webpipe.sdk.options.MapOptions;
import ai.webpipe.sdk.options.NavigateOptions;
import ai.webpipe.sdk.options.ScrapeOptions;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WebPipe SDK quickstart demo (Java ≥ 17).
 *
 * API key (checked in this order — get one at https://webpipe.ai/api-keys.html):
 *   1. --api-key wc-...            flag
 *   2. WEBPIPE_API_KEY=wc-...      environment variable
 *   3. interactive prompt / pipe:  echo wc-... | mvn -q exec:java -Dexec.args="scrape"
 *
 * Usage (every command has a built-in demo URL, or pass your own):
 *   mvn -q compile exec:java -Dexec.args="scrape [url] [--api-key wc-...]"
 * Commands: scrape (default https://example.com) | map (https://nginx.org) |
 *           crawl (https://quotes.toscrape.com) | browser (login-flow demo)
 */
public class Demo {
    static final Map<String, String> DEFAULT_URLS = Map.of(
            "scrape", "https://example.com",
            "map", "https://nginx.org",
            "crawl", "https://quotes.toscrape.com");

    public static void main(String[] args) throws Exception {
        List<String> positionals = new ArrayList<>();
        String apiKeyFlag = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--api-key") && i + 1 < args.length) {
                apiKeyFlag = args[++i];
            } else {
                positionals.add(args[i]);
            }
        }
        if (positionals.isEmpty() || (!DEFAULT_URLS.containsKey(positionals.get(0))
                && !positionals.get(0).equals("browser") && !positionals.get(0).equals("async"))) {
            System.err.println("usage: <scrape|map|crawl|browser|async> [url] [--api-key wc-...]");
            System.exit(1);
        }
        String command = positionals.get(0);

        WebpipeClient client = WebpipeClient.builder()
                .apiKey(resolveApiKey(apiKeyFlag))
                .build();
        try {
            switch (command) {
                case "scrape" -> cmdScrape(client, argOr(positionals, 1, DEFAULT_URLS.get("scrape")));
                case "map" -> cmdMap(client, argOr(positionals, 1, DEFAULT_URLS.get("map")));
                case "crawl" -> cmdCrawl(client, argOr(positionals, 1, DEFAULT_URLS.get("crawl")));
                case "browser" -> cmdBrowser(client);
                case "async" -> cmdAsync(client);
            }
        } catch (WebpipeException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Async demo: run scrape + map concurrently with CompletableFuture. */
    static void cmdAsync(WebpipeClient client) throws Exception {
        System.out.println("→ Async demo: concurrent scrape + map via CompletableFuture ...");
        long start = System.currentTimeMillis();

        var scrapeFuture = client.scrapeAsync("https://example.com",
                new ScrapeOptions().formats(List.of("markdown", "metadata")));
        var mapFuture = client.mapAsync("https://nginx.org", new MapOptions().limit(10));

        // Wait for both — they run concurrently, so total time ≈ the slower one.
        java.util.concurrent.CompletableFuture.allOf(scrapeFuture, mapFuture).join();

        System.out.println("  scrape title: " + scrapeFuture.join().metadata().get("title"));
        System.out.println("  map links:    " + mapFuture.join().size());
        System.out.println("  both finished in " + (System.currentTimeMillis() - start) + "ms (concurrent)");
    }

    static String argOr(List<String> args, int i, String fallback) {
        return args.size() > i ? args.get(i) : fallback;
    }

    static String resolveApiKey(String flag) throws Exception {
        if (flag != null && !flag.isEmpty()) return flag;
        String env = System.getenv("WEBPIPE_API_KEY");
        if (env != null && !env.isEmpty()) return env;
        System.out.println("API key not found in --api-key or WEBPIPE_API_KEY.");
        System.out.println("Get one at https://webpipe.ai/api-keys.html");
        if (System.console() != null) System.out.print("Enter your API key (wc-...): ");
        String line = new BufferedReader(new InputStreamReader(System.in)).readLine();
        if (line != null && !line.trim().isEmpty()) return line.trim();
        System.err.println("error: no API key provided");
        System.exit(1);
        return "";
    }

    static void cmdScrape(WebpipeClient client, String url) {
        System.out.println("→ Scraping " + url + " ...");
        var doc = client.scrape(url, new ScrapeOptions().formats(List.of("markdown", "metadata", "links")));
        System.out.println("  title:  " + doc.metadata().get("title"));
        System.out.println("  status: " + doc.metadata().get("statusCode"));
        System.out.println("  links:  " + doc.links().size());
        System.out.println("--- markdown (first 400 chars) ---");
        System.out.println(doc.markdown().substring(0, Math.min(400, doc.markdown().length())));
    }

    static void cmdMap(WebpipeClient client, String url) {
        System.out.println("→ Mapping " + url + " ...");
        var links = client.map(url, new MapOptions().limit(50));
        System.out.println("  found " + links.size() + " URLs (limit 50):");
        links.stream().limit(10).forEach(l -> System.out.println("  - " + l.url()));
        if (links.size() > 10) System.out.println("  ... and " + (links.size() - 10) + " more");
    }

    static void cmdCrawl(WebpipeClient client, String url) {
        System.out.println("→ Crawling " + url + " (limit 5, polling until done) ...");
        var job = client.crawl(url,
                new CrawlOptions().limit(5)
                        .scrapeOptions(new ScrapeOptions().formats(List.of("markdown", "metadata"))),
                2, 300);
        System.out.println("  done: " + job.completed() + "/" + job.total() + " pages");
        job.data().forEach(p -> System.out.println(
                "  - " + p.metadata().get("sourceURL") + " | " + p.metadata().get("title")));
    }

    static void cmdBrowser(WebpipeClient client) {
        String url = "https://quotes.toscrape.com/login";
        System.out.println("→ Browser login demo on " + url + " (any credentials work) ...");
        var session = client.browser.create(new CreateSessionOptions().url(url));
        try {
            session.waitUntilRunning(2, 90);
            System.out.println("  session running, filling the login form ...");
            session.fill("input[name=username]", "demo");
            session.fill("input[name=password]", "demo");
            session.click("input[type=submit]", 2000);
            var doc = session.scrape(new NavigateOptions().formats(List.of("markdown")));
            boolean ok = doc.markdown() != null && doc.markdown().contains("Logout");
            System.out.println(ok ? "  login succeeded ✓ (Logout link found)" : "  login — check output below");
            System.out.println("--- markdown after login (first 300 chars) ---");
            System.out.println(doc.markdown().substring(0, Math.min(300, doc.markdown().length())));
        } finally {
            session.close();
        }
        System.out.println("  session closed.");
    }
}
