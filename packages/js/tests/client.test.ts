import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  AuthenticationError,
  CrawlError,
  NotFoundError,
  Webpipe,
  WebpipeTimeoutError,
} from "../src/index.js";

const BASE = "https://api.web2json.ai";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const fetchMock = vi.fn();

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  fetchMock.mockReset();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("constructor", () => {
  it("throws without an API key", () => {
    vi.stubEnv("WEBPIPE_API_KEY", "");
    expect(() => new Webpipe()).toThrow(/WEBPIPE_API_KEY/);
  });

  it("reads the key from env", () => {
    vi.stubEnv("WEBPIPE_API_KEY", "wc-env");
    expect(() => new Webpipe()).not.toThrow();
  });
});

describe("scrape", () => {
  it("parses the document and sends snake_case body", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, {
        success: true,
        data: {
          markdown: "# Example",
          metadata: { title: "Example", sourceURL: "https://example.com", statusCode: 200 },
        },
      }),
    );
    const client = new Webpipe({ apiKey: "wc-test", maxRetries: 0 });
    const doc = await client.scrape("https://example.com", {
      formats: ["markdown", "metadata"],
      useProxy: true,
    });

    expect(doc.markdown).toBe("# Example");
    expect(doc.metadata?.sourceURL).toBe("https://example.com");

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe(`${BASE}/api/v1/scrape`);
    expect(init.method).toBe("POST");
    expect(init.headers.Authorization).toBe("Bearer wc-test");
    expect(init.headers["User-Agent"]).toMatch(/^webpipe-sdk-js\//);
    expect(JSON.parse(init.body)).toEqual({
      url: "https://example.com",
      formats: ["markdown", "metadata"],
      use_proxy: true,
    });
  });

  it("does not transform user keys inside jsonSchema", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { success: true, data: { json: {} } }));
    const client = new Webpipe({ apiKey: "wc-test", maxRetries: 0 });
    await client.scrape("https://example.com", {
      formats: ["json"],
      jsonSchema: {
        type: "object",
        properties: { myField: { type: "string" } },
      },
    });
    const body = JSON.parse(fetchMock.mock.calls[0]![1].body);
    expect(body.json_schema.properties).toHaveProperty("myField");
  });

  it("maps 401 to AuthenticationError with the server message", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(401, { success: false, error: "invalid API key" }),
    );
    const client = new Webpipe({ apiKey: "wc-test", maxRetries: 0 });
    const err = await client.scrape("https://example.com").catch((e) => e);
    expect(err).toBeInstanceOf(AuthenticationError);
    expect(err.statusCode).toBe(401);
    expect(err.message).toContain("invalid API key");
  });

  it("retries on 429 then succeeds", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(429, { success: false, error: "slow down" }))
      .mockResolvedValueOnce(jsonResponse(200, { success: true, data: { markdown: "ok" } }));
    const client = new Webpipe({ apiKey: "wc-test", maxRetries: 1 });
    const doc = await client.scrape("https://example.com");
    expect(doc.markdown).toBe("ok");
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});

describe("map", () => {
  it("returns discovered links", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, {
        success: true,
        links: [
          { url: "https://example.com/about", title: "About", description: "" },
          { url: "https://example.com/blog" },
        ],
      }),
    );
    const client = new Webpipe({ apiKey: "wc-test", maxRetries: 0 });
    const links = await client.map("https://example.com", { limit: 100, sameDomain: false });
    expect(links).toHaveLength(2);
    expect(links[0]!.url).toBe("https://example.com/about");
    const body = JSON.parse(fetchMock.mock.calls[0]![1].body);
    expect(body.same_domain).toBe(false);
  });
});

describe("crawl", () => {
  it("submits and polls until completed", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { success: true, id: "job-1" }))
      .mockResolvedValueOnce(
        jsonResponse(200, { success: true, status: "scraping", total: 2, completed: 1, data: [] }),
      )
      .mockResolvedValueOnce(
        jsonResponse(200, {
          success: true,
          status: "completed",
          total: 2,
          completed: 2,
          data: [{ markdown: "# A" }, { markdown: "# B" }],
          errors: [],
          expires_at: "2026-07-28T00:00:00+00:00",
        }),
      );
    const client = new Webpipe({ apiKey: "wc-test", maxRetries: 0 });
    const job = await client.crawl("https://x", { limit: 2, pollInterval: 0.01 });
    expect(job.status).toBe("completed");
    expect(job.data).toHaveLength(2);
    expect(job.expiresAt).toBe("2026-07-28T00:00:00+00:00");
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("throws CrawlError on failed jobs", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { success: true, id: "job-2" }))
      .mockResolvedValueOnce(
        jsonResponse(200, { success: true, status: "failed", errors: ["boom"] }),
      );
    const client = new Webpipe({ apiKey: "wc-test", maxRetries: 0 });
    await expect(client.crawl("https://x", { pollInterval: 0.01 })).rejects.toBeInstanceOf(
      CrawlError,
    );
  });

  it("times out while polling", async () => {
    // Fresh Response per call — a Response body can only be consumed once.
    fetchMock.mockImplementation((_url: string, init?: RequestInit) =>
      Promise.resolve(
        init?.method === "POST"
          ? jsonResponse(200, { success: true, id: "job-3" })
          : jsonResponse(200, { success: true, status: "scraping", data: [], errors: [] }),
      ),
    );
    const client = new Webpipe({ apiKey: "wc-test", maxRetries: 0 });
    await expect(
      client.crawl("https://x", { pollInterval: 0.01, timeout: 0.05 }),
    ).rejects.toBeInstanceOf(WebpipeTimeoutError);
  });

  it("maps 404 to NotFoundError", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(404, { success: false, error: "gone" }));
    const client = new Webpipe({ apiKey: "wc-test", maxRetries: 0 });
    await expect(client.getCrawlStatus("gone")).rejects.toBeInstanceOf(NotFoundError);
  });
});
