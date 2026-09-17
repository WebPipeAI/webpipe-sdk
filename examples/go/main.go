// WebPipe SDK quickstart demo (Go).
//
// API key (checked in this order — get one at https://webpipe.ai/api-keys.html):
//
//  1. --api-key wc-...            flag
//  2. WEBPIPE_API_KEY=wc-...      environment variable
//  3. interactive prompt / pipe:  echo wc-... | go run . scrape
//
// Usage (every command has a built-in demo URL, or pass your own):
//
//	go run . scrape [url] [--api-key wc-...]    # default https://example.com
//	go run . map [url] [--api-key wc-...]       # default https://nginx.org
//	go run . crawl [url] [--api-key wc-...]     # default https://quotes.toscrape.com
//	go run . browser [--api-key wc-...]         # login-flow demo on quotes.toscrape.com
package main

import (
	"bufio"
	"context"
	"fmt"
	"os"
	"strings"
	"time"

	webpipe "github.com/webpipe-ai/webpipe-sdk/packages/go"
)

var defaultURLs = map[string]string{
	"scrape": "https://example.com",
	"map":    "https://nginx.org",
	"crawl":  "https://quotes.toscrape.com",
}

// resolveAPIKey: --api-key flag > env var > prompt/pipe.
func resolveAPIKey(flag string) string {
	if flag != "" {
		return flag
	}
	if env := os.Getenv("WEBPIPE_API_KEY"); env != "" {
		return env
	}
	fmt.Println("API key not found in --api-key or WEBPIPE_API_KEY.")
	fmt.Println("Get one at https://webpipe.ai/api-keys.html")
	reader := bufio.NewReader(os.Stdin)
	if fi, _ := os.Stdin.Stat(); fi.Mode()&os.ModeCharDevice != 0 {
		fmt.Print("Enter your API key (wc-...): ")
	}
	line, _ := reader.ReadString('\n')
	if key := strings.TrimSpace(line); key != "" {
		return key
	}
	fmt.Fprintln(os.Stderr, "error: no API key provided")
	os.Exit(1)
	return ""
}

func main() {
	// Parse args: positionals = <command> [url], plus --api-key <key>
	var positionals []string
	var apiKeyFlag string
	args := os.Args[1:]
	for i := 0; i < len(args); i++ {
		if args[i] == "--api-key" && i+1 < len(args) {
			apiKeyFlag = args[i+1]
			i++
		} else {
			positionals = append(positionals, args[i])
		}
	}
	if len(positionals) == 0 {
		fmt.Fprintln(os.Stderr, "usage: go run . <scrape|map|crawl|browser> [url] [--api-key wc-...]")
		os.Exit(1)
	}
	command := positionals[0]

	client, err := webpipe.NewClient(webpipe.WithAPIKey(resolveAPIKey(apiKeyFlag)))
	if err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(1)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
	defer cancel()

	url := func(fallback string) string {
		if len(positionals) > 1 && positionals[1] != "" {
			return positionals[1]
		}
		return fallback
	}

	var runErr error
	switch command {
	case "scrape":
		runErr = cmdScrape(ctx, client, url(defaultURLs["scrape"]))
	case "map":
		runErr = cmdMap(ctx, client, url(defaultURLs["map"]))
	case "crawl":
		runErr = cmdCrawl(ctx, client, url(defaultURLs["crawl"]))
	case "browser":
		runErr = cmdBrowser(ctx, client)
	default:
		fmt.Fprintln(os.Stderr, "usage: go run . <scrape|map|crawl|browser> [url] [--api-key wc-...]")
		os.Exit(1)
	}
	if runErr != nil {
		fmt.Fprintln(os.Stderr, "webpipe error:", runErr)
		os.Exit(1)
	}
}

func cmdScrape(ctx context.Context, client *webpipe.Client, url string) error {
	fmt.Printf("→ Scraping %s ...\n", url)
	doc, err := client.Scrape(ctx, url, &webpipe.ScrapeOptions{
		Formats: []string{"markdown", "metadata", "links"},
	})
	if err != nil {
		return err
	}
	if doc.Metadata != nil {
		fmt.Println("  title: ", doc.Metadata.Title)
		fmt.Println("  status:", doc.Metadata.StatusCode)
	}
	fmt.Println("  links: ", len(doc.Links))
	fmt.Println("--- markdown (first 400 chars) ---")
	fmt.Println(truncate(doc.Markdown, 400))
	return nil
}

func cmdMap(ctx context.Context, client *webpipe.Client, url string) error {
	fmt.Printf("→ Mapping %s ...\n", url)
	links, err := client.Map(ctx, url, &webpipe.MapOptions{Limit: 50})
	if err != nil {
		return err
	}
	fmt.Printf("  found %d URLs (limit 50):\n", len(links))
	for i, l := range links {
		if i >= 10 {
			fmt.Printf("  ... and %d more\n", len(links)-10)
			break
		}
		fmt.Println("  -", l.URL)
	}
	return nil
}

func cmdCrawl(ctx context.Context, client *webpipe.Client, url string) error {
	fmt.Printf("→ Crawling %s (limit 5, polling until done) ...\n", url)
	job, err := client.Crawl(ctx, url,
		&webpipe.CrawlOptions{
			Limit:         5,
			ScrapeOptions: &webpipe.ScrapeOptions{Formats: []string{"markdown", "metadata"}},
		},
		&webpipe.CrawlWaitOptions{PollInterval: 2, Timeout: 300},
	)
	if err != nil {
		return err
	}
	fmt.Printf("  done: %d/%d pages\n", job.Completed, job.Total)
	for _, page := range job.Data {
		src, title := "?", ""
		if page.Metadata != nil {
			src, title = page.Metadata.SourceURL, page.Metadata.Title
		}
		fmt.Printf("  - %s | %s\n", src, title)
	}
	return nil
}

func cmdBrowser(ctx context.Context, client *webpipe.Client) error {
	url := "https://quotes.toscrape.com/login"
	fmt.Printf("→ Browser login demo on %s (any credentials work) ...\n", url)
	session, err := client.Browser.Create(ctx, &webpipe.CreateSessionOptions{URL: url})
	if err != nil {
		return err
	}
	defer session.Close(ctx)

	if err := session.WaitUntilRunning(ctx, 2, 90); err != nil {
		return err
	}
	fmt.Println("  session running, filling the login form ...")
	if _, err := session.Fill(ctx, "input[name=username]", "demo"); err != nil {
		return err
	}
	if _, err := session.Fill(ctx, "input[name=password]", "demo"); err != nil {
		return err
	}
	if _, err := session.Click(ctx, "input[type=submit]", 2000); err != nil {
		return err
	}
	doc, err := session.Scrape(ctx, &webpipe.NavigateOptions{Formats: []string{"markdown"}})
	if err != nil {
		return err
	}
	ok := strings.Contains(doc.Markdown, "Logout")
	if ok {
		fmt.Println("  login succeeded ✓ (Logout link found)")
	} else {
		fmt.Println("  login — check output below")
	}
	fmt.Println("--- markdown after login (first 300 chars) ---")
	fmt.Println(truncate(doc.Markdown, 300))
	fmt.Println("  session closed (deferred).")
	return nil
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n]
}
