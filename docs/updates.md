# 热下发：包、投递协议、发布协议与客户端

- 状态：已定（2026-09-18；2026-11-22 发布目标 `app` / `hostVersion` / `updatesUrl` 可写进 `tinyui.config.json`，见 build-chain.md §2.1；2026-11-22 改 §1.4：内置包由宿主构建时解析、不入库，`tinyui pull` 待两个宿主迁完后删；2026-11-18 加 §8 包下线；2026-11-13 加 `tinyui embed`（§1.4）；2026-09-19 加发布协议、签名、服务端形态；2026-09-19 改为多包模型——App 由 N≥1 个包组成，包名进模块名与 URL，公钥进 manifest，`Updates` 以一组包为单位；2026-09-22 修订：签名覆盖不可变 `manifest.json` 的原始字节，指针文件 `current.json` 只含 `version` / `rollout` / `signature`，启动时重算 installed 包的 sha256；2026-09-23 兼容键 `runtimeVersion` 改名 `hostVersion` 并限定为正整数；同日加 §1.3 发布前核对目标宿主版本、§6 的 app token 与宿主快照端点；2026-09-24 按 ADR-006 §2.11 修订：运行时随宿主、包只含页面，manifest 去 `runtime`，`tinyui` 从"等于宿主"改为"与宿主兼容"（同 major 且不高于宿主），宿主快照的 `tinyui` 行改为下限，单纯升 tinyui 不加 `hostVersion`；2026-09-25 按 ADR-007：manifest 加 `i18n`、`requires` 与宿主快照加网络通道、`hostVersion` 口径随宿主契约收窄；2026-10-27 §4.4：`Running` 在选定之后、重算 sha256 之前发出，损坏时再发一次；同日 §4.5：回退先重挂页面、再等包锁落盘，不可取消，落盘失败不抛；同日加 §4.6：可选的 `UpdatesScheduler` 统一检查时机，检查结果事件带 `trigger`）；已实现，进度见 [updates-plan.md](./updates-plan.md)
- 来源：[ADR-006](./adr-006-hot-updates.md)；[build-chain.md](./build-chain.md) §2（manifest、模块名、`tinyui.config.json`）、§7（buildId 与 source map）
- 四侧：CLI 产出与发布包（`tinyui build` / `bundle` / `publish`）；服务端实现投递与发布两组端点（参考实现 `tinyui-updates-server`，托管实例 `updates.tinyui.app`）；Kotlin 的 `Bundle`（core 库）与 `Updates`（`app.tinyui:tinyui-updates`）
- 协议规范只在本文一处；服务端仓的一致性测试以本文为准，不复制。**发布后字段与端点只增不改，未知字段透传**——这是两个仓能各自演进的前提

## 0. 模型

**App 由 N≥1 个包组成。包 = 所有权单元 = 分发单元 = 一次 `tinyui build` 的页面产物；运行时随宿主，不在包里（ADR-006 §2.11）。** 单包 App 是 N=1，没有任何特殊路径。

- 包名 `[a-z0-9-]+`，来自 JS 工程的 `tinyui.config.json`，同时是模块名前缀、URL 路径段、服务端的密钥与 token 粒度；按业务域命名（`subscription`），不按团队命名
- 包之间运行时没有共享模块、没有直接通道（共用代码在构建时各编一份）；运行时来自宿主、各包共用；跨包只走 Kotlin 中转的 JSON（[app-model.md](./app-model.md) §3、§7）
- 每个包独立校验、独立落盘、独立掷骰、独立回退；一个包坏了不影响其他包

## 1. 包

一个包 = 一次 `tinyui build` 的输出目录去掉 `.js` / `.js.map`：

```
manifest.json
pages/**/*.jsb
```

### 1.1 `manifest.json`

在 build-chain.md §2 的四个字段上扩展：

| 字段 | 写入者 | 内容 |
|---|---|---|
| `pages` / `files` / `buildIds` | `tinyui build` | 不变；`pages` 与 `files` 的键是含包名的模块名（`subscription/home`）。没有 `runtime` 字段：运行时模块随库（ADR-006 §2.11） |
| `name` | `tinyui build` | 包名，读自 `tinyui.config.json` |
| `publicKey` | `tinyui build` | 验签公钥（§7 格式），读自 `tinyui.config.json`；**信任来源只有内置包里的这份**，下载的 manifest 里的只用于诊断 |
| `version` | `tinyui build` | 包标识，目录名，只求唯一：`<createdAt 紧凑形式>-<git 短 sha（12 位）或 nogit>`，如 `20260918T100212Z-3f2a1c9e04b7`；`--version` 可覆盖 |
| `createdAt` | `tinyui build` | ISO 8601 UTC，新旧比较只看它 |
| `engine` | `tinyui build` | 字节码文件头里的引擎 commit（40 位 hex，所有 `.jsb` 一致，取第一个）；客户端要求等于宿主 |
| `hashes` | `tinyui build` | 模块名 → 该模块 `.jsb` 的 sha256 hex，键与 `files` 一致；另含每个 i18n 文件，键为其相对路径（`i18n/en.json`） |
| `presentation` | `tinyui build` | 页面模块名 → 呈现方式（`"sheet"`），只列声明了非缺省值的页面，都是缺省时省略（build-chain.md §11）。宿主经 `pageInfo` 读（§4.2）；不认识的值按全屏页处理 |
| `contracts` | `tinyui build` | 页面模块名 → `{ params, result? }`：页面收什么、带回什么的形状（build-chain.md §10），取自 `tinyui.config.json` 的 `contracts` 指向的文件，没配置时省略。`Bundle.page` 把它带进 `PageModule`，挂载时校验 props；宿主也可以经 `pageInfo(name).contract` 拿来校验自己构造的参数 |
| `i18n` | `tinyui build` | `{ "default": 默认语言, "files": { 语言: 相对路径 } }`，包没有 i18n 资源时省略（build-chain.md §8） |
| `tinyui` | `tinyui build` | 页面需要的最低运行时：JS 工程构建时的 `tinyui-core` 版本（能用哪些内置组件与运行时 API）与 `tinyui-cli` 版本（产物布局由同版本起的库读取）取较新者；`publish` 核对目标宿主版本的 tinyui 下限与它兼容（§1.3），客户端核对它与 `TinyUI.version` 兼容（§4.3）。**兼容 = major 相同，且宿主的版本不低于包的**（semver `^`；0.x 之间按版本号全序比较，1.0 之前不允许破坏公开面）。成立的前提是运行时公开面只增不删（runtime-api.md §11）；升 major 时宿主加 `hostVersion`（§4.1），旧 major 的包因不兼容被拒收 |
| `requires` | `tinyui build` | 页面模块名 → `{ components, capabilities, channels, routes }`（`routes` 是页面跳到包外的页面名：原生路由或别的包的页面，native-api.md §3；`publish` 对不在快照里的只警告，`PackageCheck` 给了其他包的清单时才判错）。前三项：该页用到的带点宿主组件名、`host.call` 能力名与 `http.client` 通道名，各自排序；内置组件与 `default` 通道不列。`publish` 据此核对目标宿主版本（§1.3）；`Updates` 不读它，宿主可以用 `PackageCheck` 在设备上再核对一遍 |
| `hostVersion` | `tinyui bundle` | 发布目标，等于宿主声明值；写入后文件定稿，签名覆盖它的原始字节（§7） |

签名值与 `rollout` 不在 manifest 里，在指针文件 `current.json`（§1.2）。内置包没有指针文件，不参与灰度、不验签；库不读内置包 manifest 的 `hostVersion`（宿主知道自己的值）。从通道取来的内置包带着这个字段，本地源码构建的没有（§1.4）。

### 1.2 `tinyui bundle`

```
tinyui bundle --signing-key <私钥 PEM> [--host-version <hostVersion>] [--rollout <p>] [--out dist/ota]
```

读 `tinyui build` 的输出目录，写（`hostVersion` 取 `tinyui.config.json` 的同名字段，`--host-version` 可覆盖，build-chain.md §2.1）：

```
dist/ota/<pkg>/<hostVersion>/current.json             指针：{ "version", "rollout", "signature" }
dist/ota/<pkg>/<hostVersion>/<version>/manifest.json  build 的 manifest + hostVersion，写定后不再变
dist/ota/<pkg>/<hostVersion>/<version>/pages/**/*.jsb
dist/ota/<pkg>/<hostVersion>/<version>/i18n/<locale>.json   manifest 的 i18n 列出的文件（有才写）
```

`current.json` 只有三个字段：`version` 指向哪个目录；`rollout` 0～100 的整数，缺省 100，服务端可改；`signature` 是对 `<version>/manifest.json` 原始字节的签名（§7）。指针里只有投递策略与签名值，没有任何需要签名保护的字段。

这个目录是包的最终形态：交给 `tinyui publish` 上传，或者原样放到任何静态目录（§2.2）——它的布局就是 §2 客户端要请求的路径。`.js.map` 不进包（build-chain.md §7）。

### 1.3 发布前核对目标宿主版本

目标宿主版本是发布方手填的（`tinyui.config.json` 的 `hostVersion`，或 `--host-version`）。填成一个仍有设备在用的旧值（例：main 已为宿主版本 3 用上新能力 `coupon.apply`，却以 `--host-version 2` 发出一个修文案的包），旧宿主会照常装上，页面调用它没有的能力只得到 `E_UNSUPPORTED`、用到它没有的组件只渲染占位——都不崩溃，§4.5 的回退接不住。宿主侧的构建检查（§4.1）只管宿主漏 bump，管不到发布方填错。

所以 `tinyui publish` 在上传前核对：**这个包用到的宿主东西，目标宿主版本都提供**。放在 `publish` 而不是 `bundle`：`bundle` 是纯离线命令（签名、写目录，静态托管就用它的产物），`publish` 本来就带着 app、实例地址与 token 在和服务端说话，快照也在服务端上；静态托管没有快照，也就没有这层核对。

| 输入 | 来源 |
|---|---|
| 包用到了什么 | `tinyui build` 从每页打包产物里静态找出，写进 manifest 的 `requires`（页 → `{ components, capabilities, channels }`）：`host.call` 的能力名、`session.signIn` 与 `i18n.setLocale`（页面调用了它们时）、带点的宿主组件名（`ta.Icon`）、`http.client` 的通道名；内置组件与 `default` 通道不列，随 tinyui 版本走（规则见 build-chain.md §5.1） |
| 目标宿主版本提供了什么 | 宿主快照 `tinyui-host/<hostVersion>.txt`（宿主组件、能力名、通道名、tinyui 下限）。`hostVersion` 加 1 合入宿主 main 时由宿主 CI 上传到热下发服务，每个 (app, hostVersion) 只能写一次；`publish` 从服务端读 |

不通过即拒绝发布（一个请求都不上传），报出哪一页缺了什么：页面用到的能力、通道或宿主组件不在快照里；或快照里的 tinyui 下限与 manifest 的 `tinyui` 不兼容（§1.1；包更新：页面可能用到新版运行时的东西，这个 `hostVersion` 下最旧的宿主没有，装不上而静默跳过；major 不同：公开面已有破坏）。目标宿主版本还没有快照（宿主没上传过）也拒绝。

核对的是名字，覆盖不到已有能力的参数形状与行为，那部分仍按 §4.1 的 bump 规则由人判断。绕过 `publish` 直接调发布端点的，目前拦不住；`requires` 进 manifest 并受签名覆盖，日后服务端在 `PUT current.json` 时同样可以核对。快照的上传与读取见 §6.4。

### 1.4 内置包：宿主构建时解析

2026-11-22 改定（ADR-006 §2.12）。此前内置包是宿主仓里提交的字节，由 `tinyui pull` 刷新；现在它是宿主构建的一个输入，由 Gradle 插件在构建时解析进 build 目录，宿主仓不再提交内置包。

内置包是 App 的地板，只要求**兼容**：引擎与宿主一致、tinyui 与宿主兼容（§1.1），用到的宿主东西宿主都有（宿主测试用 `PackageCheck` 核对，§4.1）。新旧尽力而为：旧一点，新装用户首启后 `check()` 会补上。

插件 `app.tinyui`（`app.tinyui:tinyui-gradle-plugin`，与库同版本号同 tag 发布；源码在 `gradle-plugin/`，是独立的 Gradle 构建，宿主本地联调框架时在 settings 的 `pluginManagement` 里 `includeBuild("<tinyui>/gradle-plugin")`），用在读内置包的那个模块上：

```kotlin
plugins { id("app.tinyui") }

tinyui {
    app = "shop"                      // 热下发服务上的 app；updatesUrl 缺省为托管实例
    hostVersion = "3"                 // 宿主里唯一写它的地方，Kotlin 侧读插件生成的常量（见下）
    channel = providers.gradleProperty("…")   // 这次构建的 App 运行时检查哪个通道，内置包就取哪个通道；运行时读生成的 TinyUIBuild.CHANNEL
    generatedPackage = "com.example.shop.tinyui"   // 生成的 TinyUIBuild 放在哪个 Kotlin 包
    packages {
        register("login") { publicKey = "BN5p…" }   // 该包 tinyui.config.json 的 publicKey，客户端与构建共同的信任锚
    }
}
```

每个包按下面的顺序取，取到即止：

| 来源 | 条件 | 做什么 | 要 Node |
|---|---|---|---|
| 页面源码 | Gradle 属性或根工程 `local.properties` 有 `tinyui.<pkg>.dir=<JS 工程>` | 在该工程里跑它自己装的 `tinyui embed`（`node_modules/.bin/tinyui`，带 source map），页面源码变了才重跑。`local.properties` 里的这一行遇上 `production` 通道即构建失败：留在开发机上的配置不能把本地构建带进正式包，确要如此用 `-Ptinyui.<pkg>.dir` 明说 | 是 |
| 通道 | 其余情况 | 从投递端点（§2）取 `channel` 当前指向的版本，像设备一样验签、核对 name / version / hostVersion、逐文件核对 sha256 | 否，插件用 JVM 实现 |

从通道取的规则：

- 每次构建都取一次指针（约 100 字节），版本没变就不再下载
- 指针正在灰度（`rollout` < 100）→ 构建失败并报出比例：灰度中的版本不能成为每个新装用户的地板，而公开端点上拿不到上一个全量版本的签名。放量到 100 再构建
- 下载的 manifest 的 `publicKey` 与声明的不同 → 失败。公钥轮换即改这里的声明，本来就与 `hostVersion` 加 1 同时发生（§7）
- 当前 `hostVersion` 下该通道还没有版本（宿主刚加 1，页面还没在新版本下发布）：`production` 失败；其他通道依次回落到更小的 `hostVersion`，取到第一个为止并警告。回落来的包能不能跑仍由 `PackageCheck` 的宿主测试判定
- 取不到指针（没有网络）：`production` 失败；其他通道沿用 build 目录里上次解析的结果并警告，没有则失败

产物与接线：

- 每个包写成 `tinyui embed` 的布局（`manifest.json`、`pages/`、`i18n/`），接进该模块的 Compose 资源 `files/tinyui/<pkg>/`，宿主读内置包的代码不变（§3）
- Compose 资源每个源集只认一个目录，所以插件把模块自己的 `src/commonMain/composeResources` 与各内置包合并到 `build/tinyui/compose-resources` 再接入；源码树里还留着 `files/tinyui/<pkg>/` 时合并报重复而失败
- 生成 `TinyUIBuild`（`HOST_VERSION`、`CHANNEL`、`PACKAGES`）进 `commonMain`，`Updates(hostVersion = …)` 与宿主快照测试都读它。`CHANNEL` 即这里声明的 `channel`：宿主运行时检查更新的通道读它，内置包取自哪个通道、App 就查哪个通道，两处是同一个值（2026-11-22 加；此前运行时的通道由宿主另算一遍，与构建时的可能不一致）。因此 `channel` 必须声明，全部包都从页面源码构建时也一样
- `tinyui.embeddedDir` 是这次构建的内置包目录（每包一个子目录）的 Provider，带着对解析任务的依赖；宿主把它作为输入接给跑 `PackageCheck` 的测试任务
- 构建日志的 `TinyUI: <pkg> <version> (…)` 行与 `build/tinyui/resolved/<pkg>.json` 记下每个包解析到的版本、来源与通道：一个安装包内置了哪版页面以这里为准，仓里没有记录。`resolveTinyUIPackages` 只做解析

不做 lock 文件（把解析结果钉成入库的一行）：它换来同一提交重复构建结果相同，代价是页面每发一版宿主仓都要跟一次提交，正是这次要去掉的步骤。正式包只构建一次、冒烟与发布用同一个产物时，不需要它。按版本号重建某个旧安装包要投递端点提供每个版本的签名（现在签名只在指针里），触发条件记 roadmap D 组。

去掉的东西：`tinyui pull`（两个宿主迁完后删除）；「内置包必须是发布版本」「`hostVersion` 等于当前值」这两条发版检查——前者不再可能违反（本地源码产物不进仓），后者由 `production` 通道的严格规则取代。

从源码构建且不允许联网的分发渠道（如 F-Droid）用第一种来源：`tinyui.<pkg>.dir` 指向入库的页面源码快照。

**`tinyui embed`** 仍是「把一个 JS 工程编成内置包」的唯一实现，插件的第一种来源调的就是它：

```
tinyui embed --out <目录> [--maps] [--root <JS 工程>] [--dist <构建目录>]
```

- 先 `tinyui build`，再让 `--out` 恰好是一个内置包：`manifest.json`、各页的 `.jsb`、文案文件，别的构建产物不进；`--maps` 连同 source map（失败页能显示 JS 调用栈，会把页面源码带进安装包，只用于调试）
- 整目录换入：删掉的页面不残留，失败时原来的包还在。目录里已有别的包（`manifest.json` 的包名不同）、或有文件却不是一个包时拒绝；`--out` 的上级目录不存在时也拒绝
- 它写的 manifest 没有 `hostVersion`，不验签、不看灰度
- 「哪些文件算内置包」只在 CLI 里。业界同类是 Expo 的 `export:embed`、React Native 的 `bundle --bundle-output … --assets-dest …`
- 类型检查、`i18n --check`、`routes --check` 不在里面：那是页面工程自己的检查，由工程的脚本串在它前面

## 2. 投递协议

客户端只会发两种 GET，路径相对宿主配置的 base URL：

| 路径 | 内容 | 缓存 |
|---|---|---|
| `<pkg>/<hostVersion>/current.json` | 可变指针 | `Cache-Control: no-store` |
| `<pkg>/<hostVersion>/<version>/manifest.json` | 不可变，签名覆盖其原始字节 | `Cache-Control: public, max-age=31536000, immutable` |
| `<pkg>/<hostVersion>/<version>/<files[module]>.jsb` | 不可变内容 | 同上 |
| `<pkg>/<hostVersion>/<version>/<i18n.files[locale]>` | 不可变内容（包内 i18n 资源，build-chain.md §8） | 同上 |

- `pkg` 库从内置 manifest 读，`hostVersion` 宿主给；宿主只拼 base，一个 App 不管几个包都是一个 base URL、一个 `fetch`
- 请求不带 query、不带自定义 header、不带任何设备信息；宿主的 `fetch` 实现可以自行加 header 或按路径分流到不同来源，库不知情
- 同一 `<version>` 目录下的文件永不改内容（改内容必换 version），CDN 因此不会陈旧
- 无更新时客户端只下载指针（约 100 字节）；`manifest.json` 与文件只在有新 version 且中签时才取
- 指针只往前走：回滚 = 用旧内容发一个新 version（§6.2）；灰度 = 改 `rollout`，不碰 `<version>/` 下的任何文件
- 投递面没有鉴权：包在安装包里本来就能反出来；来源与完整性由签名（§7）保证

### 2.1 托管与私有化实例

`tinyui-updates-server`（独立仓，Cloudflare Workers 参考实现）实现本节与 §6。托管实例 `updates.tinyui.app`；私有化 = 部署同一份代码到自己的 Cloudflare 账号。

```
base = https://updates.tinyui.app/<app>/<channel>
完整路径 = /<app>/<channel>/<pkg>/<hostVersion>/current.json
```

托管实例的指针、release 记录与 token 都存 R2（强一致）：发布、回滚、吊销 token 立即生效。代价是 `current.json` 的读取不走 KV 的边缘缓存，延迟略高，对 App 启动时的后台 `check()` 可接受。

进 URL 的只有永久不变的身份：`app` 由实例管理员分配、全局唯一（§6.3）；`channel` 由宿主构建变体决定（`production` / `staging` / `dev`），是 App 级的部署环境而不是包级的；`pkg`、`hostVersion` 见上。

### 2.2 静态目录

不需要服务端代码：把 §1.2 的 `dist/ota/` 放到任何能设缓存头的静态托管，base URL 指向它。没有发布协议、没有服务端验签，签名仍由客户端验。

## 3. Kotlin：`Bundle`（core 库）

把宿主现在手写的"读 manifest → 读页面与 map"收进库；运行时字节码随库（ADR-006 §2.11），不从包里读：

```kotlin
fun interface BundleFiles { suspend fun read(path: String): ByteArray? }   // path 如 pages/home.jsb、manifest.json

class Bundle(val manifest: BuildManifest, files: BundleFiles) {
    val name: String get() = manifest.name
    suspend fun page(name: String): LoadedPage     // name 是含包名的模块名；运行时取库内置的那份；map 读得到就交给 SourceMaps
    companion object { suspend fun load(files: BundleFiles): Bundle }   // 读 manifest.json
}
class LoadedPage(val module: PageModule, val sourceMaps: SourceMaps, val bundle: Bundle)
```

`BuildManifest` 解析 §1.1 全部字段，`name` / `publicKey` 缺失即解析失败。内置包的 `BundleFiles` 由宿主用 `Res.readBytes` 实现（每包一个资源子目录，如 `files/tinyui/<pkg>/`）；`Bundle` 不知道字节从哪来。

## 4. Kotlin：`Updates`（`tinyui-updates`）

主仓 Gradle 模块 `updates/`，与 `compose/` 同版本号同 tag 发 Maven。

### 4.1 宿主提供什么

```kotlin
class Updates(
    packages: List<Bundle>,                      // 内置包，各自 manifest.name 即包名；重名 → 构造抛错；engine 与宿主不符或 tinyui 不兼容 → 发 EmbeddedIncompatible 后照常构造（§4.4）
    val hostVersion: String,
    dir: Path,                                   // 宿主给的目录，如 Android filesDir/tinyui、iOS Application Support/tinyui；库内按 <dir>/<pkg>/ 分
    installId: String,                           // 稳定的安装标识；库不生成、不持久化、不上传
    fetch: suspend (path: String) -> ByteArray,  // 宿主用自己的 HTTP 栈；path 是 §2 的相对路径，库已拼好 <pkg>/<hostVersion>/ 前缀，宿主只拼 base
    onEvent: (UpdateEvent) -> Unit = {},
)
```

验签公钥不由宿主传：每个包的信任锚是它内置 manifest 里的 `publicKey`（§1.1、§7）。单包宿主写 `Updates(listOf(embedded), …)`。

**`fetch` 的现成实现**：宿主用 ktor 时写 `fetch = ktorFetch(client) { "$base/$channel" }`（`tinyui-updates` 自带；ktor-client-core 本就在 core 库的依赖面上，不新增依赖）。客户端仍是宿主的，超时、代理、引擎、`expectSuccess` 都由宿主定。**`fetch` 必须有超时**：`check()` 下载期间持有包锁，一个永不返回的 `fetch` 会让此后的检查和 §4.5 回退的落盘一直等下去（页面不受影响，已切到 embedded）；base 每次调用时读，切通道不用重建 `Updates`。它只接管失败文案，因为 `Failed.message` 是传输层唯一能带出来的东西，而宿主上报时还要截到很短：非 2xx 记 `HTTP <状态码> cf-ray=<值或 -> server=<值或 -> body=<前 60 字符>`——Cloudflare 的每个响应都带 `cf-ray`，没有它的 5xx 是设备与服务端之间的代理伪造的，不是源站问题；超时去掉 ktor 附带的 `[url=…]`；DNS / 连接 / TLS 失败保留引擎原话。用别的 HTTP 栈的宿主自己实现 `fetch` 时照这个口径拼文案，各宿主的看板规则才能共用。

**`hostVersion` 的口径**（作用同 Expo 的 `runtimeVersion`，但只取正整数、数的是宿主的变化；不沿用那个名字，是因为 tinyui 里 runtime 已指运行时模块与引擎）：宿主仓持有的递增正整数字符串（`"1"`、`"2"`……；`1.2.0` 这类 App 版本号在 CLI、`Updates`、服务端三处都被拒），语义是"这个值下发布的任何包都能在本宿主上跑"；一个宿主一个值，挂在它上面的所有包共用。**它是宿主对页面承诺的版本，不是 tinyui 版本**（ADR-006 §2.11）。宿主给页面的承诺变了就加 1：宿主组件增删或改 schema；宿主能力增删，或改已有能力的参数与行为；网络通道增删，或改已有通道带的身份；原生路由增删，或改已有路由的参数（native-api.md §3）；提供或撤掉会话（快照里记作能力 `session.signIn`）。语言、埋点出口、链接打开器等框架标准接口缺省时有可用的默认行为，形状随 tinyui 走，宿主接没接、怎么实现都不加（ADR-007 §3.9）；抬高 tinyui 下限（页面要用新版运行时的东西，或升到新 major）；换引擎（含随 tinyui 升级带来的 quickjs-kmp 上游 commit 变化，build-chain.md §5；上游 commit 不变的 quickjs-kmp 升级不算）。单纯升级 tinyui、只改宿主内部实现、其他原生页面、修崩溃，不加。包的 JS 工程把这个值写在 `tinyui.config.json` 的 `hostVersion`（build-chain.md §2.1），`tinyui bundle` 等命令读它，不自行推导。JS 侧取错分两个方向：取成不存在或更新的值，包只是送不到（`releases list` 与客户端事件可见）；取成一个仍有设备在用的旧值，旧宿主会照常装上，而页面可能用到它没有的东西——宿主构建的检查拦不住这个方向，由 `publish` 发布前核对（§1.3）。

漏加的后果是旧宿主收到跑不了的页面（不崩溃的错误不会触发 §4.5 回退），多加的后果是更早的宿主从此收不到更新。漏加只可能发生在宿主仓，由宿主构建拦：宿主快照 `tinyui-host/<hostVersion>.txt`（宿主组件 schema、能力名、通道名、tinyui 下限）每个版本一份入库，当前宿主与当前版本的快照不符即构建失败；已合入 main 的版本，其快照冻结不可改。快照覆盖不到已有能力的参数与行为变化，这部分按上面的规则由人判断。为什么是精确匹配而不是 `>=` 范围，见 ADR-006 §2.2。

快照由 `HostSnapshot.render(host, hostVersion)` 从宿主的 `TinyUIHost` 生成（所以能力必须在 App 级注册，native-api.md §7），检查写成宿主自己的 host 侧单元测试，照 sample 的 `HostSnapshotTest`：

- 当前 `hostVersion` 的快照文件不存在，或组件、能力、通道三段与 `render` 结果不同，或当前 tinyui 与快照的下限不兼容（§1.1）→ 失败，提示加 `hostVersion`。`tinyui` 行只判兼容：同 major 内单纯升 tinyui 不会让测试失败，跨 major 会
- 加 `-Ptinyui.updateHostSnapshot` 跑同一个测试 → 写入当前结果，`tinyui` 行即当前 tinyui 版本，成为这个 `hostVersion` 的下限。这等于人声明"这个版本还没合入 main"；本地不判断，冻结由服务端兜底：`hosts upload` 同版本不同字节得 409（§6.4）
- 宿主 main 的 CI 在每次 push 时跑这个测试并 `hosts upload`（同字节幂等），`hostVersion` 加 1 的提交一进 main 快照即上传、冻结（M6 拍板第 13 点）。上传不等到发 App：发版前要先在新版本下发布包（内置包、staging 验证都要），而发布前要读快照
- 快照文件在 `.gitattributes` 里固定 `eol=lf`：上传的是原始字节，CRLF 的副本会被当成另一份快照而得 409
- `hostVersion` 在宿主里只写一处常量，`Updates(hostVersion = …)` 与这个测试都读它

测试只构造 `TinyUIHost`、不执行任何能力与组件，所以能在 JVM 上跑；能力实现需要的仓库类对象在测试里传假的即可。

内置包另有一个宿主测试（照 sample 的 `EmbeddedPackageTest`）：对这次构建解析到的每个内置包的 manifest 跑 `PackageCheck.problems(manifest, host)`，要求为空。内置包是不是给当前 `hostVersion` 发布过的，由插件在 `production` 通道下保证（§1.4），不再是测试。

**接入约定：`check()` 在任何 TinyUI 页面挂载之前调用**（App 启动即调；用 §4.6 的 `UpdatesScheduler` 时即 `start()`）。回滚（一个新 version）对已装上的用户生效靠的是下一次 `check()` 装上它；页面若先于它崩溃，回滚永远送不到。进程级崩溃（native 段错误）不在 §4.5 的回退范围内，出口就是灰度 + 回滚 + 这条约定；挂载标记见 ADR-006 §4.3 推迟项。

文件与 sha256 用 okio（`FileSystem` + `ByteString.sha256`）；core 库自 ADR-007 起也依赖 okio（`storage`）。ECDSA 验签走平台 API（§7）。

### 4.2 API 面

| 成员 | 语义 |
|---|---|
| `fun current(pkg: String): Bundle` | 构造时按包定死（§4.4）；进程内只在 §4.5 回退后换回该包的内置 |
| `fun pageInfo(name: String): PageInfo?` | 页面 `name`（含包名）在当前包里声明了什么（`presentation`，build-chain.md §11）；App 里没有这个包、或包里没有这一页时为 null。宿主打开页面前先问：null 就不打开并上报，不等 `UpdatesPage` 抛错（native-api.md §3） |
| `suspend fun check(trigger = MANUAL): Map<String, CheckResult>` | 全部包各跑一遍 §4.3，彼此独立并行，一包失败不影响其他；同一实例串行，重入直接返回进行中的结果（事件沿用发起那次的 `trigger`） |
| `suspend fun check(pkg: String, trigger = MANUAL): CheckResult` | 只查一个包 |
| `val status: StateFlow<Map<String, PackageStatus>>` | 每个包在本进程的状态，给调试面板与 `UpdatesStatusLine` 看（§4.7）；只读，不影响选包 |
| `class UpdatesScheduler(updates, scope, interval = 1.hours, retryAfter = 10.seconds)` | 可选：统一的检查时机，`start()` 默认连同平台前后台一起接上，另有 `onForeground()` / `onBackground()` / `checkNow()`（§4.6） |
| 顶层 `@Composable fun UpdatesPage(updates, name, host, propsJson, locals, modifier, error, onHost)` | 与 `TinyUIPage` 同参外加 `updates`；`name` 是含包名的模块名，第一个 `/` 之前即包，`current(pkg).page(name)` → `TinyUIPage`；`error` 前先走 §4.5 的回退 |

`CheckResult`：`UpToDate` / `Installed(version)` / `Skipped(reason)` / `Failed(version, stage, message)`。`UpdateEvent` 是同一组事实加 `Running`、`EmbeddedIncompatible` 与 `RolledBack`，给宿主打日志与埋点；**所有事件都带 `pkg`**。宿主的分析口径会依赖它，所以发布后字段同样只增不改：

| 事件 | 何时 | 字段（均含 `pkg`） |
|---|---|---|
| `EmbeddedIncompatible` | 构造时，内置包不能在本宿主上跑 | `version`、`mismatch`：`ENGINE` / `TINYUI` 的集合（§4.4） |
| `Running` | 构造时每包一次 | `version`（内置或 installed 的 manifest `version`）、`source`：`EMBEDDED` / `INSTALLED` |
| `UpToDate` | `check()` | `version`、`trigger` |
| `Skipped` | `check()` | `version`、`reason`：`INCOMPATIBLE` / `FAILED_BEFORE` / `OLDER_THAN_EMBEDDED` / `ROLLOUT`；`INCOMPATIBLE` 附 `mismatch`：`NAME` / `VERSION` / `ENGINE` / `TINYUI` / `HOST_VERSION` 的集合、`trigger` |
| `Installed` | `check()` | `version`、`trigger` |
| `Failed` | `check()` | `version`（指针阶段就失败时为空）、`stage`：`POINTER` / `MANIFEST` / `SIGNATURE` / `DOWNLOAD` / `INTEGRITY` / `STORAGE`；`INTEGRITY` 也在启动校验（§4.4）时发出，`STORAGE` 也在回退落盘失败（§4.5）时发出、`message`（`SIGNATURE` 时注明下载 manifest 的 `publicKey` 是否等于内置——区分被篡改与公钥已轮换）、`trigger`（不来自 `check()` 的为空） |
| `RolledBack` | §4.5 | `version`、`page`（模块名）、`kind`（`E2` / `E6`）、`buildId`、`message` |

`trigger`：`STARTUP` / `RETRY` / `FOREGROUND` / `MANUAL`，说明这次 `check()` 由什么发起（§4.6）；宿主直接调 `check()` 时为 `MANUAL`。重试与前台检查会让失败事件的次数变多，失败率按设备去重不受影响，按次数看时按 `trigger` 拆开。

事件在库里定形，上报到哪里是宿主的事。

### 4.3 `check(pkg)` 状态机

```
fetch <pkg>/<hostVersion>/current.json ─解析失败──────────────────────────────▶ Failed(pointer)
  │
  ├─ version == installed.version 或 version ∈ failed ────────────────▶ UpToDate / Skipped(failed)
  ├─ version == embedded.version（内置可用）──────────────────────────▶ UpToDate   // 不取 manifest、不看 rollout
  ├─ hash(installId + ":" + pkg + ":" + version) % 100 ≥ rollout ─────▶ Skipped(rollout)
  │
  ▼ fetch <pkg>/<hostVersion>/<version>/manifest.json，拿到原始字节
  ├─ 用内置 publicKey 对原始字节验 signature 失败 ─────────────────────▶ Failed(signature)   // 上报
  ├─ 解析失败 ────────────────────────────────────────────────────────▶ Failed(manifest)
  ├─ name ≠ pkg / version ≠ 指针 version / hostVersion ≠ 宿主值
  │  / engine ≠ QuickJs.upstreamCommit / tinyui 与 TinyUI.version 不兼容 ▶ Skipped(incompatible)   // 发错目录，上报
  ├─ createdAt ≤ embedded.createdAt（内置可用）────────────────────────▶ Skipped(older-than-embedded)
  │
  ▼ 逐文件 fetch <pkg>/<hostVersion>/<version>/<file>（files 的 .jsb 与 i18n.files）→ <pkg>/staging/<version>/，每个核对 sha256
  ├─ 任一失败 ─删 staging──────────────────────────────────────────────▶ Failed(download | integrity)
  ▼ manifest 原始字节写入 staging/<version>/manifest.json，staging/<version> 改名 installed/<version>，state.installed = version，删其他 installed
  ▼ Installed(version)   // 下次启动生效
```

manifest 一到手先验签再解析：签名不对的 manifest 里任何字段都不可信。验签用的公钥永远是内置 manifest 的，下载 manifest 的 `publicKey` 不参与验签。指针不在签名内，篡改它最多让客户端跳过更新或去取一个本就合法的包，与切断网络等价。掷骰哈希是 sha256 前 4 字节按大端读作无符号整数再取模 100，以 `pkg` 与 `version` 为盐：每个包每次发布独立抽样（monorepo 里同一次 CI 产出的多个包 `version` 可能相同，不加 `pkg` 会让它们的灰度人群重合）；已装上的用户不因 `rollout` 下调而回退。

指针只往前走（§6.2），所以正常流程里指针不会指向比已装版本更旧的版本；`older-than-embedded` 只出现在 App 升级带来了比线上更新的内置包时。

### 4.4 启动选择

存储布局，每包一份：

```
<dir>/<pkg>/state.json            { "installed": "<version>" | null, "failed": ["<version>", …] }   // failed 最多留 10 条
<dir>/<pkg>/staging/<version>/    下载中
<dir>/<pkg>/installed/<version>/  manifest.json + pages/
```

`state.json` 写入走临时文件加 rename，任何时刻磁盘上都是一份完整的它。

构造时对每个包：`installed` 存在、`name` 等于包名、`hostVersion` / `engine` 等于宿主值、`tinyui` 与宿主兼容（§1.1）、不在 `failed`、`createdAt` 新于 `embedded`、**逐文件重算 sha256 与 manifest 一致** → `current(pkg) = installed`，否则 `= embedded`。sha256 不一致的（磁盘损坏、半个文件）按失败处理：`failed += version`、`installed = null`、`Failed(integrity)`；校验时读进内存的字节直接作为该包 `Bundle` 的来源，进页面不再读磁盘。`createdAt` 不新于内置、`hostVersion` / `engine` 不等于宿主值或 `tinyui` 不兼容的 installed 当场删除（App 升级带来了更新的内置包、bump 了 hostVersion 或换了引擎；同 major 内宿主升 tinyui，已装的包不因此失效）；残留的 `staging/` 删除；`<dir>` 下不属于任何内置包的子目录删除（App 升级去掉了某个包）。这些清理都是尽力而为：删不掉的目录留到下次启动再试，不让构造失败，也不影响选包（留下的旧版本目录从不会被选中）；`check()` 装上新版本后清理旧目录同理，不算检查失败。不重验签：签名在落盘前验过，之后文件内容由 sha256 锁住；整包几十到一两百 KB，重算一遍的开销可以忽略。

`Running` 在按 manifest 选定之后、读页面文件重算 sha256 之前发出：宿主通常拿它给遥测挂「本进程跑哪个版本」，构造又多在后台协程里，等读完整包再发会晚于宿主启动后的第一条事件（Android 的 `ProcessLifecycleOwner` 在首个 Activity 启动时就触发）。installed 损坏时依次发 `Running(installed)`、`Failed(integrity)`、`Running(embedded)`，以最后一次为准；三者都在构造返回前发出，页面拿到 `Updates` 时已经是校验过的包。

内置包的引擎与宿主不符或 tinyui 不兼容时不抛错（M6 拍板第 14 点）：发 `EmbeddedIncompatible`，该内置包只在名义上是地板——它的页面加载即 E6、走宿主的 `error` 槽，不参与上面的 `createdAt` 比较，也不参与 §4.3 的"等于内置版本"与 older-than-embedded；`check()` 照常跑，装上兼容的版本后下次启动恢复。这只是兜底，发版前由宿主测试（§4.1）拦住。

### 4.5 失败回退

`UpdatesPage` 挂的页面来自某包的 installed 且报 E2 / E6（`PageHost.failure`）时：先用该包的 `embedded.page(name)` 重挂同一页面（`TinyUIPage` 的 `remember` 键含 `page`，换 `PageModule` 即重建引擎），此后新开的页面也立即拿到 embedded；再在包锁内 `failed += version`、`state.installed = null` 并落盘，然后删该 installed 目录，最后 `onEvent(RolledBack)`。页面不等这一步：`check()` 下载期间一直持有包锁，弱网下可能要等十几秒。这一步不随页面离开而取消；`state.json` 写不进去（如磁盘已满）时发 `Failed(storage)`、不抛出，本进程照样留在 embedded，installed 目录照删：留着它下次启动会再跑一遍坏包，删了最多在磁盘恢复后被 `check()` 再装回一次、再失败一次后拉黑。启动时的落盘同样失败不抛，`Updates` 的构造不因磁盘已满而失败。先落盘再删目录：两步之间进程被杀，下次启动按黑名单跳过，不会让 `check()` 把它再装回来。E2 / E6 本身照常经 `PageSink.error` 上报，带 `buildId`。其他包不受影响。

页面来自 embedded 时的失败走宿主自己的 `error` 槽，与不用 `tinyui-updates` 时相同。

进程内已经挂在该包 installed 上的其他页面不动（各自独立 Runtime），下次启动统一回到 embedded。

### 4.6 检查时机

`Updates` 只在被调用时检查。何时调用对每个宿主都一样，又直接决定新版本与回滚多快送到，库提供可选的 `UpdatesScheduler` 统一它；要自己控制时机的宿主照旧直接调 `check()`。

```kotlin
val scheduler = UpdatesScheduler(updates, processScope)   // interval = 1.hours, retryAfter = 10.seconds
scheduler.start()                                          // 启动即调，先于任何页面挂载（§4.1）；同时接上前后台
scheduler.checkNow()                                       // 切了通道或服务地址后
```

| 时机 | 条件 | `trigger` |
|---|---|---|
| `start()` | 每进程一次，重复调用无效 | `STARTUP` |
| 启动检查失败后 `retryAfter` | 失败在网络阶段（`POINTER` / `MANIFEST` / `DOWNLOAD`），且此刻在前台；只重试一次 | `RETRY` |
| 回到前台 | 上次检查在网络阶段失败且已过 1 分钟（`interval` 更短时取 `interval`），或上次成功已过 `interval` | `FOREGROUND` |
| `checkNow()` | 无条件 | `MANUAL` |

- 冷启动那几秒网络常常还没就绪，弱网地区尤甚；进程又常常活好几天，只在冷启动检查的话，一次失败要等下次冷启动、进程活着时发的新版本也收不到。
- 签名、完整性、落盘失败再问也不会好，不重试，也不让前台检查绕过 `interval`。指针或 manifest 解析失败仍算网络阶段：强制门户、代理会拿自己的页面应答。
- 前台检查装上的版本同样**下次启动生效**（§4.4）；它把下载与冷启动解耦：网络好时先装好，下次冷启动即便没网也直接用上。
- `interval` 默认 1 小时：每次检查只取一次指针，有新版本才下载；额度紧的宿主调大。开发构建可以调到十秒量级，测试人员切回 App 就能拿到刚发布的版本（§4.7）；不要给 0，启动检查与随之而来的前台事件会连查两次。`start()` 之前的前台事件不触发检查，第一次检查留给 `STARTUP`。
- 各触发串行：一次检查进行中到来的触发，等它结束后按它的结果判断要不要再查；间隔从检查**结束**算起。`scope` 由宿主给、进程级，检查不随页面取消；它并入的一次 `check()` 若随别处调用方的 scope 取消，记为一次失败，照常重试。
- `start()` 只是发起：页面不等它完成，否则弱网下首屏要等十几秒。§4.1 的约定是发起先于挂载；回滚送达靠这次或随后的检查，进程级崩溃的出口仍是灰度与回滚（ADR-006 §4.3）。
- 每进程一个 `Updates`、一个 `UpdatesScheduler`，放在进程级的地方（`Application`、单例），不放进会随 Activity 重建的 composition。
- `onEvent` 不应抛异常；抛了的检查不当作网络失败重试。
- 前后台：Android 用 `ProcessLifecycleOwner`（依赖 `androidx.lifecycle:lifecycle-process`），iOS 用 `didBecomeActive` / `didEnterBackground`；`start()` 时已在前台的补一次 `onForeground()`，`scope` 一取消就摘除观察者。自己定义前后台的宿主（多窗口、桌面端、测试）用 `start(followAppLifecycle = false)`，再自己调 `onForeground()` / `onBackground()`。

### 4.7 状态与状态行

开发、测试阶段要回答两个问题：这台设备跑的是哪个版本，刚发布的版本到了没有。生效机制不为此另开一套——装上的版本仍然下次启动生效（§4.4）——库只把状态摆出来，由宿主决定给谁看。

`Updates.status` 按包名给出 `PackageStatus`：

| 字段 | 含义 |
|---|---|
| `running` / `source` | 本进程页面来自的包的 manifest，与它是内置还是已安装；回退（§4.5）后变回内置 |
| `pending` | 本进程的 `check()` 装上、等下次启动生效的版本的 manifest；没有则为 `null` |
| `checking` | 该包的 `check()` 正在进行 |
| `lastCheck` | 本进程最近一次 `check()` 的 `CheckResult`；还没查完过为 `null` |

它与 `onEvent` 是同一组事实的两种形态：事件给日志与埋点，状态给界面。

`UpdatesStatusLine(updates, pkg, onCheck, onRestart, note, labels, versionLabel)` 是建在它上面的一行状态，给开发构建用，宿主自己决定放在哪里、哪些构建显示：

| 状态 | 显示 | 点击 |
|---|---|---|
| 平时 | `<pkg> · <note> · <运行版本>`，内置包再加 `embedded` | `onCheck` |
| 检查中 | 末尾加 `checking…` | 无 |
| 有版本等待生效 | `… → <新版本> · tap to restart`，高亮 | `onRestart` |
| 新版本不兼容本宿主（`HOST_VERSION` / `ENGINE` / `TINYUI`） | 末尾加「需要新的安装包」，高亮 | `onCheck` |
| 新版本在本机失败过（`FAILED_BEFORE`） | 末尾加「在本机失败过」，高亮 | `onCheck` |
| 检查失败 | 末尾加「检查失败，点击重试」 | `onCheck` |

- `onCheck` 通常接 `UpdatesScheduler.checkNow()`；`onRestart` 由宿主实现——结束进程，或在平台允许时重启。库不替宿主做这件事：能不能自行退出是平台与上架规则的事。
- `note` 放宿主想补充的信息，比如通道不是该构建的默认值时把通道名显示出来。
- 文案经 `UpdatesStatusLineLabels` 给，默认英文；版本的读法经 `versionLabel` 给，默认 `shortVersion`：版本恰是 `tinyui build` 的默认形式（`createdAt` 的紧凑形式加 12 位 git sha，§1.1）时显示为 `MMdd-HHmmZ · <git sha 前 7 位>`（时间取 `createdAt`，UTC），其他形式原样显示。要本地时间的宿主自己换。
- 没有 `Updates` 在跑时（构建没启用热下发，或它构造失败），页面跑内置包，状态行同样有用：另一个重载 `UpdatesStatusLine(manifest, modifier, note = …, labels = …, versionLabel = …)` 直接给内置包的 manifest（包名取 `manifest.name`），显示 `<pkg> · <note> · <内置版本> · embedded`，不可点击（没有检查可发起）。`note` 适合写为什么没在跑，比如未启用、构造失败。
- 发到错误目录的包（不符的字段里有 `NAME` / `VERSION`，即便同时还有宿主一侧的不符）与灰度未中不在状态行上提示：换安装包解决不了，测试人员也无从处理。

## 5. 与既有契约的关系

- `PageHost` / `TinyUIPage` / patch 协议 / schema / `PageSink` 不因热下发而变（M6 把组件、能力、sink 收进 App 级 `TinyUIHost` 是为了宿主快照，见 native-api.md §7）；`Updates` 全部搭在 `Bundle` 与 `TinyUIPage` 之上
- 引擎的 `JsEngineConfig.moduleLoader` 不接入：它服务页内 `import()`，不是页面级分发（ADR-006 §4.3）
- `PageError` 不加字段：`buildId` 已能定位到具体 build，`RolledBack` 事件带 `pkg` 与 `version`
- 跨包的 params / store / events 约定在 app-model.md §7，热下发只保证每个包内部一致

## 6. 发布协议

服务端实现的第二组端点，`tinyui-cli` 是它的客户端。所有请求 `Authorization: Bearer <token>`。

用词：**app** 是一个宿主 App——URL 的 `<app>` 段、`hostVersion` 序列、宿主快照、权限都挂在它上面。

凭据三层，下层由上层签发：

| 凭据 | 范围 | 签发者 |
|---|---|---|
| 实例的 `ADMIN_TOKEN` | 所有 app；只有它能建 app、签发与吊销 app token | 部署时设为 Worker secret |
| app token | 一个 app 的完整管理权：注册包、登记与轮换公钥、签发与吊销本 app 各包的发布 token、查看 release、上传本 app 的宿主快照；碰不到别的 app | `ADMIN_TOKEN` |
| 发布 token | 一个 (app, pkg)，限定可写的 channel 集合：上传、发布、回滚、晋级、改灰度；可读本 app 的宿主快照 | `ADMIN_TOKEN` 或本 app 的 app token |

app token 与发布 token 一样可以签多个、只返回一次明文、服务端只存哈希、随时吊销。app token 能给自己签本 app 的发布 token，所以实际能左右本 app 所有包的指针；但它和 `ADMIN_TOKEN` 一样碰不到客户端的信任链——设备只认内置包里的公钥（§7）。

服务端把内容按 (app, pkg, hostVersion, version) 存一份，channel 只是指针：同一个 version 发到 staging 验证后，把 production 的指针指过去即是发布，不重传、不重签。内容 GET 路径里的 `<channel>` 段因此不参与寻址。

### 6.1 发布

| 请求 | 语义 | 服务端校验 |
|---|---|---|
| `PUT /<app>/<pkg>/<hostVersion>/<version>/<path>`，body 为文件字节 | 上传内容，含 `manifest.json` | 幂等；同路径已有不同内容 → 409（version 不可变） |
| `PUT /<app>/<channel>/<pkg>/<hostVersion>/current.json`，body 为指针 | 写指针，即发布 | token 覆盖该 channel；`<version>/manifest.json` 已上传，用该包登记的公钥对它的原始字节验 `signature`（§7）；manifest 的 `name` == `<pkg>`、`hostVersion` == `<hostVersion>`、`version` == 指针 `version`、`publicKey` == 登记值；`files` 列出的每个文件已在 `<version>/` 下且 sha256 与 `hashes` 一致；通过后记录 release（含 `signature`）、切指针 |

顺序由 CLI 保证：内容先、指针后。指针请求在内容不齐时拒绝，所以乱序不会产生半个包。

服务端不核对宿主快照：§1.3 的核对只在 `publish` 里，**CLI 是唯一受支持的发布路径**。绕过 CLI 直接写指针，`tinyui` 不符仍由客户端拒装（§4.3），`requires` 不满足则会照常下发，页面降级（E5 占位、`E_UNSUPPORTED`）且不触发 §4.5 回退。§6.2 的移动指针只在同一 `hostVersion` 已发布过的版本间进行，快照又冻结不可改，发布时的核对对它继续成立，无需复核。服务端复核的触发条件见 roadmap.md D 组。

`tinyui publish --channel <c> [--app <app>] [--dir dist/ota]`：读 §1.2 的目录（包名从 manifest 来），先 PUT `<version>/` 下全部文件含 `manifest.json`（服务端幂等，同字节即已存在），再 PUT `current.json`。token 只从 `$TINYUI_TOKEN` 读，管理端点从 `$TINYUI_ADMIN_TOKEN` 读，实例地址取 `--url` / `$TINYUI_UPDATES_URL` / 托管实例。

### 6.2 管理 release

| 请求 | 语义 |
|---|---|
| `GET /<app>/<pkg>/<hostVersion>/releases` | 已发布的 version 列表：`createdAt`（包的构建时刻，来自 manifest）、`publishedAt`（服务端记下的发布时刻）、各 channel 的指针与 `rollout` |
| `POST /<app>/<channel>/<pkg>/<hostVersion>/pointer`，body `{ "version": …, "rollout"?: … }` | 晋级：指针指向 (app, pkg, hostVersion) 下已发布、且比当前更新的 version；或只改当前指针的 `rollout`。服务端用记录里该 version 的 `signature` 重写 `current.json` |

服务端按 version 保存 release 记录（`createdAt`、`signature`），指针切换不需要重新上传；改 `rollout` 只改指针，签名不受影响（§7）。CLI：`tinyui releases list` / `rollout <p>` / `promote <version> --to <channel>`。

**指针只往前走**（M6 拍板第 15 点）：发布（`PUT current.json`）与晋级都拒绝把指针指向 `createdAt` 不新于当前指向的版本（409），同一 version 重发、只改灰度不算移动。回滚 = 在 JS 仓 revert、发布一个内容等于旧版的新 version、晋级——它比所有设备上的版本都新，包括内置了坏版本的新装用户，所以回滚一定落得到；设备侧也就不需要"指回旧版本"的分支。

### 6.3 管理 app 与包

| 请求 | 凭据 | 语义 |
|---|---|---|
| `POST /apps`，body `{ "id", "name", "org"? }` | `ADMIN_TOKEN` | 建 app；`org` 是可选的归属备注，建 app 时存下，不进任何路径 |
| `POST /apps/<app>/tokens` | `ADMIN_TOKEN` | 签发 app token，只返回一次；服务端只存哈希 |
| `DELETE /apps/<app>/tokens/<tokenId>` | `ADMIN_TOKEN` | 吊销 app token |

以下各条 `ADMIN_TOKEN` 或该 app 的 app token 均可：

| 请求 | 语义 |
|---|---|
| `POST /apps/<app>/packages`，body `{ "name", "publicKey" }` | 建包，登记验签公钥 |
| `PUT /apps/<app>/packages/<pkg>/publicKey` | 换公钥：此后该包所有 `hostVersion` 的指针都只收新钥的签名，已发布的不受影响；流程见 §7 |
| `POST /apps/<app>/packages/<pkg>/tokens`，body `{ "channels": [...] }` | 签发发布 token，只返回一次；服务端只存哈希 |
| `DELETE /apps/<app>/packages/<pkg>/tokens/<tokenId>` | 吊销 |

app token 不能签发或吊销 app token：一枚泄露的 app token 没法给自己续命。

CLI：`tinyui apps create` / `tinyui apps tokens create|revoke` / `tinyui packages create` / `tinyui tokens create` / `tinyui tokens revoke`；管理命令从 `$TINYUI_ADMIN_TOKEN` 读凭据，放实例的 `ADMIN_TOKEN` 或某个 app 的 app token 都行，服务端按 token 判定范围。没有控制台，这就是全部管理面。

### 6.4 宿主快照

宿主快照（§1.3、§4.1）是 `tinyui-host/<hostVersion>.txt` 的原始字节：该宿主版本提供的宿主组件、能力名、网络通道名与 tinyui 下限。第二行的 tinyui 是下限：`hostVersion` 加 1 时写入当时的 tinyui 版本，此后宿主升 tinyui 不改快照（ADR-006 §2.11）。`hostVersion` 加 1 合入宿主 main 时由其 CI 上传（§4.1），`publish` 发布前读取核对。格式是宿主侧生成工具与 CLI 之间的契约：

```
hostVersion 2
tinyui 0.3.0

components
  ta.Icon     name: string, size?: dp = 24, tint?: color = primary; layout
  ta.Rating   value: number; events onChange(value: number); commands reset(); layout

capabilities
  checkout.start
  coupon.apply

channels
  app

routes
  account/choose  {"type":"object","properties":{"accessToken":{"type":"string"}},"required":["accessToken"]}
```

前两行固定；`components`、`capabilities`、`channels`、`routes` 四段按此顺序各出现一次，没有条目也要写段名（缺段会被读成"什么都不提供"，所以一律拒绝）。例外：`channels` 段随 ADR-007 加入、`routes` 段随 0.14.0-alpha04 加入，此前冻结的快照没有它们，`tinyui` 行低于引入那一段的 tinyui 版本的快照缺这一段时读作不提供；`routes` 段每行名字之后是参数的形状（build-chain.md §10 的那个子集，一行 JSON），页面工程的类型从它生成，所以这一段的其余部分 CLI 是要读的；每项缩进两格、一行一项、按名排序，名字补空格对齐。`tinyui hosts upload` 先解析并核对首行的 `hostVersion` 与 `--host-version` 相符再上传——服务端只收第一份。**每行第一个词是名字**，CLI 核对只看名字；其后是给人看、也给宿主侧检查判断 schema 变没变的完整 schema，改哪一处都会让快照不同：

- 各段以 `; ` 分隔，依次为 props、`events …`、`commands …`、`children`、`layout`、`noIntrinsics`（schema `intrinsics: false` 时才写，缺省不写，所以加这个标记之前冻结的快照字节不变），没有的段省略
- prop 按名排序，`名字: 类型`；可选的名字后加 `?`，有默认值加 ` = 默认值`（`string` 的默认值写成 JSON 字符串，带引号与转义，免得换行或 `;` 弄乱一行一项），只在创建时读的加 ` (initial)`；枚举写 `enum(a|b)`，取值排序
- 事件与命令按名排序，参数写在括号里，参数按名排序，类型为 `string` / `number` / `boolean`

宿主组件只列带点的，内置组件随 `tinyui` 版本。

| 请求 | 凭据 | 语义 |
|---|---|---|
| `PUT /apps/<app>/hosts/<hostVersion>`，body 为快照字节 | `ADMIN_TOKEN` 或本 app 的 app token | 每个 (app, hostVersion) 只写一次：同字节重传返回已存在，不同字节 → 409——已合入 main 的宿主版本，其快照冻结 |
| `GET /apps/<app>/hosts/<hostVersion>` | 以上两种，或本 app 任一包的发布 token | 原字节；没上传过 → 404，`publish` 据此拒绝发布 |

CLI：`tinyui hosts upload <file> --host-version <n> [--app <app>]`（宿主 main 的 CI 用）。

## 7. 签名

- 算法 **ECDSA P-256 + SHA-256**，签名值 DER 编码后 base64 写入 `current.json` 的 `signature`（Java 与 iOS 原生都出 / 收 DER；WebCrypto 是 r‖s，服务端验签前转一次，约 20 行）。选它而非 ed25519：两端零依赖——Android `java.security.Signature("SHA256withECDSA")`，iOS `Security.framework` 的 `SecKeyVerifySignature`（C API，Kotlin/Native 可调；ed25519 在 iOS 只有 Swift-only 的 CryptoKit）
- 被签内容：`<version>/manifest.json` 文件的**原始字节**。`bundle` 写定该文件后对字节签名，此后任何一端都不重新序列化，客户端与服务端拿到的字节就是被签的字节，不存在规范化这一步（DSSE、JWS、Expo code signing、APT 的通行做法；多端各实现一份规范化 JSON 是 TUF 早期路线，任一处偏差即全部客户端拒收全部更新且只能发 App 修）。签名值放在指针 `current.json` 里；指针与 `rollout` 不在签名内，服务端因此能改灰度比例、切回滚而不碰私钥，篡改指针最多改变谁拿到一个本就合法的包。`name` 与 `hostVersion` 在签名内，所以一个包的产物不可能被发成另一个包或另一个 hostVersion
- 公钥格式统一为 **X9.63 未压缩点（`04‖X‖Y`，65 字节）的 base64**：iOS `SecKeyCreateWithData` 直接收，WebCrypto `importKey("raw")` 直接收，Android 侧加固定 26 字节的 P-256 SPKI DER 头再交 `X509EncodedKeySpec`——三处都不用解析 PEM。`tinyui.config.json` 的 `publicKey`、manifest 的 `publicKey`、`POST /apps/<app>/packages` 的 `publicKey`、`keys generate` 的公钥输出都是它
- 密钥按 (app, pkg) 一对：`tinyui keys generate` 产私钥 PEM（PKCS#8）与上述格式的公钥。私钥只在该包发布方的 CI（`tinyui bundle --signing-key`）；公钥两处登记——包的 `tinyui.config.json`（随 build 进 manifest，随内置包进 App，是客户端的信任锚），服务端包记录（§6.3）。服务端永远接触不到私钥：token 被盗发不出客户端认的包，服务端被攻破发出的包客户端不认。一个团队持有的私钥只能签自己的包
- **下载的 manifest 里的 `publicKey` 永远不是信任来源**，客户端只拿它与内置的比对以给出诊断信息
- `channel` 不在被签内容里：同一份签名产物从 staging 晋级到 production 不重签；staging 与 production 的隔离靠 token 的 channel 范围（§6）
- 公钥轮换**必须同时给宿主加 `hostVersion`，旧 `hostVersion` 从此冻结**：服务端每包只登记一把公钥、每个指针只有一个签名，旧 App 只认内置的旧钥，同一 `hostVersion` 上新旧 App 不可能都验过。步骤：
  1. 确认旧 `hostVersion` 各 channel 的指针停在想冻结的版本——换钥后它不能再发布，也不能回滚（回滚即发新版本）
  2. `tinyui keys generate` 出新钥，改 `tinyui.config.json` 的 `publicKey`
  3. 宿主 `hostVersion` 加 1（快照合入 main 即上传，§4.1），`tinyui packages rotate-key` 登记新钥
  4. 用新钥 `bundle` / `publish` 到新 `hostVersion`，宿主把构建脚本里该包的 `publicKey` 改成新钥（§1.4），随 App 发版

  旧 App 停在冻结的版本上，升级 App 后恢复更新。私钥泄露时先做 `rotate-key`（服务端即刻拒收旧钥签名，冻结点就是当时的指针），再走其余步骤。不中断旧 App 的轮换（预置备用钥）见 roadmap.md D 组

## 8. 包下线

一个包的页面全部迁出 TinyUI，或并进别的包时，宿主把它从 App 里移除。协议不为此加任何东西，原则：

- **只在宿主侧下线**：新版本宿主不再内置、不再检查这个包；旧版本宿主照常请求它，服务端的包与指针保留——删掉指针只会让旧宿主每次检查都报 `Failed(pointer)`、从此收不到回滚，没有收益。旧版本还发不发新内容，由包的发布方定
- **设备侧不写迁移**：`Updates` 构造时删掉 `<dir>` 下不属于任何内置包的子目录（§4.4），已下载的这个包随 App 升级自动清掉
- **`hostVersion` 按宿主契约判断**：只移除包不改宿主对页面的承诺，不加；连带撤掉了只给它用的宿主组件、能力或网络通道，才按 §4.1 加 1。此后 `hostVersion` 加 1 时，已下线的包不用在新版本下发布
- **口径以宿主版本为界**：宿主按包上报的版本信息（如埋点里每包一个的版本属性）随包一起去掉，说明里写明从哪个 App 版本起不再带，而不是日期
