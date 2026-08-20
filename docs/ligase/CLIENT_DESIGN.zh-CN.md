# Ligase Android 客户端设计

本文档描述默认分支 `ligase/android` 的当前产品结构和接口边界。
涉及导航、游戏库、Host 身份、偏好或验收流程的代码变更，应同步更新本文档。
代码依赖方向、唯一状态 owner 与设备验收范围见
[Android 架构文档](ANDROID_ARCHITECTURE.zh-CN.md)。

## 产品结构

- 核心目标是让不了解串流协议的用户也能按“添加电脑 → 配对 → 选择游戏 →
  开始串流”的主路径完成操作。产品 UI 不展示 UUID、appid、端口、revision
  或协议错误码。
- 顶级壳层只有 `LigaseActivity`，产品 UI 使用 Jetpack Compose。
- 首页就是游戏库，不再先显示旧式电脑卡片页，也不以 `PcView` / `AppView`
  作为产品入口。
- 首页顶部显示当前电脑。点击后打开独立电脑管理弹窗：
  - 查看并切换所有已发现或手动添加的电脑；
  - 添加电脑；
  - 按 Host UUID 区分并移除旧电脑，删除前二次确认。
- 手机竖屏使用底部导航；手机横屏和平板由应用自己的响应式导航壳切换为左侧导航。
- 手机底栏模式下，游戏库、输入和设置的可滚动内容统一预留底栏安全区；平板左栏
  模式不添加该底部留白。
- 游戏库支持搜索、本机显示排序以及用户可选的列表/竖向海报布局。名称、添加时间和
  最近游玩时间只取自 Host Sync；本机排序仅改变当前客户端的展示，不写回 Host。
- 首页游戏库支持下拉刷新当前电脑。刷新复用现有 server state、Sync v1 与 applist
  UUID-only 合并链；刷新期间保留当前内容、滚动位置、电脑和导航，失败时保留旧内容并
  给出可重试提示。同一电脑刷新 single-flight，切换电脑后旧结果不得回写。手势过程
  中内容跟手下移并露出纯白文字区，依次显示“下拉刷新 / 松开刷新 / 正在刷新”，不使用
  图案；标题栏使用独立的 `surfaceVariant` 层级色。
- 最后成功的游戏库内容保存在 Activity 范围的 immutable session state 中；页面重入、
  旋转、Host checking/offline 轮询和刷新错误只更新连接、加载或错误维度，不把内容
  置空。离线时保留选中 Host 身份、页面与最后快照，但所有启动、输入和网络写入都由
  实时 `ONLINE + operate` 门拒绝；重新在线后原地强制拉取 Sync+applist，以 ticket
  边界替换快照。只有明确切换或删除 Host 才清除上一台电脑的快照。
- 当前跨进程旧缓存只有 GameStream applist，没有 Sync 快照、库权限来源或
  `LigaseClientAccessMode` 授权证明，不能安全恢复为 Ligase 产品库。进程重启后的离线
  Host 因而只恢复电脑身份并显示离线空态，不读取或泄露旧 applist；未来若加入磁盘
  snapshot，必须同时持久化并复核 Host UUID、证书身份和已授权库读取状态，权限撤销时
  删除私有游戏元数据。
- 每个游戏卡片右下角提供独立设置入口；该入口与卡片启动点击隔离，用于选择
  “使用全局设置”或“自定义分辨率”。
- 游戏的发现、添加、删除、可执行路径和 Steam App 管理只存在于 Ligase Host。
  Android 不提供添加、导入、扫描或删除游戏入口，也不通过 API 修改游戏集合。
  空游戏库引导用户到电脑端 Ligase Host 添加游戏，并仅提供刷新。
- “添加电脑”属于 Android 的连接管理能力，与“添加游戏”严格区分。
- 设置页提供串流输入、明亮/黑暗主题和语言（跟随系统、简体中文、English）。
- 高级串流设置采用渐进披露：全局分辨率位于设置页，单游戏覆盖位于卡片设置；
  均不进入首次启动主路径。
- 分辨率编辑器提供设备最大、最佳16:9以及720p/1080p/1440p/2160p固定档，并逐项说明
  是否超过当前显示能力；自定义宽高仍保留。帧率是Android本机设置，可跟随设备显示或
  选择30/60/90/120 FPS，推荐只提示、不自动改变用户选择。设备能力未知时不伪装支持。

## 输入页与全局触控布局

- 普通输入页固定为“顶部当前选择卡 + 下方条件内容”。顶部卡显示输入模式、已选设备
  或布局、连接状态和“更改”；旋转、切页和重进应用后从本地偏好恢复。
- 普通 UI 只有三种模式：
  - 手柄：按 `InputDevice` 的 gamepad/joystick capability 列出外接设备；
  - 键盘鼠标：按 capability 将键盘和鼠标分别列出，允许只连接其中一类；
  - 无外接设备（触屏）：列出本机已有 TouchKit 布局并选择一个全局布局。
- 键盘还必须具备 `KEYBOARD_TYPE_ALPHABETIC`；仅暴露非字母媒体按键的手环、遥控器
  等设备不列为实体键盘。外设选择保存
  `descriptor + vendorId + productId + category` 生成的稳定键，不保存易变
  `deviceId`，也不按设备名称识别类型。能通过 `UsbManager` 确认时显示 USB/OTG；
  其他设备显示“外接设备”，不猜测为蓝牙。普通 HID 不请求直连 USB 权限。
- 设备断开后保留稳定选择并显示“已断开”，不会静默切到触屏或另一设备；重新连接同一
  稳定键后恢复“已连接”。开始串流时若所选外设均不可用，会返回输入页要求连接。
- 触屏页明确属于 Android 本机全局设置，overlay 仅有虚拟手柄、云游戏控件、不显示；
  云游戏控件仅有单点触控、多点触控、触摸板。单游戏覆盖只绑定 Host Sync 的 canonical
  game UUID，不按名称或 App ID 推断。observe 权限强制投影“不显示”并禁用修改，但不会
  写回或清除已保存偏好；恢复 operate 后原偏好重新生效。
- 输入页与设置页只提供同一个 v3 布局大厅入口；不再展示 v1 布局选择、编辑当前布局、
  copy-on-write 或全屏预览入口。
- Hall 只展示 v3 新建、恢复与已严格验证的本机 generation。产品启动不携带 v1 layout
  ID，也不读取旧 profile/game store。v3 runtime 尚未实现时由 typed gate 明确提示
  不可用，不能用默认 TouchKit、numeric appid 或 `unknown_pc` 兜底。
- Touch Layout v3是唯一新内容契约，使用独立strict schema、journal、generation
  repository与Workspace/Activity owner，不读写v1 SharedPreferences；失效v2
  production owner已移除，不保留双读写或兼容fallback。
- Compose卡片式画布已退出产品主路径。当前编辑体验使用独立`sensorLandscape`、
  沉浸式黑色TouchKit画布：新布局由Activity在完整edge-to-edge overlay bounds稳定后
  创建canvas；已有草稿仅以opaque draft ID交给独立Activity独占恢复；
  Intent 不携带 raw state、路径、hash 或扩展字段。拖动和缩放在 View 内逐帧预览，
  每次手势只在结束时提交一次 typed action。画布占满 Activity 可用内容区；新增、
  属性、层级、保存和离开动作放在可收起的半透明覆盖层中，开关覆盖层不重排画布，
  覆盖层只拦截自身区域，点控件会直接打开属性并把浮层换到控件相反侧；未被覆盖的
  画布仍可选择和拖动，右下角缩放手柄只负责改变尺寸。位置细调使用方向键排布，
  支持轻点一步与长按本地预览、释放单次提交。键盘键与鼠标按钮默认采用圆形，用户可
  显式切换为圆角矩形或矩形；形状随v3 typed properties进入同一严格保存链。控件可
  部分越过全屏canvas，只要仍有协议规定的可见交集；重叠合法，zOrder决定绘制与命中。
  编辑工具明确拆成两个互斥入口：“控件属性”只展示所选控件的可读键名、说明、形状、
  层级和删除；“布局设置”只展示新增、批量键盘、布局名称、保存与保留草稿。属性页
  不展示numeric InputCode。控件文字按实际尺寸放大并在空间不足时省略。单控件
  不透明度入口已撤下；布局设置提供唯一全局不透明度，释放滑杆时经Session原子提交，
  canvas只读取root draft值统一合成，不能用逐元素修改模拟。组合键与轮盘只出现在
  选中控件的属性层：复用真实键盘picker选择closed InputCode列表并一次替换payload；
  轮盘动作以backend返回的稳定actionId完成新增、删除、排序、标签与组合键编辑。
  新建Combo或Radial时，前端只提交完整chord/action request一次，不生成identity、
  rect、zOrder或默认payload。
  typed失败不关闭上下文、不伪成功；运行态仍保持不可用。
- v1 TouchKit 布局运行与持久化已退出产品消费链；残余类仅等待后续精确删除。编辑器
  不以decoded video rect、DPI或system inset替代full-overlay坐标权威。
- “添加键盘按键”打开半透明 ANSI 键盘浮层，键帽支持多选；选中键帽以蓝色填充、
  边框和无障碍 selected 状态表达，键名始终几何居中，不显示会挤动文字的勾号；
  右下角“添加（N）”只在 N 大于零时可用。确认后前端只发一次 batch action，后端
  原子创建并把全部按键放在画布中心；重叠是合法布局，不做碰撞避让。键盘优先
  等比缩小适配横屏，低于可读下限才滚动；主键区、导航区和数字区分别对齐，
  方向键保持倒 T，数字区 `+`/Enter 跨两行且 `0` 跨两列。画布按 canonical
  `InputCode` 显示真实键名；用户可在属性浮层编辑或清空既有
  `Appearance.description` 功能说明，并随同一严格保存链持久化。
  临时逐键循环或伪入口。

## 跨端视觉颜色契约

- Android 颜色语义以 Ligase Host `visual-color-tokens-v1.md` 为跨端权威；当前冻结
  基线是 Host `5d4a20d89a9ed283bed2b97de3c3bb2e72d0dd50`。Android 不另建产品 palette，也不要求 WinUI 与 Material
  逐像素一致。
- `LigaseTheme.kt` 是 Compose 的单一颜色入口，显式映射 Material 3 1.4.0 的全部
  `ColorScheme` 槽位；未审议的新槽位禁止回退 Material factory default。
- XML `LigaseThemeBase` 使用同一组 light/dark 资源。Compose 与 XML 资源由自动测试
  对照，避免再次形成两套漂移的主题。
- 默认关闭系统动态色；“跟随系统”只跟随明暗模式，不能覆盖品牌色、状态色或焦点色。
- `selected` 只作为选中容器，普通文字和图标使用 `textPrimary`，`brandPrimary`
  只用于选中指示或关键图标。状态必须同时提供文字或图标，不能只靠颜色。
- `disabled` 是必要内容的可读性下限，禁止通过整组件透明度把必要文字降到
  `4.5:1` 以下。普通文字要求至少 `4.5:1`，大文字、关键图标、焦点环和有意义边界
  至少 `3:1`；测试从机器色值重新计算，不信任文档中的手算结果。
- 当前 Compose Material 3 1.4.0 没有 `shadow` 槽位；阴影仅作为 Ligase 自定义
  alpha overlay primitive。浅色使用 `textPrimary`，深色使用 `background`。
- `Game` 视频 Surface、串流 HUD、TouchKit 与游戏海报上的 media/artwork overlay
  不映射为产品 `surface`，继续保持隔离；后者待独立媒体覆盖层契约再集中处理。

## 强制 Ligase Sync v1 产品边界

- Host 固定的 Android Sync v1 machine schema 是新增 identity、布局绑定与封面字段的
  唯一 wire 权威。Android strict codec 拒绝未知字段、替代 identity 形状、非 canonical
  UUID/整数以及不完整 cover authority；拒绝结果不得覆盖 last-success Host 快照。
- `portableIdentity` 只投影 Host 已验证的 Steam identity；Android 不从名称、启动 ID、
  `steamAppId` 或封面路径补猜。`layoutBinding` 只解析精确 `(layoutId, revision)`；未知、
  retired 或本机未安装分别投影 typed 状态，绝不回退到名称或自动匹配。
- 游戏卡对 canonical Steam identity 显示 `Steam · App ID <id>`；非 Steam、字段缺失或
  非 canonical 值不显示伪身份。布局绑定用自然语言区分未绑定、不存在、已停用、
  草稿未安装与本机已就绪。本阶段的已就绪状态不展示 raw layout UUID、路径、hash、
  title 或 revision，也不表示布局 runtime 已可用。
- 封面只有在 Sync app UUID、Sync expected SHA、`/appasset` UUID/SHA/PNG/长度 headers 与
  1..8 MiB 响应字节 SHA 全部一致后才可标为 current。失败时可保留一份先前独立验证的
  同 authority 图片并明确标 stale，但不得把旧缓存冒充当前 Host 内容。
- 有 cover authority 的卡片禁止调用 legacy `getBoxArt` 或读取其 disk cache；loader 不可用、
  authority 改变或三方校验失败时显示 placeholder（或同 UUID、同 authority 且重新验真的
  stale cover）。无 authority 的系统/legacy 项也不得把旧图片标为 verified/current。

- `serverinfo.LigaseSyncVersion == 1` 且存在 `LigaseSyncPath` 才能进入产品游戏库。
  缺失能力时展示“需要升级 Ligase Host”和“重试”，禁止用旧 applist 回退构建库。
- `GET /ligase/v1/sync` 是游戏集合、类型、名称、UUID、排序、时间、全局分辨率、
  单游戏分辨率和 Host HDR 编码能力的唯一产品权威。
- 同步请求使用 `serverinfo.HttpsPort`、已配对客户端身份、证书固定与现有 NvHTTP
  TLS 行为；不能从 HTTP 端口推导 HTTPS 端口。
- Android 只写串流分辨率。应用的新增、删除与 Host canonical order 只由 Host 管理；
  Android 的排序菜单属于按 Host 元数据生成的本机显示偏好。
- Host 串流设置初始 revision `0` 是合法的 safe integer。分辨率编辑 Dialog 必须把
  request 连同 revision 原样保存和恢复；缺失或损坏的 Fragment 参数只能安全关闭，
  不得猜默认 request 或在 Compose composition 中抛异常。
- 写入遇到 revision 冲突时重新拉取完整快照，提示用户重新操作，不自动重放旧写入。
- NvHTTP 对非成功响应保留结构化错误体，便于 Sync 写入诊断；日志只能记录契约错误
  JSON 和不含 query/response body 的安全路径摘要。`/launch` 的 `rikey` 等 query
  参数、客户端证书、私钥、TLS 密钥或配对秘密均不得进入日志。
- UI 不直接呈现 Sync v1、404、409、revision 等实现细节；失败状态提供升级 Host、
  检查 Host/网络或重试等下一步。

## 多客户端可见性与配对演进

- LAN discovery 对已完成 `serverinfo` 身份校验、状态为 ONLINE 且明确 NOT_PAIRED 的
  Host 在首页直接投影配对卡。卡片身份只使用 canonical Host UUID，并以已取得的证书
  指纹辅助去重；IPv4/IPv6/多地址不产生重复卡片，缺失或非法 UUID fail closed。
  点击仍进入既有 attended/legacy 用户确认与 Host 审批流程，发现本身绝不自动配对。

- Host 后续 Sync item 的 `publishedToClients` 在 Android DTO 中必须是 nullable
  Boolean；字段缺失按可见处理，即 `hostPublished = value != false`。
- 本机隐藏按规范化小写 `(hostUniqueId, appUuid)` 分区，只存 Android 本地，
  不上传、不改变 Host revision；有效可见性为 Host 已发布且不在本机隐藏集合中。
- `desktop` 和 `virtualDesktop` 首版均不可隐藏。“已隐藏游戏”只能恢复本机隐藏，
  不能覆盖 Host 全局隐藏。Android 仍不能写游戏集合或 Host publication。
- attended-pairing v1 已在 Android 形成独立
  `AttendedPairingRepository` / `AttendedPairingCoordinator`，按 Host 冻结契约执行
  X25519、HKDF-SHA-256、ChaCha20-Poly1305、RFC 8785 JCS、DER 证书绑定、
  双端 `XXXX-XXXX` 安全码、120 秒单调时钟超时及 fail-closed 清理。
- `LigaseAttendedPairingVersion == 1` 且 path 存在时，普通配对 UI 只显示设备名、
  安全短码、等待电脑批准及取消，不显示 legacy PIN、管理端口或管理账号。Host
  缺少 capability 时才提供带明确风险说明的“高级兼容配对”，不会静默回退。
- Coordinator 以 `SystemClock.elapsedRealtime()` 建立最多 120 秒本地 deadline，
  最多每秒轮询一次；普通失败与所有终态停止 held pair、轮询并清理秘密，不自动
  重放。用户取消先 DELETE，再只用暂存 bearer 做一次短超时 authenticated status
  probe，随后无条件清理。
- 配对后的 HTTPS `serverinfo.LigaseClientAccessMode` 是本客户端权限唯一产品投影。
  `operate` 允许启动、Sync 写、结束会话和输入；`observe` 只读。未知自定义值按
  observe，公共 HTTP 中字段缺失不被误判为权限。运行时 403 刷新权限并提示用户，
  不自动重放写入。
- 119-case fixture 与 schema 从 Host 权威提交逐字复制，资源 SHA 固定写入 JVM
  测试；JCS 与 strict JSON 不由 Gson 承担。

## 保留的 GameStream 传输 ABI

Ligase 不兼容旧 Apollo 产品配置或 UI，但在替换串流传输之前继续复用：

- 电脑发现、在线状态与配对；
- `serverinfo` 的 Host 身份、配对、在线/运行状态、HTTPS 端口及远程发现/WOL 字段；
- 成功取得 Sync v1 后，applist 仅提供应用 UUID 到数字 `appId` 的启动映射；
- `appUUID + appId` 启动、恢复和运行状态；
- `appasset(appid)` 封面；
- 现有 RTSP、视频、音频、加密和输入链；
- 已选择的手柄、键鼠或无外接设备输入模式会传入串流。

## Endpoint v2 与 IPv6/双栈边界

Android 与 Host 以 Host 的 `endpoint-contract-v2.md` 为共同权威。电脑连接位置使用
独立 `LigaseEndpoint` 值对象，不再把 `host:port` 字符串当身份：

- 字段为 `scheme, host, port, zone?, source?`；scheme 机器值是
  `http | https | rtsp`，source 是
  `manual | mdns | local | remote | loopback`。
- host 规范化后不含方括号、端口或 zone。支持 IDNA hostname、IPv4、global/ULA/
  loopback IPv6 与带 zone 的 link-local IPv6；IPv6 multicast 不在 v2 范围。
- link-local IPv6 必须显式提供接口名或 1..4294967295 的 scope id；其他 IPv6
  禁止携带 zone。持久化的 zone 不含 `%`，URI formatter 输出
  `[host%25encodedZone]:port`。
- Host 身份仍是规范化 `hostUniqueId + pinned certificate`。地址只是可变化的候选，
  source 不参与身份或候选去重。

`ComputerDatabaseManager` 的 `Addresses` 列写入：

```json
{
  "version": 2,
  "endpoints": [
    {
      "scheme": "http",
      "host": "ligase-host.local",
      "port": 48989,
      "source": "manual"
    }
  ]
}
```

读取继续兼容原 `local/remote/manual/ipv6` 四槽 JSON；迁移不改变电脑 UUID、证书、
IPv4 文本或端口。当前发现与 GameStream 传输仍通过首个同来源候选投影到旧
`AddressTuple`，这是明确的过渡 ABI，不是第二套身份源。

双栈选择策略已接入电脑轮询执行链：literal IPv6/IPv4 候选交错，启动间隔
250ms，单候选探测最多 3s、整体最多 5s；只有 `serverinfo` 健康且 uniqueid
匹配才算成功，配对后 HTTPS 还必须通过证书固定。取消竞速只停止其余只读探测，
不会重复配对、启动或写入。

OkHttp 4.12 不接受含 scope 的 host。HTTP/HTTPS 使用 synthetic URL host 与自定义
DNS，把已校验的 `link-local%zone` 转为 scoped `Inet6Address`，避免把 RFC 6874
authority 直接交给 OkHttp。当前真实 LAN 联合验收覆盖 global IPv6；link-local
HTTP/HTTPS 与 native RTSP 仍缺少可达实网，不得宣称通过。Hostname 当前作为一个
有界候选交给 OkHttp，尚未把 DNS 返回的 IPv6/IPv4 地址展开为独立的 250ms
stagger 尝试，这是后续基础设施风险，不影响已验证的 literal IPv4/IPv6 路径。

普通“添加电脑”界面将地址和端口分栏，默认端口 48989。用户可输入 hostname、
IPv4、IPv6 或 `link-local%接口`；协议、路径、query、fragment 和 userinfo 会被拒绝。
方括号及旧 `host:port` 仅由迁移兼容 parser 接受，随后立即转为结构化字段。

## P1：旋转与串流会话控制

- 手机横屏不能沿用底部导航。导航位置由当前 Compose `LocalConfiguration`
  实时决定：任何横屏或宽度至少 600dp 使用左侧 navigation rail，其余手机竖屏
  使用底部 navigation bar。旋转不重建产品状态源，不改变当前页面或当前电脑。
- Ligase 启动的串流默认不常驻左上浮动按钮；进入串流时以短时提示说明返回键或五指
  轻点可打开控制菜单。两种入口均保留，退出动作始终可达；legacy 非Ligase串流仍按
  自身浮动按钮偏好显示。
- 主菜单只保留继续、断开、显式上传/获取剪贴板、键盘、更多与结束会话；缩放、server
  command等低频功能进入“更多”。剪贴板双向操作均需用户逐次确认，只接受单条
  `text/plain` 且UTF-8不超过64KiB；Ligase串流禁用焦点变化自动同步，拒绝URI、Intent、
  HTML、多项或超限内容，不把本机剪贴板静默覆盖。
- 快捷菜单明确区分：
  - “返回游戏库（保持游戏运行）”：只断开 Android 串流；
  - “结束串流并关闭游戏”：确认一次后调用现有 GameStream `quitApp()`，结束 Host
    会话并返回游戏库。
- 结束请求由独立 `StreamSessionExitCoordinator` 做一次性门控；重复点击不会重复
  断开或结束。Host 不可达、已结束或返回失败时只显示自然语言恢复建议，不暴露
  HTTP/RTSP 等协议细节。
- 2026-07-23 真机验证：
  - V2353A 竖屏底栏 → 横屏左栏 → 竖屏底栏，同一 Activity、当前电脑与首页状态
    保持，无重叠或裁切；
  - AGS2-AL00 横屏仍为左栏且只有一个 `LigaseActivity`；
  - V2353A 从监控桌面进入串流后通过当时版本的“串流控制”入口确认结束并返回原游戏库，
    `Game` Activity 清除，Host 回到 `currentgame=0 / SUNSHINE_SERVER_FREE`，
    随后可再次启动并再次正常结束。

启动前严格按 UUID 合并 Sync item 与 applist。缺少映射的项目仍显示在游戏库，
但标记“启动映射缺失”并禁用启动；禁止按名称匹配。客户端不会接收或拼接 Windows
路径、工作目录或命令行。

## 稳定系统入口

- `desktop` / 监控桌面：
  `78A25216-F239-45BD-B4AA-F41C814066E9`
- `virtualDesktop` / 虚拟桌面：
  `8902CB19-674A-403D-A587-41B092E900BA`

系统类型由 Sync v1 的 `kind` 决定，UUID 是稳定身份。虚拟桌面若存在于 Sync
快照但没有对应 applist 项，仍显示但不可启动。

## 排序、分辨率与 HDR

Sync v1 仍兼容读取并校验以下 `sortMode` 机器值：

- `nameAscending`
- `nameDescending`
- `addedNewest`
- `addedOldest`
- `lastPlayedNewest`
- `manual`

Host 的 `revision`、字段内容与 `items[]` 是数据权威。Android 按电脑保存名称、添加
时间和最近游玩的本机显示排序偏好，这些模式不调用 library sort POST。名称和时间比较
只使用 Sync 字段；相同或缺失时间使用名称与 canonical UUID 作稳定 tie-break。若 Host
未提供任何 `lastPlayedAt`，“最近游玩”选项禁用并明确提示暂无 Host 记录，禁止使用
Android 启动历史补猜。下拉刷新成功后对最新 Sync 表重新应用本机显示排序，失败时保留
上次内容及显示顺序。

“手动排序”是共享 Host canonical order。Android 首页编辑态允许移动全部 published
项目（包括“监控桌面”和“虚拟桌面”，系统入口仍不可删除或隐藏），并向
`POST /ligase/v1/library/sort` 提交全部 published 项目各一次组成的 lowercase
canonical UUID 序列以及最后成功 Sync 的 `baseRevision`。请求与成功响应执行严格
safe-integer、字段、UUID、去重和完整集合校验；成功后按 Host 返回序列原子更新 session
content。409 只触发一次 GET Sync 并提示用户基于新快照重新排列，绝不自动重放 POST；
observe 为 403 且不产生乐观写。名称与时间排序始终保持纯本机偏好。

有效分辨率优先使用单游戏覆盖，否则继承全局分辨率。该结果会在 Ligase 启动入口
覆盖旧本地分辨率偏好并传入现有 `Game`，Host 在 `/launch` 再次执行同一规则。
恢复继承时显式提交 `resolution: null`。

HDR 分层处理：

- `sync.capabilities.hdrEncodingSupported` 表示 Host 当前可编码 HDR/10-bit；
- Android 独立检测当前显示设备和解码能力；
- Android 同时读取用户 HDR 开关，并派生稳定 typed reason：用户关闭、Host 编码不支持、
  显示不支持、解码不支持、能力待检测或最终可用；不需要新增 Host DTO；
- 真正请求 HDR 必须同时满足用户启用、Host 编码、Android 显示与解码能力；
- applist 的旧 `IsHdrSupported` 仅保留在底层兼容对象中，不能作为游戏内容 HDR
  能力或产品库元数据。

## 开发期代码边界

- Sync JSON DTO 与校验、Host 写请求集中在 `LigaseSyncRepository`；UI 不直接拼接路由
  或 JSON。
- `LigaseLibraryAdapter` 只接受成功的 Sync v1 快照，并按 UUID 合并 applist 启动参数；
  旧 `fromGameStream` 产品库回退和临时 appId 身份已删除。
- Compose 消费 `LibrarySessionState` 中相互独立的 last-successful content、
  connectivity、initialLoading、refreshing 和 typed error；旧电脑首页产品页面已删除，
  避免与“游戏库即首页”长期并存。
- `LibrarySessionViewModel` 与纯 `LibrarySessionStore` 已从 `LigaseActivity` 拆出唯一
  library 状态源；`LibraryHostCoordinator` 负责所选 Host 的 refresh ticket、
  last-success 快照接受、applist poller 与 asset loader 生命周期，
  `LegacyGameStreamLibraryTransport` 集中封装 `ComputerManagerService`、`NvHTTP`、
  `NvApp` 和 `CachedAppAssetLoader` 的旧 GameStream Java ABI。Activity 只组合
  生命周期、导航、产品反馈、启动和传统 View 弹窗；data/domain 不反向依赖
  Activity 或 Compose，也不复制第二套状态源或改写稳定配对/串流链。
- Gson 反射读取的 Sync DTO 必须保留精确 R8 keep 规则；minified debug APK 也是
  必测产物。传统 Material Dialog 的 positive button 必须在 `show()` 后绑定，避免
  按钮只关闭弹窗而未触发添加电脑或分辨率写入；对应行为需有回归测试。

## 构建与验收

Ligase 调试包必须构建 `nonRoot_gameDebug`：

```powershell
.\gradlew.bat :app:assembleNonRoot_gameDebug
```

ARM64 APK：
`app/build/outputs/apk/nonRoot_game/debug/app-nonRoot_game-arm64-v8a-debug.apk`

不要使用 `rootDebug` 验收 Ligase：该变体的 applicationId 是
`com.limelight.root.debug`，应用标签是 Diana。Ligase 调试包的 applicationId
是 `com.litchicore.ligase.debug`。

设备验收以当前可授权设备为准，至少覆盖一台手机和一台宽屏/平板设备，只操作可识别的
应用内控件。当前主设备矩阵是 Xiaomi M2007J17C 与 AGS2-AL00；历史 V2353A 证据
仅用于对应旧提交，不能替代当前 APK 的手机验收。

若在线 Host 尚未部署 Sync v1，只能验收“不兼容、升级、重试”状态，不能声称
游戏库同步、分辨率写回、冲突恢复或 HDR 已端到端通过。部署新核心后还需
逐项完成真实双端联合验收。

2026-07-23 已在 V2353A 对隔离 Host `10.168.1.191:49989` 完成真实联合验收：
Sync GET、UUID-only merge、当时版本的排序写入、全局/单游戏分辨率、恢复全局继承、409 重拉且
不重放、HDR 分层以及携带 `appuuid + appid` 的 1600×900 physical desktop launch
均通过。

同日 AGS2-AL00 使用 fresh identity 的隔离验收实例完成 literal LAN global IPv6
全链路验收：手动添加、Host UUID 校验、legacy pair、X.509 DER 指纹固定、
HTTPS Sync v1、UUID-only applist merge、physical desktop launch、bracketed IPv6
RTSP authority、控制/视频/音频/输入流、首帧硬解码，以及应用内确认结束会话均
通过。Host 复核结束后 `currentgame=0 / SUNSHINE_SERVER_FREE` 且连接与 UDP
串流端口已释放。验收实例、自动 PIN 通道和凭据仅属于测试基础设施，不构成产品
配对 UI；新 attended pairing 的真机 allow/reject/timeout/cancel/rebuild 与
operate/observe 联合验收记录见 `ATTENDED_PAIRING.zh-CN.md`。
