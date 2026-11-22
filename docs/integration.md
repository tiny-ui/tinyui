# 接入指南

给要把 TinyUI 接进自己 App 的人看：按顺序做什么。每一步只写做法，为什么这样定见各节末尾链到的设计文档。

读完能做到：宿主 App 里跑起 TinyUI 页面，页面在独立的工程里写、合入即发布到测试通道、晋级后到线上，宿主仓不为页面的更新做任何提交。

## 0. 先认三样东西

| 东西 | 是什么 | 在哪 |
|---|---|---|
| **宿主** | 你的 App。提供页面要用的东西（网络通道、登录态、原生页面……），内置一份页面作地板 | App 的仓库 |
| **页面包** | 一组页面，一次构建、整包发布、整包回退。一个 App 可以有几个包，按业务模块分 | 每个包一个 JS 工程，可以与宿主同仓，也可以各自成仓 |
| **热下发服务** | 存放发布出来的包，App 启动时来问有没有新版本 | 托管实例，或把 [tinyui-updates-server](https://github.com/tiny-ui/tinyui-updates-server) 部署到自己的 Cloudflare 账号 |

把三者连起来的只有一个数：**`hostVersion`**，宿主对页面承诺的版本，正整数。宿主给页面的东西变了（加、删、改了能力 / 组件 / 网络通道 / 原生路由）就加 1；页面包声明自己是按哪个 `hostVersion` 写的，只会发给这个版本的宿主。它不是 App 版本号，也不是 tinyui 版本号。

下文用到的例子：app 叫 `shop`，包叫 `login`，热下发服务在 `https://updates.example.com`。

## 1. 一次性准备

在热下发服务上登记 app 和包。命令都是 `tinyui-cli` 的，凭据从环境变量读。

```sh
# 实例管理员做一次：建 app，给它签一个 app token（管理这个 app 下的一切，只显示一次）
TINYUI_ADMIN_TOKEN=<实例的 admin token> tinyui apps create shop --name "Shop" --url https://updates.example.com
TINYUI_ADMIN_TOKEN=<实例的 admin token> tinyui apps tokens create shop --url https://updates.example.com

# 每个包做一次（在包的 JS 工程里跑，app 与服务地址取自 tinyui.config.json，见 §3）
tinyui keys generate --out keys/login-signing-key.pem          # 私钥写进文件，公钥打印出来
TINYUI_ADMIN_TOKEN=<app token> tinyui packages create --public-key <公钥>
TINYUI_ADMIN_TOKEN=<app token> tinyui tokens create --channels staging       # 发布 token，只显示一次
TINYUI_ADMIN_TOKEN=<app token> tinyui tokens create --channels production
```

得到的东西各放哪：

| 东西 | 放哪 | 丢了或泄露 |
|---|---|---|
| 签名私钥 | 只放页面包的 CI（secret），不入库 | 最贵：换钥要宿主加 `hostVersion` 并发新 App（[updates.md](./updates.md) §7） |
| 公钥 | 页面包的 `tinyui.config.json` 与宿主的 `tinyui { }` 各写一份 | 公开的，不怕泄露 |
| staging / production 发布 token | 页面包的 CI（secret）；production 的也可以只留在负责晋级的人手里 | 用 app token 吊销重签 |
| app token | 宿主的 CI（secret），上传宿主快照用 | 用 admin token 吊销重签 |

## 2. 宿主

### 2.1 依赖与插件

```kotlin
// settings.gradle.kts：插件与库都在 Maven Central
pluginManagement { repositories { mavenCentral(); gradlePluginPortal(); google() } }

// 读内置包的那个模块（KMP + Compose Multiplatform）
plugins {
    id("app.tinyui") version "<tinyui 版本>"
}
kotlin.sourceSets.commonMain.dependencies {
    implementation("app.tinyui:tinyui:<tinyui 版本>")
    implementation("app.tinyui:tinyui-updates:<tinyui 版本>")   // 热下发
}
```

预发布版本可能只发在 Central 的 snapshot 仓库，见 §6。

### 2.2 声明内置包

```kotlin
tinyui {
    updatesUrl = "https://updates.example.com"     // 用托管实例时不写
    app = "shop"
    hostVersion = providers.gradleProperty("tinyui.hostVersion")   // 全仓只写一处，如 gradle.properties 里 tinyui.hostVersion=1
    channel = providers.gradleProperty("appChannel").map { "production" }.orElse("staging")
    generatedPackage = "com.example.shop.tinyui"
    packages {
        register("login") { publicKey = "<login 包的公钥>" }
    }
}
```

- 内置包不入库。插件在构建时把每个包解析进 build 目录，接进 Compose 资源 `files/tinyui/<包名>/`
- `channel` 要与这次构建的 App **运行时检查的通道一致**：测试包取 `staging`，正式包取 `production`。上面的写法是「传了 `-PappChannel` 即正式包」，换成你自己区分构建的方式
- 插件生成 `TinyUIBuild.HOST_VERSION` 与 `TinyUIBuild.PACKAGES`，Kotlin 代码读它们，不另写常量

规则（回落、灰度、离线各是什么行为）见 [updates.md](./updates.md) §1.4。

### 2.3 运行时接线

```kotlin
// App 级，进程里只建一次
val host = TinyUIHost(
    components = ComponentRegistry().registerBuiltins(),
    sink = /* 页面错误与日志去哪 */,
    channels = mapOf("app" to /* 带登录态的 HttpChannel */),
    /* locale、session、navigator、analytics……按需，见 native-api.md */
)

suspend fun embedded(pkg: String): Bundle = Bundle.load { path ->
    try { Res.readBytes("files/tinyui/$pkg/$path") } catch (e: MissingResourceException) { null }
}

// 这次构建的 App 检查哪个通道。插件不生成它：由你区分测试包与正式包的方式决定，
// 且必须与 §2.2 的 `tinyui { channel = … }` 得出同一个值，否则内置的是一个通道的版本、运行时查的是另一个
val channel = if (isInternalBuild) "staging" else "production"

val updates = Updates(
    packages = TinyUIBuild.PACKAGES.map { embedded(it) },
    hostVersion = TinyUIBuild.HOST_VERSION,
    dir = /* Android filesDir/tinyui，iOS Application Support/tinyui */,
    installId = /* 稳定的安装标识，灰度掷骰用 */,
    fetch = ktorFetch(httpClient) { "https://updates.example.com/shop/$channel" },
)
UpdatesScheduler(updates, processScope).start()   // App 启动即调，先于任何 TinyUI 页面

// 渲染一页
UpdatesPage(updates, name = "login/signin", host = host)
```

`start()` 必须先于页面挂载：回滚是「发一个新版本」，要靠检查才送得到。新版本下次启动生效；下发的页面出错时当场换回内置包。各接口见 [native-api.md](./native-api.md)，`Updates` 见 [updates.md](./updates.md) §4。

### 2.4 两个宿主测试

写成能在 JVM 上跑的宿主测试，照 `sample/shared` 里的 `HostSnapshotTest` 与 `EmbeddedPackageTest`：

| 测试 | 拦什么 |
|---|---|
| 宿主快照：`HostSnapshot.problems(读 tinyui-host/<n>.txt, host, n)` 为空 | 改了宿主给页面的东西却没加 `hostVersion` |
| 内置包：对每个内置包 `PackageCheck.problems(manifest, host)` 为空 | 内置包用到了宿主没有的东西（页面打开是白屏，编译不报错） |

```kotlin
tasks.withType<Test>().configureEach {
    inputs.files(layout.projectDirectory.dir("tinyui-host"))
    providers.gradleProperty("tinyui.updateHostSnapshot").orNull?.let { systemProperty("tinyui.updateHostSnapshot", "true") }
    val embedded = tinyui.embeddedDir          // 这次构建的内置包，每包一个子目录
    inputs.dir(embedded)
    doFirst { systemProperty("tinyui.embedded", embedded.get().asFile.path) }
}
```

第一份快照：把 `tinyui.hostVersion` 定为 `1`，带 `-Ptinyui.updateHostSnapshot` 跑一次快照测试，生成 `tinyui-host/1.txt`，提交。

### 2.5 CI：上传宿主快照

主干上 `tinyui-host/` 有改动时，先跑快照测试，再上传。上传后这个版本的快照即冻结：

```sh
host=$(sed -n 's/^tinyui\.hostVersion=\([0-9]*\)$/\1/p' gradle.properties)
TINYUI_ADMIN_TOKEN=<app token> npx --yes -p "tinyui-cli@<tinyui 版本>" \
    tinyui hosts upload "tinyui-host/$host.txt" --host-version "$host" --app shop --url https://updates.example.com
```

同一份重传无害；报 409 说明改了已上传版本的契约，要再加 1。页面包要在新 `hostVersion` 下发布，得等这一步跑完。

## 3. 页面包

### 3.1 工程

```sh
pnpm add tinyui-core tinyui-native
pnpm add -D tinyui-cli typescript
```

三个包同一个版本号，且不得高于宿主快照第二行的 tinyui 版本。`tinyui.config.json`：

```json
{
    "name": "login",
    "publicKey": "<公钥>",
    "defaultLocale": "en",
    "contracts": "src/generated/routes.json",
    "app": "shop",
    "hostVersion": "1",
    "updatesUrl": "https://updates.example.com"
}
```

`app`、`hostVersion`、`updatesUrl` 说的是这个包发布到哪，命令都从这里取，脚本和 CI 里不再写（[build-chain.md](./build-chain.md) §2.1）。页面放 `src/pages/`，一个文件一页，页面名是 `<包名>/<相对路径>`，文案放 `i18n/<语言>.json`。

宿主有原生路由时，把宿主的 `tinyui-host/<hostVersion>.txt` 放到本工程同名位置（`tinyui hosts download`，或从宿主仓拷），类型从它生成。

`package.json` 的脚本：

```json
{
    "i18n": "tinyui i18n --ts src/generated/i18n.ts",
    "routes": "tinyui routes --ts src/generated/routes.ts --schema src/generated/routes.json",
    "check": "tinyui i18n --check --ts src/generated/i18n.ts && tinyui routes --check --ts src/generated/routes.ts --schema src/generated/routes.json && tsc --noEmit",
    "build": "tinyui build"
}
```

`src/generated/` 提交进仓。写页面见 [runtime-api.md](./runtime-api.md)、[components.md](./components.md)、[native-api.md](./native-api.md)。

### 3.2 CI：检查与发布

两个任务。MR / 分支上只跑检查；合入主干后发到 `staging`：

```sh
# 检查
pnpm install --frozen-lockfile
pnpm check
pnpm build

# 发布到 staging（主干，且只在页面相关文件有改动时）
printf '%s\n' "$SIGNING_KEY" > signing-key.pem
pnpm exec tinyui bundle --signing-key signing-key.pem
rm -f signing-key.pem
TINYUI_TOKEN="$STAGING_TOKEN" pnpm exec tinyui publish --channel staging
```

同一时刻只跑一个发布（指针只往前走，并发的后到者会被拒绝）。`publish` 会对照宿主快照核对：包用到的能力、组件、网络通道宿主都有，三包版本不高于宿主的下限；不满足即拒绝，并指出是哪一页缺了什么。

## 4. 日常

**改页面。** 页面包合入主干 → CI 发到 `staging` → 测试包下次构建自动内置这一版，已装的测试包下次启动检查到、再下次启动生效。宿主仓什么都不用做。

**上线。** 验过之后晋级：

```sh
TINYUI_TOKEN=<production 发布 token> tinyui releases list
TINYUI_TOKEN=<production 发布 token> tinyui releases promote <version> --to production
TINYUI_TOKEN=<production 发布 token> tinyui releases rollout 20 --channel production    # 想灰度时
```

**本地联调。** 在宿主的 `local.properties` 加一行，之后照常构建、安装，页面从源码现编（要 Node，页面工程已装依赖）：

```properties
tinyui.login.dir=../shop-pages-login
```

联调完删掉这一行。留着它去打正式包会失败。

**宿主给页面加东西（或删、改）。**

1. 宿主：改代码，`tinyui.hostVersion` 加 1，带 `-Ptinyui.updateHostSnapshot` 跑快照测试生成新的 `tinyui-host/<n>.txt`，合入主干，CI 上传快照
2. 页面包：`tinyui.config.json` 的 `hostVersion` 改成新值，取新快照（`TINYUI_TOKEN=<发布 token> tinyui hosts download`，写到 `tinyui-host/<n>.txt`；或从宿主仓拷），重新 `pnpm routes`，合入主干，CI 在新版本下发布

第 1 步之后、第 2 步之前，宿主的测试包构建会回落到上一个 `hostVersion` 的包并打警告，不会失败。什么变化要加 1、什么不用，见 [updates.md](./updates.md) §4.1。

**升级 tinyui。** 分两种：

- 只升宿主的 tinyui（库与插件），页面包不动：不用加 `hostVersion`，快照也不变。快照第二行记的是这个 `hostVersion` 的 tinyui **下限**，宿主比它新没有关系
- 页面包也要升（要用新版运行时或新版 CLI 的东西）：页面包三包的版本不能高于快照里的下限，而上传过的快照是冻结的，所以要先在宿主把 `hostVersion` 加 1（新快照的下限即当时的 tinyui 版本），页面包再升级并跟进到新 `hostVersion`。只升页面包会在 `publish` 时被拒

**发 App。** 发版前把要随包发出去的页面版本晋级到 `production`、放量到 100。正式包构建时取 `production` 当前版本作内置包。

**回滚。** 没有「切回上一版」：在页面包仓 revert 坏的那次合入，合入主干发到 `staging`，再晋级。它是一个比所有设备上都新的版本，所以一定送得到。

## 5. 出问题时看哪

构建日志里每个包有一行 `TinyUI: <包名> <版本> (<通道>, host version <n>)`，`build/tinyui/resolved/<包名>.json` 记着同样的内容：这个安装包内置了哪版页面，以它为准。

| 看到 | 意思 | 怎么办 |
|---|---|---|
| 构建失败：`nothing is published at …/production/<pkg>/<n>` | 正式包要的 `hostVersion` 下，这个包还没晋级过 production | 页面包在该版本下发布并晋级 |
| 构建失败：`… is rolling out to 20% …` | production 正在灰度，灰度中的版本不能作地板 | 放量到 100 再构建 |
| 构建警告：`nothing is published for host version <n> yet, embedding … of host version <m>` | 宿主刚加 1，页面包还没跟上，测试包先用旧版本 | 页面包按 §4 跟进 |
| 构建警告：`cannot reach …, keeping …` | 连不上热下发服务，沿用上次取到的版本（只在非 production） | 检查网络；正式包这种情况会失败 |
| 构建失败：`local.properties has tinyui.<pkg>.dir=…, and this build embeds for production` | 联调的配置没删就打正式包 | 删掉那一行 |
| 构建失败：`… is not signed with the declared key` | 线上的包不是 `tinyui { }` 里那把公钥签的 | 确认是否换过钥；换钥流程见 [updates.md](./updates.md) §7 |
| 构建失败：合并资源时报重复文件 | 源码树里还留着 `composeResources/files/tinyui/<包名>/` | 删掉，内置包不入库 |
| 内置包的宿主测试失败 | 内置的包用到了宿主没有的东西 | 等页面包发出修好的版本，或把宿主缺的补回来 |
| `publish` 被拒：没有该 `hostVersion` 的宿主快照 | 宿主还没上传 | 等宿主主干的上传任务跑完 |
| `publish` 被拒：某页用到的能力 / 组件 / 通道快照里没有 | 页面用了目标宿主没有的东西，或 `hostVersion` 填错 | 改页面，或对齐 `hostVersion` |
| `publish` 被拒：tinyui 高于宿主下限 | 页面包三包的版本比宿主快照记的新 | 宿主升 tinyui 并加 `hostVersion`，或页面包降回去 |
| `routes` 报 `tinyui-host/<n>.txt does not exist` | 改了 `hostVersion` 没取新快照 | `tinyui hosts download` |
| 发了新版本，设备上却没有换 | 新版本下次启动才生效；装不上时，测试包里的状态行（`UpdatesStatusLine`）会写明原因，如需要新的安装包、在本机失败过 | [updates.md](./updates.md) §4.7 |

## 6. 版本从哪来

- npm：`tinyui-core` / `tinyui-native` / `tinyui-cli`，三包同号。预发布版本（带 `-`，如 `0.14.0-alpha06`）在 dist-tag `next`，要写明版本号
- Maven：`app.tinyui:tinyui`、`app.tinyui:tinyui-updates`、Gradle 插件 `app.tinyui`，与 npm 同号
- 预发布版本的 Maven 产物可能只在 Central 的 **snapshot 仓库**，版本号多一个 `-SNAPSHOT` 后缀（npm 那边没有后缀；库自报的 `TinyUI.version` 也没有，包与宿主按不带后缀的版本比较）。用它时在插件仓库与依赖仓库各加：

  ```kotlin
  maven("https://central.sonatype.com/repository/maven-snapshots/") {
      mavenContent { includeGroup("app.tinyui"); snapshotsOnly() }
  }
  ```

  snapshot 约 90 天后被清理，只用于过渡：对应的正式版本发到 Maven Central 后把后缀去掉。CI 里按宿主的 tinyui 版本去装 `tinyui-cli` 时记得去掉这个后缀
