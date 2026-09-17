# WebPipe SDK 发布流程指南

> 本文档面向维护者，说明如何把 8 个语言的 SDK 发布到各自的官方 registry。
> 首次发布按「一次性准备」做一遍，之后每次发版按「常规发版流程」执行。

## 0. 先搞清楚：monorepo 对发布的影响

**结论：对 6/8 语言无影响，对 Go 和 PHP 有两个必须遵守的约定。**

| 语言 | Registry | 发布物 | monorepo 影响 |
|---|---|---|---|
| Python | PyPI | wheel/sdist 产物 | ✅ 无影响。用户装的是产物，不 clone 仓库 |
| JS | npm | tgz 产物 | ✅ 无影响，同上 |
| Ruby | RubyGems | `.gem` 产物 | ✅ 无影响，同上 |
| Java | Maven Central | jar + pom | ✅ 无影响，同上 |
| Rust | crates.io | `.crate` 产物 | ✅ 无影响，同上 |
| .NET | NuGet | `.nupkg` 产物 | ✅ 无影响，同上 |
| Go | Go Modules | **git tag 本身** | ⚠️ 子目录模块，tag 必须是 `packages/go/vX.Y.Z` 格式 |
| PHP | Packagist | **git tag 本身** | ⚠️ Packagist 只读仓库根的 composer.json，需要 split 镜像仓库（见 §3） |

> 关于"Python 是 clone 后直接 build"的疑问：这是误解。`pip install webpipe-sdk`
> 下载的是发布者预先 `hatch build` 出来的 wheel，和源码仓库布局无关。
> 用户 clone 源码 build 只发生在「从 GitHub 直接安装」的少数场景。

### Git tag 约定（monorepo 关键）

统一版本号（如 `0.2.0`），按语言打前缀 tag：

```
python-v0.2.0   js-v0.2.0   php-v0.2.0   ruby-v0.2.0
java-v0.2.0     rust-v0.2.0 dotnet-v0.2.0
packages/go/v0.2.0          ← Go 例外，路径前缀是 Go Modules 的硬性要求
```

## 1. 一次性准备（每语言只做一次）

| Registry | 准备事项 |
|---|---|
| PyPI | 注册 pypi.org 账号 → Account Settings → API token（建议开 2FA，token 限定项目） |
| npm | 注册 npmjs.com → 开 2FA → `npm login` 或创建 Granular Access Token |
| RubyGems | 注册 rubygems.org → `gem signin` |
| crates.io | 用 GitHub 账号登录 crates.io → Account Settings → API token |
| NuGet | 注册 nuget.org（Microsoft 账号）→ API Keys → 创建 scoped key |
| Maven Central | 注册 [central.sonatype.com](https://central.sonatype.com) → 验证 `ai.webpipe` 命名空间（用 GitHub 账号快捷验证 io.github.<user>，或自有域名 webpipe.ai 加 DNS TXT 记录）→ 生成 GPG 密钥并发布公钥到 keys.openpgp.org |
| Go | 无需账号，proxy.golang.org 自动抓取公开 git tag |
| Packagist | 注册 packagist.org → 见 §3 的 split 仓库方案 |

## 2. 常规发版流程（每次发版）

1. **版本号同步**：8 个包的 version 字段改成同一新版本（`packages/*/`: `_version.py`、`package.json`、go 无（靠 tag）、`WebpipeClient.php`/`gemspec`/`pom.xml`/`Cargo.toml`/`csproj`）。
2. **本地验证**：
   ```bash
   scripts/test.sh          # 8 语言全绿
   scripts/build.sh         # 产物齐集 artifacts/
   ```
3. **提交并打 tag**（§0 的约定）。
4. **逐语言发布**（顺序随意，见 §4 命令卡）。
5. **验证**：每种语言用全新环境按 README 的安装命令装一遍，跑 `examples/` 的 `scrape` 子命令冒烟。

## 3. PHP 的 split 镜像仓库（重要）

Packagist 要求 `composer.json` 在仓库根目录。monorepo 的解法（symfony/laravel 同款）：

1. 建一个只读镜像仓库，如 `webpipe-ai/webpipe-sdk-php`。
2. 每次发版时把 `packages/php` 的内容同步过去并打纯版本号 tag：
   ```bash
   git subtree split --prefix=packages/php -b php-split
   git push git@github.com:webpipe-ai/webpipe-sdk-php.git php-split:main --force-with-lease
   git push git@github.com:webpipe-ai/webpipe-sdk-php.git v0.2.0
   ```
3. 在 packagist.org 提交 `webpipe-ai/webpipe-sdk-php` 仓库地址，开启 GitHub webhook 自动同步。
4. 此流程可用一个 GitHub Action 在推送 `php-v*` tag 时自动完成。

> Go 不需要 split：`go get github.com/webpipe-ai/webpipe-sdk/packages/go` 直接可用
> （模块路径已在 go.mod 中声明为含子目录的路径）。想更短可加 vanity import，非必需。

## 4. 各语言发布命令卡

### Python → PyPI

```bash
cd packages/python
pip install hatch twine
hatch build                 # 产物在 dist/
twine upload dist/*         # 或 hatch publish
# 验证: pip install webpipe-sdk==<version>
```

### JS → npm

```bash
cd packages/js
npm ci && npm run build
npm publish --access public  # 首次发布必须加 --access public
# 验证: npm install webpipe-sdk@<version>
```

### Go → Go Modules

```bash
# 确保 go.mod 的 module 路径 = github.com/webpipe-ai/webpipe-sdk/packages/go
git tag packages/go/v0.2.0
git push origin packages/go/v0.2.0
# 几分钟后自动出现在 proxy.golang.org；可用以下命令强制拉取验证：
GOPROXY=https://proxy.golang.org go list -m github.com/webpipe-ai/webpipe-sdk/packages/go@v0.2.0
```

### PHP → Packagist

完成 §3 的 split 推送后，Packagist webhook 自动收录新 tag，无需手动命令。
验证：`composer require webpipe/webpipe-sdk`

### Ruby → RubyGems

```bash
cd packages/ruby
gem build webpipe-sdk.gemspec   # 产物 webpipe-sdk-0.2.0.gem
gem push webpipe-sdk-0.2.0.gem
# 验证: gem install webpipe-sdk -v 0.2.0
```

### Java → Maven Central

`~/.m2/settings.xml` 配置 Sonatype token 与 GPG 后：

```bash
cd packages/java
# pom.xml 需补齐发布配置（见下方清单），然后：
mvn -B clean deploy
# 到 central.sonatype.com 的 Portal 手动点击 "Publish"（或配置自动发布）
# 验证: 30 分钟后 mvnrepository.com/artifact/ai.webpipe/webpipe-sdk 可见
```

pom.xml 发布清单（发版前必须补齐）：`<distributionManagement>`（Central Portal 的 staging 地址）、`maven-gpg-plugin`（签名，Central 强制）、`maven-source-plugin` + `maven-javadoc-plugin`（源码与 javadoc jar，Central 强制）、`<scm>`/`<developers>` 元数据。

### Rust → crates.io

```bash
cd packages/rust
cargo login <crates.io token>
cargo publish
# 验证: cargo add webpipe-sdk@0.2.0
```

### .NET → NuGet

```bash
cd packages/dotnet/src/WebpipeSdk
dotnet pack -c Release
dotnet nuget push bin/Release/Webpipe.Sdk.0.2.0.nupkg \
  --api-key <nuget key> --source https://api.nuget.org/v3/index.json
# 验证: dotnet add package Webpipe.Sdk --version 0.2.0
```

## 5. 建议的 CI 自动化（可选，后续做）

在 `.github/workflows/` 下两个工作流：

- **ci.yml**：push/PR 触发，跑 `scripts/test.sh` 全矩阵（GitHub Actions runner 自带 Docker）。
- **publish.yml**：`*-v*` tag 推送触发，矩阵按语言分别执行 §4 的发布命令（secrets 存各 registry token）；Go/PHP 的 tag 推送只触发 git 操作即可。

## 6. 发版检查清单（打印版）

- [ ] 8 个包版本号一致
- [ ] `scripts/test.sh` 全绿
- [ ] `scripts/build.sh` 产物齐集 `artifacts/`
- [ ] CHANGELOG（如维护）已更新
- [ ] 8 个语言 tag 已推送（Go 为 `packages/go/v*`）
- [ ] PHP split 仓库已同步并打 tag
- [ ] 各 registry 页面可见新版本
- [ ] 每语言用 README 安装命令 + `examples/` scrape 冒烟通过
