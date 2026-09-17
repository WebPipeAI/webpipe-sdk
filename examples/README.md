# Examples — WebPipe SDK quickstart demos

Each language ships the same mini CLI with four commands mapping to the four
API features. Every command has a built-in demo URL, so you can run it
immediately — or pass your own URL as the last argument.

| Command | Demo URL | What it shows |
|---|---|---|
| `scrape [url]` | `https://example.com` | Single page → markdown + metadata + links |
| `map [url]` | `https://nginx.org` | Discover all URLs of a site |
| `crawl [url]` | `https://quotes.toscrape.com` | Recursive crawl with automatic polling |
| `browser` | `https://quotes.toscrape.com/login` | Full login flow in a headless browser |

## API Key

Get one at https://webpipe.ai/api-keys.html (free tier: 3,000 pages/month).
All demos accept it in two ways — flag first, env var second:

```bash
# 1. Command-line flag
python examples/python/demo.py scrape --api-key wc-...

# 2. Environment variable
export WEBPIPE_API_KEY=wc-...
python examples/python/demo.py scrape
```

(If neither is present, the demos prompt interactively; piping also works:
`echo wc-... | python examples/python/demo.py scrape`.)

## Python

```bash
pip install -e packages/python          # or: pip install webpipe-sdk
python examples/python/demo.py scrape
python examples/python/demo.py map
python examples/python/demo.py crawl
python examples/python/demo.py browser
```

## TypeScript / Node.js

```bash
cd packages/js && npm install && npm run build && cd -
cd examples/js && npm install
node demo.mjs scrape
node demo.mjs map
node demo.mjs crawl
node demo.mjs browser
```

## Go

```bash
cd examples/go
go run . scrape
go run . map
go run . crawl
go run . browser
```

## PHP

```bash
cd examples/php
composer install
php demo.php scrape
php demo.php map
php demo.php crawl
php demo.php browser
```

No PHP locally? Run it in Docker (from the repo root):

```bash
docker run --rm -v "$PWD:/repo" -w /repo/examples/php -e WEBPIPE_API_KEY composer install
docker run --rm -v "$PWD:/repo" -w /repo/examples/php -e WEBPIPE_API_KEY --entrypoint php composer demo.php scrape
```

## Ruby

```bash
ruby examples/ruby/demo.rb scrape      # no install needed — uses the in-repo lib
ruby examples/ruby/demo.rb map
ruby examples/ruby/demo.rb crawl
ruby examples/ruby/demo.rb browser
```

## Java

```bash
cd packages/java && mvn install -DskipTests && cd -   # install the SDK locally once
cd examples/java
mvn -q compile exec:java -Dexec.args="scrape"
mvn -q exec:java -Dexec.args="map"
mvn -q exec:java -Dexec.args="crawl"
mvn -q exec:java -Dexec.args="browser"
```

## Rust

The Rust demo lives with the crate (cargo convention):

```bash
cd packages/rust
cargo run --example demo -- scrape
cargo run --example demo -- map
cargo run --example demo -- crawl
cargo run --example demo -- browser
```

## C# / .NET

```bash
cd examples/dotnet
dotnet run -- scrape
dotnet run -- map
dotnet run -- crawl
dotnet run -- browser
```
