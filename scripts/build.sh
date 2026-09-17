#!/usr/bin/env bash
# Build release artifacts for each SDK in Docker — no host toolchain required.
# Artifacts land in artifacts/{lang}/. Go and PHP have no build artifacts
# (they distribute source via git tags); for them we verify only.
#
# Usage:
#   scripts/build.sh            # all languages
#   scripts/build.sh python js  # subset
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/artifacts"

LANGUAGES=(python js go php ruby java rust dotnet)
if [ $# -gt 0 ]; then
  LANGUAGES=("$@")
fi

FAILED=()

run() {
  local lang="$1"; shift
  echo "================================================================"
  echo "▶ build ${lang}"
  echo "================================================================"
  mkdir -p "$OUT/$lang"
  if "$@"; then
    echo "✅ ${lang} → artifacts/${lang}/"
  else
    echo "❌ ${lang} FAILED"
    FAILED+=("$lang")
  fi
  echo
}

build_python() {
  docker run --rm -v "$ROOT:/repo" -v webpipe-pip-cache:/root/.cache/pip \
    -w /repo/packages/python python:3.12-slim \
    sh -c "pip install -q hatch && hatch build"
  cp packages/python/dist/* "$OUT/python/"
}

build_js() {
  docker run --rm -v "$ROOT:/repo" -v webpipe-npm-cache:/root/.npm \
    -w /repo/packages/js node:22 \
    sh -c "npm ci --silent && npm run build --silent"
  rm -rf "$OUT/js" && mkdir -p "$OUT/js"
  cp -r packages/js/dist/* "$OUT/js/"
  cp packages/js/package.json packages/js/README.md "$OUT/js/"
}

build_go() {
  # No artifact — go get pulls source by git tag (packages/go/vX.Y.Z). Verify only.
  docker run --rm -v "$ROOT:/repo" -v webpipe-go-cache:/go/pkg \
    -w /repo/packages/go golang:1.23 \
    sh -c "gofmt -l . && go vet ./... && go build ./..."
  echo "(go: no artifact; publish via git tag packages/go/vX.Y.Z)"
}

build_php() {
  # No artifact — Packagist pulls source by git tag. Validate only.
  docker run --rm -v "$ROOT/packages/php:/app" composer:2 \
    composer validate --strict
  echo "(php: no artifact; publish via Packagist + git tag)"
}

build_ruby() {
  docker run --rm -v "$ROOT/packages/ruby:/app" -w /app ruby:3.4 \
    gem build webpipe-sdk.gemspec
  cp packages/ruby/webpipe-sdk-*.gem "$OUT/ruby/"
}

build_java() {
  docker run --rm -v "$ROOT/packages/java:/app" -v webpipe-maven-repo:/root/.m2 \
    -w /app maven:3.9-eclipse-temurin-17 mvn -B -q package -DskipTests
  cp packages/java/target/webpipe-sdk-*.jar "$OUT/java/"
}

build_rust() {
  docker run --rm -v "$ROOT/packages/rust:/app" \
    -v webpipe-cargo-registry:/usr/local/cargo/registry \
    -v webpipe-cargo-target:/app/target \
    -w /app rust:1.86 cargo package --allow-dirty
  cp packages/rust/target/package/webpipe-sdk-*.crate "$OUT/rust/"
}

build_dotnet() {
  docker run --rm -v "$ROOT/packages/dotnet:/app" -v webpipe-nuget-cache:/root/.nuget \
    -w /app/src/WebpipeSdk mcr.microsoft.com/dotnet/sdk:8.0 \
    dotnet pack -c Release --output /app/artifacts-out
  cp packages/dotnet/artifacts-out/Webpipe.Sdk.*.nupkg "$OUT/dotnet/"
  rm -rf packages/dotnet/artifacts-out
}

for lang in "${LANGUAGES[@]}"; do
  case "$lang" in
    python|js|go|php|ruby|java|rust|dotnet) run "$lang" "build_${lang}" ;;
    *) echo "unknown language: $lang"; exit 1 ;;
  esac
done

echo "================================================================"
if [ ${#FAILED[@]} -eq 0 ]; then
  echo "🎉 all built: ${LANGUAGES[*]} (artifacts in artifacts/)"
else
  echo "💥 failed: ${FAILED[*]}"
  exit 1
fi
