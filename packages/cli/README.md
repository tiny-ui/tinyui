# tinyui-cli

[TinyUI](https://github.com/tiny-ui/tinyui) 的构建工具：`tinyui build` 把 `tinyui.config.json` 描述的包（`src/pages/**/*.tsx`）编成每页一个 ESM 模块字节码（含运行时模块与 `manifest.json`），`tinyui bundle` 把一次 build 签名成可上传 / 可静态托管的热下发目录，`tinyui keys generate` 生成签名密钥对，`tinyui schema` 从组件 schema 生成 TS 类型与 Kotlin 侧代码。

`tinyui publish` 先对照目标宿主版本的快照核对包用到的宿主能力与组件，再把 `bundle` 的产物传到热下发服务并切换 channel 指针；`tinyui apps` / `packages` / `tokens` / `releases` / `hosts` 是这套服务的管理面（建 app 与签发 app token、登记包的验签公钥、签发与吊销发布 token、列 release 与回滚 / 晋级 / 改灰度、宿主 CI 上传快照）。实例地址取 `--url`、`$TINYUI_UPDATES_URL`、`https://updates.tinyui.app` 三者之一；凭据只从环境变量读，不接受命令行参数：`$TINYUI_TOKEN` 是发布 token，`$TINYUI_ADMIN_TOKEN` 放实例的管理 token 或某个 app 的 app token。`--app` 可用 `$TINYUI_APP` 代替，`--pkg` 缺省取当前目录 `tinyui.config.json` 的 `name`，`--channel` 必须每次显式写。联网命令遵循 `HTTPS_PROXY` / `HTTP_PROXY` / `NO_PROXY`（大小写均可），与 curl、npm 一致；本机回环地址（`localhost`、`127.0.0.1`、`::1`）始终直连，代理变量无法解析时提示后直连、不影响离线命令；没设代理变量时不加载代理实现。

字节码编译用 quickjs-kmp 的宿主工具 `qjsc-kmp`：依赖里的 npm 包 `qjsc-kmp` 带 macOS 与 Linux 的预编译版，装完即用；其他平台用 `--qjsc` 或 `TINYUI_QJSC` 指向自己编的那份。`qjsc-kmp` 必须与 App 链接的 quickjs-kmp 同一上游 commit，编出的字节码才认 App 里的引擎；版本号可以不同（如 `qjsc-kmp` 0.1.2 与 quickjs-kmp 0.1.4，后者只改了 Android `.so` 的页面对齐）。用法见仓库 `docs/build-chain.md` 与 `docs/updates.md`；与 `tinyui-core` 同版本号。
