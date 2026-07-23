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
- 游戏库支持搜索、稳定排序值以及用户可选的列表/竖向海报布局。
- 每个游戏卡片右下角提供独立设置入口；该入口与卡片启动点击隔离，用于选择
  “使用全局设置”或“自定义分辨率”。
- 游戏的发现、添加、删除、可执行路径和 Steam App 管理只存在于 Ligase Host。
  Android 不提供添加、导入、扫描或删除游戏入口，也不通过 API 修改游戏集合。
  空游戏库引导用户到电脑端 Ligase Host 添加游戏，并仅提供刷新。
- “添加电脑”属于 Android 的连接管理能力，与“添加游戏”严格区分。
- 设置页提供串流输入、明亮/黑暗主题和语言（跟随系统、简体中文、English）。
- 高级串流设置采用渐进披露：全局分辨率位于设置页，单游戏覆盖位于卡片设置；
  均不进入首次启动主路径。

## 强制 Ligase Sync v1 产品边界

- `serverinfo.LigaseSyncVersion == 1` 且存在 `LigaseSyncPath` 才能进入产品游戏库。
  缺失能力时展示“需要升级 Ligase Host”和“重试”，禁止用旧 applist 回退构建库。
- `GET /ligase/v1/sync` 是游戏集合、类型、名称、UUID、排序、时间、全局分辨率、
  单游戏分辨率和 Host HDR 编码能力的唯一产品权威。
- 同步请求使用 `serverinfo.HttpsPort`、已配对客户端身份、证书固定与现有 NvHTTP
  TLS 行为；不能从 HTTP 端口推导 HTTPS 端口。
- Android 只写排序和串流分辨率。应用的新增与删除只由 Host 管理。
- 写入遇到 revision 冲突时重新拉取完整快照，提示用户重新操作，不自动重放旧写入。
- NvHTTP 对非成功响应保留结构化错误体，便于 Sync 写入诊断；日志只能记录契约错误
  JSON，不能记录客户端证书、私钥、TLS 密钥或配对秘密。
- UI 不直接呈现 Sync v1、404、409、revision 等实现细节；失败状态提供升级 Host、
  检查 Host/网络或重试等下一步。

## 多客户端可见性与配对演进

- Host 后续 Sync item 的 `publishedToClients` 在 Android DTO 中必须是 nullable
  Boolean；字段缺失按可见处理，即 `hostPublished = value != false`。
- 本机隐藏按规范化小写 `(hostUniqueId, appUuid)` 分区，只存 Android 本地，
  不上传、不改变 Host revision；有效可见性为 Host 已发布且不在本机隐藏集合中。
- `desktop` 和 `virtualDesktop` 首版均不可隐藏。“已隐藏游戏”只能恢复本机隐藏，
  不能覆盖 Host 全局隐藏。Android 仍不能写游戏集合或 Host publication。
- attended-pairing v1 已完成跨端安全审议，但尚未实现。实现必须使用独立
  `AttendedPairingRepository` / `AttendedPairingCoordinator`，按 Host 冻结契约执行
  X25519、HKDF-SHA-256、ChaCha20-Poly1305、RFC 8785 JCS、DER 证书绑定、
  双端 `XXXX-XXXX` 安全码、120 秒单调时钟超时及 fail-closed 清理。
- 普通配对 UI 最终不显示 legacy PIN；旧 PIN 仅在 attended pairing 完成手机/平板
  allow、reject、timeout、replay、restart 和 re-pair 验收前保留为高级兼容路径。

## 保留的 GameStream 传输 ABI

Ligase 不兼容旧 Apollo 产品配置或 UI，但在替换串流传输之前继续复用：

- 电脑发现、在线状态与配对；
- `serverinfo` 的 Host 身份、配对、在线/运行状态、HTTPS 端口及远程发现/WOL 字段；
- 成功取得 Sync v1 后，applist 仅提供应用 UUID 到数字 `appId` 的启动映射；
- `appUUID + appId` 启动、恢复和运行状态；
- `appasset(appid)` 封面；
- 现有 RTSP、视频、音频、加密和输入链；
- 已选择的手柄、键鼠或无外接设备输入模式会传入串流。

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

排序机器值固定为：

- `nameAscending`
- `nameDescending`
- `addedNewest`
- `addedOldest`
- `lastPlayedNewest`

Host 的 `revision` 和 `sortMode` 为权威；本地偏好只作为同步完成前的适配状态。
客户端提交 `baseRevision + sortMode`。

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
游戏库同步、排序/分辨率写回、冲突恢复或 HDR 已端到端通过。部署新核心后还需
逐项完成真实双端联合验收。

2026-07-23 已在 V2353A 对隔离 Host `10.168.1.191:49989` 完成真实联合验收：
Sync GET、UUID-only merge、排序、全局/单游戏分辨率、恢复全局继承、409 重拉且
不重放、HDR 分层以及携带 `appuuid + appid` 的 1600×900 physical desktop launch
均通过。AGS2-AL00 已验证横屏左侧导航、空电脑引导、手动添加与 Host 身份识别；
第二台设备 legacy PIN 因隔离 Host 49990 管理凭据不可恢复而未完成提交，不能宣称
平板配对/Sync 端到端通过。
