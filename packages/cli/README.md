# tinyui-cli

[TinyUI](https://github.com/tiny-ui/tinyui) 的构建工具：`tinyui build` 把 `tinyui.config.json` 描述的包（`src/pages/**/*.tsx`）编成每页一个 ESM 模块字节码（含运行时模块与 `manifest.json`），`tinyui bundle` 把一次 build 签名成可上传 / 可静态托管的热下发目录，`tinyui keys generate` 生成签名密钥对，`tinyui schema` 从组件 schema 生成 TS 类型与 Kotlin 侧代码。

`tinyui publish` 把 `bundle` 的产物传到热下发服务并切换 channel 指针，`tinyui apps` / `packages` / `tokens` / `releases` 是这套服务的管理面（建 app、登记包的验签公钥、签发与吊销发布 token、列 release 与回滚 / 晋级 / 改灰度）。实例地址取 `--url`、`$TINYUI_UPDATES_URL`、`https://updates.tinyui.app` 三者之一；凭据只从环境变量读，不接受命令行参数：`$TINYUI_TOKEN` 是发布 token，`$TINYUI_ADMIN_TOKEN` 是实例管理 token。`--app` 可用 `$TINYUI_APP` 代替，`--pkg` 缺省取当前目录 `tinyui.config.json` 的 `name`，`--channel` 必须每次显式写。

字节码编译需要 quickjs-kmp 的宿主工具 `qjsc-kmp`（`--qjsc` 或 `TINYUI_QJSC`）。用法见仓库 `docs/build-chain.md` 与 `docs/updates.md`；与 `tinyui-core` 同版本号。
