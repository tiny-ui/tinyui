# ADR-007 · 框架职责边界：能随包变的都进包，宿主只留不可约的

- 状态：已定（2026-09-25）；实现待开，里程碑见 roadmap.md 第 8 条
- 结论：**判据是"改它要不要宿主发版"——可能独立于原生代码变化的东西（接口地址、请求、文案、远程配置、埋点事件名、业务流程、图标）都进页面包，随热下发改；通用的原生能力由框架自己实现（网络、持久化存储、toast / 对话框、打开链接、图标与加载指示、包内 i18n），不再是等宿主填的空接口；宿主只提供页面拿不到的东西——网络通道（身份在里面）、用户语言、登录会话、埋点出口、链接打开器，全部是框架定义形状的标准接口，挂在 App 级 `TinyUIHost` 上。`host.call` 与宿主组件降为兜底。`HostServices` 删除，core 库放开零 I/O（自带 ktor 与 okio）**
- 契约：[native-api.md](./native-api.md)（`tinyui-native` 全部 API 与宿主接口）、[app-model.md](./app-model.md)（store、会话）、[components.md](./components.md)（`Icon`、`Loading`、`role` / `selected`）、[build-chain.md](./build-chain.md)（包内 `i18n/`、通道名静态识别）、[updates.md](./updates.md)（manifest、快照的通道段、`hostVersion` 口径）
- 影响：ADR-002 §3.1 的 J2 白名单成员改变（删 `config.get`，加 `storage.get` / `storage.set`、`i18n.locale`、`session.get`，`i18n.t` 改由框架实现）；ADR-006 §2.4 "core 库零 I/O"作废；native-api.md §7 "下单、登录、埋点不该进框架"作废；2026-09-17 "框架不内置 Icon、加载指示走宿主组件"作废；roadmap D 组"宿主契约 schema 化"的触发前提大半消失

## 1. 背景

TrendingAI 订阅页（roadmap 第 6 条）是第一个真实页面。它对宿主的依赖：

| 依赖 | 实际要做的事 |
|---|---|
| `host.call("billing.prices")` | GET 一个不需要登录的公开接口 |
| `host.call("checkout.start")` | 带 token POST 下单 → 打开收银台 → 记下"去过收银台" |
| `host.call("analytics.checkoutStep")` | 上报一个埋点 |
| `host.call("ui.snackbar")` + `PageLocal<SnackbarHostState>` | 弹一条轻提示 |
| `host.call("auth.signIn")` | 拉起原生登录 |
| 宿主组件 `ta.Icon`、`ta.Loading` | Material 图标、加载指示 |
| props 里十几条文案 | 多语言字符串 + 远程 `pro_paywall` 覆盖，Kotlin 拼好传入 |
| 每页新建 `InMemoryStore`，两个 `LaunchedEffect` 桥登录态与 Pro 态 | App 级状态 |

框架其实有 `http`、`i18n.t`、`config.get`、`store`，但 Kotlin 侧都是 `HostServices` 上等宿主实现的接口，默认实现什么都不做，TrendingAI 一个也没接，全部绕到 `host.call`。结果：

- 页面里多一行字、换一个接口地址、加一个埋点，都要改 props 或能力 → `hostVersion` 加 1 → 发 App，热下发能改的只剩布局
- 宿主契约的"快照盲区"（能力参数形状、页面 props、store key，updates.md §4.1）几乎全部来自这些本不该在宿主的东西
- 第二个接入的 App 要把同样的能力再实现一遍

## 2. 判据与三层

一个东西可能不经原生代码改动而变化，它就属于页面包。据此分三层：

| 层 | 随什么变 | 负责 |
|---|---|---|
| 页面包 | 热下发 | 接口地址与请求、文案与多语言资源、远程配置的读取与缓存、埋点事件名与字段、业务流程与状态、图标数据 |
| 框架（`app.tinyui:tinyui` + `tinyui-native`） | tinyui 版本（随宿主） | 通用原生能力的**实现**：HTTP 默认通道、按包持久化存储、toast / 对话框、打开链接、`Icon` 渲染、`Loading`、包内 i18n 加载、App 级 store、页面可见性 |
| 宿主 | App 发版 | 页面物理上拿不到的：身份（网络通道里的 token）、用户语言、登录会话与登录流程、埋点出口、链接打开策略；以及真正 App 专属、需要返回值的动作（`host.call`）与必须原生渲染的视图（宿主组件） |

宿主那一层，除 `host.call` 与宿主组件外，都是框架定义形状的标准接口：宿主只提供实现，不起名字、不定参数，所以不进宿主契约，也不影响 `hostVersion`。

框架层的实现原则：**框架提供能力与默认实现，宿主可以接管执行，页面不感知**。网络通道与链接打开器是可接管的两处。

## 3. 候选与取舍

### 3.1 网络：页面按请求选通道

框架自带默认通道（内置 ktor，不带身份）；宿主可注册具名通道，接到自己的 HTTP 栈上（token、刷新、公共头都在通道里，页面看不到 token）。难点是哪些请求走宿主通道，用四个场景判：拉价（公开接口）、下单（必须带 token）、请求 `api.github.com`（绝不能带）、后端换域名（新域名上仍须带）。

| | 做法 | 请求第三方 | 换域名 |
|---|---|---|---|
| A. 注册了就全走 | 所有请求走宿主通道 | 带上 token，泄露；要堵就得在宿主按域名过滤 | 过滤了就要宿主发版 |
| B. 页面按请求选 | `http.client("app").post(…)`，默认走框架通道 | 不带 | 只改包里的 baseUrl |
| C. 宿主域名白名单 | 白名单内走宿主通道 | 不带 | 要宿主发版 |

选 B。代价：页面把第三方请求交给 `app` 通道就会发出 token，与原生代码用错 client 同类，靠审查；被篡改的包过不了签名（updates.md §7）。宿主若想更保守，可在自己的通道里再加域名检查，框架不强制。

通道挂 `TinyUIHost`，通道名是宿主契约（页面用了宿主没有的通道会失败），进快照（§3.9）。响应语义保持现状（非 2xx 以 `E_HTTP` reject），`HostError` 增加 `status` / `headers` / `body`，页面据此处理 404、409——不改已公开的语义（runtime-api.md §11）。

### 3.2 i18n：字典进包，框架加载

| | 做法 | 对照 | 问题 |
|---|---|---|---|
| A. 字典在宿主（现状） | 宿主实现 `I18n.translate` | — | 改文案要宿主发版 |
| B. 框架只给语言 | 页面自选 i18n 库 | RN（i18next）、小程序 | 每包重复打库；缺键检查、key 类型各自做 |
| C. 字典进包、框架加载 | 包级 `i18n/<locale>.json`，框架读当前语言进内存，`i18n.t` 仍走 J2 | Flutter ARB、Android 资源 | 框架多一件事 |

选 C。字典经 `Bundle` 的加载器读取（与页面字节码同一来源、同一签名覆盖），只把当前语言放进 Kotlin 内存，J2 查询是纯内存操作（ADR-002 对 J2 的要求）。语言优先取宿主推来的用户语言（App 内语言开关只有宿主知道），缺省用系统语言；宿主推新语言时框架换字典、经 K5 通知活页面，`t()` 绑定重算，页面不重建。回退链 `zh-Hant-TW → zh-Hant → zh → 包的默认语言`，全部缺失显示 key 并经 `PageSink` 上报一次。只支持 `{name}` 插值；`tinyui build` 以默认语言为准校验各语言 key 齐全，并生成 key 的 TS 类型。

QuickJS 没有 `Intl`：复数、本地化数字与日期第一批不做，触发时加 J2 `i18n.format`（原生侧实现）。

挡住：宿主不能再向页面提供文案；包内页面共用 key 空间（按页面加前缀）；`i18n.t` 保持同步，不做按需远程加载——远程文案走 §3.3。

### 3.3 远程配置：不是框架概念

| | 做法 | 问题 |
|---|---|---|
| A. 宿主转交（`config.get`） | 宿主把缓存的配置给页面 | 来源、字段、缓存都依赖宿主 |
| B. 页面自取自缓存 | `http` 请求 + `storage` 缓存，先旧后新 | 与宿主冷启动的请求重复一次；装机后首次打开没有缓存 |
| C. 框架内置配置服务 | 热下发服务端按包下发配置 | 与 App 自己的后端形成两个真值来源 |

选 B。删 `config.get` 与宿主 `Config` 接口；`tinyui-native` 提供纯 JS 的 `cached(key, fetcher)`：以 `storage` 同步读到的值为初值，后台请求，成功即更新并写回，失败保留旧值。按语言挑远程文案的逻辑在页面里，远程缺失回退到 `t()`；远程晚到时原地更新，页面不重建。

挡住：框架不提供功能开关 / 远程配置服务（C 推迟，触发条件见 §5.3）；宿主已有的配置页面要用只能自己请求；首次打开会有一次"默认 → 远程"的文案切换，不能接受的页面先显示加载指示。

### 3.4 持久化存储：预载 + 同步读

| | 做法 | 对照 | 问题 |
|---|---|---|---|
| A. 纯异步 | `await storage.get(key)` | RN AsyncStorage | 渲染期读要先显示一帧加载 |
| B. 预载 + 同步读、异步写盘 | 包首次使用时整体读进内存，J2 读内存，写入改内存后合并写盘 | 小程序 `getStorageSync`、Web `localStorage`、Flutter shared_preferences | 容量必须设限 |
| C. 并进 `store`，加持久化开关 | `store.set(k, v, { persist })` | — | `store` 跨包共享且宿主会写，所有权与清理说不清 |

选 B，与 `store` 分开：`store` 是 App 级内存、跨包、宿主可读写；`storage` 落盘、只有本包页面可见、宿主不读。实现为每包一个 JSON 文件（`<宿主给的目录>/storage/<包名>.json`，okio 写临时文件后原子替换）。写盘合并意味着进程被杀可能丢最后几十毫秒的写入，不适合关键数据。每包设总量上限（约 1 MB，实现时按内存与解析耗时定），超出 `set` 抛 `E_QUOTA`。不做 `watch`、不加密、不区分用户（用户相关数据 key 带 `userId` 或在会话变化时自清）、不做迁移（跨版本与回滚的格式兼容归页面：换 key 或容错）。

挡住：大数据、文件、数据库（触发见 §5.3）；读取保持同步，不改懒加载；包间不经 `storage` 共享；宿主不直接读页面存的东西。

### 3.5 共享状态与会话

订阅页用到的两个 App 级状态：

| 状态 | 真值 | 页面能自己拿到吗 | 归属 |
|---|---|---|---|
| 登录态 | 宿主（token 在通道里，登录是原生流程） | 否：看不到 token，别处登录登出也收不到 | 宿主推送 |
| Pro 态 | 服务端 | 能：`app` 通道请求 `/api/me` | 页面自取，可见时刷新 |

登录态怎么给页面：

| | 做法 | 问题 |
|---|---|---|
| A. 宿主写 store | 各 App 自定 key | key 与形状各不相同，页面不能跨 App 复用，key 名也是契约 |
| B. 框架标准会话 | `session()` → `{ loggedIn, userId }`，宿主提供 `StateFlow<Session>` 与 `signIn` | 框架要固定会话形状 |
| C. 页面自己推断 | 请求"我是谁"接口，可见时重取 | 首屏不确定、前台时感知不到登出、要求后端有该接口 |

选 B（C 记作备查）。登录动作并入会话：`session.signIn(source)`，登录流程结束时 resolve，替代 `host.call("auth.signIn")`。`userId` 是稳定、非敏感的标识，不含 token；页面在 effect 里观察 `userId` 变化来清理用户数据，不另设登出事件。

`store` 从每次挂载的参数改为 App 级框架单例（app-model.md 本就这样设计，实现没跟上）；宿主写入改为带类型的 `set` / `bind(key, flow)`；宿主不能再自定义 `Store` 实现。

`pageVisible()` 此前在 Kotlin 侧从未被驱动（`PageHost.visible` 没有调用方），"可见时刷新"依赖它：由 `TinyUIPage` 跟随 Lifecycle 的 START / STOP 驱动，前后台切换与被其他页面覆盖都算。

挡住：多账号、匿名分级等更复杂的身份模型进不了 `session`，只能走 store；业务状态默认由页面自问服务端并自己决定刷新时机。

### 3.6 通用 UI 能力

| 能力 | 框架实现 | 宿主可接管 |
|---|---|---|
| toast | `TinyUIPage` 自带 SnackbarHost，画 M3 Snackbar | 否：经 MaterialTheme 继承宿主主题 |
| 对话框 | M3 AlertDialog，命令式纯文本 `ui.alert` / `ui.confirm` | 否 |
| 打开链接 | Android `ACTION_VIEW`、iOS `UIApplication.open` | 是：宿主注册链接打开器，保留自己的策略（如 TrendingAI 的 Custom Tab 开关与 WebView 兜底） |

对话框取命令式（小程序 `wx.showModal`、RN `Alert.alert`）而不是声明式 `<Dialog>`：第一批只要纯文本；声明式 `Dialog` / `BottomSheet` 推迟。`openUrl` 在链接交出时 resolve，不等用户回来（用 `pageVisible()`）；scheme 不限，App 自己的 scheme 回到宿主的深链处理；页面不能指定应用内或外部打开。页面关闭时未结束的 toast 与对话框随之消失，Promise 以 `E_CANCELLED` 失败。

挡住：toast 只在页面区域内，跨页提示改为回传结果由上一页弹；对话框样式固定 M3；同一个包在不同宿主里链接打开方式可能不同（有意为之）；没有应用内 WebView。

### 3.7 内置组件：`Icon`、`Loading`

| | 做法 | 问题 |
|---|---|---|
| A. 内置 `material-icons-core` + `extended`，按名字查 | 生成"名字 → ImageVector"表 | 查找表让 R8 / iOS 死代码裁剪失效，整库进 App（extended 的 Android AAR 35.7 MB、iOS klib 18.9 MB，本机缓存实测）；该库已停在 1.7.3 |
| B. 图标字体 | Material Symbols 字体 | 字体数 MB，裁剪又回到固定集合 |
| C. 图标数据随包，框架只渲染 | 页面 import 图标数据，esbuild 只打进用到的；`Icon` 把 path 渲染成 ImageVector | 只支持单色 path |

选 C。新 npm 包 `tinyui-icons` 从 Material Symbols（Apache 2.0）生成，一个图标一个具名导出；图标是字符串 `"<viewBox>|<d>"`（prop 不过桥对象，patch-protocol.md §4），页面可手写品牌图标。Kotlin 按字符串缓存解析结果。服务端 key 到图标的映射搬进页面，新 key 发包即可。多色与位图归 Image 管线。

`Loading` 用 M3 Expressive 的 `LoadingIndicator`（实验 API，Compose 升级时框架跟进），只做不定进度。

同时为可点击容器补无障碍语义：公共 prop `role` 与 `selected`（components.md §2），订阅页的选档卡片由此成为读屏的一个"单选、已选中"节点，与原生版等价。

挡住：推翻 2026-09-17 "框架不内置 Icon"；`Icon` 不能退化为"Kotlin 只收名字"（那就回到全量打包）。宿主组件机制保留给地图、视频、原生广告这类必须原生渲染的视图。

### 3.8 埋点：框架门面 + 宿主出口

| | 做法 | 问题 |
|---|---|---|
| A. 每种事件一个宿主能力（现状） | `host.call("analytics.checkoutStep")` | 加埋点要宿主发版 |
| B. 通用 `track` + 宿主出口 | `analytics.track(name, props)`，宿主注册 `AnalyticsSink` 原样转交自己的 SDK | 页面事件失去宿主侧的编译期词汇检查 |
| C. 页面直接请求埋点服务端 | `http` POST | 全局属性、批量、离线队列要在 JS 重写；两条管道对不上 |

选 B。出口是不可约的（页面事件要与原生事件进同一条流、带同一批全局属性），但事件名与字段由包定义：包内一个 TS 文件集中声明页面事件，页面只经它上报。框架不往 props 里自动加东西，页面身份单独传给出口；没有出口即丢弃。`screen_viewed` 仍归宿主导航。

### 3.9 剩余的宿主契约

| 机制 | 用途 |
|---|---|
| 框架标准接口（`TinyUIHost` 上） | 网络通道、语言、会话、埋点出口、链接打开器、导航 |
| `events` | 页面单向通知宿主业务事件（如 `trendingai.checkout.opened` → 宿主开始对账计时），宿主没监听页面照常工作 |
| `host.call` | 兜底：App 专属、要返回值、框架不打算标准化的动作；新增前要先论证它不能是标准接口或事件 |
| 宿主组件 | 必须原生渲染的视图 |

`HostServices` 删除：`navigator` 挪到 `TinyUIHost`，`locals` 改为 `TinyUIPage` 的可选参数（只给 `host.call` 的能力用）。

宿主快照收录宿主组件、`host.call` 能力名，**新增网络通道名**；事件 topic、埋点事件名、框架标准接口不进快照。`hostVersion` 的口径随之收窄为这三类的集合及其参数与行为。事件改名后宿主静默收不到，快照拦不住，所以页面到宿主的事件要尽量少。跳原生页的路由名第一批不进快照：`Navigator` 认不出时返回失败，由页面处理。

## 4. 决策

| 项 | 结论 |
|---|---|
| 判据 | 能独立于原生代码变化的进包；框架实现通用原生能力；宿主只留不可约的，且以框架标准接口表达 |
| 网络 | 框架默认通道（ktor，无身份）+ 宿主具名通道；页面按请求选；通道名进快照；非 2xx 仍 reject，`HostError` 带 `status` / `headers` / `body` |
| i18n | 包级 `i18n/<locale>.json`，框架加载、J2 查询、语言由宿主推送、切换不重建、回退链、`{name}` 插值、构建期缺键校验与 key 类型；删宿主 `I18n` |
| 远程配置 | 不是框架概念；删 `config.get` 与 `Config`；`cached()` 工具 |
| 存储 | `storage`，按包隔离、预载同步读、合并写盘、约 1 MB 上限、`E_QUOTA` |
| 会话与状态 | 框架标准会话 `{ loggedIn, userId }` + `signIn`；业务状态页面自取；store App 级单例；`pageVisible()` 由 `TinyUIPage` 驱动 |
| UI 能力 | `ui.toast`、`ui.alert` / `ui.confirm`、`linking.openUrl`（宿主可接管） |
| 组件 | `Icon`（path 数据，`tinyui-icons`）、`Loading`（`LoadingIndicator`）、公共 prop `role` / `selected` |
| 埋点 | `analytics.track` + 宿主 `AnalyticsSink` |
| 宿主契约 | 标准接口 > `events` > `host.call` 兜底 + 宿主组件；删 `HostServices`；快照加通道段 |
| 库的 I/O | core 库放开零 I/O，直接依赖 ktor 与 okio（ADR-006 §2.4 该句作废）；`tinyui-updates` 的边界不变（仍不做网络，`fetch` 由宿主给） |

## 5. 后果与遗留

### 5.1 推翻的已定事项

| 原结论 | 出处 | 现在 |
|---|---|---|
| J2 白名单四个：`device.info` / `i18n.t` / `config.get` / `store.get`，实现在宿主 | native-api.md §1、ADR-002 §3.1 | 删 `config.get`；`i18n.t` 由框架实现；加 `storage.get` / `storage.set`、`i18n.locale`、`session.get` |
| 下单、登录、埋点是 App 自己的动作，不该进框架 | native-api.md §7 | 请求、打开链接、上报的通用部分归框架；登录并入标准会话；只有业务语义本身留宿主 |
| 宿主给页面的东西分 App 级 `TinyUIHost` 与每次挂载的 `HostServices` | native-api.md 开头 | 只剩 `TinyUIHost`；每次挂载只有 `locals` |
| 框架不内置 Icon，加载指示走宿主组件 | roadmap 第 6 条（2026-09-17） | 内置 `Icon`（path 数据）与 `Loading` |
| core 库零 I/O、零文件依赖 | ADR-006 §2.4 | 放开 |
| 持久化由宿主决定 | app-model.md §3、native-api.md §3 | `store` 仍为内存；持久化由框架的 `storage` 提供 |

### 5.2 兼容

- JS 公开面只增不改（runtime-api.md §11）：新增 `storage` / `i18n` / `session` / `ui` / `linking` / `analytics` / `http.client` / `cached` 与 `HostError` 的字段，`http` 已有语义不变，`pageVisible()` 从"从不变化"变为按规定工作。所以随 0.x minor 发出，不需要 1.0
- Kotlin 侧 API 有破坏（删 `HostServices`、`TinyUIPage` 与 `TinyUIHost` 的参数变化），宿主升级时改接入代码；宿主契约随之变化的按 updates.md §4.1 加 `hostVersion`

### 5.3 推迟项（记 roadmap D 组）

| 项 | 触发条件 |
|---|---|
| 复数与本地化数字 / 日期（J2 `i18n.format`） | 第一个需要复数或本地日期的页面 |
| 框架远程配置 / 功能开关服务（§3.3 方案 C，可能是托管实例的付费功能） | 第二个 App 没有自己的配置后端又需要功能开关 |
| 大数据存储（SQLite / 文件 API） | 第一个要缓存大量数据的页面 |
| 声明式 `Dialog` / `BottomSheet` | 第一个需要自定义内容对话框的页面 |
| 剪贴板、分享、震动、选图等通用能力 | 第一个需要的页面；均按"框架默认实现、宿主可接管"加 |
| `openUrl` 的打开方式提示（`mode`） | 页面确实要求应用内或外部打开 |
| 多色 / 位图图标 | 并入 Image 管线 |
| 流式响应 / SSE、上传下载、请求缓存 | 第一个需要的页面 |
| 会话之外的身份模型（多账号等） | 出现这类宿主 |

### 5.4 验收：TrendingAI 订阅页

订阅页按本文重写后，宿主只剩：`app` 通道、用户语言、会话（含 `signIn`）、埋点出口、链接打开器、监听一个 `trendingai.checkout.opened` 事件。`Capabilities.kt` 的五个能力、页面 props、`ta.Icon` / `ta.Loading` 与整条宿主 schema 生成链删除。同时修掉首次接入 review 发现的行为差异：拉价失败一直转圈、顶部多出 8 dp、props 变化整页重建、选档卡片的读屏语义。
