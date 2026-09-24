# ADR-006 · 热下发：整包原子、宿主 hostVersion 为兼容键、内置包是地板

- 状态：已定（2026-09-18；2026-09-19 修订：服务端定为开源可私有化的托管服务，签名进 MVP，客户端与服务端分仓；2026-09-19 再修订：多包模型——App 由 N≥1 个包组成，包名进模块名与 URL，公钥进 manifest；2026-09-22 修订：签名覆盖不可变 manifest 的原始字节而非规范化 JSON，指针文件只含 version / rollout / signature；启动时重算 installed 包 sha256，挂载标记作推迟项；2026-09-23 §2.2 补精确匹配与 `>=` 范围的对比，bump 口径进 updates.md §4.1；同日兼容键改名 `hostVersion`，见 updates.md §4.1；同日按 M6 拍板改 §4.2：TrendingAI 的包名为 `trendingai`，内置包由 `tinyui pull` 取 production 那版，见 updates-plan.md 第 7、11 点；2026-09-24 按 M6 拍板第 12–15 点修订 §2.8 回滚与 §4.2 内置包：指针只往前走、回滚是用旧内容发新版本，内置包只求兼容、由宿主 pull 刷新；同日新增 §2.11：运行时随宿主、包只含页面，包与宿主的 tinyui 版本从"相等"放宽为"宿主不低于包"，升 tinyui 不再加 `hostVersion`，§2.10"运行时仍放包里"作废）；实现进度见 updates-plan.md
- 结论：**App 由 N≥1 个包组成，包是所有权单元与分发单元，一个包是一次 `tinyui build` 的页面产物（运行时随宿主，§2.11），整包原子生效、下次启动切换，各包独立发布、独立回退；包名进模块名（`subscription/home`）与投递 URL，密钥与发布 token 按包发，验签公钥随包进 manifest、以内置包的为信任锚；兼容键是宿主声明的 `hostVersion`，引擎 commit 要求相等、包的 tinyui 版本要求与宿主兼容（同 major 且宿主不低于包），两者只做校验；内置包永远是地板，下发包失败即回退内置并拉黑；库（独立 artifact `tinyui-updates`）以一组包为单位做校验 / 落盘 / 选择 / 回退，不做网络、调度、UI；服务端协议是两次 GET——可变指针 + 不可变内容，指针背后是静态文件还是动态端点客户端不关心；灰度靠 manifest 里的百分比 + 客户端掷骰；回滚 = 用旧内容发一个新版本（指针只往前走）；完整性 = 逐文件 sha256 + manifest 签名（ECDSA P-256，发布方持私钥）；服务端参考实现 `tinyui-updates-server` 独立开源仓、Cloudflare 为主、可私有化，托管实例 `updates.tinyui.app`**
- 契约（manifest 字段、投递与发布协议、签名、客户端状态机、API 面）：[updates.md](./updates.md)
- 影响：docs/README.md 首段与 ADR-005 决策表的"热下发不在本期"改为指向本文；roadmap D 组该项转入实现；build-chain.md §2 的模块名加包名前缀、manifest 字段扩展、`tinyui.config.json`；app-model.md 的路由键与跨包约定；ADR-005 §4 的模块名规则。§2.11 另影响：updates.md §1.1（manifest 去 `runtime`，`tinyui` 改义）、§1.3（publish 判下限）、§4.1（加 1 口径）、§4.3（客户端核对）、§6.4（快照 `tinyui` 行改义）；patch-protocol.md §6（K0 版本核对删去）；build-chain.md §2（build 不产出 `runtime/`，运行时由库构建）；runtime-api.md 写入公开面铁律；roadmap D 组"运行时 ABI 版本"一行删去

## 1. 背景

ADR-005 把热下发划出当期，条件是"内核稳定后另立 ADR"。TrendingAI 订阅页合入 main（2026-09-18）后，运行时 API、patch 协议、schema 链、错误上报都经过了一个真实页面的检验，内核算稳定。

现有设计已经把热下发需要的支点铺好了，本文是在这些支点上补"包的边界、兼容键、失败语义、库与宿主的分工"四条铁律：

| 支点 | 在哪 | 对热下发的意义 |
|---|---|---|
| 页面 = 模块 = 字节码，自包含无共享 chunk | build-chain.md §3 | 包只是页面的集合，分发单元不用再设计 |
| 字节码与字长无关、只绑引擎 commit，文件头自带 commit，加载时自校验 | quickjs-kmp `docs/decisions.md` | Android / iOS 共用同一份包；引擎不匹配是确定性的 E6 |
| tinyui 版本在 K0 核对 → E6；schema 不匹配走 E5 跳过 + `Placeholder`；`__mount` 清单让 JS `host.has()` 降级 | patch-protocol.md §6–7、ADR-003 | 硬兼容 / 软兼容的分界已画好，热下发只需把硬的两条提前到下载前（§2.11 起 K0 核对删去，tinyui 版本改为下载前判"宿主不低于包"） |
| `PageHost` 只收字节，库零 I/O，读资源在宿主 | `PageHost.kt`、TrendingAI `TinyUIHost.kt` | 热下发对库来说是"多一个字节来源 + 选哪个" |
| release 不带 map，靠 `buildId` 离线对映射 | build-chain.md §7 | 错误上报链路不变 |

产物尺寸：`core.bin` 20 KB、`native.bin` 3 KB、页面 1～4 KB；十几页的 App 整包不到 100 KB。运行时随宿主后（§2.11）包里只剩页面，运行时的 23 KB 随库进 App。

## 2. 候选与取舍

### 2.1 包的粒度

| | 整包（运行时 + 全部页面） | 按页独立发 |
|---|---|---|
| 业界 | Expo Updates、CodePush | Lynx / Kuikly 的模板包 |
| 运行时与页面的匹配 | 天然一致，问题不存在（§2.11 起运行时随宿主，匹配改由"宿主不低于包"保证） | 版本矩阵：页面要声明需要哪个运行时，或运行时另走一条分发 |
| 跨页 params 契约 | 同一次 build，一致 | 可能漂 |
| 代价 | 多传几十 KB | 分发系统复杂一个量级 |

选整包。"页面自包含"（build-chain.md §3）与"一次发全部页面"不冲突。"整包"的边界在哪、一个 App 能有几个包，见 §2.10。

### 2.2 兼容键

| | 宿主声明 `hostVersion`（Expo 的 `runtimeVersion` 规则） | 多维匹配（引擎 commit × tinyui 版本 × 宿主 schema 哈希） | semver 范围 |
|---|---|---|---|
| 规则 | 宿主承诺"同 hostVersion 下任何包都能跑"，改了宿主组件 / 能力、抬了 tinyui 下限、换了引擎就 bump（§2.11 起单纯升 tinyui 不 bump） | 服务端按多维求交 | 包声明 `>=1.2 <2` |
| 谁负责判断 | 人（发版时） | 服务端逻辑 | 客户端解析 |
| 静态托管可行 | 是（作路径） | 否 | 勉强 |

选 hostVersion，它同时是服务端目录名。`engine`（引擎 commit，要求相等）与 `tinyui`（版本，要求同 major 且宿主不低于包，§2.11）写进 manifest 只做校验，防"发错目录"这种人为错误，不参与匹配逻辑。

兼容键回答的是"这一版页面能发给哪些已装好的 App"。页面依赖宿主给它的东西——宿主组件、宿主能力、tinyui 本身——而宿主只能随 App 发版改，页面随时可以热下发，两边的新旧必然错开。以一个订阅页为例：

| App | 宿主给页面的东西 | hostVersion |
|---|---|---|
| 1.0 | `checkout.start` | 1 |
| 1.1 | 新增 `coupon.apply` | 2 |
| 1.2 | 同 1.1（只修了宿主内部的崩溃） | 2 |
| 2.0 | `checkout.start` 改名为 `checkout.begin` | 3 |

调用 `coupon.apply` 的新订阅页发到 hostVersion 2：1.1、1.2 收到；1.0 收不到，继续用它的内置页。

**不按 App 版本号**：1.2 与 1.1 给页面的东西相同。按版本号分，每改一次页面就要给每个 App 版本各发一份，否则没升级的用户收不到；而 App 版本号的变化大多与页面无关。

**不用 `>=` 范围**（页面声明最低 App 版本，CodePush 的 `--targetBinaryVersion` 属此类）。在"宿主新增能力"这种情形下它和 hostVersion 结果相同，还省掉了 bump，但另有三处不成立：

1. **宿主删或改东西时**：4 月发的页面声明 `>= 1.0`，会自动匹配到 2.0，去调已经改名的 `checkout.start`。要写上界 `< 2.0`，可写范围的是 4 月写页面的人，知道 2.0 改了什么的是后来改宿主的人。hostVersion 让改宿主的人声明，旧页面自动到不了新宿主。
2. **一个 channel 会同时有多个"当前版本"**：1.0 该拿 `>= 1.0` 的最新一版，1.1 起该拿 `>= 1.1` 的。投递就不再是每个 channel 一个指针的静态路径，要么服务端按请求求解，要么客户端拉全表自己挑；把共享同一个当前版本的 App 版本归成一组，得到的就是 hostVersion。
3. **App 版本号跨平台不对齐**：Android 与 iOS 的 1.1 未必提供同样的能力。hostVersion 是宿主代码对自身的声明，与发版号无关。

`>=` 成立的前提是宿主只加不删不改——微信小程序的"最低基础库版本"就建立在基础库的这条承诺上。要求每个宿主永久保留旧能力（换了支付渠道也得留着旧入口）代价过重，所以取精确匹配，与 Expo 的 `runtimeVersion` 一致。什么变化必须 bump、漏 bump 由谁拦，见 [updates.md](./updates.md) §4.1。

### 2.3 生效时机

| | 下次启动（Expo 默认） | 下次进页面 | 无活页时立即切 |
|---|---|---|---|
| 一致性 | 一个进程一个包 | 同一会话里旧页 push 新页，params 契约可能漂 | 一个进程内切换，但要追踪活页 |
| 实现 | `current` 构造时定死 | 每次 `page()` 重选 | 计数活 PageHost |

选下次启动。"无活页即切"留触发条件（用户第二次打开才见到更新成为运营问题）。

### 2.4 库的边界

库做校验、落盘、选择、回退；不做网络（收宿主的 `fetch(path)`）、不做调度（宿主决定何时 `check()`）、不做 UI（没有"有更新"弹窗）。放独立 artifact `app.tinyui:tinyui-updates`：core 库保持零 I/O、零文件依赖；不需要热下发的宿主直接不依赖它。

### 2.5 服务端形态

| 路线 | 形态 | 评价 |
|---|---|---|
| 纯静态目录 | 对象存储 / CDN | 零代码，服务端不能做决策 |
| 专用更新服务 | `GET /updates?runtimeVersion&platform&appVersion&deviceId…`（Expo 协议、CodePush、Shorebird） | 能力全，但客户端协议绑死服务端实现，每个宿主得自建一套 |
| 托管 SaaS | EAS Update / App Center CodePush / Shorebird | 各绑各的客户端；CodePush 2025 已退役。排除 |
| **两次 GET，指针与内容分离** | 客户端只 `fetch(相对路径)`：一次拿指针 `current.json`（可变，`no-store`，约 100 字节），有新版本且中签时再拿 `<version>/manifest.json` 与 `<version>/<file>`（不可变，`immutable`） | 静态目录是最小实现，动态端点是同协议下的服务端升级，客户端不改 |

选最后一种。库只定义"客户端会请求什么路径"，不定义"服务端怎么决定"——这是 §2.4 在服务端的投影。请求不带任何设备 / 版本信息；app 与渠道（staging / production）折在 base URL 里，库只知道包名（从内置 manifest 读）与 hostVersion，没有 app / channel 概念；定向发布不做，"能不能跑"归 hostVersion，"想不想给"要做就 bump hostVersion 或宿主在自己的 `fetch` 里加 header 让服务端判，库不知情。

### 2.6 服务端实现放哪

投递协议定了之后，服务端代码的归属是另一个问题。首批租户有两个（TrendingAI 与第二个 App），而且从第一天就是两个不同后端栈的 App。

| | A 各宿主自己写路由 | B npm 包挂进宿主后端 | **C 独立服务，开源可私有化** |
|---|---|---|---|
| 两个租户 | 两份实现 | Java 后端挂不进 npm 包 | 各配一个 base URL |
| 发布面 | 无，靠上传脚本 | 包里定义 | 服务定义，CLI `tinyui publish` 直连 |
| 私有化 | — | 只能挂进 Node 后端 | 部署同一份代码到自己的 Cloudflare 账号 |
| 挡住什么 | CLI 直发；SaaS 期两份实现要保持一致 | 包必须运行时无关 | 投递 URL 形态一经发布不能变（旧 App 版本永远打它） |

选 C。独立仓 `tinyui-updates-server`，Hono（Fetch API 之上的薄路由，Workers 原生、Node / Bun / Deno 同一份代码可跑），存储只用一个 R2 桶（2026-09-22 实现时从 KV + R2 收成 R2 单绑定：KV 最终一致会让吊销的 token 继续可用一分钟、发布后的指针在别的边缘节点读不到，R2 强一致且有条件写与前缀列举，元数据按版本 / channel 独立成对象后不需要任何读改写）——绑定越少，私有化越接近一条 `wrangler deploy`；存储收在 `Storage` 接口后，D1（触发：控制台需要跨维度查询）与文件系统 + SQLite 的 Node 适配器（触发：出现非 Cloudflare 的私有化需求）各带触发条件。管理面先不做控制台：`ADMIN_TOKEN` 作 Worker secret + CLI。静态目录自托管在协议上仍然合法，是不想跑服务的人的出口。

许可证：服务端仓与主仓一致用 MIT；托管实例对外提供前重评（AGPL 能挡"拿它开托管服务"，私有化部署不受影响；单作者可对新版本换许可证）。

### 2.7 仓边界：客户端在主仓，服务端独立

判据是"和谁一起变、和谁一起发、谁来克隆"：`tinyui-updates` 直接依赖 core 库的 `Bundle` / `BuildManifest`，与 core 同一次 PR 改、同版本号同 tag 发 Maven，属于主仓（Gradle 模块 `updates/`）；服务端只依赖协议、push 即部署没有版本号、私有化部署者要的是克隆一个 Worker 仓而不是带 Gradle 与 Xcode 的框架仓。Expo / CodePush / Capgo / Sentry 全部是 SDK 与服务分仓。代价是协议改动要跨仓协调，用"字段只增不改、未知字段透传、规范只在 updates.md 一处"压到可忽略。否掉"新开一个仓同放客户端与服务端"：客户端离开 core 后，`Bundle` 每动一次就是一轮跨仓发版。

### 2.8 灰度与回滚

灰度：manifest 带 `rollout` 百分比，客户端用宿主给的稳定 `installId` 哈希落桶（CodePush 做法）。静态托管也能灰度，是"全量推坏包"这一最大风险的止血阀，v1 就做。代价是库多收一个 `installId`（宿主给，库不生成不持久化、不上传）。

回滚：用旧内容发一个新 version 再晋级，指针只往前走，服务端拒绝把指针指向不更新的版本（2026-09-24 修订，M6 拍板第 15 点）。新 version 比所有设备上的版本都新，包括内置了坏版本的新装用户，所以回滚一定落得到；设备侧没有"指回旧版本"的分支，心智上只有一个方向。代价是回滚要走一次构建与发布，比改指针慢几分钟。Expo 的 `rollBackToEmbedded` 指令不做：服务端不知道每个 App 版本内置的是哪个包，多 App 版本共存时该语义本来就含糊。

### 2.9 完整性与来源

逐文件 sha256 锁内容，manifest 签名锁来源。签名进 MVP 而不推迟：租户从托管实例拉可执行代码，实例对租户就是"不受自己控制的第三方"，HTTPS + sha256 过不了任何安全评审。私钥只在发布方 CI，服务端登记公钥并在发布时验签，客户端再验一次——token 被盗发不出客户端认的包，服务端被攻破发出的包客户端不认，服务永远只是搬运工。

客户端的公钥从哪来：宿主逐包传入 vs 写在包的 `tinyui.config.json`、随 build 进 manifest、随内置包进 App。选后者：信任根同样是 App 二进制，但包变成自描述（名字、公钥、hostVersion 都在 manifest 里），宿主接线缩到 `(包列表, hostVersion, dir, installId, fetch)`，多包下"N 把公钥与 N 个包一一对应"这类接线错误没有发生的地方；公钥轮换本来就要 App 发版，与"换内置包"是同一个事件。唯一要写死的规则：**下载的 manifest 里的 `publicKey` 永远不是信任来源**，验签只用内置包的那份。

被签内容是 `<version>/manifest.json` 的原始字节，不是规范化 JSON。原拟把 `rollout` 留在 manifest 里、签名覆盖去掉它之后的规范化 JSON（RFC 8785 子集），代价是 Node、Kotlin、Workers 三端各实现一份规范化，任一处偏差（非 ASCII 模块名的转义、新增字段类型）即全部客户端拒收全部更新，且只能发 App 修——这是 TUF 早期路线，后来被 DSSE 取代的原因正在于此。签原始字节是 DSSE / JWS / Expo code signing / APT 的通行做法。随之指针与 manifest 分成两个文件：可变的 `current.json` 只含 `version` / `rollout` / `signature`（对应 Sparkle appcast、OCI tag），manifest 进不可变路径。副产品：无更新时只下指针，manifest 可进 CDN 缓存。挡住的是"往指针里放需要签名保护的字段"。

算法 ECDSA P-256 + SHA-256：Android `java.security`、iOS `Security.framework` C API 两端零依赖；ed25519 在 iOS 只有 Swift-only 的 CryptoKit，Kotlin/Native 调不到。

### 2.10 多包：一个 App 由几个包组成

多业务线的 App 里，各团队要各自独立发页面。三种粒度：

| | 单包（整个 App 一个包） | **多包（包 = 所有权单元）** | 单页独立下发 |
|---|---|---|---|
| 团队独立性 | 无，任何页面改动都要全 App 一起发 | 每个团队自己的包、自己的密钥、自己的节奏 | 每页独立 |
| 运行时与页面匹配 | 天然一致 | 包内天然一致 | 版本矩阵：页面要声明依赖哪个运行时 |
| 服务端 / 库 | 现状 | 路径与存储多一层 `pkg`，库按包索引；协议形态不变 | 库要理解"页"这一级，指针按页 |
| 跨单元契约漂移 | 无 | 只在包边界，而包边界 = 团队边界，本来就是团队间 API | 每一对页面之间都可能漂 |
| 代价 | — | 每包多一份运行时（23 KB；§2.11 起运行时随宿主，此项消失） | 分发系统复杂一个量级 |

选多包，并且**多包是基本情形，单包是 N=1**：文档、库 API、服务端都按多包写，没有单包特化路径。单页独立下发在所有维度上都被多包覆盖（一页一包也合法），不再保留为推迟项。

随之定下的几件事：

- **包名进模块名**：`<pkg>/<src/pages 下相对路径>`，`subscription/home`。"页面名 = 模块名 = 路由键"在多包下原样成立，任何一侧都不拼接；`pages/` 段去掉——包名已是命名空间，它不再携带信息。包名 `[a-z0-9-]+`，按业务域命名不按团队命名（团队会重组）
- ~~**运行时仍放包里**：多包让"A 团队升 `tinyui-core` 不牵动 B 团队"成为刚需，宿主带运行时会把版本矩阵带回来~~——2026-09-24 作废，运行时随宿主，理由见 §2.11
- **URL 只放永久身份**：`/<app>/<channel>/<pkg>/<hostVersion>/…`。组织 / 租户是计费主体，会改名转让，只在管理面作 app 的可改字段，不进 URL。`channel` 是宿主构建变体决定的 App 级部署环境，排在 `pkg` 前，宿主的 base 仍是一段 `<app>/<channel>`
- **密钥与 token 按 (app, pkg)**：A 团队物理上发不出 B 包；`name` 与 `hostVersion` 都在签名内，包不可能被发成别的包
- **服务端内容按 (app, pkg, hostVersion, version) 存一份，channel 只是指针**：staging 验过的同一个 version 晋级到 production 不重传不重签，隔离靠 token 的 channel 范围
- **不做跨包共享模块**：包自包含，共用工具代码各编一份；build-chain.md §3 原来留的"App 级共享模块"出口一并砍掉——多包下它会变成一条独立的分发链
- **跨包通信照旧走 Kotlin**，store key 与 events topic 按包名加前缀，params 契约当团队间接口版本管（app-model.md §7）

### 2.11 运行时随宿主，tinyui 版本从"相等"放宽为"宿主不低于包"（2026-09-24）

原设计运行时随包，包与宿主的 tinyui 版本要求相等（publish、客户端下载前、K0 三处核对），升 tinyui 必加 `hostVersion`。TrendingAI 在 09-23～24 跟着 tinyui 升了 0.4.0 → 0.4.1 → 0.5.0 → 0.6.0，`hostVersion` 1 → 4，四份宿主快照之间只有 `tinyui` 一行不同；同期 `packages/core`、`packages/native` 的源码 diff 只有 `VERSION` 常量与 `PROTOCOL` → `version` 改名，`engine` 未变。三次加 1 没有一次对应真实的不兼容，却每次都要 JS 在新 `hostVersion` 下重发、晋级、宿主 pull 之后才能发版。根子是拿整个库的版本号当"运行时 ↔ Kotlin"内部协议的兼容键，而库版本还随 `updates` 模块这类与包无关的改动变。

| | 运行时随包 + 版本相等（原） | 运行时随包 + 独立 ABI 号 | **运行时随宿主 + 宿主不低于包** |
|---|---|---|---|
| 运行时 ↔ Kotlin 协议 | 同一 tinyui 版本才对得上 | 协议变了由人加 ABI 号 | 同一个产物，不存在错位 |
| 升 tinyui | 加 `hostVersion`，全部包重发 | ABI 未变则不加 | 不加 |
| 靠什么守 | 相等核对 | 人记得加号——`PROTOCOL` 从 M1 到删除一直是 1，改 mount 参数、加 `host.call` 时都没加 | 运行时公开面只增不删（下方铁律） |
| 运行时热修 | 能 | 能 | 不能，随 App 发版 |

选运行时随宿主。ABI 号是把已被证明没人会加的 `PROTOCOL` 换个名字重来一遍。

§2.10 原判"宿主带运行时会把版本矩阵带回来"：矩阵来自"运行时版本 × 页面构建版本"的任意组合都要单独判定；公开面只增不删之后，兼容退化成一条全序——页面在构建时的版本及以上的运行时都能跑——不存在矩阵。"A 团队升 `tinyui-core` 不牵动 B 团队"在相等规则下本就不成立（包的版本必须等于宿主）；随宿主之后，A 要用新运行时的东西仍需宿主升级并抬下限（加 `hostVersion`），B 只需把现有内容重发到新 `hostVersion`，不必重建。

决定：

- 包 = 页面字节码 + `manifest.json`。`tinyui-core` / `tinyui-native` 由库在构建时用与所链接引擎同一 commit 的 qjsc 编成字节码，以生成的 Kotlin 常量随库发布（core 库仍零 I/O、零文件依赖）；运行时的 source map 与 `buildId` 随库
- manifest 的 `tinyui` 仍记构建时的 `tinyui-core` 版本，含义改为"页面需要的最低运行时"。客户端（启动选包、下载前）与 `PackageCheck` 判两者**兼容：major 相同，且 `TinyUI.version >= manifest.tinyui`**（semver `^` 的语义）；K0 的版本核对删去。1.0 之前 0.x 之间按版本号全序比较，不允许破坏公开面，真要破坏即发 1.0
- 升 major（公开面有破坏）：宿主升到新 major 时加 `hostVersion`、下限抬到新 major；旧 major 构建的包客户端判不兼容而拒收，回落到内置包（发版时已按新 major 构建）；JS 工程迁到新 major 后发布到新 `hostVersion`
- 宿主快照的 `tinyui` 行改为该 `hostVersion` 的下限，即加 1 时的 tinyui 版本。宿主快照测试判"当前与下限兼容"（跨 major 即失败，逼加 `hostVersion`），单纯升 tinyui 快照不变；`publish` 判"下限与包的 `tinyui` 兼容"，拒绝"老宿主会静默跳过"的发布
- `hostVersion` 加 1 的情形缩为：宿主组件增删或改 schema；宿主能力增删或改参数与行为；抬 tinyui 下限（页面要用新运行时的东西）；换引擎。单纯升 tinyui 不加
- `engine` 仍要求相等：页面字节码绑引擎 commit，换引擎按上条加 1
- **铁律：运行时公开面只增不删、不改语义。** 公开面 = 编译器注入的 `h` / `Fragment` / `thunk`、runtime-api.md 列出的 API、内置组件 schema。破坏性修改只能加新名字，或升 major。挡住的改动：内置组件 prop 改名或改义、删除已公开的 API、改编译器注入函数的调用约定

**`hostVersion` 是宿主对页面承诺的版本，不是 tinyui 版本。** 承诺指宿主组件、宿主能力、保证提供的运行时下限与字节码引擎，承诺变了才加 1；tinyui 版本是库的实现版本，升级实现而承诺不变时 `hostVersion` 不动。原规则"升 tinyui 必加 1"让两者同步变化（0.4.0 → 0.6.0 对应 1 → 4），容易被当成同一个东西，而四份快照除 `tinyui` 行外完全相同，说明宿主承诺一次也没变过。

`hostVersion` 在此之后的职责。"包能不能在本机跑"客户端已能从 manifest 自算（`engine` 相等、tinyui 兼容、`requires` ⊆ 宿主提供），`hostVersion` 不再是兼容判断的必需品，留下的是客户端做不到的三件事：

1. **投递分组**：宿主给页面的东西变了之后，老 App 与新 App 各要一个"当前版本"。只有一个指针时，页面一用新能力，老 App 就再也收不到任何更新（含 bug 修复）；指回不用新能力的版本，新 App 又退化。`hostVersion` 是这个分组键
2. **发布前知道目标人群有什么**：`publish` 对着该 `hostVersion` 的快照判定，把"老设备静默跳过"拦在发布那一刻
3. **同名改义的声明**：能力改了参数或行为但名字不变，客户端核对不出来；由改宿主的人加 1，旧页面到不了新宿主

替代方案（指针列出多个候选版本、客户端挑能跑的最新一版，配"能力改义即换名"约定）要改投递协议，而协议一经发布不能再变（§2.6），为省下偶尔一次加 1 不值得。触发条件记 roadmap D 组：`hostVersion` 加 1 的频率重新成为负担。

四个版本号各管一件事，不要混用：

| 版本 | 形如 | 由谁定、记在哪 | 回答的问题 | 用在哪 |
|---|---|---|---|---|
| `hostVersion` | `5` | 宿主仓手动加 1（TrendingAI 的 `HOST_VERSION`），快照 `tinyui-host/<n>.txt` | 宿主对页面承诺了什么（能力契约的版本） | 投递路径分组、`publish` 核对 |
| tinyui 版本 | `0.7.0` | 宿主的 Maven 依赖 `app.tinyui:tinyui`，即 `TinyUI.version`；JS 侧是 `tinyui-core` 的版本，写进 manifest 的 `tinyui` | 宿主：本机运行时是哪版；包：页面至少需要哪版运行时 | 客户端判兼容（同 major 且宿主不低于包）；快照的 `tinyui` 行是该 `hostVersion` 的下限 |
| 包版本 | `20260924T030750Z-<12 位 sha>` | `tinyui build` 按构建时间 + JS 工程的 git 短 sha 生成（可用参数指定），manifest 的 `version` / `createdAt` | 这是页面内容的哪一次构建 | 指针指向谁、"比内置新才装"、拉黑、回滚（用旧内容发新版本） |
| CLI 版本 | `0.7.0` | JS 工程的 `tinyui-cli` npm 依赖，与 `tinyui-core`、Kotlin 库同号同发 | 用哪版工具链构建 | 决定包的 `tinyui` 值与 qjsc（即 `engine`）；不得高于目标 `hostVersion` 的下限 |

引擎 commit（manifest 的 `engine`）不单列：它由 CLI 与库各自钉死的 quickjs-kmp 决定，要求相等，换引擎按上文加 `hostVersion`。

代价：运行时的 bug 只能随 App 修（0.4 → 0.6 运行时无功能改动，20 KB 且稳定）；库作者承担公开面的兼容；JS 工程的 `tinyui-cli` 不得高于目标 `hostVersion` 的下限，由 `publish` 报错指出。

迁移：包格式不兼容（包内无 `runtime/`，manifest 去掉 `runtime` 字段），随 tinyui 0.7.0 一起落地，各宿主加 1 次 `hostVersion`，此前各 `hostVersion` 下已发布的包作废。TrendingAI 线上尚无带热下发的版本（1.9.0-beta.1 只含首次接入），无兼容负担。

## 3. 决策

| 项 | 结论 |
|---|---|
| 包 | App 由 N≥1 个包组成；一个包 = 一次 `tinyui build` 的产物（`pages/**/*.bin` + `manifest.json`；运行时随宿主，§2.11），整包原子生效，各包独立；不做单页下发、不做跨包共享模块、不做 zip、不做 diff |
| 包名与模块名 | 包名 `[a-z0-9-]+` 来自 `tinyui.config.json`；模块名 = 路由键 = `<pkg>/<相对路径>`，无 `pages/` 段 |
| 兼容键 | 宿主声明的 `hostVersion`，作服务端路径；`engine` 要求相等、`tinyui` 要求与宿主兼容（同 major 且宿主不低于包），只校验；单纯升 tinyui 不加 `hostVersion` |
| 运行时 | `tinyui-core` / `tinyui-native` 随库编成字节码进 App，包里不带；运行时公开面只增不删、不改语义（§2.11 铁律） |
| 启用规则 | 下发包 `version ≠ installed` 且 `createdAt` 新于内置才装；App 升级带来更新的内置包时自动弃掉 installed；不能用下发把 App 降到比内置更老 |
| 生效时机 | 下次进程启动 |
| 失败语义 | 下发包的页面报 E2 / E6 → 当场用内置包重挂这一页，整包持久化拉黑并上报；E5 不算失败。启动时重算 installed 包 sha256，不一致同样拉黑。进程级崩溃不在库的回退范围：出口是灰度 + 回滚 + "`check()` 先于任何页面挂载"的接入约定，挂载标记见 §4.3。运行时永远来自宿主；不同页面可以短暂来自不同包（各自独立 Runtime） |
| 库的边界 | `tinyui-updates` 独立 artifact；`Updates(packages: List<Bundle>, …)` 以一组包为单位做校验 / 落盘 / 选择 / 回退；不做网络、调度、UI；宿主给内置包列表、`fetch(path)`、`installId`、存储目录、`hostVersion`，不传公钥 |
| 服务端协议 | 指针 + 内容；`<base>/<pkg>/<hostVersion>/current.json`（`no-store`，只含 `version` / `rollout` / `signature`）+ `<base>/<pkg>/<hostVersion>/<version>/…`（`immutable`，含 `manifest.json`）；请求不带参数；base = `/<app>/<channel>`，组织不进 URL |
| 灰度 | manifest `rollout` 百分比，客户端按 `installId` 掷骰 |
| 回滚 | 用旧内容发新版本，指针只往前走；无 `rollBackToEmbedded` |
| 完整性 | 逐文件 sha256 + manifest 签名（ECDSA P-256，覆盖 `<version>/manifest.json` 的原始字节，无规范化）；密钥按 (app, pkg)，发布方持私钥，服务端与客户端各验一次；公钥写在 `tinyui.config.json` 随 build 进 manifest，客户端只信内置包的 |
| CLI | `tinyui.config.json`（`name` / `publicKey` / `pages`）；`tinyui build` 不产出 `runtime/`，manifest 加 `name` / `publicKey` / `version` / `createdAt` / `engine` / `tinyui` / `hashes`；`tinyui publish` 核对包的 `tinyui` 不高于目标 `hostVersion` 的下限；`tinyui bundle --host-version --signing-key` 产出上传目录 `dist/ota/<pkg>/<hostVersion>/`（`current.json` + `<version>/`）；`tinyui publish` 走发布协议；`keys` / `apps` / `packages` / `tokens` / `releases` 子命令是全部管理面 |
| 服务端 | 独立开源仓 `tinyui-updates-server`（MIT，托管前重评）：Hono，单 R2 桶，`Storage` 接口后置；内容按 (app, pkg, hostVersion, version) 存，channel 是指针；托管实例 `updates.tinyui.app`，私有化 = 部署同一份代码；没有控制台 |
| 仓边界 | `tinyui-updates` 在主仓 `updates/` 模块，与 core 同版本发；服务端独立仓；协议只写在 updates.md，发布后字段只增不改 |

## 4. 后果

### 4.1 应用商店政策

App Store Review Guidelines 2.5.2 只豁免由 WebKit / JavaScriptCore 执行的下载代码，QuickJS 不在名单里。React Native + Hermes 的 CodePush、字节的 Lynx 实践上未被拦，但这是"事实容忍"不是"规则允许"。F-Droid 收录政策对运行时下载并执行代码有限制（原文措辞待核对）。

### 4.2 首批租户

TrendingAI 与第二个 App 都作为 `updates.tinyui.app` 的 app 接入，各自一个 `app` id、自己 bump 的 `hostVersion`；TrendingAI 一个包（`trendingai`，页面 `trendingai/subscription`），第二个 App 按业务线分包，每个包自己的密钥对与 token。宿主后端不需要任何改动，只配 base URL。发布由各包 JS 工程的 CI 完成：`tinyui bundle --signing-key` → `tinyui publish`。内置包只求兼容：是给当前 `hostVersion` 发布过的某个版本，由宿主仓用 `tinyui pull` 刷新（updates.md §1.4），新旧尽力而为。TrendingAI 的页面模块名已从 `pages/…` 改为 `trendingai/…`（M6）。

### 4.3 推迟项（记 roadmap D 组）

| 项 | 触发条件 |
|---|---|
| 无活页时立即切换 | "第二次打开才见到更新"成为运营问题 |
| `rollBackToEmbedded` 指令 | 必须让所有用户立刻回内置、又没有可发的好包 |
| 服务端定向灰度（按用户属性） | 百分比灰度不够用 |
| store key / events topic 的包名前缀由库强制（`tinyui-native` 自动加） | 出现跨包撞 key / 撞 topic |
| `Updates` 的分包 `check()` 调度（按包设不同检查时机） | 某个包要求与其他包不同的更新节奏 |
| 控制台 | CLI 管理面不够用（多人协作、非开发者操作回滚） |
| 遥测（客户端上报 `UpdateEvent`） | 控制台需要采用率与回退率。定了做法（2026-09-19）：服务端接 eventbase（`createIngest` / `createQuery` 挂进 tinyui-updates-server，租户各一个 appKey，`active` 即采用率分母，按 props 切片先走 `POST /sql`；投递日志用 `createTracker`），协议归 eventbase 仓 `docs/protocol.md`，本仓只引用；客户端 `tinyui-updates` 不依赖 eventbase-kt，提供按该协议格式化的 `EventbaseTelemetry(app, send)` 适配器（`install` = `sha256(installId + app)`，按 app 而非按包去重，事件 props 带 `pkg`，无队列），已用 eventbase-kt 的宿主直接把 `onEvent` 桥到 `track`。前置：eventbase 加租户级取数（按 appKey 作用域的 token） |
| 服务端 D1 | 控制台需要跨维度查询；遥测立项时随 eventbase 的 migrations 到位 |
| 非 Cloudflare 的私有化适配器（文件系统 + SQLite，Docker 镜像） | 出现非 Cloudflare 的私有化需求 |
| 计费与计费身份（opt-in 的安装标识 header，宿主 `fetch` 加、库不知情；MAU 按 app 内 `install` 去重，不因分包重复计数） | 托管实例对外收费；业界参照见 §4.4 |
| 挂载标记：`UpdatesPage` 挂 installed 页面前落盘 `attempting`、首帧后清除，构造时残留计数达 2 即拉黑（CodePush `notifyAppReady` / Android A/B boot-success 同类） | 出现以 TinyUI 页面作启动首屏的宿主，或线上出现热下发包导致的崩溃循环。在此之前：启动 sha256 校验挡磁盘损坏；页面代码触发的引擎崩溃是确定性的、同一引擎 commit 在开发与 staging 就会复现，线上靠灰度 + 指针回滚 + `check()` 先于页面挂载 |
| 增量传输 | 整包超过 1 MB |
| 页内 `import()` 懒加载（接 `JsEngineConfig.moduleLoader`） | 出现单页字节码过大的页面 |

### 4.4 计费的业界参照：Expo EAS Update（2026-09-19 抄自 expo.dev/pricing）

| 档 | 月费 | 更新 MAU | 边缘带宽 | 存储 | 端到端代码签名 |
|---|---|---|---|---|---|
| Free | $0 | 1,000 | 100 GiB | 20 GiB | 无 |
| Starter | $19 + 超量 | 3,000 | 100 GiB，超量 $0.10/GiB | 20 GiB，超量 $0.05/GiB | 无 |
| Production | $199 + 超量 | 50,000 | 1 TiB | 1 TiB | 有 |
| Enterprise | 定制 | 1,000,000+ | 40 TiB | 10 TiB | 有 |

- 计费单位是 MAU（计费周期内至少下载过一次更新的用户；同一用户多次下载算 1 个；只检查不下载不算），更新次数不限。单位跟租户业务规模走、不惩罚频繁发版，也不需要客户端上传包大小之类的数据——与 §4.3"计费身份只是一个 opt-in header"的形态相容。
- 代码签名被当作 $199 档以上的付费分层点。tinyui 把签名放进 MVP（§2.9）且服务端开源，这是相对 EAS 的差异点，不能反过来拿签名做分层。
- Expo 同时保留免费出口：`expo-updates` 协议公开，`updates.url` 指自建服务器即可脱离 EAS（官方 `custom-expo-updates-server` 示例仓）。对应本文 §2.5 的静态目录自托管与 §2.6 的私有化部署。

### 4.5 不变的东西

ADR-001～004 不动；ADR-005 §4 的模块名规则改为含包名（本文 §2.10）；`PageHost` / `TinyUIPage` 签名不变（`RuntimeBundle` 改由库提供，§2.11）；patch 协议的 op 与入口、schema 生成链、错误上报不变；quickjs-kmp 引擎不改（`engine` 从字节码文件头读，`QuickJs.upstreamCommit` 已有）；`qjsc-kmp` 二进制分发是构建链事项，见 roadmap D 组。
