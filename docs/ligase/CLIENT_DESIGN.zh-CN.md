# Ligase Android 客户端设计

本文档描述 `ligase/compose-client-redesign` 分支的当前产品结构和接口边界。
涉及导航、游戏库、Host 身份、偏好或验收流程的代码变更，应同步更新本文档。

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
- 手机使用底部导航，横屏和平板由 Material 3 adaptive navigation 自动切换为左侧导航。
- 手机底栏模式下，游戏库、输入和设置的可滚动内容统一预留底栏安全区；平板左栏
  模式不添加该底部留白。
- 游戏库支持搜索、本机显示排序以及用户可选的列表/竖向海报布局。名称、添加时间和
  最近游玩时间只取自 Host Sync；本机排序仅改变当前客户端的展示，不写回 Host。
- 首页游戏库支持下拉刷新当前电脑。刷新复用现有 server state、Sync v1 与 applist
  UUID-only 合并链；刷新期间保留当前内容、滚动位置、电脑和导航，失败时保留旧内容并
  给出可重试提示。同一电脑刷新 single-flight，切换电脑后旧结果不得回写。手势过程
  中内容跟手下移并露出纯白文字区，依次显示“下拉刷新 / 松开刷新 / 正在刷新”，不使用
  图案；标题栏使用独立的 `surfaceVariant` 层级色。
- 每个游戏卡片右下角提供独立设置入口；该入口与卡片启动点击隔离，用于选择
  “使用全局设置”或“自定义分辨率”。
- 游戏的发现、添加、删除、可执行路径和 Steam App 管理只存在于 Ligase Host。
  Android 不提供添加、导入、扫描或删除游戏入口，也不通过 API 修改游戏集合。
  空游戏库引导用户到电脑端 Ligase Host 添加游戏，并仅提供刷新。
- “添加电脑”属于 Android 的连接管理能力，与“添加游戏”严格区分。
- 设置页提供串流输入、明亮/黑暗主题和语言（跟随系统、简体中文、English）。
- 高级串流设置采用渐进披露：全局分辨率位于设置页，单游戏覆盖位于卡片设置；
  均不进入首次启动主路径。

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
- 全局触控布局只保存稳定 TouchKit layout ID。已保存 ID 被删除时保持缺失状态并要求
  重选，不按显示名、`sourceLayoutId`、游戏名、numeric appid 或 `unknown_pc` 替换。
- 当前尚未接入 `layout-contract-v1` 的游戏级 identity/variant，因此所有 Ligase 触屏
  启动使用显式全局 layout ID。未来 2C 接入后，精确游戏 variant 优先，全局布局仅作
  未绑定回退；本阶段不迁移或删除旧 TouchKit game store。
- Ligase 启动 Intent 显式携带输入模式、全局 layout ID 和屏幕控件选择。触屏用户
  在“虚拟键盘（TouchKit）/虚拟手柄/不显示屏幕控件”中三选一，产品 UI 不允许
  两层叠加。虚拟键盘模式才要求稳定 layout ID；其他模式保留原选择但不加载布局。
  实体手柄、键盘鼠标模式会强制关闭触控覆盖。Ligase 产品启动绕开旧的 numeric
  appid/unknown_pc 布局回退，旧非 Ligase 入口暂时保持原行为。
- 布局大厅将在 catalog 可用后作为独立页面实现；当前输入页不显示空壳大厅入口，
  也不提前实现搜索、下载、编辑或游戏级应用。

## 跨端视觉颜色契约

- Android 颜色语义以 Ligase Host `visual-color-tokens-v1.md` 为跨端权威；当前冻结
  基线是 Host `ac35ee8a`。Android 不另建产品 palette，也不要求 WinUI 与 Material
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

- `serverinfo.LigaseSyncVersion == 1` 且存在 `LigaseSyncPath` 才能进入产品游戏库。
  缺失能力时展示“需要升级 Ligase Host”和“重试”，禁止用旧 applist 回退构建库。
- `GET /ligase/v1/sync` 是游戏集合、类型、名称、UUID、排序、时间、全局分辨率、
  单游戏分辨率和 Host HDR 编码能力的唯一产品权威。
- 同步请求使用 `serverinfo.HttpsPort`、已配对客户端身份、证书固定与现有 NvHTTP
  TLS 行为；不能从 HTTP 端口推导 HTTPS 端口。
- Android 只写串流分辨率。应用的新增、删除与 Host canonical order 只由 Host 管理；
  Android 的排序菜单属于按 Host 元数据生成的本机显示偏好。
- 写入遇到 revision 冲突时重新拉取完整快照，提示用户重新操作，不自动重放旧写入。
- NvHTTP 对非成功响应保留结构化错误体，便于 Sync 写入诊断；日志只能记录契约错误
  JSON 和不含 query/response body 的安全路径摘要。`/launch` 的 `rikey` 等 query
  参数、客户端证书、私钥、TLS 密钥或配对秘密均不得进入日志。
- UI 不直接呈现 Sync v1、404、409、revision 等实现细节；失败状态提供升级 Host、
  检查 Host/网络或重试等下一步。

## 多客户端可见性与配对演进

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
- Ligase 启动的串流必须始终显示可拖动的“串流控制”按钮，不能依赖系统返回键、
  Home、多指手势或高级设置。
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
  - V2353A 从监控桌面进入串流后可见“串流控制”，确认结束后返回原游戏库，
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

Host 的 `revision`、字段内容与 `items[]` 是数据权威。Android 按电脑保存本机显示排序
偏好，但不调用 library sort POST。名称和时间比较只使用 Sync 字段；相同或缺失时间
使用名称与 canonical UUID 作稳定 tie-break。若 Host 未提供任何 `lastPlayedAt`，
“最近游玩”选项禁用并明确提示暂无 Host 记录，禁止使用 Android 启动历史补猜。
下拉刷新成功后对最新 Sync 表重新应用本机显示排序，失败时保留上次内容及显示顺序。

未来“手动排序”属于独立的共享 Host canonical order：Android 进入明确的拖拽编辑态，
仅按 canonical app UUID 提交完整顺序和 `baseRevision`，409 后重拉并要求用户重新
排列，禁止自动重放。该模式在 Host DTO/路由冻结并下发前不展示、不猜接口；名称与
时间排序始终保持纯本机偏好。

有效分辨率优先使用单游戏覆盖，否则继承全局分辨率。该结果会在 Ligase 启动入口
覆盖旧本地分辨率偏好并传入现有 `Game`，Host 在 `/launch` 再次执行同一规则。
恢复继承时显式提交 `resolution: null`。

HDR 分层处理：

- `sync.capabilities.hdrEncodingSupported` 表示 Host 当前可编码 HDR/10-bit；
- Android 独立检测当前显示设备和解码能力；
- 用户只看到最终“HDR 可用/不可用”；真正请求 HDR 必须同时满足 Host 和 Android；
- applist 的旧 `IsHdrSupported` 仅保留在底层兼容对象中，不能作为游戏内容 HDR
  能力或产品库元数据。

## 开发期代码边界

- Sync JSON DTO 与校验、Host 写请求集中在 `LigaseSyncRepository`；UI 不直接拼接路由
  或 JSON。
- `LigaseLibraryAdapter` 只接受成功的 Sync v1 快照，并按 UUID 合并 applist 启动参数；
  旧 `fromGameStream` 产品库回退和临时 appId 身份已删除。
- Compose 只消费明确的加载、就绪、不兼容和同步失败状态；旧电脑首页产品页面已删除，
  避免与“游戏库即首页”长期并存。
- 当前最高结构债务是 `LigaseActivity` 仍同时承担电脑连接协调、同步 UI state、写入、
  启动和传统 View 弹窗。后续功能增长前应拆出可测试的 library state holder/coordinator，
  但不得因此复制第二套状态源或改写稳定的配对与串流链。
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

设备验收至少覆盖 V2353A 手机与 AGS2-AL00 平板，只操作可识别的应用内控件。
手机检查底部导航和内容不遮挡；平板横屏检查左侧导航与自适应游戏网格。

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
