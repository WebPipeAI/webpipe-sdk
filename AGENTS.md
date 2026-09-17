# AGENTS.md

> 本文件是 webpipe-sdk 项目的"宪法"。任何 Agent / 贡献者在本仓库工作前必须阅读。
> 修改了本文档中提到的任何结构、约定、流程时，**必须同步更新本文档**。
> 本地补充（API 怪癖、内部 bug、测试账号）：`AGENTS.local.md`（gitignored，不入库）。

## 1. 项目定位

**webpipe-sdk** 是 [WebPipe.ai](https://webpipe.ai/) 的官方多语言 SDK monorepo。
目标：**让用户用 3 行代码接入 WebPipe API**。

- 设计参考：[firecrawl/firecrawl](https://github.com/firecrawl/firecrawl) 的 SDK 体系
- 取其精华：单 Client 入口、异步任务自动轮询、强类型、环境变量回退
- 去其糟粕：**拒绝方法参数膨胀**（firecrawl v2 单方法 20+ kwargs）、**拒绝 deprecated 别名堆积**、**拒绝过度抽象**

### 核心原则（按优先级）

1. **简单优先**：`client.scrape(url, formats=["markdown"])` 就能用。高级参数全部走可选的 options 对象。
2. **类型安全**：Python 用 pydantic v2，TS 开 strict。公开 API 100% 类型标注。
3. **最小依赖**：Python 仅 `httpx + pydantic`；TS **零运行时依赖**（native fetch，Node ≥ 18）。
4. **异步任务透明化**：`crawl()` 默认自动轮询到完成；`start_crawl()` 立即返回 job 句柄。
5. **浏览器会话对象化**：`session = client.browser.create()` 返回带方法的对象，而非裸 id。
6. **错误分层**：精确的异常类型（认证失败 ≠ 限流 ≠ 参数错误），绝不只抛一个笼统 Error。
7. **自动重试**：429 / 5xx 指数退避，默认重试 2 次，可配置。
8. **配置链**：构造参数 > 环境变量 > 默认值。绝不硬编码密钥。

## 2. API 参考（SDK 的唯一事实来源）

- **生产 Base URL**：`https://api.web2json.ai`
- **自托管**：`http://localhost:8080`（文档示例地址）
- **认证**：请求头 `Authorization: Bearer wc-xxxx...`（API Key，`wc-` 前缀共 67 字符）
- **环境变量**：`WEBPIPE_API_KEY`（密钥）、`WEBPIPE_API_URL`（覆盖 base URL）
- **错误格式**：HTTP 错误码 + JSON body `{ "success": false, "error": "..." }`

| 错误码 | SDK 异常 | 含义 |
|---|---|---|
| 400 | BadRequestError | 参数错误（缺 url、格式非法） |
| 401 | AuthenticationError | Key 缺失 / 无效 / 已删除 |
| 403 | ForbiddenError | 访问他人 Browser Session |
| 404 | NotFoundError | Session/Job 不存在或已过期 |
| 429 | RateLimitError | 超出频率 / Session 数量达上限（默认 2 个） |
| 5xx | ServerError | 抓取超时、目标站不可达 |

### 2.1 Scrape — `POST /api/v1/scrape`

抓取单页。参数：

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `url` | string | **必填** | 目标 URL |
| `formats` | string[] | `["markdown"]` | `markdown` / `html` / `rawHtml` / `links` / `metadata` / `json` |
| `json_schema` | object | `{}` | formats 含 `json` 时生效；支持 `x-source` 扩展指定字段来源 |
| `use_proxy` | bool | `false` | 启用后端代理池 |
| `proxy` | string | 自动 | 自定义代理，优先级高于代理池 |
| `cookie` | string | `""` | Header 格式 Cookie 字符串 |

响应：`{ success: true, data: { markdown?, html?, rawHtml?, links?: [{text, href}], metadata?, json? } }`

### 2.2 Map — `POST /api/v1/map`

发现网站所有 URL（robots.txt → sitemap → 页内链接）。参数：`url`(必填)、`limit`(5000，上限 50000)、`search`、`sitemap`(`include`/`only`/`exclude`)、`same_domain`(true)、`use_proxy`、`proxy`、`cookie`。
响应：`{ success: true, links: [{ url, title, description }] }`。
实测：无结果时额外返回 `warning`（建议改用 Browser Session 的提示文案）和 `browser_docs` 字段，SDK 无需特殊处理。

### 2.3 Crawl — 异步任务

`POST /api/v1/crawl` 提交 → 返回 `{ success, id, url }`；`GET /api/v1/crawl/{id}` 轮询。

参数：`url`(必填)、`limit`(100，上限 10000)、`max_discovery_depth`、`include_paths`(正则数组)、`exclude_paths`(正则数组)、`allow_subdomains`(false)、`allow_external_links`(false)、`crawl_entire_domain`(false)、`ignore_query_params`(false)、`sitemap`(`include`/`skip`/`only`)、`delay`(0)、`max_concurrency`(3，1–10)、`scrape_options`（透传 Scrape 参数）、`use_proxy`、`proxy`、`cookie`。

轮询响应：`{ success, status, total, completed, data: [ScrapeResult...], errors, expires_at }`，`status` ∈ `scraping` / `completed` / `failed`。**结果仅保留 24 小时。**

### 2.4 Browser Session — 持久化无头浏览器

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/browser` | 创建（异步，立即返回 `id`，`status=starting`） |
| GET | `/api/v1/browser/{id}` | 查询状态 + 历史 + 最近数据 |
| DELETE | `/api/v1/browser/{id}` | 停止会话 |
| POST | `/api/v1/browser/{id}/navigate` | 跳转新 URL 并抓取 |
| POST | `/api/v1/browser/{id}/scrape` | 抓取当前页（不跳转，更快） |
| POST | `/api/v1/browser/{id}/action` | 交互操作 |
| GET/POST | `/api/v1/browser/{id}/cookies` | 读取 / 注入 / 清空 Cookie |
| POST | `/api/v1/browser/{id}/save_session` | 持久化登录状态（配合 `session_name` 复用） |

创建参数：`url?`、`cookie?`、`proxy?`、`session_name?`。
navigate 参数：`url`、`formats`、`scroll_to_bottom`、`max_scrolls`(≤30)、`scroll_wait`(ms)、`scroll_step`(px)。
action 类型：`click` / `fill` / `select` / `press` / `scroll` / `wait` / `wait_for` / `screenshot` / `evaluate` / `hover` / `check`。

实测响应形态：
- navigate / scrape：`{ success, ok, cmd, data: { markdown?, html?, links?, metadata?, url, title } }`，SDK 取 `data`。
- action（含 evaluate/screenshot）：`{ success, ok, cmd, type, result? }`，`evaluate` 的 JS 返回值在 `result` 字段。SDK 的 `action()` 返回原始 payload，不猜测字段。

**关键行为**：
- Session 状态机：`starting → running → stopped / error / expired`；30 分钟空闲自动销毁；每用户最多 2 个并发 running。
- `starting` 状态下发的命令服务端会等待就绪（≤60s），但 SDK 仍提供 `wait_until_running()` 以获得可控体验。
- navigate 与 scrape 的区别：navigate 带反爬处理 + 清缓存 + 跳转；scrape 只提取当前页。

## 3. 仓库结构

```
webpipe_sdk/
├── AGENTS.md            ← 本文件
├── README.md            ← 对外门面（英文）
├── scripts/
│   ├── test.sh          # 全语言测试矩阵（Docker 优先，无宿主依赖；可传语言名跑子集）
│   └── build.sh         # 全语言打包（产物 → artifacts/；Go/PHP 无产物仅校验）
├── packages/
│   ├── python/          → PyPI: webpipe-sdk  (import webpipe)
│   │   ├── pyproject.toml
│   │   ├── src/webpipe/
│   │   │   ├── __init__.py      # 公开导出
│   │   │   ├── client.py        # WebpipeClient / AsyncWebpipeClient
│   │   │   ├── browser.py       # BrowserSession / AsyncBrowserSession
│   │   │   ├── types.py         # pydantic 模型
│   │   │   ├── errors.py        # 异常分层
│   │   │   └── _http.py         # httpx 封装（重试/错误映射），私有
│   │   └── tests/
│   ├── js/              → npm: webpipe-sdk
│   │   ├── package.json
│   │   ├── src/
│   │   │   ├── index.ts         # 公开导出
│   │   │   ├── client.ts        # Webpipe
│   │   │   ├── browser.ts       # BrowserSession
│   │   │   ├── types.ts
│   │   │   ├── errors.ts
│   │   │   └── http.ts          # fetch 封装，私有
│   │   └── tests/
│   ├── go/              → go get github.com/webpipe-ai/webpipe-sdk/packages/go
│   │   ├── go.mod               # 零依赖，仅标准库
│   │   ├── webpipe.go           # Client + NewClient(functional options)
│   │   ├── browser.go           # BrowserResource / BrowserSession
│   │   ├── types.go             # struct + json tag（snake_case 天然映射）
│   │   ├── errors.go            # 哨兵错误 + APIError（errors.Is/As）
│   │   ├── http.go              # net/http 封装（重试/错误映射），私有
│   │   └── webpipe_test.go
│   ├── php/             → Composer: webpipe/webpipe-sdk
│   │   ├── composer.json        # PHP ≥8.1，仅 Guzzle 一个依赖
│   │   ├── src/
│   │   │   ├── WebpipeClient.php
│   │   │   ├── BrowserResource.php / BrowserSession.php
│   │   │   ├── Document/Link/MapLink/CrawlJob/CrawlJobStart.php  # readonly DTO
│   │   │   ├── Exception/       # 异常分层
│   │   │   └── Internal/HttpClient.php  # Guzzle 封装，私有
│   │   └── tests/               # PHPUnit + Guzzle MockHandler
│   ├── ruby/            → RubyGems: webpipe-sdk
│   │   ├── webpipe-sdk.gemspec  # Ruby ≥3.0，零依赖（stdlib net/http）
│   │   ├── lib/webpipe/
│   │   │   ├── client.rb        # Webpipe::Client（kwargs 与 API snake_case 1:1）
│   │   │   ├── browser.rb       # BrowserSession / BrowserResource
│   │   │   ├── types.rb         # Struct + from_hash
│   │   │   ├── errors.rb        # 异常分层
│   │   │   ├── http_client.rb   # net/http 封装，私有
│   │   │   └── version.rb
│   │   └── test/                # minitest + WEBrick DispatchServlet mock
│   ├── java/            → Maven: ai.webpipe:webpipe-sdk
│   │   ├── pom.xml              # Java ≥17，仅 Jackson 一个依赖（HTTP 走 java.net.http）
│   │   └── src/
│   │       ├── main/java/ai/webpipe/sdk/
│   │       │   ├── WebpipeClient.java       # builder 模式
│   │       │   ├── BrowserResource.java / BrowserSession.java
│   │       │   ├── options/     # fluent options（public 字段 + 链式 setter）
│   │       │   ├── model/       # records + @JsonProperty
│   │       │   ├── exceptions/  # 异常分层，ApiException.forStatus()
│   │       │   └── internal/HttpClient.java
│   │       └── test/java/       # JUnit 5 + JDK 内置 HttpServer mock
│   ├── rust/            → crates.io: webpipe-sdk
│   │   ├── Cargo.toml           # async（reqwest rustls + tokio + serde + thiserror）
│   │   ├── src/
│   │   │   ├── lib.rs           # 公开导出
│   │   │   ├── client.rs        # Client + ClientBuilder
│   │   │   ├── browser.rs       # BrowserResource / BrowserSession
│   │   │   ├── types.rs         # serde structs（snake_case 靠 serde 属性）
│   │   │   ├── error.rs         # thiserror 分层
│   │   │   └── http.rs          # reqwest 封装（重试/错误映射），私有
│   │   ├── tests/client.rs      # wiremock
│   │   └── examples/demo.rs     # cargo run --example demo（兼作快速入门 CLI）
│   └── dotnet/          → NuGet: Webpipe.Sdk
│       ├── src/WebpipeSdk/      # .NET 8+，零运行时依赖（HttpClient + System.Text.Json）
│       │   ├── WebpipeClient.cs
│       │   ├── BrowserResource.cs / BrowserSession.cs
│       │   ├── Models.cs        # records + [JsonPropertyName]
│       │   ├── Options.cs       # 类 + 对象初始化器，显式 JsonPropertyName 映射
│       │   ├── Exceptions.cs    # 异常分层，ApiException.ForStatus()
│       │   └── Internal/HttpClientWrapper.cs
│       └── tests/WebpipeSdk.Tests/  # xUnit + mock HttpMessageHandler
├── examples/            # 快速入门 demo：每语言一个迷你 CLI，4 子命令对应 4 大功能
│   ├── README.md        # 各语言运行方式
│   ├── python/demo.py   # python demo.py <scrape|map|crawl|browser> [url]
│   ├── js/demo.mjs      # node demo.mjs <...>（package.json file: 依赖本地 SDK）
│   ├── go/main.go       # go run . <...>（go.mod replace 指向本地 SDK）
│   ├── php/demo.php     # php demo.php <...>（composer path 仓库指向本地 SDK）
│   ├── ruby/demo.rb     # ruby demo.rb <...>（require_relative 本地 lib，零安装）
│   ├── java/            # mvn exec:java（pom 依赖本地 install 的 SDK）
│   ├── dotnet/          # dotnet run（csproj 引用本地 SDK 项目）
│   └── → Rust 的 demo 在 packages/rust/examples/demo.rs（cargo 惯例）
└── docs/                # 设计文档、API 快照
```

新语言放 `packages/{lang}/`，目录结构对齐上述语言。

## 4. SDK 公共契约（所有语言必须一致）

### 4.1 客户端构造

```
api_key    : 构造参数 > WEBPIPE_API_KEY 环境变量；缺失时抛错并提示获取地址
base_url   : 构造参数 > WEBPIPE_API_URL > https://api.web2json.ai
timeout    : 默认 60s（Browser 命令耗时长，文档建议 ≥30s）
max_retries: 默认 2（429/5xx 指数退避）
```

所有请求带 `User-Agent: webpipe-sdk-{lang}/{version}`。

### 4.2 方法签名（概念层，各语言按惯例落地）

| 方法 | 行为 | 返回 |
|---|---|---|
| `scrape(url, options?)` | 同步抓取单页 | `Document` |
| `map(url, options?)` | 发现 URL | `MapLink[]` |
| `start_crawl(url, options?)` | 提交任务，立即返回 | `{ id, url }` |
| `get_crawl_status(id)` | 查一次状态 | `CrawlJob` |
| `crawl(url, options?, poll_interval=2s, timeout?)` | 提交并轮询到 completed/failed | `CrawlJob` |
| `browser.create(options?)` | 创建会话 | `BrowserSession` |

`BrowserSession` 方法：`refresh()` / `wait_until_running()` / `navigate(url, opts?)` / `scrape(opts?)` / `action(action)` / 便捷方法 `click` `fill` `select` `press` `scroll` `wait` `wait_for` `screenshot` `evaluate` `hover` `check` / `get_cookies` `set_cookies` `clear_cookies` / `save_session()` / `close()`。支持上下文管理器（Python `with` / JS `using` 或 try-finally），退出自动 `close()`。

### 4.3 命名映射

- API 请求字段全部 snake_case，**原样发送，不做递归 key 转换**（`json_schema.properties` 里的用户字段名不可被改动！）。
- Python：参数 snake_case，与 API 一致；响应模型用 alias 兼容 `sourceURL`/`statusCode`/`rawHtml`。
- TS：参数 camelCase（`jsonSchema`/`useProxy`/`scrollToBottom`…），在请求构造器里**显式**映射为 snake_case；响应字段保持 API 原样（API 响应本身就是混合命名，top-level 的 `expires_at` 等映射为 camelCase）。

### 4.4 轮询语义

- `crawl()`：`status == "completed"` 返回；`"failed"` 抛 `CrawlError`；超 `timeout` 抛 `TimeoutError`（Python 内置 TimeoutError / JS `WebpipeTimeoutError`）。
- `wait_until_running()`：`running` 返回；`error/expired/stopped` 抛错；默认超时 60s。

## 5. 各语言工程约定

### Python（packages/python）
- 构建：hatchling；`requires-python >= 3.9`；依赖仅 `httpx>=0.27`、`pydantic>=2`。
- 同步 `WebpipeClient` + 异步 `AsyncWebpipeClient` 双客户端，共享 `_http` 与 `types`。
- 代码风格：ruff，line-length 100；snake_case。
- 测试：pytest + respx（mock HTTP，不依赖真实网络/密钥）。
- 命令：
  ```bash
  cd packages/python
  python3 -m venv .venv && source .venv/bin/activate
  pip install -e ".[dev]"
  pytest
  ```

### TypeScript（packages/js）
- 运行时：Node ≥ 18（native fetch），零运行时依赖。
- 构建：tsup → ESM + CJS 双产物 + `.d.ts`；`tsc --noEmit` 严格类型检查。
- 测试：vitest（mock fetch）。
- 命令：
  ```bash
  cd packages/js
  npm install
  npm run build && npm run typecheck && npm test
  ```

### Go（packages/go）
- **零依赖**，仅标准库；Go ≥ 1.22；module path `github.com/webpipe-ai/webpipe-sdk/packages/go`。
- `NewClient(WithAPIKey/WithBaseURL/WithTimeout/WithMaxRetries/WithHTTPClient)` functional options。
- 错误分层用 Go 惯例：哨兵错误（`ErrAuthentication`…）+ `errors.Is` 分类，`errors.As` 取 `*APIError` 详情；可选 bool 用 `*bool`（区分"未设置"与"显式 false"，如 `SameDomain`）。
- 请求体直接用 struct json tag 映射 snake_case，天然无递归转换问题。
- 测试：`net/http/httptest` mock server。命令：`go build ./... && go vet ./... && gofmt -l . && go test ./...`。

### PHP（packages/php）
- PHP ≥ 8.1，唯一依赖 Guzzle 7；命名空间 `Webpipe\`，PSR-4。
- 全部命名参数（`$client->scrape($url, formats: ['markdown'])`），与 Python kwargs 对齐。
- DTO 为 final readonly 属性类 + `fromArray()`；异常在 `Webpipe\Exception\` 下分层，`ApiException::forStatus()` 工厂映射。
- **异步薄弱语言的回调约定**：`crawl(..., onProgress: callable)` 每次轮询回调当前 `CrawlJob`，用于进度展示（PHP 无主流异步运行时，回调即惯例）。
- **无 PHP 环境时用 Docker 验证**（composer 镜像自带 PHP + Composer）：
  ```bash
  cd packages/php
  docker run --rm -v "$PWD:/app" composer install
  docker run --rm -v "$PWD:/app" -w /app --entrypoint php composer vendor/bin/phpunit
  ```

### Ruby（packages/ruby）
- Ruby ≥ 3.0，**零运行时依赖**（stdlib `net/http`）；kwargs 与 API snake_case 1:1，天然无 key 转换问题。
- **回调约定**：`crawl(url, ...) { |job| ... }` 每次轮询 yield 当前 `CrawlJob`（block 即 Ruby 惯用回调）。
- 测试：minitest + WEBrick。注意坑：`mount_proc` 只注册 do_GET/do_POST（DELETE 会 405）——必须用自定义 servlet 覆写 `service()`。
- **无现代 Ruby 时用 Docker 验证**（gems 缓存在命名卷）：
  ```bash
  cd packages/ruby
  docker run --rm -v "$PWD:/app" -v webpipe-ruby-gems:/usr/local/bundle -w /app ruby:3.4 \
    sh -c "bundle install --quiet && ruby -Ilib -Itest test/client_test.rb && ruby -Ilib -Itest test/browser_test.rb"
  ```

### Java（packages/java）
- Java ≥ 17（records）；唯一依赖 Jackson Databind；HTTP 用 JDK 内置 `java.net.http`。
- options 为 public 字段 + 链式 setter；请求序列化用 Jackson `SNAKE_CASE` 命名策略（只作用于 POJO 属性，`jsonSchema` Map key 不受影响）；响应 record 用 `@JsonProperty` 对齐混合命名。
- **双 API 风格**：默认同步阻塞方法；每个方法都有 `*Async` 变体（`sendAsync` + `CompletableFuture<T>`，轮询走调度器不占线程），用户按需选择。
- 测试：JUnit 5 + JDK 内置 `com.sun.net.httpserver.HttpServer`。
- **无 Maven 时用 Docker 验证**（.m2 缓存在命名卷）：
  ```bash
  cd packages/java
  docker run --rm -v "$PWD:/app" -v webpipe-maven-repo:/root/.m2 -w /app \
    maven:3.9-eclipse-temurin-17 mvn -B test
  ```

### Rust（packages/rust）
- async（reqwest rustls + tokio + serde + thiserror）；`Client::new` / `Client::from_env` / `Client::builder`。
- 错误分层用 thiserror enum（`WebpipeError::Authentication`…，`map_status` 映射）；serde 属性处理 snake_case 与 `rawHtml` rename。
- 测试：wiremock。注意：**rust:1.83 镜像太旧**（新版 crate 要求 edition2024 / Rust ≥1.85），固定用 `rust:1.86`（crate 生态要求会持续上移，构建失败时先上调镜像版本）：
  ```bash
  cd packages/rust
  docker run --rm -v "$PWD:/app" -v webpipe-cargo-registry:/usr/local/cargo/registry \
    -v webpipe-cargo-target:/app/target -w /app rust:1.86 cargo test
  ```

### .NET（packages/dotnet）
- .NET 8+；**零运行时依赖**（`HttpClient` + `System.Text.Json` 全在框架内）；xUnit 测试 + mock `HttpMessageHandler`。
- Options 为类 + 对象初始化器（`new ScrapeOptions { Formats = ["markdown"] }`），显式 `[JsonPropertyName]` 映射 snake_case；`JsonNode` **没有** `Deserialize<T>()` 实例方法，反序列化统一走内部 `Json.Deserialize<T>`（`ToJsonString` + `JsonSerializer`）。
- **Docker 验证**（NuGet 缓存在命名卷）：
  ```bash
  cd packages/dotnet
  docker run --rm -v "$PWD:/app" -v webpipe-nuget-cache:/root/.nuget -w /app/tests/WebpipeSdk.Tests \
    mcr.microsoft.com/dotnet/sdk:8.0 dotnet test
  ```

## 6. 新增语言 SDK 流程（经验沉淀）

1. **复制心智模型**：读 `packages/python/src/webpipe/client.py` 与 `packages/js/src/client.ts`，它们是参考实现。
2. **建目录** `packages/{lang}/`，对齐 §3 结构（client / types / errors / http / tests）。
3. **实现 §4 公共契约**的全部方法与异常，命名遵循该语言惯例（Go 用 `NewClient` + functional options，Rust 用 builder…）。
4. **README** 必须含：安装命令、3 个最小示例（scrape / crawl / browser）。
5. **测试**：mock HTTP 层，覆盖：成功路径、401→认证异常、429→重试、crawl 轮询到完成、browser session 生命周期。
6. **Examples**：在 `examples/{lang}/` 加同款迷你 CLI（scrape/map/crawl/browser 4 子命令，默认 URL 与其他语言一致），并用本地路径依赖（非发布版）验证能跑通。
7. **更新本文件** §3、§5 与根 README 的语言表格。
8. 版本号全语言同步，从 `0.1.0` 起步。

## 7. 反模式清单（明确禁止）

- ❌ 单方法超过 ~10 个**非透传**参数 → 收进 options 对象（firecrawl 的教训：20+ kwargs 混合常用项与企业级冷门项）。**例外**：与 API 字段 1:1 对应的透传参数允许超出此限（如 `start_crawl` 的 16 个字段），但必须全部 keyword-only、有默认值、有类型标注，不得发明 API 没有的派生参数。
- ❌ 为兼容保留 deprecated 别名 → 0.x 阶段直接改，不背历史包袱。
- ❌ SDK 内部递归转换请求体 key 命名 → 会破坏 `json_schema` 用户字段。
- ❌ 吞掉 API 错误信息 → 异常必须携带服务端 `error` 原文与 HTTP 状态码。
- ❌ 引入重依赖（Python 不要 requests+urllib3 老栈；JS 不要 axios）。
- ❌ 把 API Key 写进任何代码、测试、示例。

## 8. 决策记录

| 日期 | 决策 | 理由 |
|---|---|---|
| 2026-07-27 | Monorepo + `packages/` 布局 | 对齐 firecrawl `apps/`，统一版本与文档 |
| 2026-07-27 | 首批 Python + TypeScript | 覆盖 AI/数据管道 与 Web 两大生态；其余语言按 §6 流程扩展 |
| 2026-07-27 | 默认 base_url = 生产地址 | 文档示例全是 localhost 属自托管遗留；SDK 面向付费用户应开箱即用 |
| 2026-07-27 | `crawl()` 默认阻塞轮询 | firecrawl 验证过的最佳 DX；异步控制交给 `start_crawl()` |
| 2026-07-27 | Python 双客户端（sync/async） | 现代 SDK 标配，httpx 天然支持 |
| 2026-07-27 | JS 零依赖 native fetch | Node 18+ 已内置，axios 是糟粕 |
| 2026-07-27 | `action()` 返回原始 payload dict | evaluate/screenshot 的返回字段文档未明确约定，实测为 `result`；不猜测、不封装，待 API 契约稳定后再加类型化封装 |
| 2026-07-27 | Java 双 API 风格（同步 + `*Async` CompletableFuture） | 同步是 Java 用户默认预期；异步给并发/响应式场景，实现零额外依赖（`sendAsync` 内置） |
| 2026-07-27 | PHP/Ruby 用回调而非异步运行时 | PHP 无主流异步、Ruby async 生态小众；`onProgress`/yield 进度回调是两语言惯用法 |
| 2026-07-27 | 构建验证全走 Docker + 固定镜像版本 | 宿主零依赖、结果可复现；rust 固定 1.85（edition2024 地板），composer 固定 2 |
| 2026-07-27 | monorepo 发布 tag 约定 `{lang}-vX.Y.Z`，Go 用 `packages/go/vX.Y.Z` | Go Modules 对子目录模块的硬性路径前缀要求；PHP 需 split 镜像仓库（Packagist 只认根 composer.json），详见 docs/publishing.md |

## 9. 已知 API 问题（已反馈官方，SDK 侧已做兼容）

1. 文档环境变量名不统一（`WEBCRAWLER_API_KEY` vs `WEBPIPE_API_KEY`）→ SDK 统一用 `WEBPIPE_API_KEY`。
2. 文档示例 base URL 全是 `localhost:8080`，与生产 `api.web2json.ai` 不一致。
3. Crawl 提交响应的 `url` 字段值是 `"GET /api/v1/crawl/{id}"` 这样的字符串而非真实 URL —— SDK 只取 `id`，不依赖该字段。
4. 错误响应缺机器可读的 `code` 字段 → SDK 按 HTTP 状态码映射异常。
5. 暂无 `DELETE /api/v1/crawl/{id}`（取消任务）与批量 scrape 端点 → 暂不封装，待 API 支持。

> 实测发现的 API 怪癖细节、已上报的官方 bug、本地测试账号等**不便公开**的
> 内容，记录在本地的 `AGENTS.local.md`（已 gitignore，不存在时可忽略）。
