#!/usr/bin/env bash
# Run the full SDK test matrix in Docker — no host toolchain required.
#
# Usage:
#   scripts/test.sh            # all languages
#   scripts/test.sh python js  # subset
#
# Cache volumes per ecosystem keep repeat runs fast.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

LANGUAGES=(python js go php ruby java rust dotnet)
if [ $# -gt 0 ]; then
  LANGUAGES=("$@")
fi

FAILED=()

run() {
  local lang="$1"; shift
  echo "================================================================"
  echo "▶ ${lang}"
  echo "================================================================"
  if "$@" ; then
    echo "✅ ${lang} passed"
  else
    echo "❌ ${lang} FAILED"
    FAILED+=("$lang")
  fi
  echo
}

test_python() {
  docker run --rm -v "$ROOT:/repo" -v webpipe-pip-cache:/root/.cache/pip \
    -w /repo/packages/python python:3.12-slim \
    sh -c "pip install -q -e '.[dev]' && python -m pytest -q"
}

test_js() {
  docker run --rm -v "$ROOT:/repo" -v webpipe-npm-cache:/root/.npm \
    -w /repo/packages/js node:22 \
    sh -c "npm ci --silent && npm run build --silent && npm run typecheck --silent && npm test -- --run"
}

test_go() {
  docker run --rm -v "$ROOT:/repo" -v webpipe-go-cache:/go/pkg \
    -w /repo/packages/go golang:1.23 \
    sh -c "gofmt -l . && go vet ./... && go test ./..."
}

test_php() {
  docker run --rm -v "$ROOT/packages/php:/app" -v webpipe-composer-cache:/tmp/composer \
    -e COMPOSER_CACHE_DIR=/tmp/composer -w /app composer:2 \
    sh -c "composer install --quiet && vendor/bin/phpunit"
}

test_ruby() {
  docker run --rm -v "$ROOT/packages/ruby:/app" -v webpipe-ruby-gems:/usr/local/bundle \
    -w /app ruby:3.4 \
    sh -c "bundle install --quiet && ruby -Ilib -Itest test/client_test.rb && ruby -Ilib -Itest test/browser_test.rb"
}

test_java() {
  docker run --rm -v "$ROOT/packages/java:/app" -v webpipe-maven-repo:/root/.m2 \
    -w /app maven:3.9-eclipse-temurin-17 mvn -B test
}

test_rust() {
  docker run --rm -v "$ROOT/packages/rust:/app" \
    -v webpipe-cargo-registry:/usr/local/cargo/registry \
    -v webpipe-cargo-target:/app/target \
    -w /app rust:1.86 cargo test
}

test_dotnet() {
  docker run --rm -v "$ROOT/packages/dotnet:/app" -v webpipe-nuget-cache:/root/.nuget \
    -w /app/tests/WebpipeSdk.Tests mcr.microsoft.com/dotnet/sdk:8.0 dotnet test
}

for lang in "${LANGUAGES[@]}"; do
  case "$lang" in
    python|js|go|php|ruby|java|rust|dotnet) run "$lang" "test_${lang}" ;;
    *) echo "unknown language: $lang"; exit 1 ;;
  esac
done

echo "================================================================"
if [ ${#FAILED[@]} -eq 0 ]; then
  echo "🎉 all passed: ${LANGUAGES[*]}"
else
  echo "💥 failed: ${FAILED[*]}"
  exit 1
fi
