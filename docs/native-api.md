# `tinyui-native` 与宿主接口

- 状态：已定（2026-09-16）；2026-09-25 按 [ADR-007](./adr-007-host-boundary.md) 重写：框架自己实现网络、存储、i18n、toast / 对话框、打开链接、埋点门面与会话，宿主只提供标准接口的实现；删 `HostServices`、`config.get`、宿主 `I18n` / `Config` / `HttpClient`
- 来源：ADR-002 §3.1（J2 白名单、J3 / J4）、[app-model.md](./app-model.md)（路由、store、会话、事件）、ADR-004 §3.1（连续事件阈值）、ADR-007（职责边界）
- 两侧：JS 的 `tinyui-native` 是 `__host_*` 的类型化封装；Kotlin 侧宿主给页面的东西只有 App 级的 `TinyUIHost`（§1），启动时建一次，交给每个 `TinyUIPage`

## 1. `TinyUIHost`

```kotlin
val host = TinyUIHost(
    components = ComponentRegistry().registerBuiltins(),   // + 宿主组件
    sink = pageSink,
    dataDir = filesDir / "tinyui-data",                    // storage 落盘目录（§7）
    channels = mapOf("app" to KtorChannel(appHttpClient)), // §6
    locale = uiLanguage,                                   // StateFlow<String>，BCP 47；缺省跟系统（§8）
    session = SessionSource(state, signIn),                // §9
    analytics = AnalyticsSink { name, props, page -> … },  // §12
    links = LinkOpener { url, page -> … },                 // §11，缺省 LinkOpener.System
    navigator = appNavigator,                              // §3
    capabilities = CapabilityRegistry().apply { … },       // §13，兜底
)

TinyUIPage(module, host, propsJson = "{}", locals = emptyList(), modifier = …)
```

除 `components`、`sink` 外都可缺省，缺省行为见各节（没有 `dataDir` 时 `storage` 答 `E_UNSUPPORTED`）。标准接口（通道之外）的形状由框架定，不进宿主快照；进快照的只有宿主组件、`capabilities` 的名字、`channels` 的名字，以及是否提供会话（记作能力 `session.signIn`，§9）（updates.md §6.4）。`host.store`、`host.events` 是框架建的 App 级实例，宿主经它们与页面交换状态和事件（§4、§5）。

`locals` 是这一次挂载交给 `host.call` 能力的对象（§13），只有兜底能力用得到。

## 2. J2 白名单

框架固定，业务不能加；同步返回 JSON 文本，没有就是 `null`。实现全部在框架内、只读内存：

| 名字 | 返回 |
|---|---|
| `device.info` | 对象：`os`、`osVersion`、`model` |
| `store.get` | 任意（§4） |
| `storage.get` | 任意（§7） |
| `storage.set` | `null`，或错误码 `"E_QUOTA"`；`tinyui-native` 收到错误码即抛 `HostError`（§7） |
| `i18n.t` | 字符串（§8） |
| `i18n.locale` | 字符串（§8） |
| `session.get` | `{ loggedIn, userId }`（§9） |

`storage.set` 是白名单里唯一的写：它只改内存、写盘另行合并，所以能在调用线程立即返回；放 J2 是为了让超额在调用处同步报出。

## 3. `navigation`

```ts
navigation.push("subscription/detail", { id: 42 });   // J4 → Navigator.push(page, paramsJson)；页面名含包名
navigation.pop({ saved: true });                // J4 → Navigator.pop(resultJson)
navigation.onResult((result, from) => …);       // 渲染期调用；K5 topic navigation.result
```

宿主实现 `Navigator` 接口，交给 `TinyUIHost`；把结果送回上一页时，宿主持有该页的 `PageHost`（`TinyUIPage` 的 `onHost` 回调拿到），调 `host.emit("navigation.result", json)`，`json` 形如 `{ "result": …, "from": "subscription/detail" }`。缺省 `Navigator.None`（调用无效果）。跳原生页的路由名由 `Navigator` 解释，不进快照；`push` 是 J4，页面拿不到结果，认不出的名字由 `Navigator` 忽略并经 `PageSink` 上报。

## 4. `store`

```ts
store.get<T>(key)             // J2，快照
store.set(key, value)         // J4，value 会被 JSON.stringify
const cart = store.watch(key) // 渲染期调用，返回 accessor；K5 topic store:<key> 到达时更新
```

App 级内存键值，真值在框架的 `host.store`，全 App 一份、跨包共享、进程结束即失。宿主写入用带类型的方法：

```kotlin
host.store.set("trendingai.flag", true)            // 按 kotlinx.serialization 编码；原始 JSON 用 setJson / getJson
host.store.bind("trendingai.flag", flow, scope)    // 把一个 Flow 持续写入
```

`watch` 经 J4 `store.subscribe` 登记，只有登记过的页面收到该 key 的变更。key 带包名前缀（app-model.md §7）。宿主不能替换 store 的实现；要落盘用 §7。

## 5. `events`

```ts
events.emit("trendingai.checkout.opened", { plan })   // J4 → host.events
events.on("network", (p) => …)                        // 渲染期调用；J4 events.subscribe + K5
```

`host.events` 是 App 级 `EventBus`，宿主原生代码用同一个实例 `emit` 网络状态、主题等，也用它 `subscribe` 页面发来的业务事件。订阅随页面 owner 结束，页面关闭时 Kotlin 侧的监听一并注销。页面发给宿主的事件不进快照：宿主没监听，页面照常工作；改名后宿主静默收不到，这类事件要尽量少。

## 6. `http`

```ts
const { status, headers, body } = await http.get<Todo[]>("https://api.example.com/todos", { timeout: 5000 });
await http.post(url, { title }, { headers: { "X-Trace": id } });

const api = http.client("app");      // 宿主注册的通道
await api.post(`${BASE}/api/billing/checkout`, { plan });
```

J3 `http.request`，参数 `{ channel, method, url, headers?, body?, timeout? }`；`http.get` / `post` / `request` 走 `channel: "default"`。URL 由页面给全，框架不拼 base。

| 通道 | 实现 | 用途 |
|---|---|---|
| `default` | 框架内置 ktor client，不带任何身份 | 公开接口、第三方接口 |
| 宿主注册的名字（`app` 等） | `TinyUIHost.channels`，宿主接自己的 HTTP 栈 | 需要以当前用户身份发出的请求 |

哪个请求走哪条通道由页面决定：token 只在宿主通道里，页面看不到；把第三方请求交给身份通道就会发出 token，靠审查，被篡改的包过不了签名（ADR-007 §3.1）。

```kotlin
fun interface HttpChannel {
    suspend fun request(request: HttpRequest): HttpResponse
}
class HttpRequest(val method: String, val url: String, val headers: Map<String, String>, val body: String?, val timeoutMs: Long?)
class HttpResponse(val status: Int, val headers: Map<String, String>, val body: String)

KtorChannel(client: io.ktor.client.HttpClient)   // 把宿主已有的 client 包成通道
```

- 请求体：对象按 JSON 编码并设 `Content-Type: application/json`，字符串原样发送
- 响应体：`Content-Type` 为 JSON 时解析成对象，否则为文本
- 2xx resolve `{ status, headers, body }`；非 2xx 以 `E_HTTP` reject，`HostError` 带 `status`、`headers`、`body`（解析规则同上）
- 通道名 `default` 保留，宿主不能注册；页面用了宿主没注册的名字 → `E_UNSUPPORTED`。`http.client` 的参数必须是字符串字面量（build-chain.md §5.1），通道名随 `requires` 进 manifest、发布前对照快照核对

`setTimeout` / `clearTimeout` 是 core 提供的全局，经 J3 `timer.schedule` / J4 `timer.cancel`，宿主侧由 `PageHost` 实现，随页面关闭取消。

## 7. `storage`

```ts
storage.get<T>(key)          // J2，同步
storage.set(key, value)      // J2，超额抛 HostError E_QUOTA
storage.remove(key)          // J4
storage.clear()              // J4，清本包全部

const [paywall] = cached("paywall.v1", () => api.get(`${BASE}/api/app-config`).then((r) => r.body.pro_paywall));
```

- 按包隔离：键空间是 `<包名>`，同一包的页面共享，别的包与宿主不读
- 包的第一个页面挂载时，框架把 `<dataDir>/storage/<包名>.json`（`dataDir` 不要与 `Updates` 的目录相同，那里按包名分目录） 整体读进内存；读写都在内存，写盘在最后一次写入后合并进行（写临时文件再原子替换）。进程被杀可能丢失最后几十毫秒的写入
- 每包总量上限（序列化后字节数）由框架常量定，约 1 MB；超出时 `set` 不生效并抛 `E_QUOTA`
- 按设备存，不区分用户：用户相关数据 key 带 `session().userId`，或在其变化时自行清理
- 不加密，不存敏感信息；框架不做格式迁移，跨版本与回滚的兼容由页面负责（换 key 或读取时容错）

`cached(key, fetcher)` 是纯 JS 工具（先旧后新）：渲染期调用，返回 `[data, { loading, error, refetch }]`，与 `resource` 同形；`data` 初值是 `storage.get(key)`，随后执行 `fetcher`，成功即更新并 `storage.set`，失败保留旧值并置 `error`。

## 8. `i18n`

```ts
i18n.t("subscription.title")
i18n.t("subscription.savings", { percent: "43%" })
i18n.locale()   // accessor，如 "zh"
```

- 字典在包里：`i18n/<locale>.json`，扁平的 key → 字符串，包的默认语言写在 `tinyui.config.json`（build-chain.md §8）
- 包第一次挂页面时，各语言的字典全部读进内存（manifest 的 `i18n`，与字节码同一来源）
- 当前语言：`TinyUIHost.locale` 的值，缺省为系统语言；变化时经 K5 topic `i18n.locale` 通知活页面，`t()` 与 `locale()` 的绑定重算，页面不重建
- 查找回退：`zh-Hant-TW → zh-Hant → zh → 默认语言`；全部缺失返回 key 本身，并经 `PageSink` 每个 key 上报一次
- 插值只有 `{name}`，值按字符串替换；没有复数与数字 / 日期格式化（引擎无 `Intl`）
- key 的 TS 类型由 CLI 生成（build-chain.md §8），写错的 key 编译期报

## 9. `session`

```ts
session.state()                // accessor：{ loggedIn: boolean, userId: string | null }
await session.signIn("paywall") // 拉起宿主的登录流程，流程结束时 resolve（成功与否看 state()）
```

```kotlin
class Session(val loggedIn: Boolean, val userId: String?)
class SessionSource(val state: StateFlow<Session>, val signIn: suspend (source: String) -> Unit)
```

- J2 `session.get` 取初值，K5 topic `session` 推送变化；框架订阅 `state`，推给所有活页面，页面不重建
- `userId` 是稳定、非敏感的用户标识，不是 token
- 宿主没提供 `session` 时：`state()` 恒为 `{ loggedIn: false, userId: null }`，`signIn` 以 `E_UNSUPPORTED` reject
- 提供了 `session` 的宿主，快照的能力段多一行 `session.signIn`；调用了 `session.signIn` 的页面把它记进 `requires`，发布前照能力名核对（updates.md §1.3）。提供或撤掉会话都是宿主契约变化，`hostVersion` 加 1。`session.signIn` 是框架名，`host.call` 注册同名能力在 `register` 时报错（§13），所以快照里的这一行只可能来自会话

## 10. `ui`

```ts
const r = await ui.toast("已复制", { action: "撤销", duration: "short" })   // "action" | "dismissed"
await ui.alert({ title?, message, confirm? })
const ok = await ui.confirm({ title?, message, confirm?, cancel? })         // boolean
```

- toast 由 `TinyUIPage` 自带的 SnackbarHost 显示为 M3 Snackbar，位于页面区域底部；对话框为 M3 AlertDialog，只有纯文本。样式经 `MaterialTheme` 跟随宿主主题
- 按钮文字缺省用框架内置的文案（随 §8 的当前语言，内置中英文，其余回退英文）
- 页面关闭时未结束的 toast 与对话框随之消失，Promise 以 `E_CANCELLED` reject

## 11. `linking`

```ts
await linking.openUrl(url)
```

```kotlin
fun interface LinkOpener {
    suspend fun open(url: String, page: PageContext)
    companion object { val System: LinkOpener }   // Android ACTION_VIEW、iOS UIApplication.open
}
```

- 链接交给系统或宿主后 resolve，不等用户回来；回来与否看 `pageVisible()`（runtime-api.md §8）
- URL 不合法 → `E_INVALID`；没有应用能打开 → `E_UNSUPPORTED`
- scheme 不限；宿主提供 `links` 即接管全部打开行为，可在其中对部分 URL 调 `LinkOpener.System`

## 12. `analytics`

```ts
analytics.track("checkout_step", { step: "plan_selected", plan: "annual" })   // J4，无返回
```

```kotlin
fun interface AnalyticsSink {
    fun track(name: String, props: Map<String, Any?>, page: PageContext)
}
```

- `props` 只允许一层，值为字符串、数字、布尔或 `null`；其他形状在 JS 侧抛 `E_INVALID`
- 框架不往 `props` 里加任何东西，页面身份经 `page` 单独给出；没有 `analytics` 时丢弃，只在 debug 日志打印
- 事件名与字段由包定义，框架不校验

## 13. 宿主自定义能力：`host.call`（兜底）

```ts
const r = await host.call<{ ok: boolean }>("trendingai.coupon.apply", { code });
```

J3，一个名字一个能力，在 App 级注册一次，与宿主组件同属宿主契约。先用 §3–§12；只有 App 专属、需要返回值、框架不打算标准化的动作才用它（ADR-007 §3.9）。

```kotlin
val Coupon = PageLocal<CouponViewModel>()

TinyUIHost(…, capabilities = CapabilityRegistry().apply {
    register("trendingai.coupon.apply") { argsJson, page ->
        val vm = page[Coupon] ?: throw HostException("E_UNSUPPORTED", "no coupon on ${page.name}")
        …
    }
})

TinyUIPage(module, host, locals = listOf(Coupon provides viewModel))
```

- `HostCapability.call(argsJson, page)`：suspend，收 JS 传的对象（JSON 文本），返回 JSON 文本（`null` 即 `undefined`）；抛 `HostException` 走 E3 码，其他异常按 `E_NET`。没注册的名字拒绝 `E_UNSUPPORTED`
- 能力只依赖 App 级对象；依赖某一屏的东西由挂载方经 `locals` 以 `PageLocal` 为键传入，能力从 `page[local]` 取
- 注册表启动时建好即不可变，放进 `TinyUIHost` 后再 `register` 直接报错。名字不强制前缀，但不能与框架名（§2–§12 用到的 J2 / J3 / J4 名字）重合，不能含空白，也不能重复注册，都在 `register` 时报错
- 挂载清单的 `capabilities` 列出框架名 + 注册表里的全部名字，页面可据此判断宿主是否提供某能力

能力的类型声明由宿主的 JS 工程自己封装，框架不生成。

## 14. E3 错误码与阈值

| code | 含义 |
|---|---|
| `E_UNSUPPORTED` | 能力、通道或标准接口宿主没有提供，或名字未知 |
| `E_INVALID` | 参数不合法 |
| `E_NET` | 网络层失败，或宿主实现抛了非 `HostException` 的异常 |
| `E_HTTP` | 非 2xx；`HostError` 带 `status` / `headers` / `body` |
| `E_TIMEOUT` | 超时 |
| `E_DENIED` | 权限拒绝 |
| `E_CANCELLED` | 页面关闭或宿主取消 |
| `E_QUOTA` | `storage` 超出本包上限 |
| `E_BAD_JSON` | 载荷不是合法 JSON（JS 侧生成） |

阈值（可调，写在代码常量里）：

| 项 | 值 | 位置 |
|---|---|---|
| K 入口超时 | 5 s，超时 interrupt 判 E6 页面失败 | `PageHost.entryTimeoutMs` |
| `onReachEnd` | 最后一行进入可见区时触发一次；列表长度变化后再武装 | `LazyColumnComponent` |
| `onScrollEnd` | 滚动停止时触发，带首个可见行 index | 同上 |
| `onProgress` 类节流 | 1 s | 首批组件没有，写在这里备查 |
| `storage` 每包上限 | 约 1 MB | `Storage` |
