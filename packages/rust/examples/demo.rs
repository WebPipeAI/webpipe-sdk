//! WebPipe SDK quickstart demo (Rust).
//!
//! API key (checked in this order — get one at https://webpipe.ai/api-keys.html):
//!   1. --api-key wc-...            flag
//!   2. WEBPIPE_API_KEY=wc-...      environment variable
//!   3. interactive prompt / pipe:  echo wc-... | cargo run --example demo -- scrape
//!
//! Usage (every command has a built-in demo URL, or pass your own):
//!   cargo run --example demo -- scrape [url] [--api-key wc-...]    # default https://example.com
//!   cargo run --example demo -- map [url] [--api-key wc-...]       # default https://nginx.org
//!   cargo run --example demo -- crawl [url] [--api-key wc-...]     # default https://quotes.toscrape.com
//!   cargo run --example demo -- browser [--api-key wc-...]         # login-flow demo

use std::env;
use std::io::{BufRead, Write};
use std::time::Duration;

use webpipe_sdk::{
    Client, CreateSessionOptions, CrawlOptions, MapOptions, NavigateOptions, ScrapeOptions,
};

fn resolve_api_key(flag: Option<String>) -> String {
    if let Some(k) = flag.filter(|k| !k.is_empty()) {
        return k;
    }
    if let Some(k) = env::var("WEBPIPE_API_KEY").ok().filter(|k| !k.is_empty()) {
        return k;
    }
    println!("API key not found in --api-key or WEBPIPE_API_KEY.");
    println!("Get one at https://webpipe.ai/api-keys.html");
    if atty_stdin() {
        print!("Enter your API key (wc-...): ");
        let _ = std::io::stdout().flush();
    }
    let mut line = String::new();
    std::io::stdin().lock().read_line(&mut line).ok();
    let key = line.trim().to_string();
    if key.is_empty() {
        eprintln!("error: no API key provided");
        std::process::exit(1);
    }
    key
}

fn atty_stdin() -> bool {
    // Good enough for a demo: /dev/tty unavailable on Windows; prompt is cosmetic anyway.
    true
}

#[tokio::main(flavor = "multi_thread")]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Parse args: positionals = <command> [url], plus --api-key <key>
    let mut positionals = Vec::new();
    let mut api_key_flag = None;
    let mut args = env::args().skip(1);
    while let Some(arg) = args.next() {
        if arg == "--api-key" {
            api_key_flag = args.next();
        } else {
            positionals.push(arg);
        }
    }
    if positionals.is_empty() {
        eprintln!("usage: demo <scrape|map|crawl|browser> [url] [--api-key wc-...]");
        std::process::exit(1);
    }
    let command = positionals[0].as_str();

    let client = Client::builder(resolve_api_key(api_key_flag)).build()?;

    match command {
        "scrape" => {
            let url = positionals.get(1).map(String::as_str).unwrap_or("https://example.com");
            println!("→ Scraping {url} ...");
            let doc = client
                .scrape(
                    url,
                    Some(&ScrapeOptions {
                        formats: Some(vec!["markdown".into(), "metadata".into(), "links".into()]),
                        ..Default::default()
                    }),
                )
                .await?;
            if let Some(meta) = &doc.metadata {
                println!("  title:  {}", meta["title"].as_str().unwrap_or_default());
                println!("  status: {}", meta["statusCode"]);
            }
            println!("  links:  {}", doc.links.map(|l| l.len()).unwrap_or(0));
            println!("--- markdown (first 400 chars) ---");
            println!("{:.400}", doc.markdown.unwrap_or_default());
        }
        "map" => {
            let url = positionals.get(1).map(String::as_str).unwrap_or("https://nginx.org");
            println!("→ Mapping {url} ...");
            let links = client
                .map(url, Some(&MapOptions { limit: Some(50), ..Default::default() }))
                .await?;
            println!("  found {} URLs (limit 50):", links.len());
            for l in links.iter().take(10) {
                println!("  - {}", l.url);
            }
            if links.len() > 10 {
                println!("  ... and {} more", links.len() - 10);
            }
        }
        "crawl" => {
            let url = positionals
                .get(1)
                .map(String::as_str)
                .unwrap_or("https://quotes.toscrape.com");
            println!("→ Crawling {url} (limit 5, polling until done) ...");
            let job = client
                .crawl(
                    url,
                    Some(&CrawlOptions {
                        limit: Some(5),
                        scrape_options: Some(ScrapeOptions {
                            formats: Some(vec!["markdown".into(), "metadata".into()]),
                            ..Default::default()
                        }),
                        ..Default::default()
                    }),
                    Duration::from_secs(2),
                    Some(Duration::from_secs(300)),
                )
                .await?;
            println!("  done: {}/{} pages", job.completed, job.total);
            for page in &job.data {
                let meta = page.metadata.clone().unwrap_or_default();
                println!("  - {} | {}", meta["sourceURL"].as_str().unwrap_or("?"), meta["title"].as_str().unwrap_or(""));
            }
        }
        "browser" => {
            let url = "https://quotes.toscrape.com/login";
            println!("→ Browser login demo on {url} (any credentials work) ...");
            let mut session = client
                .browser()
                .create(Some(&CreateSessionOptions {
                    url: Some(url.into()),
                    ..Default::default()
                }))
                .await?;
            session
                .wait_until_running(Duration::from_secs(2), Duration::from_secs(90))
                .await?;
            println!("  session running, filling the login form ...");
            session.fill("input[name=username]", "demo").await?;
            session.fill("input[name=password]", "demo").await?;
            session.click("input[type=submit]", Some(2000)).await?;
            let doc = session
                .scrape(Some(&NavigateOptions {
                    formats: Some(vec!["markdown".into()]),
                    ..Default::default()
                }))
                .await?;
            let md = doc.markdown.unwrap_or_default();
            if md.contains("Logout") {
                println!("  login succeeded ✓ (Logout link found)");
            } else {
                println!("  login — check output below");
            }
            println!("--- markdown after login (first 300 chars) ---");
            println!("{:.300}", md);
            session.close().await?;
            println!("  session closed.");
        }
        _ => {
            eprintln!("usage: demo <scrape|map|crawl|browser> [url] [--api-key wc-...]");
            std::process::exit(1);
        }
    }
    Ok(())
}
