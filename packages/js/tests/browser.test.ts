import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Webpipe } from "../src/index.js";

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
});

describe("browser session", () => {
  it("runs the full lifecycle", async () => {
    fetchMock
      // create
      .mockResolvedValueOnce(
        jsonResponse(200, {
          success: true,
          id: "sess-1",
          status: "starting",
          url: "https://example.com",
          session_name: "demo",
        }),
      )
      // refresh: starting, then running
      .mockResolvedValueOnce(jsonResponse(200, { success: true, id: "sess-1", status: "starting" }))
      .mockResolvedValueOnce(jsonResponse(200, { success: true, id: "sess-1", status: "running" }))
      // scrape current page
      .mockResolvedValueOnce(
        jsonResponse(200, { success: true, data: { markdown: "# Hi", metadata: { title: "Hi" } } }),
      )
      // action
      .mockResolvedValueOnce(jsonResponse(200, { success: true, ok: true }))
      // close
      .mockResolvedValueOnce(jsonResponse(200, { success: true }));

    const client = new Webpipe({ apiKey: "wc-test", maxRetries: 0 });
    const session = await client.browser.create({
      url: "https://example.com",
      sessionName: "demo",
    });
    expect(session.id).toBe("sess-1");
    expect(session.status).toBe("starting");

    // create body maps sessionName -> session_name
    const createBody = JSON.parse(fetchMock.mock.calls[0]![1].body);
    expect(createBody.session_name).toBe("demo");

    await session.waitUntilRunning({ timeout: 5, pollInterval: 0.01 });
    expect(session.status).toBe("running");

    const doc = session ? await session.scrape({ formats: ["markdown"] }) : null;
    expect(doc?.markdown).toBe("# Hi");

    await session.click("button.more", { waitAfter: 500 });
    const actionBody = JSON.parse(fetchMock.mock.calls[4]![1].body);
    expect(actionBody).toEqual({ type: "click", selector: "button.more", wait_after: 500 });
    expect(fetchMock.mock.calls[4]![0]).toBe(`${BASE}/api/v1/browser/sess-1/action`);

    await session.close();
    expect(session.status).toBe("stopped");
    expect(fetchMock.mock.calls[5]![1].method).toBe("DELETE");
  });

  it("gets, sets and clears cookies", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { success: true, id: "sess-2", status: "running" }))
      .mockResolvedValueOnce(
        jsonResponse(200, {
          success: true,
          cookies: [{ name: "session", value: "abc", domain: "example.com" }],
        }),
      )
      .mockResolvedValueOnce(jsonResponse(200, { success: true }))
      .mockResolvedValueOnce(jsonResponse(200, { success: true }));

    const client = new Webpipe({ apiKey: "wc-test", maxRetries: 0 });
    const session = await client.browser.create();

    const cookies = await session.getCookies();
    expect(cookies[0]!.name).toBe("session");

    await session.setCookies([{ name: "x", value: "1", domain: "example.com", path: "/" }]);
    expect(JSON.parse(fetchMock.mock.calls[2]![1].body).op).toBe("set");

    await session.clearCookies();
    expect(JSON.parse(fetchMock.mock.calls[3]![1].body)).toEqual({ op: "clear" });
  });
});
