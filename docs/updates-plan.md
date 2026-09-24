# 热下发实施计划

- 状态：已定（2026-09-19；同日按多包模型修订；2026-09-22 按签名改签原始字节、指针文件 `current.json`、启动 sha256 校验修订；2026-09-23 按 M6 拍板七点修订：插入 M5.1 改名 `hostVersion` 与 M5.2 `qjsc-kmp` 平台包，M6 改为 CI 发布）；进度在此更新，不回改 ADR-006 / updates.md
- 来源：[ADR-006](./adr-006-hot-updates.md)、[updates.md](./updates.md)；roadmap 第 7 条的展开
- 范围：四个仓（tinyui、tinyui-updates-server、quickjs-kmp、各 App 的 JS 工程与宿主）九个里程碑；每个里程碑独立验收、独立合入

## 依赖关系

```
tinyui:  M1 CLI 产包与签名 ──▶ M2 core Bundle ──▶ M3 updates 模块 ──┐
                    │                                              │
                    └──▶ tinyui-updates-server: M4 服务 MVP ──▶ M5 CLI publish / 管理
                                                                   │
         M5.1 改名 hostVersion ──▶ M5.2 qjsc-kmp 平台包 ──▶ 发 tinyui 0.3.0 ──▶ M6 TrendingAI 接入 ──▶ M7 第二个 App
```

M1 之后 M2 → M3 与 M4 并行；M5.1、M5.2 与 tinyui 0.3.0（npm 三包 + Maven `tinyui` / `tinyui-updates`，0.2.0 还不含 M1 起的任何热下发代码）都是 M6 的前置。

## M1 · CLI：产包与签名

仓：tinyui，`packages/cli` + `packages/core` + `sample/js`。约 400 行 / 8 文件，PR。

| 交付 | 要点 |
|---|---|
| `tinyui.config.json` | 工程根，`name`（`[a-z0-9-]+`，必填）/ `publicKey`（必填）/ `pages`（缺省 `src/pages`）；`build` 无配置文件即报错 |
| 模块名改 `<pkg>/<相对路径>` | esbuild 入口 → `-n <pkg>/home`，`import.meta.url = tinyui:<pkg>/home`；manifest 的 `pages` / `files` / `buildIds` / `hashes` 键随之改；sample 包名 `sample`，`sample/shared` 与 CLI 测试里的 `pages/…` 全部改 |
| `manifest.json` 加 `name` / `publicKey` / `version` / `createdAt` / `engine` / `protocol` / `hashes` | `name` / `publicKey` 抄配置；`engine` 从第一个 `.bin` 的文件头读（`QJKB` 起偏移 12 的 40 字节 hex）；`protocol` 读 `tinyui-core/protocol` 子路径导出，core 包加这个入口 |
| `tinyui keys generate` | Node `crypto.generateKeyPairSync("ec", { namedCurve: "P-256" })`；输出私钥 PKCS#8 PEM 与 X9.63 裸点 base64 公钥（updates.md §7） |
| `tinyui bundle --runtime-version --signing-key [--rollout] [--out]` | manifest 加 `runtimeVersion` 后写定为 `<pkg>/<rv>/<version>/manifest.json`，对该文件字节 `crypto.sign("sha256", …, { dsaEncoding: "der" })`，签名与 `version` / `rollout` 写 `<pkg>/<rv>/current.json`；`.bin` 复制进 `<version>/`；签名私钥的公钥必须等于 manifest 的 `publicKey`，否则拒绝 |
| 测试 | 夹具目录 → 字段齐全、模块名带包名、`hashes` 对得上、`current.json` 的签名可用公钥对 `manifest.json` 字节验回、改 `current.json` 的 `rollout` 不影响验签、私钥与 `publicKey` 不配对被拒、缺配置文件被拒 |

验收：`sample/js` 跑 `tinyui build && tinyui bundle`，双端显示 `tinyui:sample/home`，产出目录用 `openssl dgst -sha256 -verify` 对 `<version>/manifest.json` 验 `current.json` 里的签名。

## M2 · core 库 `Bundle`

仓：tinyui，`compose/`。约 150 行，小 PR 或直接提交。

- `BuildManifest` 加新字段解析，`name` / `publicKey` 缺失即解析失败，`rollout` 缺省 100
- 新增 `BundleFiles` / `Bundle` / `LoadedPage`（updates.md §3），`Bundle.name`
- `sample/shared` 改用 `Bundle.load(files).page(name)`，资源目录改为 `files/tinyui/<pkg>/`；TrendingAI `TinyUIHost.page()` 同样改法留到 M6
- commonTest：内存 `BundleFiles` 夹具；运行时只读一次；map 缺失时 `SourceMaps.EMPTY`

验收：sample 双端行为不变。

## M3 · `updates/` 模块

仓：tinyui，新 Gradle 模块，artifact `app.tinyui:tinyui-updates`。约 600 行 / 15 文件，PR。

| 交付 | 要点 |
|---|---|
| 模块骨架 | 依赖 `compose` + okio；复用 `build-logic` convention；publish.yml 加 artifact，与 `tinyui` 同版本同 tag |
| `Updates` | updates.md §4.1 构造参数（`packages: List<Bundle>`，重名 / engine 不符构造抛错）；§4.3 状态机按包各跑（指针 → 掷骰 → manifest 原始字节验签 → 校验 → 下载），`check()` 并行；`<dir>/<pkg>/state.json` 临时文件加 rename 写入；staging → installed 改名；§4.4 启动选择：逐文件重算 sha256（读到的字节直接喂 `Bundle`）、删 `createdAt` 旧于内置或 rv 不匹配的 installed、清理不属于任何内置包的目录 |
| 验签 expect/actual | Android：裸点加 26 字节 SPKI 头 → `KeyFactory("EC")` → `Signature("SHA256withECDSA")`；iOS：`SecKeyCreateWithData`（`kSecAttrKeyTypeECSECPrimeRandom`）→ `SecKeyVerifySignature(kSecKeyAlgorithmECDSASignatureMessageX962SHA256)`，cinterop 用 `platform.Security` |
| `UpdatesPage` | `name` 取第一个 `/` 前为包 → `current(pkg).page(name)` → `TinyUIPage`；E2 / E6 且来自该包 installed → 拉黑 + 换该包 embedded 重挂（§4.5） |
| 测试 | 假 `fetch` 按协议喂：指针解析失败、签名错（含公钥轮换的诊断信息）、`name` 不匹配、manifest `version` 与指针不符、engine 不匹配、旧于内置、rollout 不中、sha256 错、全通过各一条；两个包一坏一好互不影响；启动选择四种情况 + installed 文件被改一个字节即回内置并 `Failed(integrity)` + 清理被去掉的包与 rv 不匹配的 installed；Android host 与 iOS simulator 各跑一遍验签 |
| sample | 第二个包 `sample-extra`（一页即可）验多包；一个开关把 base URL 指向本机 `python3 -m http.server` 起的 `dist/ota/`，手动验"下次启动生效"与回退 |

验收：sample 在 Android 与 iOS 模拟器上从本机静态目录装上新包、重启生效；故意给一个包发坏包（改一个 `.bin` 字节、或页面顶层 `throw`）能回退并收到带 `pkg` 的 `RolledBack`，另一个包不受影响。

## M4 · `tinyui-updates-server` MVP

仓：新建 `tiny-ui/tinyui-updates-server`，MIT。约 800 行。

| 交付 | 要点 |
|---|---|
| 建仓 | pnpm + Hono + wrangler；vitest 用 `@cloudflare/vitest-pool-workers`；Cloudflare Git 集成 push 即 deploy；README 写私有化步骤：fork → 建 R2 桶 → 设 `ADMIN_TOKEN` → `wrangler deploy` → `tinyui apps create` → `tinyui packages create` |
| 投递端点 | `GET /:app/:channel/:pkg/:rv/current.json`（`no-store`）、`GET /:app/:channel/:pkg/:rv/:version/*`（键为 (app, pkg, rv, version, path)，含 `manifest.json`，`channel` 不参与寻址，`immutable`，流式） |
| 发布端点 | `PUT /:app/:pkg/:rv/:version/*`（幂等，冲突 409）；`PUT /:app/:channel/:pkg/:rv/current.json`（token 归属与 channel 范围 → 取 R2 里 `<version>/manifest.json` 的原始字节，WebCrypto 验签，DER → r‖s 转换 → `name` == `:pkg`、`runtimeVersion` == `:rv`、`version` == 指针 `version`、`publicKey` == 登记值 → `<version>/` 齐全且 sha256 一致 → 记 release（含 `signature`）、切指针） |
| 管理端点 | `/apps`（含可改 `org` 字段）、`/apps/:app/packages`、`/apps/:app/packages/:pkg/publicKey`、`/apps/:app/packages/:pkg/tokens`（带 `channels`；`ADMIN_TOKEN` secret；token 只存 sha256，签发只返回一次） |
| release 端点 | `GET /:app/:pkg/:rv/releases`、`POST /:app/:channel/:pkg/:rv/pointer`（回滚与跨 channel 晋级同一操作） |
| `Storage` 接口 | R2 适配器（元数据与内容都在一个桶，元数据按版本 / channel 独立成对象，上传用条件写保证不可变）+ 内存适配器 |
| 一致性测试 | 用 `tinyui-cli` 产的夹具包按 updates.md §6 顺序打：先指针后内容被拒、坏签名被拒、`name` 与路径不符被拒、指针 `version` 与 manifest 不符被拒、token 跨包 / 跨 channel 被拒、staging 发布后 production 指针晋级不重传、回滚后 `current.json` 带回该 version 的签名、改 rollout 后签名仍有效 |
| 部署 | `updates.tinyui.app`（DNS 在 Cloudflare）、R2 桶、`ADMIN_TOKEN` |

验收：本机 `wrangler dev` 起服务，M3 的 sample 把 base URL 换成它，整条链跑通。

## M5 · CLI 发布与管理

仓：tinyui，`packages/cli`。约 200 行。

`tinyui publish` / `apps create` / `packages create` / `tokens create|revoke` / `releases list|rollback|rollout|promote`，全是对 M4 端点的薄封装；`--url` 缺省 `https://updates.tinyui.app`。凭据只从 `$TINYUI_TOKEN` / `$TINYUI_ADMIN_TOKEN` 读，不给 `--token`；`--app` 可用 `$TINYUI_APP`，`--channel` 必须显式。测试对着本仓 `node:http` 起的薄 fake 跑（只回应协议的形状，不复刻验签与 sha256 校验——那些归服务端仓的测试），端到端另跑一次真服务端。

## M5.1 · 改名 `runtimeVersion` → `hostVersion`

仓：tinyui + tinyui-updates-server，小 PR 各一。

- 字段、CLI 参数（`--host-version`）、`Updates` 构造参数、`BuildManifest`、服务端 manifest 校验、文档全部改名；文档补一句"对应 Expo 的 `runtimeVersion`"。改名原因：tinyui 里"runtime"已指运行时模块（`tinyui-core` / `tinyui-native`、manifest 的 `runtime` 字段）与 JS 引擎，而这个值恰恰不是它们的版本
- 值限定为正整数字符串：`"1.2.0"` 这类 App 版本号在 CLI、`Updates` 构造、服务端三处都被拒
- 文档里的"契约快照"改称 `tinyui-host/<hostVersion>.txt`
- 顺序：服务端先部署，再合 CLI；0.2.0 与线上都还没有这个名字，此时改名只是机械替换

## M5.2 · `qjsc-kmp` npm 平台包

仓：quickjs-kmp + tinyui（`packages/cli`）。原是 M7 前置，提前：它不只挡 CI，还挡"`pnpm install` 之后就能 build"，PingPong 与托管实例的外部用户都需要。

- quickjs-kmp CI 矩阵编宿主工具（macOS arm64 / x64、Linux x64；Linux arm64 做时再定），按 esbuild 的模式每个平台发一个 npm 包，版本跟 quickjs-kmp 走
- `tinyui-cli` 的 `optionalDependencies` 钉住与 tinyui 引擎一致的那一版；CLI 查找顺序：`--qjsc` → `TINYUI_QJSC` → 平台包 → PATH
- engine 对齐由此退化为"JS 工程的 `tinyui-cli` 版本 = App 的 `tinyui` 版本"

## M6 · TrendingAI 接入

拍板（2026-09-23）：

| # | 点 | 结论 |
|---|---|---|
| 1 | 发布在哪跑 | CI：合进 main 自动构建、签名、发 `staging`；不走本地发布 |
| 2 | 兼容键口径 | `hostVersion` 由宿主仓持有、递增整数；宿主给页面的东西（组件、能力、tinyui 版本）变了就加 1；宿主构建按版本存快照拦漏加（ADR-006 §2.2、updates.md §4.1） |
| 3 | F-Droid | 不关热下发 |
| 4 | channel | `staging` + `production`；正式包里藏 channel 开关，用商店版 App 验 staging；CI token 只能发 `staging`，晋级 / 回滚 / 改灰度走需人工批准的 workflow |
| 5 | 签名私钥 | GitHub secret + `HarlonWang/secrets` 备份一份，本机生成后即删 |
| 6 | `installId` | 复用 TrendingAI 的 `getOrCreateInstallId()`，`Updates` 事件接进埋点 |
| 7 | 命名 | 包名 `trendingai`（页面 `trendingai/subscription`），线上 app id `trendingai`；宿主层面标识页面（深链、埋点页面名、日志）用 `tinyui:trendingai/subscription`，`navigation.push` 仍用不带前缀的 |
| 8 | JS 侧填错目标宿主版本 | 发布前核对（updates.md §1.3）：`tinyui build` 把每页用到的能力名与宿主组件名写进 manifest 的 `requires`；`publish` 上传前从热下发服务读目标版本的宿主快照比对，不通过即拒绝发布（`bundle` 保持离线）。快照由宿主 CI 在发 App 版本时上传，每版本只写一次。铁律：`host.call` 的名字必须是字面量、宿主组件必须能静态解析（build-chain.md §5.1）。设备侧核对暂不做 |
| 9 | 服务端凭据分层 | 在实例 `ADMIN_TOKEN` 与包的发布 token 之间补 app token：一个 app 的完整管理权（注册包、公钥、签发发布 token、上传宿主快照），暂不细分权限范围；宿主 CI 用它上传快照；本 app 的发布 token 可读快照。权限与 URL 挂在 app（一个宿主 App）上，不挂在租户（`org`，可改可转让）上（updates.md §6） |
| 10 | 宿主契约怎么枚举 | 能力名的真值在宿主 Kotlin；能力像组件一样 App 级注册（`CapabilityRegistry`），`HostCapability.call(argsJson, page)` 经 `PageLocal` 拿某一屏的对象；组件注册表、能力注册表、`PageSink` 合成 App 级 `TinyUIHost` 交给 `TinyUIPage` / `UpdatesPage`（native-api.md §7）。快照由库的 `HostSnapshot.render` 生成，检查是宿主自己的 host 侧单元测试，`-Ptinyui.updateHostSnapshot` 写入；已发版本的冻结只靠服务端 409（updates.md §4.1） |
| 11 | ~~指针指向不新于内置的版本~~ | ~~回到内置（`Reverted`）；内置包取 `production` 当时那版~~ 2026-09-24 被第 12、15 点取代 |
| 12 | 内置包的口径 | 只要求兼容：是给当前 `hostVersion` 发布过的某个版本，与宿主引擎、协议、tinyui 一致，用到的宿主东西宿主都有；是否等于当前 `production` 不作要求（新旧尽力而为）。宿主仓用 `tinyui pull` 刷新，信任锚是已提交的内置包（包名、公钥），换钥须 `--accept-key`；灰度中或不更新时保持现状（updates.md §1.4） |
| 13 | 宿主快照何时上传 | `hostVersion` 加 1 合入宿主 main 时由 main 的 CI 上传并冻结，发 App 时的上传只作幂等复核；合入后契约不能再改，只能再加 1（updates.md §4.1、§6.4） |
| 14 | 内置包不兼容 | 库不再抛错：发 `EmbeddedIncompatible`，页面走 `error` 槽，`check()` 装上兼容版本后恢复。发版前由宿主测试用 `PackageCheck` 拦（updates.md §4.1、§4.4） |
| 15 | 回滚 | 指针只往前走：服务端拒绝把指针指向不更新的版本；回滚 = 用旧内容发一个新 version 再晋级，所有设备都收得到。删 `releases rollback`（updates.md §6.2） |

交付：

- **tinyui**：`tinyui build` 静态找出每页用到的能力与宿主组件写进 manifest 的 `requires`，违反 build-chain.md §5.1 即失败；`tinyui publish` 上传前读目标宿主版本的快照核对（updates.md §1.3，快照格式 §6.4）；宿主契约快照——`TinyUIHost` 与 `HostSnapshot.render`（拍板第 10 点），sample 带一份 `HostSnapshotTest` 作宿主的写法样板。`tinyui:` 前缀约定写进 app-model.md
- **trendingai-tinyui**：先恢复可构建——依赖从 `link:` 换成 npm 上与 TrendingAI 同版本的 tinyui 三包（0.4.0），加 `tinyui.config.json`（`name: "trendingai"`，公钥），模块名 `pages/subscription` → `trendingai/subscription`；`keys generate`，私钥写进 GitHub secret 与 `HarlonWang/secrets` 的 `tinyui-updates/signing/trendingai/trendingai.pem`；CI：合 main → build → `bundle --host-version <值>` → `publish --channel staging`，`hostVersion` 取 workflow 里的一个变量（抄宿主仓当前值；抄错时 `publish` 对照该版本的服务端快照核对：快照不存在、或包用到的东西快照里没有即拒绝；抄成一个仍然兼容的旧值不会被拒，包只是发给了那个旧宿主版本，见拍板第 8 点）；`production` environment 设人工批准，手动 workflow 跑 `releases promote / rollout`；回滚 = 用旧内容发一个新 version 再 promote（第 15 点）
- **服务端代码**：app token 的签发与吊销、各管理端点改为认 app token（updates.md §6.3）；宿主快照的上传与读取端点（§6.4，每个 (app, hostVersion) 只写一次）；CLI 配套 `apps tokens`、`hosts upload`
- **服务端（线上，不可逆，执行前列命令确认）**：`apps create trendingai --name TrendingAI`（`org` 留空）；签一枚 app token 给 TrendingAI 宿主 CI 上传快照；`packages create trendingai --app trendingai`（登记公钥）；两枚发布 token，CI 用的限 `staging`，production environment 用的限 `production`
- **TrendingAI**：依赖升到带 `TinyUIHost` 的 tinyui（0.4.0）并加 `tinyui-updates`；宿主的 TinyUI 入口改用 `Updates(listOf(embedded), hostVersion = "1", dir, installId = getOrCreateInstallId(), fetch = ktor)`，base URL `https://updates.tinyui.app/trendingai/<channel>`；设置页隐藏的 channel 开关（持久化，切回即恢复 `production`）；订阅页改用 `UpdatesPage`，`PAGE = "trendingai/subscription"`；App 启动即 `check()`（先于任何 TinyUI 页挂载）；`onEvent` 接埋点；五个能力搬进 App 级 `CapabilityRegistry`（`ui.snackbar` 经 `PageLocal` 取当前屏的 snackbar），`shared/tinyui-host/1.txt` 与快照测试进 CI，发版时 CI 把快照上传到热下发服务；内置包改为发 App 时从服务端拉当时 `production` 的那一版进 `composeResources/files/tinyui/trendingai/`（`pnpm sync` 只再生成 `HostSchemas.kt`），脚本还是 CLI 子命令做时定

验收：合 main → CI 发 `staging` → 商店版 App 切到 staging 看到新文案 → 批准 workflow 晋级 `production` → 切回、重启看到；往 staging 发坏包 → 回退到内置并在埋点里看到 `RolledBack`；回滚：JS 仓 revert → 发新版本 → 晋级 → 重启回到旧内容（第 15 点）；宿主加一个能力而不加 `hostVersion` → 宿主 CI 失败。

## M7 · 第二个 App

流程同 M6，按业务线分包，每包一个 JS 工程、一对密钥、一个 token；宿主一个 `Updates` 装全部内置包。平台包已在 M5.2 做完。

## 待办

M6 收尾（2026-09-24）评审中发现、与 M6 正交的问题，各自独立，按需处理：

| 问题 | 在哪 | 现状与后果 | 方向 |
|---|---|---|---|
| 缺页会崩 | tinyui `compose/.../Bundle.kt` 的 `page()` | 页面名不在包里时 `require` 抛出，在 `UpdatesPage` 的 `produceState` 里没人接，App 崩。宿主新加一个入口指向新页面、内置包里还没有它时就会发生 | 改为走 `error` 槽 |
| 公钥无法轮换 | tinyui-updates-server 包记录、updates.md §7 | 服务端每个包只登记一把公钥；换钥后仍信旧钥的已发 App 永久收不到更新 | 服务端支持一个包登记多把公钥，或文档写明"轮换须同时加 `hostVersion`、接受旧版本停更" |
| `PROTOCOL` 形同虚设 | tinyui `PageHost.PROTOCOL` | 从 M1 起一直是 1，改 mount 参数、加 `host.call` 时都没加；"运行时 ABI"没有定义 | 定义 ABI 范围与加 1 规则；以后若要放宽"tinyui 版本相等"必须先有它 |
| TrendingAI 没有 PR 级 CI | TrendingAI `.github/workflows` | 宿主测试只在 push 到 main（快照 workflow）与发版时跑，契约改了忘加 `HOST_VERSION` 要到合入后才红 | 加 PR 检查：`HostSnapshotTest` 与常规测试 |
| 版本名后缀长度不固定 | tinyui `packages/cli/src/build.ts` | `git rev-parse --short` 的长度随对象数与克隆深度变，同一 commit 在 CI 与本机可能得到不同版本名 | 改 `--short=12` |
| 看不到设备在跑哪一版 | TrendingAI `TinyUIUpdates.report()` | `Running` 事件没上报，发现不了"一直停在内置包"或指针 404 | 抽样上报 `Running` |
| 快照盲区 | updates.md §4.1、§6.4 | 能力的参数与返回形状、页面 props、store key、`PageLocal` 都不在快照里，这些变了该不该加 `hostVersion` 全靠人判断 | 先记录；有需要时给能力加修订号或形状描述 |
| 服务端移动指针不核对快照 | tinyui-updates-server | 兼容核对只在 CLI 的 `publish` 里，绕过 CLI 直接调端点就没有核对 | `PUT current.json` / `POST pointer` 时按冻结的快照复核 `requires` |

## 进度

| 里程碑 | 状态 |
|---|---|
| M1 CLI 产包与签名（含配置文件与模块名改名） | 已完成（PR #13，2026-09-22）：`tinyui.config.json`、模块名 `<pkg>/…`、manifest 扩展、`keys generate`、`bundle` 签原始字节写 `current.json`；sample 包名 `sample` |
| M2 core `Bundle` | 已完成（2026-09-22）：`BuildManifest` 解析 §1.1 全部字段（缺 `name` / `publicKey` 即失败），`BundleFiles` / `Bundle` / `LoadedPage`，`SourceMaps.isEmpty`；sample 资源目录改 `files/tinyui/sample/`，Android host 冒烟测试改走 `Bundle` |
| M3 `updates/` 模块 | 已完成（2026-09-22）：`Updates` 状态机与启动选择（含 sha256 重算、原子 state.json）、两端 ECDSA 验签（Android `java.security`、iOS `Security.framework`）、`UpdatesPage` E2 / E6 回退；sample 加第二个包 `sample-extra`，Android 模拟器从本机 `http.server` 装包、重启生效、坏包回退并 `RolledBack` 全部验过 |
| M4 服务 MVP | 已完成（tiny-ui/tinyui-updates-server PR #1，2026-09-22）：Hono + 单 R2 桶，投递 / 发布 / release / 管理四组端点，Workers 运行时一致性测试 12 条；已部署 `updates.tinyui.app`（R2 桶 `tinyui-updates`，`ADMIN_TOKEN` 存 HarlonWang/secrets 的 `tinyui-updates/admin-token.txt`），Cloudflare Workers Builds 已连 tiny-ui/tinyui-updates-server 的 main，push 即部署（`wrangler deploy --config wrangler.tinyui.toml`） |
| M5 CLI 发布与管理 | 已完成（2026-09-22）：`publish`（发现 bundle 目录、并发上传、指针最后）、`apps` / `packages`（含 `rotate-key`）/ `tokens` / `releases` 四组命令；凭据只走环境变量，`--app` / `--channel` / `--pkg` 按名字规则校验；CLI 侧契约测试 15 条，另对本地 `wrangler dev` 起的真服务端跑通发布 / 幂等重发 / 晋级 / 改灰度 / 回滚 / 跨 channel 被拒 / 吊销即时生效 |
| M5.1 改名 `hostVersion` | 已完成（2026-09-23，tinyui PR #17、tinyui-updates-server PR #2，服务端已部署）：manifest 字段、Kotlin API、CLI `--host-version`、服务端路由与校验、文档全部改名；CLI、`Updates` 构造、服务端路径与 manifest 五处限定正整数；共用签名夹具换钥重签，Android host 与 iOS 模拟器两端验签通过 |
| M5.2 `qjsc-kmp` 平台包 | 已完成（2026-09-23，quickjs-kmp PR #13 与 0.1.2、tinyui PR #18）：入口包 `qjsc-kmp` + `@qjsc-kmp/{darwin-arm64,darwin-x64,linux-x64,linux-arm64}`，Linux 为 musl 全静态；首版本地发布，之后随 quickjs-kmp 的 tag 经 OIDC 发布；`tinyui-cli` 依赖它并由测试钉住与 `quickjsKmp` 同版本，CI 不再现编 `qjsc-kmp` |
| tinyui 0.3.0 | 已发（2026-09-23）：npm `tinyui-core` / `tinyui-native` / `tinyui-cli`，Maven `app.tinyui:tinyui` 与首发的 `app.tinyui:tinyui-updates`；`tinyui-cli` 带 `qjsc-kmp@0.1.2` |
| M6 TrendingAI 接入 | 进行中：拍板八点（2026-09-23）；`tinyui build` 写 `requires` 已完成（PR #19，TrendingAI 订阅页实测 5 个能力、2 个宿主组件）；服务端 app token 与快照端点已上线（tinyui-updates-server PR #3），CLI 的 `apps tokens` / `hosts upload` 与 `publish` 核对已完成（PR #20，本地 `wrangler dev` 端到端验过）；宿主契约收进 App 级 `TinyUIHost`（能力 `CapabilityRegistry` + `PageLocal`）与 `HostSnapshot.render` 已完成（拍板第 10 点，PR #21，sample 带 `HostSnapshotTest` 样板）；tinyui 0.4.0 已发（2026-09-23）；线上已开通 app / 包 `trendingai`、宿主快照 1、app token 与 staging / production 发布 token；trendingai-tinyui PR #1 已合（CI 发 staging、`production` environment 人工批准），TrendingAI PR #143 已合（2026-09-23）；Android 模拟器验过切 staging 装包、晋级、切回、production 回滚与 `tinyui_update_checked` 埋点（坏包回退不在线上做，M3 已在 sample 上验过）；`tinyui pull` 与指针回到内置（拍板第 11 点）随 tinyui 0.4.1 发出（PR #22）；TrendingAI 升 0.4.1、`HOST_VERSION` 2（快照 2 已上传），production 为宿主版本 2 的首个包，内置包已由 `pnpm pull` 取它（TrendingAI PR #144 已合，模拟器验过启动即 `UpToDate`）；`tinyui:` 前缀约定已写进 app-model.md §2，ADR-006 §4.2 已按拍板第 7、11 点改；2026-09-24 按拍板第 12–15 点改为：内置包只求兼容、由宿主冒烟脚本 pull 刷新，快照合入 main 即上传，内置包不兼容降级不崩溃，指针只往前走（tinyui PR #23 发 0.5.0，服务端 PR #4 已部署，TrendingAI PR #145 升 0.5.0 / HOST_VERSION 3，trendingai-tinyui PR #3）；按新流程走通：合入即上传快照 3 → staging → 晋级 → release-smoke.sh 自动拉取并提交内置包、发版检查通过、release 包冒烟 PASS 且启动即 UpToDate |
| M7 第二个 App | 待开 |
