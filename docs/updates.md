# 热下发：包、投递协议、发布协议与客户端

- 状态：已定（2026-09-18；2026-09-19 加发布协议、签名、服务端形态；2026-09-19 改为多包模型——App 由 N≥1 个包组成，包名进模块名与 URL，公钥进 manifest，`Updates` 以一组包为单位；2026-09-22 修订：签名覆盖不可变 `manifest.json` 的原始字节，指针文件 `current.json` 只含 `version` / `rollout` / `signature`，启动时重算 installed 包的 sha256；2026-09-23 兼容键 `runtimeVersion` 改名 `hostVersion` 并限定为正整数；同日加 §1.3 发布前核对目标宿主版本、§6 的 app token 与宿主快照端点）；实现依据，实现待开
- 来源：[ADR-006](./adr-006-hot-updates.md)；[build-chain.md](./build-chain.md) §2（manifest、模块名、`tinyui.config.json`）、§7（buildId 与 source map）
- 四侧：CLI 产出与发布包（`tinyui build` / `bundle` / `publish`）；服务端实现投递与发布两组端点（参考实现 `tinyui-updates-server`，托管实例 `updates.tinyui.app`）；Kotlin 的 `Bundle`（core 库）与 `Updates`（`app.tinyui:tinyui-updates`）
- 协议规范只在本文一处；服务端仓的一致性测试以本文为准，不复制。**发布后字段与端点只增不改，未知字段透传**——这是两个仓能各自演进的前提

## 0. 模型

**App 由 N≥1 个包组成。包 = 所有权单元 = 分发单元 = 一次 `tinyui build` 的完整产物。** 单包 App 是 N=1，没有任何特殊路径。

- 包名 `[a-z0-9-]+`，来自 JS 工程的 `tinyui.config.json`，同时是模块名前缀、URL 路径段、服务端的密钥与 token 粒度；按业务域命名（`subscription`），不按团队命名
- 包之间没有共享代码、没有共享运行时、没有直接通道；跨包只走 Kotlin 中转的 JSON（[app-model.md](./app-model.md) §3、§7）
- 每个包独立校验、独立落盘、独立掷骰、独立回退；一个包坏了不影响其他包

## 1. 包

一个包 = 一次 `tinyui build` 的输出目录去掉 `.js` / `.js.map`：

```
manifest.json
runtime/core.bin  runtime/native.bin
pages/**/*.bin
```

### 1.1 `manifest.json`

在 build-chain.md §2 的四个字段上扩展：

| 字段 | 写入者 | 内容 |
|---|---|---|
| `runtime` / `pages` / `files` / `buildIds` | `tinyui build` | 不变；`pages` 与 `files` 的键是含包名的模块名（`subscription/home`） |
| `name` | `tinyui build` | 包名，读自 `tinyui.config.json` |
| `publicKey` | `tinyui build` | 验签公钥（§7 格式），读自 `tinyui.config.json`；**信任来源只有内置包里的这份**，下载的 manifest 里的只用于诊断 |
| `version` | `tinyui build` | 包标识，目录名，只求唯一：`<createdAt 紧凑形式>-<git 短 sha 或 nogit>`，如 `20260918T100212Z-3f2a1c`；`--version` 可覆盖 |
| `createdAt` | `tinyui build` | ISO 8601 UTC，新旧比较只看它 |
| `engine` | `tinyui build` | 字节码文件头里的引擎 commit（40 位 hex，所有 `.bin` 一致，取第一个） |
| `protocol` | `tinyui build` | 所含 `tinyui-core` 的 `PROTOCOL`，读自子路径导出 `tinyui-core/protocol`（只含常量，不在 Node 里执行运行时模块） |
| `hashes` | `tinyui build` | 模块名 → 该模块 `.bin` 的 sha256 hex，键与 `files` 一致 |
| `tinyui` | `tinyui build` | 运行时模块取自的 `tinyui-core` 版本，决定页面能用哪些内置组件；`publish` 核对它等于目标宿主的 tinyui 版本（§1.3），客户端不读 |
| `requires` | `tinyui build` | 页面模块名 → `{ components, capabilities }`：该页用到的带点宿主组件名与 `host.call` 能力名，各自排序；内置组件不列。`publish` 据此核对目标宿主版本（§1.3），客户端不读 |
| `hostVersion` | `tinyui bundle` | 发布目标，等于宿主声明值；写入后文件定稿，签名覆盖它的原始字节（§7） |

签名值与 `rollout` 不在 manifest 里，在指针文件 `current.json`（§1.2）。内置包的 manifest 没有 `hostVersion`，也没有指针文件——宿主知道自己的 hostVersion，内置包不参与灰度、不验签。

### 1.2 `tinyui bundle`

```
tinyui bundle --host-version <hostVersion> --signing-key <私钥 PEM> [--rollout <p>] [--out dist/ota]
```

读 `tinyui build` 的输出目录，写：

```
dist/ota/<pkg>/<hostVersion>/current.json             指针：{ "version", "rollout", "signature" }
dist/ota/<pkg>/<hostVersion>/<version>/manifest.json  build 的 manifest + hostVersion，写定后不再变
dist/ota/<pkg>/<hostVersion>/<version>/runtime/*.bin
dist/ota/<pkg>/<hostVersion>/<version>/pages/**/*.bin
```

`current.json` 只有三个字段：`version` 指向哪个目录；`rollout` 0～100 的整数，缺省 100，服务端可改；`signature` 是对 `<version>/manifest.json` 原始字节的签名（§7）。指针里只有投递策略与签名值，没有任何需要签名保护的字段。

这个目录是包的最终形态：交给 `tinyui publish` 上传，或者原样放到任何静态目录（§2.2）——它的布局就是 §2 客户端要请求的路径。`.js.map` 不进包，发布方归档 build 输出目录以便按 `buildId` 离线对映射（build-chain.md §7）。

### 1.3 发布前核对目标宿主版本

`--host-version` 是发布方手填的。填成一个仍有设备在用的旧值（例：main 已为宿主版本 3 用上新能力 `coupon.apply`，却以 `--host-version 2` 发出一个修文案的包），旧宿主会照常装上，页面调用它没有的能力只得到 `E_UNSUPPORTED`、用到它没有的组件只渲染占位——都不崩溃，§4.5 的回退接不住。宿主侧的构建检查（§4.1）只管宿主漏 bump，管不到发布方填错。

所以 `tinyui publish` 在上传前核对：**这个包用到的宿主东西，目标宿主版本都提供**。放在 `publish` 而不是 `bundle`：`bundle` 是纯离线命令（签名、写目录，静态托管就用它的产物），`publish` 本来就带着 app、实例地址与 token 在和服务端说话，快照也在服务端上；静态托管没有快照，也就没有这层核对。

| 输入 | 来源 |
|---|---|
| 包用到了什么 | `tinyui build` 从每页打包产物里静态找出，写进 manifest 的 `requires`（页 → `{ components, capabilities }`）：`host.call` 的能力名、带点的宿主组件名（`ta.Icon`）；内置组件不列，随 tinyui 版本走（规则见 build-chain.md §5.1） |
| 目标宿主版本提供了什么 | 宿主快照 `tinyui-host/<hostVersion>.txt`（宿主组件、能力名、tinyui 版本）。宿主发 App 版本时由其 CI 上传到热下发服务，每个 (app, hostVersion) 只能写一次，与已随发版冻结的快照一致；`publish` 从服务端读 |

不通过即拒绝发布（一个请求都不上传），报出哪一页缺了什么：页面用到的能力或宿主组件不在快照里；或 manifest 的 `tinyui` 不等于快照里的 tinyui 版本（新版 tinyui 的内置组件，旧宿主没有）。目标宿主版本还没有快照（宿主没上传过）也拒绝。

核对的是名字，覆盖不到已有能力的参数形状与行为，那部分仍按 §4.1 的 bump 规则由人判断。绕过 `publish` 直接调发布端点的，目前拦不住；`requires` 进 manifest 并受签名覆盖，日后服务端在 `PUT current.json` 时同样可以核对。快照的上传与读取见 §6.4。

## 2. 投递协议

客户端只会发两种 GET，路径相对宿主配置的 base URL：

| 路径 | 内容 | 缓存 |
|---|---|---|
| `<pkg>/<hostVersion>/current.json` | 可变指针 | `Cache-Control: no-store` |
| `<pkg>/<hostVersion>/<version>/manifest.json` | 不可变，签名覆盖其原始字节 | `Cache-Control: public, max-age=31536000, immutable` |
| `<pkg>/<hostVersion>/<version>/<files[module]>.bin` | 不可变内容 | 同上 |

- `pkg` 库从内置 manifest 读，`hostVersion` 宿主给；宿主只拼 base，一个 App 不管几个包都是一个 base URL、一个 `fetch`
- 请求不带 query、不带自定义 header、不带任何设备信息；宿主的 `fetch` 实现可以自行加 header 或按路径分流到不同来源，库不知情
- 同一 `<version>` 目录下的文件永不改内容（改内容必换 version），CDN 因此不会陈旧
- 无更新时客户端只下载指针（约 100 字节）；`manifest.json` 与文件只在有新 version 且中签时才取
- 回滚 = `current.json` 的 `version` 与 `signature` 换回上一版；灰度 = 改 `rollout`；两者都不碰 `<version>/` 下的任何文件
- 投递面没有鉴权：包在安装包里本来就能反出来；来源与完整性由签名（§7）保证

### 2.1 托管与私有化实例

`tinyui-updates-server`（独立仓，Cloudflare Workers 参考实现）实现本节与 §6。托管实例 `updates.tinyui.app`；私有化 = 部署同一份代码到自己的 Cloudflare 账号。

```
base = https://updates.tinyui.app/<app>/<channel>
完整路径 = /<app>/<channel>/<pkg>/<hostVersion>/current.json
```

托管实例的指针、release 记录与 token 都存 R2（强一致）：发布、回滚、吊销 token 立即生效。代价是 `current.json` 的读取不走 KV 的边缘缓存，延迟略高，对 App 启动时的后台 `check()` 可接受。

进 URL 的只有永久不变的身份：`app` 由实例管理员分配、全局唯一（§6.3）；`channel` 由宿主构建变体决定（`production` / `staging` / `dev`），是 App 级的部署环境而不是包级的；`pkg`、`hostVersion` 见上。**组织 / 租户（计费主体）不进 URL**——它会改名、转让、拆合，只是 app 记录上的一个可改字段。

### 2.2 静态目录

不需要服务端代码：把 §1.2 的 `dist/ota/` 放到任何能设缓存头的静态托管，base URL 指向它。没有发布协议、没有服务端验签，签名仍由客户端验。

## 3. Kotlin：`Bundle`（core 库）

把宿主现在手写的"读 manifest → 读运行时一次 → 读页面与 map"收进库：

```kotlin
fun interface BundleFiles { suspend fun read(path: String): ByteArray? }   // path 如 pages/home.bin、manifest.json

class Bundle(val manifest: BuildManifest, files: BundleFiles) {
    val name: String get() = manifest.name
    suspend fun page(name: String): LoadedPage     // name 是含包名的模块名；运行时字节码读一次缓存；map 读得到就交给 SourceMaps
    companion object { suspend fun load(files: BundleFiles): Bundle }   // 读 manifest.json
}
class LoadedPage(val runtime: RuntimeBundle, val module: PageModule, val sourceMaps: SourceMaps, val bundle: Bundle)
```

`BuildManifest` 解析 §1.1 全部字段，`name` / `publicKey` 缺失即解析失败。内置包的 `BundleFiles` 由宿主用 `Res.readBytes` 实现（每包一个资源子目录，如 `files/tinyui/<pkg>/`）；`Bundle` 不知道字节从哪来。

## 4. Kotlin：`Updates`（`tinyui-updates`）

主仓 Gradle 模块 `updates/`，与 `compose/` 同版本号同 tag 发 Maven。

### 4.1 宿主提供什么

```kotlin
class Updates(
    packages: List<Bundle>,                      // 内置包，各自 manifest.name 即包名；重名、engine 与宿主引擎不符 → 构造抛错
    val hostVersion: String,
    dir: Path,                                   // 宿主给的目录，如 Android filesDir/tinyui、iOS Application Support/tinyui；库内按 <dir>/<pkg>/ 分
    installId: String,                           // 稳定的安装标识；库不生成、不持久化、不上传
    fetch: suspend (path: String) -> ByteArray,  // 宿主用自己的 HTTP 栈；path 是 §2 的相对路径，库已拼好 <pkg>/<hostVersion>/ 前缀，宿主只拼 base
    onEvent: (UpdateEvent) -> Unit = {},
)
```

验签公钥不由宿主传：每个包的信任锚是它内置 manifest 里的 `publicKey`（§1.1、§7）。单包宿主写 `Updates(listOf(embedded), …)`。

**`hostVersion` 的口径**（作用同 Expo 的 `runtimeVersion`，但只取正整数、数的是宿主的变化；不沿用那个名字，是因为 tinyui 里 runtime 已指运行时模块与引擎）：宿主仓持有的递增正整数字符串（`"1"`、`"2"`……；`1.2.0` 这类 App 版本号在 CLI、`Updates`、服务端三处都被拒），语义是"这个值下发布的任何包都能在本宿主上跑"；一个宿主一个值，挂在它上面的所有包共用。宿主给页面的东西变了就加 1：宿主组件增删或改 schema；宿主能力增删，或改已有能力的参数与行为；升级 tinyui。只改宿主内部实现、其他原生页面、修崩溃，不加。包的 JS 工程发布时由 `tinyui bundle --host-version` 取这个值，不自行推导。JS 侧取错分两个方向：取成不存在或更新的值，包只是送不到（`releases list` 与客户端事件可见）；取成一个仍有设备在用的旧值，旧宿主会照常装上，而页面可能用到它没有的东西——宿主构建的检查拦不住这个方向，由 `publish` 发布前核对（§1.3）。

漏加的后果是旧宿主收到跑不了的页面（不崩溃的错误不会触发 §4.5 回退），多加的后果是更早的宿主从此收不到更新。漏加只可能发生在宿主仓，由宿主构建拦：宿主快照 `tinyui-host/<hostVersion>.txt`（宿主组件 schema、能力名、tinyui 版本）每个版本一份入库，当前宿主与当前版本的快照不符即构建失败；已随发版带出去的版本，其快照冻结不可改。快照覆盖不到已有能力的参数与行为变化，这部分按上面的规则由人判断。为什么是精确匹配而不是 `>=` 范围，见 ADR-006 §2.2。

快照由 `HostSnapshot.render(host, hostVersion)` 从宿主的 `TinyUIHost` 生成（所以能力必须在 App 级注册，native-api.md §7），检查写成宿主自己的 host 侧单元测试，照 sample 的 `HostSnapshotTest`：

- 当前 `hostVersion` 的快照文件不存在，或与 `render` 结果不同 → 失败，提示加 `hostVersion`
- 加 `-Ptinyui.updateHostSnapshot` 跑同一个测试 → 写入当前结果。这等于人声明"这个版本还没随发版带出去"；本地不判断版本是否已发，冻结由服务端兜底：`hosts upload` 同版本不同字节得 409（§6.4），发版 CI 在那里失败
- 快照文件在 `.gitattributes` 里固定 `eol=lf`：上传的是原始字节，CRLF 的副本会被当成另一份快照而得 409
- `hostVersion` 在宿主里只写一处常量，`Updates(hostVersion = …)` 与这个测试都读它

测试只构造 `TinyUIHost`、不执行任何能力与组件，所以能在 JVM 上跑；能力实现需要的仓库类对象在测试里传假的即可。

**接入约定：`check()` 在任何 TinyUI 页面挂载之前调用**（App 启动即调）。指针回滚对已装上的用户生效靠的是下一次 `check()` 装上回退版本；页面若先于它崩溃，回滚永远送不到。进程级崩溃（native 段错误）不在 §4.5 的回退范围内，出口就是灰度 + 指针回滚 + 这条约定；挂载标记见 ADR-006 §4.3 推迟项。

文件与 sha256 用 okio（`FileSystem` + `ByteString.sha256`），是本 artifact 独有的依赖，core 库不引入。ECDSA 验签走平台 API（§7）。

### 4.2 API 面

| 成员 | 语义 |
|---|---|
| `fun current(pkg: String): Bundle` | 构造时按包定死（§4.4）；进程内只在 §4.5 回退后换回该包的内置 |
| `suspend fun check(): Map<String, CheckResult>` | 全部包各跑一遍 §4.3，彼此独立并行，一包失败不影响其他；同一实例串行，重入直接返回进行中的结果 |
| `suspend fun check(pkg: String): CheckResult` | 只查一个包 |
| 顶层 `@Composable fun UpdatesPage(updates, name, host, services, propsJson, modifier, error, onHost)` | 与 `TinyUIPage` 同参外加 `updates`；`name` 是含包名的模块名，第一个 `/` 之前即包，`current(pkg).page(name)` → `TinyUIPage`；`error` 前先走 §4.5 的回退 |

`CheckResult`：`UpToDate` / `Installed(version)` / `Skipped(reason)` / `Failed(stage, cause)`。`UpdateEvent` 是同一组事实加 `Running` 与 `RolledBack`，给宿主打日志与埋点；**所有事件都带 `pkg`**。宿主的分析口径会依赖它，所以发布后字段同样只增不改：

| 事件 | 何时 | 字段（均含 `pkg`） |
|---|---|---|
| `Running` | 构造时每包一次 | `version`（内置或 installed 的 manifest `version`）、`source`：`EMBEDDED` / `INSTALLED` |
| `UpToDate` | `check()` | `version` |
| `Skipped` | `check()` | `version`、`reason`：`INCOMPATIBLE` / `FAILED_BEFORE` / `OLDER_THAN_EMBEDDED` / `ROLLOUT`；`INCOMPATIBLE` 附 `mismatch`：`NAME` / `VERSION` / `ENGINE` / `PROTOCOL` / `HOST_VERSION` 的集合 |
| `Installed` | `check()` | `version` |
| `Failed` | `check()` | `version`（manifest 解析失败时为空）、`stage`：`POINTER` / `MANIFEST` / `SIGNATURE` / `DOWNLOAD` / `INTEGRITY` / `STORAGE`；`INTEGRITY` 也在启动校验（§4.4）时发出、`message`（`SIGNATURE` 时注明下载 manifest 的 `publicKey` 是否等于内置——区分被篡改与公钥已轮换） |
| `RolledBack` | §4.5 | `version`、`page`（模块名）、`kind`（`E2` / `E6`）、`buildId`、`message` |

这些字段由下面的遥测产品功能倒推而来；事件在库里定形，上报到哪里（宿主埋点、更新服务的控制台）是宿主的事。控制台的一切视图都按 (app, pkg) 切，采用率的分母 `install` 按 app 去重（ADR-006 §4.3）。

**遥测产品功能**（控制台按发布者的工作流排；"投递日志"指服务端对两种 GET 的计数）：

| 阶段 | 功能 | 看什么 | 数据 | 分期 |
|---|---|---|---|---|
| 发布后观察 | 采用曲线 | 每个 release 跑它的活跃设备占比随时间变化，多条叠看新版吃掉旧版 | `Running` 按 (pkg, version, day) 去重计数 | MVP |
| | 版本分布 | 此刻活跃设备各跑哪个 version、多少还在内置包 | `Running` 快照 | MVP |
| | 安装漏斗 | check → 中签 → 下载 → 校验 → 安装 → 生效，每级掉多少 | `Skipped(ROLLOUT)`、`Failed(DOWNLOAD / INTEGRITY)`、`Installed`、`Running` | MVP |
| | 下载量对照 | 服务端数到的 `<version>/` 下载次数 vs 客户端上报的 `Installed`，对不上即上报链路或 CDN 有问题 | 投递日志 + `Installed` | 二期 |
| 灰度决策 | 灰度命中率 | 中签比例是否贴近 `rollout`，偏离即宿主 `installId` 有问题 | `Skipped(ROLLOUT)` vs `Installed` | MVP |
| | 灰度期健康对比 | 灰度人群与内置包人群的回退率、失败率对比，决定推 100% 还是回滚 | `RolledBack`、`Failed` 按 version 分组 | MVP |
| | 拨杆就地操作 | 曲线旁直接改 `rollout` 或回滚指针（§6.2 的 UI 版） | 发布协议 | MVP |
| 止血 | 回退率告警 | `RolledBack / Installed` 超阈值即通知（webhook / 邮件） | `RolledBack`、`Installed` | 二期 |
| | 自动止血 | 超阈值自动把 rollout 拨 0 或指针退回上一个好版本，按包开关与阈值 | 同上 + 发布协议 | 二期 |
| | 失败原因分布 | `Failed` 按 stage、`Skipped` 按 reason 分布——发错目录、公钥没换、CDN 缓存坏各有各的形状 | `Failed.stage`、`Skipped.reason` | MVP |
| 排障 | 回退关联的页面错误 | `RolledBack` 的 kind / page / buildId 聚合，可对回 source map | `RolledBack` | 二期 |
| | 兼容性错配 | 各不匹配项各多少台；App 新版发出后旧 hostVersion 还剩多少活跃设备，决定何时停发旧 hostVersion | `Skipped.mismatch` | 二期 |
| | 按租户标识查询 | 租户在上报里自带用户 / 设备标识时，查该标识最近跑什么版本、有没有回退 | 上报里的 opaque 字段，服务端只存不解释 | 按需 |
| 健康巡检 | hostVersion 存量 | 每个 hostVersion 还有多少活跃设备，哪些 hostVersion 可停止维护 | `Running` 按 hostVersion | 按需 |
| | 内置包占比 | 长期跑内置包的比例，高了说明 `check()` 没被调用或更新服务被网络策略挡了 | `Running.source` | 按需 |
| | 上报健康 | 遥测事件量与投递日志的比值 | 两者 | 按需 |

不在遥测范围内：页面级性能与业务埋点（宿主埋点的事，遥测只关心包的生命周期）；逐设备实时状态与远程操作（与"投递请求不带设备信息、客户端掷骰"相冲）。

### 4.3 `check(pkg)` 状态机

```
fetch <pkg>/<hostVersion>/current.json ─解析失败──────────────────────────────▶ Failed(pointer)
  │
  ├─ version == installed.version 或 version ∈ failed ────────────────▶ UpToDate / Skipped(failed)
  ├─ hash(installId + ":" + pkg + ":" + version) % 100 ≥ rollout ─────▶ Skipped(rollout)
  │
  ▼ fetch <pkg>/<hostVersion>/<version>/manifest.json，拿到原始字节
  ├─ 用内置 publicKey 对原始字节验 signature 失败 ─────────────────────▶ Failed(signature)   // 上报
  ├─ 解析失败 ────────────────────────────────────────────────────────▶ Failed(manifest)
  ├─ name ≠ pkg / version ≠ 指针 version / hostVersion ≠ 宿主值
  │  / engine ≠ QuickJs.upstreamCommit / protocol ≠ PROTOCOL ─────────▶ Skipped(incompatible)   // 发错目录，上报
  ├─ createdAt ≤ embedded.createdAt ───────────────────────────────────▶ Skipped(older-than-embedded)
  │
  ▼ 逐文件 fetch <pkg>/<hostVersion>/<version>/<file>.bin → <pkg>/staging/<version>/，每个核对 sha256
  ├─ 任一失败 ─删 staging──────────────────────────────────────────────▶ Failed(download | integrity)
  ▼ manifest 原始字节写入 staging/<version>/manifest.json，staging/<version> 改名 installed/<version>，state.installed = version，删其他 installed
  ▼ Installed(version)   // 下次启动生效
```

manifest 一到手先验签再解析：签名不对的 manifest 里任何字段都不可信。验签用的公钥永远是内置 manifest 的，下载 manifest 的 `publicKey` 不参与验签。指针不在签名内，篡改它最多让客户端跳过更新或去取一个本就合法的包，与切断网络等价。掷骰哈希是 sha256 前 4 字节按大端读作无符号整数再取模 100，以 `pkg` 与 `version` 为盐：每个包每次发布独立抽样（monorepo 里同一次 CI 产出的多个包 `version` 可能相同，不加 `pkg` 会让它们的灰度人群重合）；已装上的用户不因 `rollout` 下调而回退。

### 4.4 启动选择

存储布局，每包一份：

```
<dir>/<pkg>/state.json            { "installed": "<version>" | null, "failed": ["<version>", …] }   // failed 最多留 10 条
<dir>/<pkg>/staging/<version>/    下载中
<dir>/<pkg>/installed/<version>/  manifest.json + runtime/ + pages/
```

`state.json` 写入走临时文件加 rename，任何时刻磁盘上都是一份完整的它。

构造时对每个包：`installed` 存在、`name` 等于包名、`hostVersion` 等于宿主值、不在 `failed`、`createdAt` 新于 `embedded`、**逐文件重算 sha256 与 manifest 一致** → `current(pkg) = installed`，否则 `= embedded`。sha256 不一致的（磁盘损坏、半个文件）按失败处理：`failed += version`、`installed = null`、`Failed(integrity)`；校验时读进内存的字节直接作为该包 `Bundle` 的来源，进页面不再读磁盘。`createdAt` 不新于内置或 `hostVersion` 不等于宿主值的 installed 当场删除（App 升级带来了更新的内置包或 bump 了 hostVersion）；残留的 `staging/` 删除；`<dir>` 下不属于任何内置包的子目录删除（App 升级去掉了某个包）。不重验签：签名在落盘前验过，之后文件内容由 sha256 锁住；整包不到 100 KB，重算不到 1 毫秒。

### 4.5 失败回退

`UpdatesPage` 挂的页面来自某包的 installed 且报 E2 / E6（`PageHost.failure`）时：该包 `failed += version`，`state.installed = null`，`onEvent(RolledBack)`，随后用该包的 `embedded.page(name)` 重挂同一页面（`TinyUIPage` 的 `remember` 键含 `page`，换 `PageModule` 即重建引擎）。E2 / E6 本身照常经 `PageSink.error` 上报，带 `buildId`。其他包不受影响。

页面来自 embedded 时的失败走宿主自己的 `error` 槽，与不用 `tinyui-updates` 时相同。

进程内已经挂在该包 installed 上的其他页面不动（各自独立 Runtime），下次启动统一回到 embedded。

## 5. 与既有契约的关系

- `PageHost` / `TinyUIPage` / patch 协议 / schema / `PageSink` 不因热下发而变（M6 把组件、能力、sink 收进 App 级 `TinyUIHost` 是为了宿主快照，见 native-api.md §7）；`Updates` 全部搭在 `Bundle` 与 `TinyUIPage` 之上
- 引擎的 `JsEngineConfig.moduleLoader` 不接入：它服务页内 `import()`，不是页面级分发（ADR-006 §4.3）
- `PageError` 不加字段：`buildId` 已能定位到具体 build，`RolledBack` 事件带 `pkg` 与 `version`
- 跨包的 params / store / events 约定在 app-model.md §7，热下发只保证每个包内部一致

## 6. 发布协议

服务端实现的第二组端点，`tinyui-cli` 是它的客户端。所有请求 `Authorization: Bearer <token>`。

用词：**app** 是一个宿主 App——URL 的 `<app>` 段、`hostVersion` 序列、宿主快照、权限都挂在它上面；**租户**是拥有 app 的计费主体，只是 app 记录上可改的 `org` 字段，会改名、转让，所以不进 URL、不挂权限。一个租户可以有多个 app。

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

`tinyui publish --channel <c> [--app <app>] [--dir dist/ota]`：读 §1.2 的目录（包名从 manifest 来），先 PUT `<version>/` 下全部文件含 `manifest.json`（服务端幂等，同字节即已存在），再 PUT `current.json`。token 只从 `$TINYUI_TOKEN` 读，管理端点从 `$TINYUI_ADMIN_TOKEN` 读，实例地址取 `--url` / `$TINYUI_UPDATES_URL` / 托管实例。

### 6.2 管理 release

| 请求 | 语义 |
|---|---|
| `GET /<app>/<pkg>/<hostVersion>/releases` | 已发布的 version 列表：`createdAt`（包的构建时刻，来自 manifest）、`publishedAt`（服务端记下的发布时刻）、各 channel 的指针与 `rollout` |
| `POST /<app>/<channel>/<pkg>/<hostVersion>/pointer`，body `{ "version": …, "rollout"?: … }` | 指针指向 (app, pkg, hostVersion) 下任一已发布 version——回滚与跨 channel 晋级是同一个操作；或只改当前指针的 `rollout`。服务端用记录里该 version 的 `signature` 重写 `current.json` |

服务端按 version 保存 release 记录（`createdAt`、`signature`），指针切换不需要重新上传；改 `rollout` 只改指针，签名不受影响（§7）。CLI：`tinyui releases list` / `rollback <version>` / `rollout <p>` / `promote <version> --to <channel>`。

### 6.3 管理 app 与包

| 请求 | 凭据 | 语义 |
|---|---|---|
| `POST /apps`，body `{ "id", "name", "org"? }` | `ADMIN_TOKEN` | 建 app；`org` 即租户，可改，不进任何路径 |
| `POST /apps/<app>/tokens` | `ADMIN_TOKEN` | 签发 app token，只返回一次；服务端只存哈希 |
| `DELETE /apps/<app>/tokens/<tokenId>` | `ADMIN_TOKEN` | 吊销 app token |

以下各条 `ADMIN_TOKEN` 或该 app 的 app token 均可：

| 请求 | 语义 |
|---|---|
| `POST /apps/<app>/packages`，body `{ "name", "publicKey" }` | 建包，登记验签公钥 |
| `PUT /apps/<app>/packages/<pkg>/publicKey` | 换公钥（轮换后旧包不再能发布，已发布的不受影响） |
| `POST /apps/<app>/packages/<pkg>/tokens`，body `{ "channels": [...] }` | 签发发布 token，只返回一次；服务端只存哈希 |
| `DELETE /apps/<app>/packages/<pkg>/tokens/<tokenId>` | 吊销 |

app token 不能签发或吊销 app token：一枚泄露的 app token 没法给自己续命。

CLI：`tinyui apps create` / `tinyui apps tokens create|revoke` / `tinyui packages create` / `tinyui tokens create` / `tinyui tokens revoke`；管理命令从 `$TINYUI_ADMIN_TOKEN` 读凭据，放实例的 `ADMIN_TOKEN` 或某个 app 的 app token 都行，服务端按 token 判定范围。没有控制台，这就是全部管理面。

### 6.4 宿主快照

宿主快照（§1.3、§4.1）是 `tinyui-host/<hostVersion>.txt` 的原始字节：该宿主版本提供的宿主组件、能力名与 tinyui 版本。宿主发 App 版本时由其 CI 上传，`publish` 发布前读取核对。格式是宿主侧生成工具与 CLI 之间的契约：

```
hostVersion 2
tinyui 0.3.0

components
  ta.Icon     name: string, size?: dp = 24, tint?: color = primary; layout
  ta.Rating   value: number; events onChange(value: number); commands reset(); layout

capabilities
  checkout.start
  coupon.apply
```

前两行固定；`components` 与 `capabilities` 两段按此顺序各出现一次，没有条目也要写段名（缺段会被读成"什么都不提供"，所以一律拒绝）；每项缩进两格、一行一项、按名排序，名字补空格对齐。`tinyui hosts upload` 先解析并核对首行的 `hostVersion` 与 `--host-version` 相符再上传——服务端只收第一份。**每行第一个词是名字**，CLI 核对只看名字；其后是给人看、也给宿主侧检查判断 schema 变没变的完整 schema，改哪一处都会让快照不同：

- 各段以 `; ` 分隔，依次为 props、`events …`、`commands …`、`children`、`layout`，没有的段省略
- prop 按名排序，`名字: 类型`；可选的名字后加 `?`，有默认值加 ` = 默认值`（`string` 的默认值写成 JSON 字符串，带引号与转义，免得换行或 `;` 弄乱一行一项），只在创建时读的加 ` (initial)`；枚举写 `enum(a|b)`，取值排序
- 事件与命令按名排序，参数写在括号里，参数按名排序，类型为 `string` / `number` / `boolean`

宿主组件只列带点的，内置组件随 `tinyui` 版本。

| 请求 | 凭据 | 语义 |
|---|---|---|
| `PUT /apps/<app>/hosts/<hostVersion>`，body 为快照字节 | `ADMIN_TOKEN` 或本 app 的 app token | 每个 (app, hostVersion) 只写一次：同字节重传返回已存在，不同字节 → 409——已随发版带出的宿主版本，其快照冻结 |
| `GET /apps/<app>/hosts/<hostVersion>` | 以上两种，或本 app 任一包的发布 token | 原字节；没上传过 → 404，`publish` 据此拒绝发布 |

CLI：`tinyui hosts upload <file> --host-version <n> [--app <app>]`（宿主 CI 用）。

## 7. 签名

- 算法 **ECDSA P-256 + SHA-256**，签名值 DER 编码后 base64 写入 `current.json` 的 `signature`（Java 与 iOS 原生都出 / 收 DER；WebCrypto 是 r‖s，服务端验签前转一次，约 20 行）。选它而非 ed25519：两端零依赖——Android `java.security.Signature("SHA256withECDSA")`，iOS `Security.framework` 的 `SecKeyVerifySignature`（C API，Kotlin/Native 可调；ed25519 在 iOS 只有 Swift-only 的 CryptoKit）
- 被签内容：`<version>/manifest.json` 文件的**原始字节**。`bundle` 写定该文件后对字节签名，此后任何一端都不重新序列化，客户端与服务端拿到的字节就是被签的字节，不存在规范化这一步（DSSE、JWS、Expo code signing、APT 的通行做法；多端各实现一份规范化 JSON 是 TUF 早期路线，任一处偏差即全部客户端拒收全部更新且只能发 App 修）。签名值放在指针 `current.json` 里；指针与 `rollout` 不在签名内，服务端因此能改灰度比例、切回滚而不碰私钥，篡改指针最多改变谁拿到一个本就合法的包。`name` 与 `hostVersion` 在签名内，所以一个包的产物不可能被发成另一个包或另一个 hostVersion
- 公钥格式统一为 **X9.63 未压缩点（`04‖X‖Y`，65 字节）的 base64**：iOS `SecKeyCreateWithData` 直接收，WebCrypto `importKey("raw")` 直接收，Android 侧加固定 26 字节的 P-256 SPKI DER 头再交 `X509EncodedKeySpec`——三处都不用解析 PEM。`tinyui.config.json` 的 `publicKey`、manifest 的 `publicKey`、`POST /apps/<app>/packages` 的 `publicKey`、`keys generate` 的公钥输出都是它
- 密钥按 (app, pkg) 一对：`tinyui keys generate` 产私钥 PEM（PKCS#8）与上述格式的公钥。私钥只在该包发布方的 CI（`tinyui bundle --signing-key`）；公钥两处登记——包的 `tinyui.config.json`（随 build 进 manifest，随内置包进 App，是客户端的信任锚），服务端包记录（§6.3）。服务端永远接触不到私钥：token 被盗发不出客户端认的包，服务端被攻破发出的包客户端不认。一个团队持有的私钥只能签自己的包
- **下载的 manifest 里的 `publicKey` 永远不是信任来源**，客户端只拿它与内置的比对以给出诊断信息
- `channel` 不在被签内容里：同一份签名产物从 staging 晋级到 production 不重签；staging 与 production 的隔离靠 token 的 channel 范围（§6）
- 公钥轮换 = 改 `tinyui.config.json` → 新内置包随 App 发版 + 服务端 `PUT /apps/<app>/packages/<pkg>/publicKey`；旧 App 版本仍认旧公钥，所以轮换期间要用两把私钥各发一份，或者接受旧版本不再收到更新
