# 应用模型：页面是 JS 的世界，应用是 Kotlin 的世界

- 状态：已对齐（2026-09-16；2026-09-19 加多包：路由键含包名、§7 跨包约定；2026-09-23 宿主层面的页面标识加 `tinyui:` 前缀，M6 拍板第 7 点；2026-09-25 按 ADR-007：store 为框架持有的 App 级单例、登录态改为框架标准会话、持久化由框架 `storage` 提供）；API 面见 native-api.md §3–§5
- 来源：ADR-002 "跨页共享状态走 Kotlin（K5 推送）"、"多页共享引擎被否：JS 全局正是隔离问题的来源"、`navigation.push` 为 J4；ADR-003 "组件注册表 App 级、不可变"

## 1. 前提

JS 侧不存在长生命周期的"应用对象"：没有 `app.tsx`、没有 `onLaunch`、没有 `globalData`。每个页面模块是一个孤岛，出口只有 `tinyui-native` 的宿主能力，入口只有 K1 生命周期、K2 事件、K3 回送、K5 推送。页面对另一个页面的存在一无所知。

## 2. 三类应用级事务的归属

| 事务 | 归属 | 机制 |
|---|---|---|
| 路由 | Kotlin 导航栈 | 页面名 = 模块名 = 路由键，含包名（`subscription/detail`，[build-chain.md](./build-chain.md) §2），跨包跳转与包内跳转写法相同。JS `navigation.push(name, params)` 经 J4 → Kotlin 建页面作用域 → K0 加载 → K1 `__mount(props)`，params 即 props。返回 = pop = `__unmount` = 关引擎 |
| 应用生命周期 | 宿主事件 | 前后台、内存警告、主题经 K5 `__emit(topic)` 投给每个订阅了的活页面；语言与登录会话是框架标准接口，各有固定 topic（native-api.md §8、§9）；`TinyUIPage` 跟随 Lifecycle 的 START / STOP 发 K1 `__visible`（前后台切换、被其他页面覆盖都算）。页面只有挂载、卸载、可见性三个钩子 |
| 跨页状态 | 框架的 App 级内存 store（`TinyUIHost.store`） | 见第 3 节；登录会话见第 3.1 节 |

**TinyUI 页从宿主看只是一个 Composable**，导航栈里可以混放原生页与 TinyUI 页。v1 不内置路由器：库提供页面 Composable 与 `Navigator` 接口，宿主用自己的导航框架实现，JS 的 `navigation.*` 委托给它（sample 是标签页结构、没有返回栈，它的 `Navigator` 只打日志；带返回栈的写法见 native-api.md §3）。

## 3. 页面之间怎么通信

页面之间没有直接通道，全部经 Kotlin 中转，载荷只有 JSON。两个引擎本来就不能共享对象，这是设计而不是限制。

| 场景 | JS 侧 | 过桥 | 到达形态 |
|---|---|---|---|
| 向前传参 | `navigation.push("subscription/detail", {id})` | J4 | 新页 K1 `__mount(props)` |
| 向前并关掉自己 | `navigation.replace("subscription/done", {id})`；带 `{ since }` 时关掉本流程从第一页到本页的一段 | J4 | 新页 K1 `__mount(props)`，占原来的位置 |
| 向后回传 | `navigation.pop(result)`；带 `{ since }` 时整段退出 | J4 | 被关掉的那一段下面的页面收 K5 固定 topic（如 `navigation.result`） |
| 共享状态 | `store.get(key)` / `store.set(key, v)` | J2 读，J4 写 | 真值在 Kotlin，变更经 K5 推给订阅了该 key 的页面 |
| 事件广播 | `events.emit(topic, payload)` / `events.on(topic, fn)` | J4 | 所有订阅了该 topic 的活页面各收一次 K5 |
| 直接调用另一页 | 无 | 无 | 不支持；需要它说明那个状态该进 store |

- **向前传参**：props 是 JSON，函数不过桥。"完成后回调我"只能靠回传或事件
- **向后回传**：原语是 K5 topic，结果投给"处在那个栈位置的页面"而不是某个闭包。`push` 返回 Promise 的写法不做：结果要投给栈上那个位置的页面，不是某个闭包；页面要这种写法可以自己用 `onResult` 包一层
- **共享状态**：真值在框架持有的 `TinyUIHost.store`，全 App 一份，原生页与 TinyUI 页共用；内存 key-value 所以 J2 同步读合法；JS 侧包成 signal，K5 到达时更新 signal，绑定了它的 prop 自动重算，与页内响应式同一套。store 不落盘；包自己的持久数据用 `storage`（按包隔离，native-api.md §7）
- **事件广播**：与宿主事件同一条总线，网络状态、主题是 Kotlin 往总线发，业务事件是 JS 往总线发，消费方不区分来源。订阅登记到 Kotlin，只推给感兴趣的页面（每次 K5 = 一次线程切换 + 一个事务）；订阅随引擎关闭消失

### 3.1 登录会话与业务状态

- **登录态**是宿主的真值（token 在宿主的网络通道里），由框架定义标准会话 `{ loggedIn, userId }`：宿主在 `TinyUIHost` 上提供 `StateFlow<Session>` 与 `signIn`，框架推给所有活页面（native-api.md §9）。不用 store 的自定 key 表达登录态
- **业务状态**（是否 Pro、额度等）真值在服务端，页面经身份通道自己请求，在 `pageVisible()` 由 false 变 true 与 `session` 变化时刷新；不由宿主写进 store
- 宿主往 store 写东西即构成宿主依赖，只用于确实要与原生互通、又没有框架标准接口的状态

## 4. 投递语义

- **状态会留下，事件会错过**：store 是状态，什么时候读都在；事件是瞬时的，页面不在时发生的事件不补发。业务判断"错过了要不要紧"，要紧就写进 store
- **顺序只在单页内保证**：每个引擎串行，同一页收到的 K5 严格有序；跨页无顺序承诺

## 5. 页面随导航条目存活

2026-10-28 定（注册流程触发：从第二步返回第一步，填好的邮箱密码不能没了）。**页面实例跟着导航条目活，和原生页一样**：条目在栈里，引擎、节点树、组件里用户输入的东西都在；条目出栈才关。不让业务把表单草稿塞进 `store` 自救——那是业务替框架补课。

机制是 Compose 官方的 `retain`（`androidx.compose.runtime:runtime-retain`，就是为"在返回栈里、此刻没被组合的导航页"设计的）：

| 层 | 做什么 |
|---|---|
| `TinyUIPage` | `PageHost` 用 `retain` 持有；离开组合只算不可见（`pageVisible()` 为 false，`uriHandler` / 剪贴板摘掉，期间开链接、写剪贴板答 `E_UNSUPPORTED`），回来按生命周期恢复可见；保留它的存储放手时才 `close()` |
| 内置组件 | 用户做出来的状态 `retain`：`TextField` 文本、`CodeField` 数字、`Column` / `Row` / `LazyColumn` 滚动位置；焦点、展开中的下拉菜单 `remember`，回来重新开始 |
| `UpdatesPage` | 当前包与已加载的页面也 `retain`：回来的是用户离开时那一页、打开时那个包，期间装了新包也不换；第一帧就组合出被保留的页面（否则存储会当它没被取回而放手） |
| 宿主 | 给每个导航条目一个保留离开值的存储，条目出栈时清掉；不给就是 Compose 的缺省存储（离开组合即丢），行为与以前完全一样 |

宿主用 Navigation 3 时是一个 decorator（框架不依赖任何导航库，写法放文档，不进库）：

```kotlin
@Composable
fun <T : Any> rememberRetainedValuesNavEntryDecorator(): NavEntryDecorator<T> {
    val registry = retainRetainedValuesStoreRegistry()
    return remember(registry) {
        NavEntryDecorator(
            onPop = { key -> registry.clearChild(key) },   // 出栈：放手，页面 close()
            decorate = { entry -> registry.LocalRetainedValuesStoreProvider(entry.contentKey) { entry.Content() } },
        )
    }
}
```

被保留的页面**照常在后台运行**，和原生页留在返回栈里时它的 ViewModel 协程照跑一样：计时器、`http`、`host.call` 都不停，`pageVisible()` 为 false，页面据此自己决定停什么（轮询、动画）。挂载时交给 `host.call` 能力的 `locals` 随页面保留，所以它们应与导航条目同寿（条目级 ViewModel 之类），不要传组合级的东西。宿主的保留存储由 `tinyui-compose` 以 `api` 依赖带出，宿主不用另加。

sample 的四个 tab 各给一个存储（`App.kt`），切走再切回，表单内容与列表滚动位置都在。

## 6. 对构建链的影响

每页一份字节码正好是路由键到产物的一一映射，`tinyui build` 输出页面清单（`manifest.json`），宿主按页面名从包里取字节码。页面名到宿主导航条目的映射由宿主自己写；App 挂多个包时键已含包名，不会撞。见 [build-chain.md](./build-chain.md)。

## 7. 多包

App 由 N≥1 个包组成（[ADR-006](./adr-006-hot-updates.md) §2.10），包边界 = 所有权边界。包之间的关系与页之间的关系相同——没有直接通道、全部经 Kotlin 中转——只是多一条：

- **不做跨包共享代码**：共用工具代码各包各编一份（build-chain.md §3）
