// `node packages/cli/scripts/release-notes.ts <tag>`: the hand-written part of a GitHub release (「升级须知」「要点」),
// drafted by a model from what changed since the previous stable tag, printed to stdout. publish.yml prepends it to
// GitHub's generated notes (`gh release create --generate-notes -F`), so the PR list stays the source of record and
// this only adds what a reader needs to upgrade. Any failure prints a warning to stderr and nothing to stdout: the
// release then goes out with the generated notes alone, never blocked by this.
import { execFileSync } from "node:child_process";

const MODEL = "gpt-5.4";
// what a dependent sees: the published ABI dumps (apiCheck keeps them current) and the published modules' dependencies
const DIFF_PATHS = ["compose/api", "updates/api", "gradle/libs.versions.toml", "compose/build.gradle.kts", "updates/build.gradle.kts"];
const MAX_DIFF = 40_000;

const git = (...args: string[]) => execFileSync("git", args, { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 }).trim();

function warn(message: string): never {
    process.stderr.write(`⚠️ release notes skipped: ${message}\n`);
    process.exit(0);
}

const tag = process.argv[2];
if (!tag) warn("usage: release-notes.ts <tag>");
const apiKey = process.env.OPENAI_API_KEY;
if (!apiKey) warn("OPENAI_API_KEY not set");

// the base is the previous stable tag (the trailing $ excludes pre-releases), so a 1.2.0-beta.2 and its 1.2.0 both
// cover everything since 1.1.x
const base = git("tag", "--merged", tag, "--sort=-v:refname").split("\n").find((t) => /^\d+\.\d+\.\d+$/.test(t) && t !== tag);
if (!base) warn("no previous stable tag");
const range = `${base}..${tag}`;
const commits = git("log", range, "--no-merges", "--format=- %s%n%b");
if (!commits) warn(`no commits in ${range}`);
let diff = git("diff", range, "--", ...DIFF_PATHS);
if (diff.length > MAX_DIFF) diff = `${diff.slice(0, MAX_DIFF)}\n…(truncated)`;

const prompt = `你在为开源库 TinyUI 的 GitHub Release ${tag}（上一版 ${base}）撰写发版说明的人工部分。读者是接入方开发者。
TinyUI 发布 Maven 库 app.tinyui:tinyui、app.tinyui:tinyui-updates（Kotlin Multiplatform）和 npm 包 tinyui-core、tinyui-native、tinyui-cli。
GitHub 会在你的内容之后自动附上 PR 列表，所以不要复述 PR 列表，也不要写版本号标题。

输出 Markdown，只包含以下两节，用中文，中英文之间加空格：

## 升级须知
只写接入方升级时必须动手或必须知道的：二进制 / 源码不兼容（以 .klib.api 差异为准，删除、改签名、新增参数或字段都算二进制不兼容）、新增的传递依赖、推荐的迁移及文档位置。通常 0-3 条，没有则整节省略。

## 要点
每个 PR 至多一条，一句话说清做了什么，结尾标 PR 号如（#40）。忽略纯内部改动（CI、构建、版本号、测试、文档、工具链对齐）。通常 2-5 条。

要求：
- 精炼，每条一句、最多两句；关键 API 名用反引号，不要「影响人群」「怎么办」之类的小标题，不要展开实现细节
- 只依据下面给出的材料，不要臆造；不要寒暄，不要输出这两节以外的任何内容

风格参照（0.12.0 的人工版本）：
## 升级须知

- **\`tinyui-updates\` 二进制不兼容**：\`Updates.check()\` 新增了 \`trigger\` 参数，\`UpdateEvent.UpToDate\` / \`Skipped\` / \`Installed\` / \`Failed\` 新增 \`trigger\` 字段。源码兼容，依赖方重新编译即可；但基于 0.11.x 编译好的中间库不能直接换上 0.12.0
- **Android 新增依赖** \`androidx.lifecycle:lifecycle-process:2.9.4\`（供 \`UpdatesScheduler.attachToAppLifecycle()\` 使用）
- 推荐接入方把自己写的启动检查换成 \`UpdatesScheduler\`，见 \`docs/updates.md\` §4.6

## 要点

- \`Running\` 在校验 installed 包的字节之前发出，宿主挂的「本进程跑哪个版本」遥测属性能赶上启动后的第一条事件（#38）
- 新增 \`UpdatesScheduler\`：启动时检查，网络失败后重试一次，回到前台按间隔（默认 1 小时）检查；检查结果事件带 \`trigger\`（#40）

## Commits
${commits}

## 公共 API 与依赖的差异
${diff ? "```diff\n" + diff + "\n```" : "（无变化）"}
`;

try {
    const response = await fetch("https://api.openai.com/v1/chat/completions", {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` },
        body: JSON.stringify({ model: MODEL, messages: [{ role: "user", content: prompt }] }),
        signal: AbortSignal.timeout(120_000),
    });
    if (!response.ok) warn(`HTTP ${response.status} ${await response.text()}`);
    const content: string = (await response.json()).choices?.[0]?.message?.content?.trim() ?? "";
    if (!content) warn("empty response");
    process.stderr.write(`✅ release notes drafted by ${MODEL} from ${range}\n`);
    process.stdout.write(`${content}\n`);
} catch (error) {
    warn(String(error));
}
