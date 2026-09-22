# tinyui-cli

[TinyUI](https://github.com/tiny-ui/tinyui) 的构建工具：`tinyui build` 把 `tinyui.config.json` 描述的包（`src/pages/**/*.tsx`）编成每页一个 ESM 模块字节码（含运行时模块与 `manifest.json`），`tinyui bundle` 把一次 build 签名成可上传 / 可静态托管的热下发目录，`tinyui keys generate` 生成签名密钥对，`tinyui schema` 从组件 schema 生成 TS 类型与 Kotlin 侧代码。

字节码编译需要 quickjs-kmp 的宿主工具 `qjsc-kmp`（`--qjsc` 或 `TINYUI_QJSC`）。用法见仓库 `docs/build-chain.md` 与 `docs/updates.md`；与 `tinyui-core` 同版本号。
