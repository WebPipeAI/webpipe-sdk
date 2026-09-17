#!/usr/bin/env python3
"""WebPipe SDK quickstart demo (Python).

Setup:
    pip install webpipe-sdk        # or: pip install -e ../../packages/python

API key (checked in this order — get one at https://webpipe.ai/api-keys.html):
    1. --api-key wc-...            flag
    2. WEBPIPE_API_KEY=wc-...      environment variable
    3. interactive prompt / pipe:  echo wc-... | python demo.py scrape

Usage (every command has a built-in demo URL, or pass your own):
    python demo.py scrape [url]    # default https://example.com
    python demo.py map [url]       # default https://nginx.org
    python demo.py crawl [url]     # default https://quotes.toscrape.com
    python demo.py browser         # login-flow demo on quotes.toscrape.com
"""

import argparse
import os
import sys

from webpipe import WebpipeClient, WebpipeError

DEFAULT_URLS = {
    "scrape": "https://example.com",
    "map": "https://nginx.org",
    "crawl": "https://quotes.toscrape.com",
}


def resolve_api_key(flag_value) -> str:
    if flag_value:
        return flag_value
    env_value = os.environ.get("WEBPIPE_API_KEY")
    if env_value:
        return env_value
    print("API key not found in --api-key or WEBPIPE_API_KEY.")
    print("Get one at https://webpipe.ai/api-keys.html")
    if sys.stdin.isatty():
        return input("Enter your API key (wc-...): ").strip()
    # Piped input, e.g. `echo wc-... | python demo.py scrape`
    piped = sys.stdin.readline().strip()
    if piped:
        return piped
    sys.exit("error: no API key provided (see --help)")


def cmd_scrape(client: WebpipeClient, url: str) -> None:
    print(f"→ Scraping {url} ...")
    doc = client.scrape(url, formats=["markdown", "metadata", "links"])
    if doc.metadata:
        print(f"  title:  {doc.metadata.title}")
        print(f"  status: {doc.metadata.status_code}")
    print(f"  links:  {len(doc.links or [])}")
    print("--- markdown (first 400 chars) ---")
    print((doc.markdown or "")[:400])


def cmd_map(client: WebpipeClient, url: str) -> None:
    print(f"→ Mapping {url} ...")
    links = client.map(url, limit=50)
    print(f"  found {len(links)} URLs (limit 50):")
    for link in links[:10]:
        print(f"  - {link.url}")
    if len(links) > 10:
        print(f"  ... and {len(links) - 10} more")


def cmd_crawl(client: WebpipeClient, url: str) -> None:
    print(f"→ Crawling {url} (limit 5, polling until done) ...")
    job = client.crawl(
        url,
        limit=5,
        scrape_options={"formats": ["markdown", "metadata"]},
        timeout=300,
    )
    print(f"  done: {job.completed}/{job.total} pages")
    for page in job.data:
        meta = page.metadata
        src = meta.source_url if meta else "?"
        title = (meta.title or "") if meta else ""
        print(f"  - {src} | {title}")


def cmd_browser(client: WebpipeClient) -> None:
    url = "https://quotes.toscrape.com/login"
    print(f"→ Browser login demo on {url} (any credentials work) ...")
    with client.browser.create(url=url) as session:
        session.wait_until_running(timeout=90)
        print("  session running, filling the login form ...")
        session.fill("input[name=username]", "demo")
        session.fill("input[name=password]", "demo")
        session.click("input[type=submit]", wait_after=2000)
        doc = session.scrape(formats=["markdown"])
        ok = "Logout" in (doc.markdown or "")
        print(
            f"  login {'succeeded ✓ (Logout link found)' if ok else '— check output below'}"
        )
        print("--- markdown after login (first 300 chars) ---")
        print((doc.markdown or "")[:300])
    print("  session closed.")


def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("command", choices=["scrape", "map", "crawl", "browser"])
    parser.add_argument("url", nargs="?", default=None, help="optional target URL")
    parser.add_argument("--api-key", default=None, help="WebPipe API key (wc-...)")
    args = parser.parse_args()

    try:
        client = WebpipeClient(api_key=resolve_api_key(args.api_key))
    except ValueError as e:
        sys.exit(f"error: {e}")

    handlers = {"scrape": cmd_scrape, "map": cmd_map, "crawl": cmd_crawl}
    try:
        if args.command == "browser":
            cmd_browser(client)
        else:
            handlers[args.command](client, args.url or DEFAULT_URLS[args.command])
    except WebpipeError as e:
        sys.exit(f"webpipe error: {e}")


if __name__ == "__main__":
    main()
