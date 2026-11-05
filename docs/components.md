# 内置组件：schema、布局 prop、首批清单

- 状态：已定（2026-09-16）；M2 的实现依据；2026-09-25 按 [ADR-007](./adr-007-host-boundary.md) 加 `Icon`、`Loading` 与公共 prop `role` / `selected`；2026-10-28 为注册流程加 `Checkbox`、`CodeField`、`Select` / `Option`、`TextField` 外观与焦点、`Text` 内联链接；2026-11-03 为登录流程加 `Tabs`；2026-11-05 为登录流程加 `IndexBar` 与 `LazyColumn` 的 `jumpTo`
- 来源：ADR-003 §3.3 / §3.4（注册表、schema、公共布局 prop）、ADR-004（事件三分、命令、文本框）；roadmap C 组
- 相关：[runtime-api.md](./runtime-api.md) §3（`h()` 怎么处理 prop）、[patch-protocol.md](./patch-protocol.md) §4（值类型）

## 1. schema 是唯一真值

组件的 prop / 事件 / 命令声明只写一处：顶层 `schema/` 目录，TS 写的声明式 DSL，一个组件一个文件，`schema/index.ts` 汇总。`pnpm schema`（即 `tinyui schema`）执行它，生成两份产物并提交进仓，CI 用 `pnpm schema:check` 保证没有手改：

| 产物 | 内容 | 谁用 |
|---|---|---|
| `packages/core/src/generated/components.ts` | `XxxProps`（JSX 属性类型）、`XxxYyyPayload`（事件 payload）、`XxxCommands`（命令参数表，配 `ref<XxxCommands>()`）、`IntrinsicElements`、组件名常量 | 业务的类型检查 |
| `compose/.../components/generated/BuiltinSchemas.kt` | 每个组件一个 `ComponentSchema`，prop 的类型 / 默认值 / `required` / `initial`，事件与命令的字段表 | Kotlin 注册表：写入时转换、E5 校验、清单下发 |

Kotlin 侧的 composable 仍手写，`register(BuiltinSchemas.Text) { scope -> … }`。宿主扩展组件（`pp.KycCard`）用同一套 DSL（`import … from "tinyui-cli/schema"`）生成到宿主自己的包里，本仓不含。

DSL：

```ts
defineComponent("TextField", {
    props: {
        initialText: string({ initial: true, default: "" }),
        keyboard: enumOf(["text", "number", "email", "phone", "password"], { default: "text" }),
    },
    events: { onChange: { text: field.string() } },
    commands: { setText: { text: field.string() }, focus: {} },
    children: false,   // 是否接受 children
    layout: true,      // 是否接受公共布局 prop，默认 true；false 即不占父容器版面（Menu / Popover / BottomSheet / Option / BackHandler），Row / Column 的 gap 不计它
});
```

prop 类型：`string` / `number` / `boolean` / `dp` / `sp` / `color`（`#RRGGBB` / `#AARRGGBB`，或 §6 的主题 token 名）/ `enumOf([...])` / `size`（数字 dp，或 `"fill"` / `"wrap"`）/ `icon`（`"<viewBox>|<d>"` 字符串，§3）。选项：`required`、`default`、`initial`（只认创建时的值，之后的写入按 E5 跳过并上报）、`doc`。事件 payload 与命令参数的字段只有 `field.string / number / boolean`，扁平。

## 2. 公共布局 prop 与 Modifier 顺序

`layout: true` 的组件都接受这些，运行时按下面的顺序合成 `Modifier`，从外到内：

| 顺序 | prop | 类型 | Modifier |
|---|---|---|---|
| 0 | `weight` | number | `weight(f)`，由父 `Row` / `Column` 施加（2026-09-17 加） |
| 1 | `width` / `height` | size | `width(dp)` / `fillMaxWidth()` / `wrapContentWidth()` |
| 1.5 | `invisible` | boolean | `alpha(0)` + 裁剪到空形状（命中测试只在裁剪区内命中，整棵子树因此不被命中、触摸穿过去）+ 清空子树语义（2026-11-02 加） |
| 2 | `cornerRadius` | dp | `clip(RoundedCornerShape)` |
| 3 | `background` | color | `background(color)` |
| 3.5 | `borderWidth` / `borderColor` | dp / color | `border(width, color, 同一个 shape)`；宽 0 即无边框（Compose 的 0.dp 是一像素 hairline，这里不沿用）；色缺省 `outline` token（2026-09-17 加） |
| 4 | （有 `onClick` handler 时）`role` / `selected` | enum / boolean | 设了 `selected` 为 `selectable(selected, role)`，否则 `clickable(role)`，都 `dispatch("onClick")`（2026-09-25 加 `role` / `selected`） |
| 5 | `padding` / `paddingHorizontal` / `paddingVertical` | dp | 一个 `padding(start, top, end, bottom)`：轴向值覆盖该轴，缺的轴用 `padding`（React Native 式级联；2026-09-18 加轴向） |

`role` 取 `button` / `checkbox` / `switch` / `radio` / `tab`，对应 Compose 的 `Role`，只给读屏用；`selected` 让容器成为一个可选中节点，读屏报"已选中 / 未选中"。可点击容器会合并子节点的语义，所以放在它里面的 `RadioButton` 不要再给 `onClick`（给了就是第二个焦点）：

```tsx
<Row role="radio" selected={p.selected} onClick={p.onClick}>
    <RadioButton selected={p.selected} />
    <Text text={p.title} />
</Row>
```

`invisible` 是 CSS `visibility: hidden`、Android `View.INVISIBLE`、Flutter `Visibility(maintainSize)`、SwiftUI `.hidden()` 的那个状态（2026-11-02 加，注册流程触发）：**占位但不画、对触摸透明、不进无障碍树**。「对触摸透明」和这些平台一致：隐形节点自己和它的子树都不被命中，触摸**穿过去**交给周围或后面的东西——按钮里叠一份隐形文案占位，按钮本身照样能点；它不是一块挡板。它在 clip / background 之外，所以背景与边框一起消失；节点自己的 `onClick` 不挂。「不占空间」不需要它：条件不渲染即可。不叫 `hidden`——HTML 的 `hidden` 属性等于 `display: none`，和 CSS `visibility: hidden` 正好相反，这个词在 Web 里本身有歧义；`invisible` 在 Android 与 Tailwind 里都明确指保留空间。典型用法是让会变的文案保持最宽状态的宽度，免得切换时整行跳动：

```tsx
<Button variant="outlined" enabled={seconds() === 0} onClick={resend}>
    <Box>
        <Text text={t("resend")} invisible />        {/* 最宽的一种文案占位 */}
        <Text text={seconds() > 0 ? `${seconds()}s` : t("resend")} />
    </Box>
</Button>
```

顺序固定：以后加 prop 只能插进这个序列，不能重排（重排会改变已有页面的视觉）。`padding` 在最里面，所以它是内容内边距；背景、边框和点击区域包含它。

`weight` 需要父作用域：`Row` / `Column` 渲染 children 时按每个 child 的 `weight` 造 `Modifier.weight` 经 CompositionLocal 交给它，child 的 `modifier()` 把它放在最外层；其他容器把这个 local 重置为空，所以写在 `Box` / `LazyColumn` 的直接 child 上不生效、也不报错；`scroll` 开着的 `Column` / `Row` 主轴无界、没有剩余空间可分，其 child 的 `weight` 同样忽略（否则 Compose 会把它压成 0）。

不在首批的：`margin`（Compose 没有对应，用父容器的 `gap` / `padding`）、`alignSelf`（等需求）、单边 `paddingTop` 等（等需求；加时进同一槽位、同一级联，不引入数组形态的 `padding`）。

## 3. 内置组件

`onClick` 所有容器和 `Text` 都有。事件的 payload 字段、命令的参数字段见 `schema/components/*.ts`，这里只列名字。

| 组件 | prop | 事件 | 命令 | children |
|---|---|---|---|---|
| `Column` / `Row` | `gap`（只隔开占版面的子节点，schema `layout: false` 的 `Menu` / `Popover` / `BottomSheet` / `Option` / `BackHandler` 不计）、`align`（交叉轴 start / center / end / stretch）、`justify`（主轴 start / center / end / spaceBetween）、`scroll`（沿主轴滚动；`padding` 在滚动内容之外，不随内容滚） | `onClick` | | 是 |
| `Box` | `align`（九宫格） | `onClick` | | 是 |
| `Text` | `text`（必填）、`style`（M3 文字样式名，§6）、`color`、`fontSize`、`fontWeight`（normal / medium / bold；设了就覆盖 `style` 的对应字段，不设则跟 `style`）、`maxLines`（0 = 不限，超出省略号）、`align` | `onClick`、`onLinkClick{id}`（给了它，`text` 里的 `[文案](id)` 渲染成链接；`**文案**` 渲染成粗体，不需要事件） | | |
| `Button` | `text`、`enabled`、`variant`（filled / outlined / text） | `onClick` | | 可选：有 children 就当标签渲染、忽略 `text`（按钮内放加载指示或图标；2026-09-17 加） |
| `RadioButton` | `selected`（必填）、`enabled` | `onClick` | | |
| `Checkbox` | `checked`（必填）、`enabled` | `onClick` | | |
| `TextField` | `initialText`（initial）、`placeholder`、`singleLine`、`keyboard`、`secure`（缺省跟 `keyboard="password"`）、`error`、`supportingText`、`prefixText`、`prefixIcon`（`icon` 类型）、`trailingIconSize`（dp，同 `prefixIconSize`）、`prefixIconSize`（dp，不设用图标自带的 24dp）、`prefixBackground`（颜色）、`trailingIcon`（`icon` 类型）、`imeAction`（done / next）、`maxLength`（0 = 不限）、`autofill`（oneTimeCode；不设交给系统） | `onChange{text}`、`onCommit{text}`（IME 动作或失焦）、`onFocusChange{focused}`、`onTrailingClick`、`onPrefixClick` | `setText{text}`、`focus`、`blur` | |
| `CodeField` | `length`（1–12，默认 6）、`secure`、`enabled`、`error`、`autofill`（none / oneTimeCode） | `onChange{text}`、`onComplete{text}`（填满最后一格） | `setText{text}`、`focus`、`clear` | |
| `Select` | `value`、`placeholder`、`label`、`enabled` | `onSelect{value}` | | 是：`Option` |
| `Option` | `value`（必填）、`text`（必填）；不接受布局 prop；`Select`、`Menu`、`Tabs` 与 `IndexBar` 读它 | | | |
| `LazyColumn` | `gap` | `onReachEnd`、`onScrollEnd{index}` | `scrollTo{index}`（带动画）、`jumpTo{index}`（一步到位） | 是，通常是一个 `<For>` |
| `Spacer` | （只有布局 prop） | | | |
| `Icon` | `icon`（必填，`icon` 类型）、`size`（dp，默认 24）、`tint`（color，缺省跟随内容色）、`label`（读屏念的名字；紧挨文字的装饰图标不设） | `onClick` | | |
| `Loading` | `size`（dp，默认 24）、`color`（缺省 `primary`） | | | |
| `BottomSheet` | `open`（必填）；不接受布局 prop | `onDismiss` | | 是：弹层的内容 |
| `Popover` | `open`（必填）、`placement`（above / below，默认 below）、`matchAnchorWidth`；不接受布局 prop | `onDismiss`（点了弹层外面） | | 是：卡片的内容 |
| `TopAppBar` | `title`、`backIcon`（`icon` 类型，设了才有返回按钮）、`backLabel`（返回按钮的读屏名）、`backIconSize`（dp，返回图标的尺寸；不设用图标自带的 24dp） | | | 是：右侧的操作 |
| `BackHandler` | `enabled`（默认 true）；不接受布局 prop | `onBack` | | |
| `Menu` | `open`（必填）；不接受布局 prop | `onSelect{value}`、`onDismiss`（点了菜单外面） | | 是：`Option` |
| `Tabs` | `value`（选中的 `Option` 的 value；不设或对不上就一个都不选） | `onSelect{value}` | | 是：`Option` |
| `IndexBar` | `value`（高亮的 `Option` 的 value；不设或对不上就都不亮） | `onSelect{value}`（手指按下时一次，之后每移到另一个标签一次） | | 是：`Option` |
| `Divider` | `thickness`（dp，默认 1）、`color`（默认 `outlineVariant`） | | | |

`align="stretch"`（2026-11-02 加，注册流程触发：验证码输入框与「重新发送」等高）与 CSS `align-items: stretch`、Flutter `CrossAxisAlignment.stretch` 同义：占版面、且没设交叉轴尺寸的子节点撑满容器的交叉轴（Row 撑高、Column 撑宽）；设了 `height` / `width` 的子节点（含 `wrap`）保持自己的，和 CSS 一样。**一个 M3 的边角**（2026-11-03）：stretch 靠 intrinsic 测量问子节点「最矮多矮」，M3 `OutlinedTextField` 内部的 `defaultMinSize(56dp)` 在 intrinsic 里无条件答 56，宿主经 `TinyUIDefaults.textField.minHeight` 给的更矮高度盖不过它（正常测量盖得过）；所以 `TextField` 所在的行不要用 stretch 对齐高度，旁边的节点直接给和输入框一样的 `height`。容器的交叉轴是固定值或 `fill` 时只是撑满；否则容器先按子节点定自己的尺寸——Row 取最高的子节点（Compose `IntrinsicSize.Min`），Column 取最宽的（`IntrinsicSize.Max`）——这要对整棵子树做固有尺寸测量，多测一遍，所以缺省仍是 `start`，不像 CSS 那样默认拉伸。回答不了固有尺寸的组件（schema `intrinsics: false`，内置只有 `LazyColumn`；宿主组件基于 `SubcomposeLayout` 的也要这样声明）在子树里时，问它会让整页崩溃，所以框架放弃拉伸、按 E5 上报（`NodeScope.reportProblem`），页面仍画出来；给容器一个固定交叉轴尺寸就不需要测量。宽高都固定的节点由尺寸直接作答、内容不会被问，所以放在这样的盒子里的 `LazyColumn` 不算（只固定一个轴不够：嵌套的 Row 分配空间时会问子节点的固有宽度）。子树是逐层读的，任何一层后来插进这样的组件，容器都会重新检查。

`RadioButton` 的选中态是普通 prop（2026-09-17 加）：它只报点击，选中哪个由 JS 决定——单选组的真值本来就在页面状态里，不属于 ADR-004 的"高频交互状态"。`TextField` 的文本与光标永远在 Kotlin 侧（ADR-004 §3.3）：`onChange` 报的是文本的每一次变化，不问是谁改的——用户敲的、`setText` 设的、`maxLength` 调低裁掉的都报，同样的文本再设一次不报（2026-11-03 改；此前 `setText` 无条件报一次，`CodeField.setText` 仍是无条件）。顺序有保证：`onCommit` 之前一定先报完 `onChange`，`setText` 当场报；用户连续敲的字经 state 观察送达，同一次调度里的多次变化只报最后一次——`onChange` 说的是「现在是什么」，不是每一次按键。`LazyColumn` 的行就是它的 children，虚拟化只在组合层（ADR-003 §3.6）。列表拿的是组合时那一份行，不是活的 children：惰性列表每次测量先取行数、再按下标逐行要，测量不在快照里，引擎线程此时应用下一批 patch（搜索框一边输入一边筛掉大半行）会让下标越界；删掉的行最多多画一帧，随后的重组换成新的一份。

`Text` 的行内标记（2026-11-03 加 `**粗体**`，注册流程触发：协议句里的链接半粗、激活提示里的邮箱与分钟数加粗）：`[文案](id)` 链接与 `**文案**` 粗体，就是 SwiftUI `Text` 读的那个 Markdown 子集，扁平、不嵌套，落单的标记按字面显示。选字符串内标记而不是拆成多个节点，是为了翻译：译者拿整句，加粗位置随语序走。渲染走 Compose 的 `AnnotatedString`：粗体是 `SpanStyle(fontWeight)`，链接是 `LinkAnnotation` + `TextLinkStyles`。链接长什么样归宿主（§6 `TinyUIDefaults.textLinks`），页面只写语义；文案里字面量 `**` 目前不支持转义，需要时再加。

`Checkbox` 与 `RadioButton` 同一模式（2026-10-28 加）：只报点击，勾没勾是页面状态。勾选框的颜色与边线粗细归宿主（§6 `TinyUIDefaults.checkbox`），尺寸是 M3 定的。勾选框带一句文案时，把两者包进 `<Row role="checkbox" selected={…} onClick={…}>`，整行一个读屏节点，`Checkbox` 本身不给 `onClick`。

`TextField` 的外观与通知（2026-10-28 加，注册流程触发）：`error` 把描边、`supportingText` 染成错误色，`supportingText` 是字段下方一行提示或错误文案；`secure` 不设时跟 `keyboard="password"`，显隐切换就是页面翻转它，配 `trailingIcon` + `onTrailingClick`（设了 `secure` 或 `keyboard="password"` 的字段是 M3 的 secure 输入框 `OutlinedSecureTextField`，翻转只换遮罩方式，焦点与键盘不丢；遮罩按 M3 默认，刚输入的那个字短暂可见，与各平台自带的密码框一致；M3 的 secure 输入框只有单行，这类字段上 `singleLine=false` 无效；2026-11-03 改，此前是 `PasswordVisualTransformation` 全遮）（清除按钮同理：`trailingIcon` + `setText("")`，不另加组件）。`prefixText` 是输入前的固定文字（区号），画在 leading 槽而不是 M3 `prefix`——后者在空且未聚焦时隐藏、也不能点；`onPrefixClick` 让它可点（打开区号选择）。前缀的文字与 `prefixIcon`（如下拉箭头，跟在文字后 4dp）用 M3 的 prefix 颜色（`TextFieldColors.prefixColor`，宿主可经 `TinyUIDefaults.textField.colors` 改），虽然借的是 leading 槽，语义上仍是前缀。`trailingIconSize`（2026-11-03 加，注册流程触发：设计稿密码框的眼睛是 20dp，打包图标是 24）同 `prefixIconSize`：只缩字形，M3 尾槽与 48dp 触控区不变，未设用图标自身尺寸——槽里放多大的图标是页面的事，M3 只管槽外。`prefixBackground`（2026-11-02 加，注册流程触发）把前缀画成一块填充：距边框起始侧 4dp，高度取输入框最小高度减上下各 4dp（M3 的 56dp 即 48dp；宿主经 `TinyUIDefaults.textField.minHeight` 改矮时跟着变）、在框内垂直居中，左右内边距 12dp，到输入文字 12dp（M3 在 leading 槽与输入之间已留 4dp，块右侧补 8dp）；圆角与输入框同心，即输入框圆角减 4dp，跟着主题的 `shapes.extraSmall` 走。整块是点击区。`imeAction="next"` 时键盘动作先 `onCommit` 再把焦点移到下一个可聚焦项。`maxLength` 超长截断而不是拒绝。`autofill` 告诉系统这一栏装的是什么、好给出建议（2026-11-02 加，注册流程触发：设计稿的验证码是一个输入框）：`oneTimeCode` 让系统建议刚收到的短信验证码，平台实现同 `CodeField`（iOS 走 `PlatformImeOptions` 的内容类型，Android 走 semantics 的 `SmsOtpCode`）。这是各平台通用文本框的同一类属性（Web `autocomplete`、iOS `textContentType`、Android `autofillHints`、Flutter `autofillHints`），取值按需再加（如 `email`、`newPassword`），名字对齐 Web / iOS 的词。不设时交给系统的启发式，所以 `TextField` 没有 `CodeField` 那样的 `none`：`CodeField` 用 `NumberPassword` 键盘，iOS 不设内容类型会当密码框弹钥匙串，要显式清掉；普通文本框没有这个问题，而各平台的「关闭自动填充」本来也不可靠。验证码用一个 `TextField`（`keyboard="number"` + `maxLength` + `autofill="oneTimeCode"`）还是 `CodeField` 的格子，按设计定；单框对粘贴、自动填充与读屏都更友好。`onFocusChange` 只在焦点真的变化时发，首次组合的"未聚焦"不算，所以 `onCommit` 也不会在首帧被误发（此前会）。文本仍归 Kotlin（ADR-004），这些都只是外观与通知。实现是 M3 `TextFieldState` 版的 `OutlinedTextField`（2026-11-03 起）：文本、光标、组合区都在 state 里，页面重载或所在弹层重组都不会把光标打回 0；高度不归页面，归宿主（§6 `TinyUIDefaults.textField`）。

`CodeField` 是一排数字格画在一个看不见的输入框之上（2026-10-28 加）。叫 Code 而不是 Pin / Otp：短信验证码与交易密码是同一个组件，`secure` 与 `autofill` 区分两者，名字不偏向任何一种。Compose 与 M3 都没有对应组件，它是框架自绘的（`BasicTextField` + 自画格子），格子尺寸由框架定、颜色跟主题。焦点与 IME 归 Kotlin，只接受数字、到 `length` 为止；不能用多个 `TextField` 拼，焦点逐格跳要跨桥往返（ADR-004 的反例）。`autofill="oneTimeCode"` 让系统把刚收到的短信验证码作为建议（Android 自动填充 `SmsOtpCode`、iOS 键盘上方「来自信息」）；缺省 `none` 时明确告诉系统什么都别建议——iOS 不设内容类型会按 `NumberPassword` 键盘把它当密码框、弹钥匙串，交易密码不该这样。iOS 只从 `PlatformImeOptions` 读内容类型、不看 semantics，所以两端各走各的路，平台差异在框架里消化。`onComplete` 每次填满最后一格时发，`clear` / `setText` 与 `TextField.setText` 一样会触发 `onChange`——所以"输错 → 清空并标红"时，清错误态要放在有输入的 `onChange` 里，否则 `clear` 带来的空 `onChange` 会立刻把红色抹掉：

```tsx
const pin = ref<CodeFieldCommands>();
<CodeField secure error={wrong()} ref={pin}
    onChange={(e) => { if (e.text) setWrong(false) }}
    onComplete={(e) => verify(e.text).catch(() => { setWrong(true); pin.cmd("clear", {}) })} />
```

`Select` 是 M3 `ExposedDropdownMenuBox`（2026-10-28 加）。选项不能做成数组 prop（prop 只过标量，patch-protocol.md §4），所以是 `Option` children：`Select` 像 `Row` 读 child 的 `weight` 一样读 `Option` 的 `value` / `text`，`Option` 自己什么都不画。选中值叫 `value` 而不是 `selected`，因为后者是公共布局 prop（可选中容器）。选中态归页面：`onSelect` 报选了哪个，页面把它写回 `value`。

`Text` 内联链接（2026-10-28 加）：给了 `onLinkClick`，`text` 里的 `[文案](id)` 渲染成 primary 色链接，点击报 `{id}`；没给时按字面显示。协议句"我同意 [隐私政策](privacy) 及 [服务协议](terms)"要行内折行，`Row` 拼几个 `Text` 做不到；markdown 链接子集是最小够用的标记，i18n 文案里直接写。只认 `[` `]` `(` `)` 紧挨、id 不含空白的形式，其余方括号原样显示；不支持嵌套与转义。

`Icon` 不内置任何图标，图标数据随包（2026-09-25，ADR-007 §3.7）。一个图标是一个字符串：填充图标 `"<viewBox>|<d>"`，描边图标 `"<viewBox>|<d>|<线宽>"`（圆头圆角），加粗图标 `"<viewBox>|<d>|+<线宽>"`（先填充再用同一颜色沿轮廓描一道，形状每边长出半个线宽；设计系统把一份几何按描边分「常规 / 加粗」两档时用它，线宽是 viewBox 单位的常数，与渲染尺寸无关，2026-11-02 加）。`viewBox` 是四个数（`0 -960 960 960`），`d` 是 SVG path 数据，多个形状合并成一个 `d`，按 `tint` 着色；用字符串是因为 prop 不过桥对象（patch-protocol.md §4）。schema 里是 prop 类型 `icon()`，Kotlin 写入时解析成 `ImageVector` 并按字符串缓存，格式不对按 E5 跳过。字符串由 `tinyui build` 的 `.svg` 加载器从 SVG 文件生成（build-chain.md §9），页面直接 import SVG：Material Symbols 用 npm 包 `@material-symbols/svg-400`，品牌图标放一个 `.svg` 进工程；类型声明见 `tinyui-core/assets`。多色图标与位图归 `Image`。

```tsx
import bolt from "@material-symbols/svg-400/outlined/bolt.svg";
import checkCircle from "@material-symbols/svg-400/outlined/check_circle.svg";
const ICONS: Record<string, string> = { quota: bolt };
<Icon icon={ICONS[key] ?? checkCircle} tint="primary" />
```

`Loading` 是 M3 Expressive 的 `LoadingIndicator`，不定进度；有进度的进度条等需求。`LoadingIndicator` 只在 CMP material3 的 alpha 线上有，库因此依赖与 CMP 同代的 material3 alpha（catalog `composeMaterial3`）。

`BottomSheet` 是 M3 `ModalBottomSheet`，浮在页面之上，不占父容器的布局（2026-10-12 加，第一个需要的业务页面触发）。`open` 由页面掌握：变 true 滑入，变 false 播完收起动画再离开组合；用户下滑、点遮罩或按返回时，弹层已经自己收起，再报 `onDismiss`，页面在里面把 `open` 置回 false——不置的话它保持收起，直到 `open` 先 false 再 true。只有展开一档（内容多高就多高，不停在半屏）。内容的布局（内边距、标题、关闭按钮）由页面用 children 自己写。命令式的 `ui.alert` / `ui.confirm` 仍是纯文本对话框；声明式 `Dialog` 继续推迟。

```tsx
const [open, setOpen] = signal(false);
<Button text="查看收款账户" onClick={() => setOpen(true)} />
<BottomSheet open={open()} onDismiss={() => setOpen(false)}>
    <Column paddingHorizontal={24} paddingVertical={16} gap={12}>…</Column>
</BottomSheet>
```

`Popover` 是锚在父节点上的浮动卡片（2026-10-29 加，注册流程触发：密码框聚焦时在它上方浮出规则清单，盖住上面的内容而不推开布局）。锚点就是父节点，不另设 ref：Compose `Popup` 本来就锚在调用处的父布局上，内置组件渲染 children 也不加包装。所以写法是用 `Box` 把锚元素和它包在一起；直接放进带 `gap` 的 `Column` 会锚到整列。它在组合里占一个零尺寸的位置，`Column` / `Row` 的 `gap` 不计它（2026-11-02 起；此前打开时会多出一份 `gap`，整行闪一下）。`placement` 是首选的一侧，那一侧放不下就翻到另一侧，两侧都放不下取空间大的一侧；水平方向与锚点起始边对齐，两个方向都不出窗口，页面滚动时跟着锚点走。`matchAnchorWidth` 让卡片与锚点同宽（输入框下的说明），不设就按内容宽（图标按钮旁的提示）。

它**永不获取焦点**：锚在聚焦输入框上的提示不能把键盘收走。显隐完全跟 `open`（淡入淡出）；点卡片外面只报 `onDismiss`、不自己收起，因为用户刚点的那个输入框也算"外面"——要不要关由页面决定，密码规则这种场景只跟焦点走、不监听它。外观归框架：`surface` 底、`onSurface` 字（显式给出，不靠 `contentColorFor` 从容器色推）、medium 圆角、3dp 阴影；内边距与内容由页面用 children 写。不做：箭头、背景色 prop、左右两侧弹出。

```tsx
const [focused, setFocused] = signal(false);
<Box width="fill">
    <TextField width="fill" keyboard="password" onFocusChange={(e) => setFocused(e.focused)} … />
    <Popover open={focused()} placement="above" matchAnchorWidth>
        <Column width="fill" padding={16} gap={8}>…</Column>
    </Popover>
</Box>
```

叫 `Popover` 而不叫 `Tooltip`：M3 里最接近的是 rich tooltip，但 M3 的 tooltip 由框架在长按、悬停时自己触发，偏短文字，宽度上限 320dp；这里是页面掌握开关、内容不限的锚定浮层，与 Web（Radix、MUI 的 `Popover` / `Tooltip` 并存）和 iOS 的 popover 同义。`Tooltip` 这个名字留给将来按 M3 语义实现的那个（图标按钮长按出一句说明）。

`TopAppBar` 是 M3 小顶栏（2026-10-29 加，注册流程触发：每一步的返回、帮助入口与语言切换不一样，放在页面里才能随页面热下发）。children 进右侧操作槽，用现有组件拼（文字按钮、`Row` + `Icon` + `Text` 的语言触发器）；`title` 可选，标题写在内容区大字里的页面不设。它自己让出状态栏：状态栏的内边距在公共 modifier 之外，所以 `height` 是栏本身的高度（缺省 M3 的 64dp），宿主已经消费过的 inset（如 `Scaffold` 的 innerPadding 配 `consumeWindowInsets`）不会再加一遍。底色缺省 `surface`，`background` 同时染状态栏那一段。

返回按钮只有设了 `backIcon` 才出现（流程第一页不设），图标照常由页面从包里引入。`backIconSize` 只改图标的尺寸（2026-11-02 加，注册流程触发：设计系统的返回图标是 32dp），按钮仍是 M3 的 48dp 触控区；不设时用图标自带的尺寸，包里的图标统一是 24dp（`parseIcon` 一处定义），所以顶栏不另写默认值。以后其他组件里的图标要调尺寸，同样叫「`<图标 prop>Size`」，如 `TextField` 的 `trailingIconSize`。

这组 prop 叫 `back*` 而不叫 M3 / Compose 插槽名 `navigationIcon`：Compose 的 `navigationIcon`（以及 Android `Toolbar` 的 `navigationIcon`）是放任意内容、点击自己写的插槽，而这里的按钮没有自己的事件，固定是一次系统返回，语义同 iOS 的返回按钮、Flutter 的 `BackButton`。换成 ✕ 图标关闭流程页也还是返回。将来左侧真要放别的（如抽屉菜单），另开 `leading` 插槽、用 children 写点击，与 `back*` 不冲突。

它**没有自己的事件**：点它就是往系统返回的 dispatcher 注入一次返回，同 Android 工具栏调 `onBackPressedDispatcher.onBackPressed()`——启用着的 `BackHandler` 先收到，没有就到宿主的导航（Navigation 3 弹出栈顶；宿主没接返回时 Android 结束 Activity，与按返回键一样）。要改返回行为只在 `BackHandler` 一处写，顶栏与系统返回是同一条路，优先级只有 dispatcher 那一份。宿主没提供 dispatcher 时没有系统返回，按钮也不起作用，与 `BackHandler` 一致；这种宿主（不是 `ComponentActivity` 的 Android 宿主、测试）要么补上 dispatcher，要么页面自己放一个调 `navigation.pop()` 的按钮。

`BackHandler` 是返回的拦截声明（2026-10-29 加）：不画任何东西，`enabled` 时把系统返回（Android 返回键与预测性返回手势、iOS 侧滑，经 `navigationevent` 的 dispatcher，与 Navigation 3 同一个；顶栏返回按钮也注入到这里）交给 `onBack`，页面在里面决定做什么，比如先确认再 `navigation.pop()`。按 ADR-002，拦不拦必须作为 prop 预先声明，宿主没法在手势发生时同步问 JS。多个启用着的以最后组合的为准（dispatcher 的规则）；打开着的 `BottomSheet` 先于它收到返回（M3 弹层自带）。宿主没提供 dispatcher 时它不起作用。

```tsx
import chevronLeft from "../icons/chevron-left.svg";
const [leaving, setLeaving] = signal(false);
<TopAppBar backIcon={chevronLeft} backLabel={t("common.back")} height={48}>
    <Button variant="text" text={t("help")} onClick={openHelp} />
</TopAppBar>
<BackHandler enabled={dirty()} onBack={async () => { if (await ui.confirm({ message: t("leave") })) navigation.pop(); }} />
```

`Menu` 是锚在父节点上的 M3 下拉菜单（2026-10-29 加，注册流程的语言切换触发）。锚点规则同 `Popover`：放进触发它的那个节点里，触发器带 `gap` 也不会因它多出一份间距。菜单项是 `Option` children，与 `Select` 同一份数据写法；`Select` 是表单字段（输入框外观、显示选中值），`Menu` 是点了才出现的一组动作或选项，触发器长什么样由页面写。开关归页面，但选中一项或点外面时菜单当场收起再上报，与 `BottomSheet` 同一模式：之后要 `open` 先变 false 再变 true 才会再打开，所以两个事件里都把 `open` 置回 false。不做：图标、分组、勾选标记、子菜单。

```tsx
const [open, setOpen] = signal(false);
<Row role="button" onClick={() => setOpen(true)} align="center" gap={4}>
    <Icon icon={globe} size={20} />
    <Text text={languageName()} />
    <Menu open={open()} onDismiss={() => setOpen(false)} onSelect={(e) => { setOpen(false); setLanguage(e.value); }}>
        <Option value="en" text="English" />
        <Option value="zh" text="简体中文" />
    </Menu>
</Row>
```

`Tabs` 是 M3 的主级标签栏 `PrimaryTabRow`（2026-11-03 加，登录流程的「手机号 / 邮箱」切换触发）。标签是 `Option` children，与 `Select` / `Menu` 同一份数据写法；选中态归页面，与 `RadioButton` 同一模式：`onSelect` 只报点了哪个，页面写回 `value`，不写回就不动。`value` 不设或对不上任何 `Option` 时一个都不选、不画指示条，页面挂载时没有默认选中。尺寸与形态全是 M3 的：标签等分宽度、文字 `titleSmall`、选中 `primary` 未选 `onSurfaceVariant`、指示条 3dp 随文字宽、底下一条分隔线、48dp 触控区；设计系统与此有偏离时按 §6 的宿主默认值规则补槽位，不在页面上调。不做：图标标签、滚动标签栏（`PrimaryScrollableTabRow`）、次级标签栏（`SecondaryTabRow`）、标签内容区（切换后画什么由页面按 `value` 条件渲染）。

```tsx
const [method, setMethod] = signal("phone");
<Tabs width="fill" value={method()} onSelect={(e) => setMethod(e.value)}>
    <Option value="phone" text={t("phone")} />
    <Option value="email" text={t("email")} />
</Tabs>
<Show when={method() === "phone"}>{() => <PhoneField />}</Show>
```

`IndexBar` 是长列表旁的一条索引（2026-11-05 加，登录流程的手机区号选择触发：按首字母分组的列表，右侧 A–Z，点按或上下滑动跳到对应分组）。标签是 `Option` children，与 `Select` / `Menu` / `Tabs` 同一份数据写法。它是第一个内置的连续手势组件（ADR-004 §3.1 的「连续」类）：手指在哪一格由 Kotlin 逐帧跟踪，**只在换格时过桥**——按下报一次，之后每移到另一个标签报一次 `onSelect`，一次滑动最多报标签数那么多次。报的是手指**现在所在**的标签：快速滑动时一次移动跨过几格，只报落点、不补报中间的——页面拿它去滚列表，补报只会让列表多跳到手指已经离开的分组（iOS 的列表索引同样只认当前触点）；页面在里面给列表发 `jumpTo`（不带动画，手指扫过一串字母时带动画的 `scrollTo` 会排队追不上）。它不认识列表：滚哪个列表、标签对应第几行、没有对应分组的标签落到哪，都是页面的事。

高亮分两段：手指按着时亮手指下面那一格（Kotlin 自己的状态，跟手），抬起后回到页面的 `value`——通常是列表当前所在的分组，页面在 `onSelect` 与 `LazyColumn` 的 `onScrollEnd` 里更新它；不设就不亮。Compose 与 M3 都没有对应组件，它和 `CodeField` 一样是框架自绘的：标签 16dp 圆、`bodySmall` 字、`onSurfaceVariant` 色，高亮是 `primary` 圆底 `onPrimary` 字；每个标签占 20dp 高的一格，放不下时各格等比收窄（标签会相互压住，但每格仍对应自己的标签），页面给了更高的 `height` 时等比拉开。触摸区就是组件自己的盒子，圆点在其中水平居中：想要比 16dp 更宽的触摸条，给它一个 `width`。条上的触摸全部消费掉，放在 `BottomSheet` 里上下滑不会拖动弹层。读屏把每个标签当一个按钮。不做：按压时的放大气泡、触感反馈、横向索引。

```tsx
const list = ref<LazyColumnCommands>();
const [section, setSection] = signal("A");
<Box width="fill" weight={1} align="centerEnd">
    <LazyColumn width="fill" height="fill" ref={list} onScrollEnd={(e) => setSection(sectionAt(e.index))}>…</LazyColumn>
    <IndexBar width={32} value={section()} onSelect={(e) => { setSection(e.value); list.cmd("jumpTo", { index: firstRowOf(e.value) }); }}>
        <For each={LETTERS} key={(l) => l}>{(l) => <Option value={l()} text={l()} />}</For>
    </IndexBar>
</Box>
```

`Divider` 是 M3 `HorizontalDivider`：占满宽度的一条线，缩进用 `paddingHorizontal`（2026-10-12 加，与 Compose 组件对齐；像素上等同 `<Box width="fill" height={1} background="outlineVariant" />`，内置是为了写法与默认值跟 M3 走）。不做：竖线（`VerticalDivider` 要靠 `IntrinsicSize` 撑高，框架还没有内在尺寸）与一像素 hairline（尺寸只按 dp 过桥）。

**`Image` 推迟**：牵出图片加载管线的选型（coil3 还是宿主提供 loader），M2 用不到，单独一次定。

## 4. 命令怎么消费

命令进节点的 `commands` 队列（不是 props），组件用 `scope.Commands { command -> … }` 在组合后按到达顺序消费，一次性。`LazyColumn` 的 `scrollTo` 与新增行同一 flush 到达时，行先进 children，命令随后执行，index 已经有效。

**处理器要在执行时读 props**：命令在它那批 flush 提交后送达，但早于这批引起的重组。处理器若用组合期捕获的值（`val maxLength = scope.get(...)` 再闭包进去），同一批里改 prop 又发命令时就用了旧值。`TextField.setText` 裁剪、`CodeField.setText` 取位数都在处理器里现读 `scope.get`，自定义组件照此办。

## 5. `Placeholder`

未注册的类型渲染 `Placeholder`，同时 E5 上报。debug 构建（Android `FLAG_DEBUGGABLE`、iOS debug binary）画红框加类型名与 id；release 渲染零尺寸的空 `Box`。

## 6. 主题 token

定于 2026-09-17。**主题色与文字样式只能以 token 名过桥，Kotlin 在组合期从 `MaterialTheme` 解析；JS 永远拿不到主题的具体值。** 深浅切换、宿主换主题色时页面不发任何 patch 就跟着变——主题是宿主的运行时状态，不是页面的数据（与 ADR-004"高频状态留 Kotlin"同一原理）。需要派生色（如 primary 变浅）时由宿主注册成新 token，不在页面里算。

- `color` 类型的 prop 同时接受字面量（`#RRGGBB` / `#AARRGGBB`）与 token 名；生成器出联合类型（`` `#${string}` | ColorToken ``），拼错的 token 编译期报，Kotlin 侧按首字符 `#` 分流。字面量留给品牌色、图表色这类本来就不属于主题的颜色
- token 词表就是 Material 3 的字段名：颜色为 `ColorScheme` 的全部字段，文字样式为 `Typography` 的十五个名字（cli `schema/tokens.ts` 是词表真值，`compose/.../schema/Theme.kt` 逐一解析，测试保证两边一致）
- 暂不做：宿主自定义 token（触发条件：某宿主出现 M3 之外的语义色要跨页复用，届时走清单下发 + 宿主类型包生成，与宿主组件同一条路）；间距 / 圆角 / 阴影的 token

**宿主覆盖组件默认值**（2026-11-02 加，注册流程触发：设计系统把输入框聚焦描边给 `onSurface`，M3 从 `primary` 推）。M3 有些组件颜色不是 token 而是从角色推出来的，`MaterialTheme` 表达不了；宿主在包 `TinyUIPage` 的主题层设一次 `LocalTinyUIDefaults`，每个字段可选，null 即 M3 默认，页面看不到它；值在宿主自己的 `MaterialTheme` 里构造，随主题重组。现有字段：`textField`，一份 `TextFieldStyle` 供 `TextField`、`Select` 与 `CodeField` 的格子共用——`colors`（`TextFieldColors`，格子取它的 focused / unfocused / error indicator 色）、`contentPadding` 与 `minHeight`（2026-11-03 加，注册流程触发：设计系统的输入框 48dp。M3 的 56dp 是浮动标签的算术——上下 8dp 内边距 + 12sp 收起标签一行 16 + 16sp 正文一行 24，标签在框外的表单用不上这 8dp；Compose 的 M3 不开放密度，但 `TextFieldState` 版 `OutlinedTextField` 开放 `contentPadding`，56dp 的最小高度是 `defaultMinSize`、外层 `heightIn` 即可盖过，所以是 M3 自己的能力，不用自己拼 `DecorationBox`——[#54](https://github.com/tiny-ui/tinyui/pull/54) 那条路走过又撤回。上下 12dp + 48dp 就是 Material 的紧凑密度；`supportingText` 挂在框下，不算在 `minHeight` 里）；`filledButton` / `outlinedButton` / `textButton`（2026-11-03 加，注册流程触发：设计系统的次级按钮是主色描边、圆角 8、内边距 16，M3 描边按钮是 `onSurfaceVariant` 字、`outline` 边、胶囊、内边距 24，而按钮形状根本不读 `MaterialTheme.shapes`），各一份 `ButtonStyle`——按 `Button` 的 `variant` 一槽一样式，就是设计系统的主 / 次 / 文字按钮；字段是 M3 `Button` 自己的参数类型（`colors` / `border` / `disabledBorder` / `shape` / `contentPadding`），每项可空、空回 M3，`disabledBorder` 空时回 M3 的禁用边而不是 `border`。`checkbox`（2026-11-03 加，注册流程触发：设计系统的勾选框是 1dp 细边，M3 是 2dp），一份 `CheckboxStyle`——`colors`、`outlineStroke`、`checkmarkStroke`，后两者是 M3 1.12 `Checkbox` 新重载的参数，像素单位、宿主在组合里按 `LocalDensity` 造；方框尺寸 M3 没有参数（`ComposeMaterial3Flags.isCheckboxStylingFixEnabled` 开则 18dp，关则旧的 20dp，是宿主进程级的开关，不在这里），所以这里也没有。`textLinks`（2026-11-03 加，注册流程触发：设计系统的链接是半粗 + 主色，M3 只给主色），一份 Compose 的 `TextLinkStyles`，`Text` 里 `[文案](id)` 的颜色 / 字重 / 装饰按状态；空即主色、沿用正文字重。这些默认值**不进宿主快照**，页面不能依赖它们存在，只是外观。规则：一个组件变体一个槽位、用 M3 自己的类型、空即 M3；这条表会随组件变长（Flutter 的 `ThemeData` 有四十多个组件主题），长不可怕，规则乱才可怕。新字段的触发条件同上：设计系统与 M3 默认值的一处具体偏离，不是页面级定制。

```kotlin
CompositionLocalProvider(LocalTinyUIDefaults provides TinyUIDefaults(textField = TextFieldStyle(colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.onSurface)))) {
    TinyUIPage(…)
}
```

