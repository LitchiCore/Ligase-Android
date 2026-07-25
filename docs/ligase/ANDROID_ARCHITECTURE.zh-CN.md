# Ligase Android 架构与验收边界

本文记录 Android 客户端的当前依赖方向、唯一状态 owner 和已经验证的产品边界。它不
复制 Host 协议、Touch Layout schema 或机器可读契约；这些内容以各自权威文档和测试
fixture 为准。

## 壳层与依赖方向

```text
LigaseActivity（composition / Android lifecycle / legacy ABI bridge）
  -> app/root/LigaseRootContent（feature 组合与内部 route）
    -> app/navigation（当前 page、底栏/侧栏、focus 与 Back 映射）
    -> feature/*/ui（Compose route 与组件）
      -> feature/*/presentation（immutable UI mapping）
        -> feature/*/application（唯一 coordinator / ViewModel owner）
          -> feature/*/domain
          -> feature/*/infrastructure 或 data
```

- `LigaseScreen.kt` 保留 Activity 使用的兼容入口；真正的 feature 组合位于
  `app/root`，导航状态只由 `app/navigation` 负责。
- `LigaseActivity` 负责 Android 生命周期、Service/legacy Java ABI 和 ViewModel
  composition，不应重新实现 feature policy，也不应让 Compose 直接拿 repository、
  `ComputerDetails`、证书、地址或线程。
- UI 只消费 immutable state 和 typed actions。presentation 可以格式化与组合安全文案，
  但不能持久化、发网络请求或创建第二个 store/ViewModel。

## Feature 唯一 owner

| Feature | 唯一 owner / policy | UI 边界 |
| --- | --- | --- |
| Host | `HostEndpointCoordinator` 与 legacy registry transport | 管理列表只投影在线、离线、需配对和选择状态；地址、MAC、UUID 不进入 semantics |
| Pairing | `HostPairingCoordinator`、`AttendedPairingCoordinator`、对应 ViewModel | UI 只显示设备名、安全码、typed countdown/终态；legacy PIN 仅高级兼容入口 |
| Library | `LibrarySessionViewModel`、`LibrarySessionStore`、`LibraryHostCoordinator` | last-success 内容与 connectivity/loading/error 分离；离线不清库 |
| Manual sort | `LibraryManualSortCoordinator` 与既有 action/session | UI 只提交完整 published UUID 顺序；409 重拉且不重放 |
| Streaming settings | `LibraryStreamingSettingsCoordinator` | global/app 分辨率使用同一 revision 与 operate gate；UI 不持 repository |
| Stream launch | `StreamLaunchCoordinator` 与 legacy launcher | 输入选择、分辨率、HDR、码率在 launch 前由 typed policy 合成 |
| Bitrate | `StreamBitrateState`、`StreamBitratePolicy`、`StreamBitratePreferences` | UI 遍历 owner 给出的 preset；custom parser、推荐与持久化不在 Compose |
| Input | `InputSelectionCoordinator` | UI 展示输入模式、稳定设备选择和布局摘要，不按设备名或易变 deviceId 猜测 |
| Settings | application owners 的组合投影 | Settings route 不创建第二份偏好或 Host state |

## Touch Layout v1 与 v2

### v1：现有运行时

- v1 TouchKit renderer、`KeyBoardController`、旧 layout loader 和 SharedPreferences 继续
  服务现有串流运行态。
- v1 的 layout ID、动态元素 map 和旧 game store 不是 v2 identity 或 content schema。
- v2 不双写 v1，不从文件名、显示名、numeric appid 或到达顺序猜 identity。

### v2：Catalog 与本机 Creator

- Core A：strict JSON/JCS、schema/cross-field 验证和 canonical content。
- Core B：catalog eligibility、variant projection 与显式 preference policy；设计参考 DPI/
  resolution 不参与排序或自动选择。
- Core C：verified content、local generation 与安全 UI projection；UI 不见 raw、path、
  hash 或 SharedPreferences。
- Core D：Source Registry、generation/last-success、来源状态与冲突处理。没有真实 video
  viewport 时 variant 为只读第三态，select 必须拒绝且零写。
- Workspace：`LayoutV2EditorSession`、版本化 journal 和 generation repository 是本机
  Creator 的唯一 owner。journal 保护未提交草稿但不冒充正式保存；正式保存必须完成
  descriptor + artifact 原子写入、readback、Registry 注册后才删除 journal。
- 已验证能力包括：无 Host 从 Settings 进入同一布局大厅、空白草稿、0/1/2/3/8 typed
  控件、旋转/后台与进程恢复、显式 resume/discard、原子保存，以及重启后
  `LOCAL_COPY / READY / contentVerified`。
- 未验证或未授权的能力不得补造：4/5/6/7/9 的生产内容、Host catalog 下载、publish、
  runtime cutover、preview/export/share。

### 计划中的黑色横屏编辑器

最终 v2 编辑体验将采用独立横屏沉浸式黑色 TouchKit 画布，而不是当前 Compose 卡片式
画布。计划遵守以下不变量：

1. Hall Workspace 先强制 checkpoint journal 并释放当前 owner。
2. Activity Intent 只携带 canonical opaque draft ID。
3. 独立 Activity 的 lifecycle owner 显式恢复并独占草稿；退出时 flush/close，Hall
   重新枚举。
4. canonical integer canvas 等比 `contain` 到黑色内容区，letterbox 不参与布局坐标；
   不使用 DPI 或物理屏幕推断 runtime compatibility。
5. 拖动/缩放可在 View 内逐帧 preview，但每次手势只在 pointer-up 提交一次 typed action。

上述内容在 handoff 与 viewport mapper 的后端实现提交前均为 **PLANNED**，不能作为当前
APK 已交付或已验收能力。

## 设备验收矩阵

| 设备 | 角色 | 当前证据边界 |
| --- | --- | --- |
| Xiaomi M2007J17C | 手机竖屏与横屏 | v2 Creator 创建、typed 控件、旋转、force-stop 恢复、保存后 READY 与重启回读通过 |
| AGS2-AL00 | 宽屏/平板 | 大厅与编辑器宽屏布局、旋转、编辑、保存与 READY 轻量门通过 |
| V2353A | 历史手机 | 仅保留旧提交的 library/manual/stream 等证据；不能替代当前源码验收 |

统一限制：

- 没有授权生产来源时，不注入 fixture 来制造 v2 catalog、模板或只读类型。
- 没有 `NEW_HOST_READY` 时，不宣称 attended pairing、Host catalog、真实写回、
  disconnect/quit 或串流 runtime 通过。
- 虚拟桌面驱动未安装时记录 `SKIP_DRIVER_NOT_INSTALLED`，不能换用桌面入口伪造通过。
- ADB 未授权时记录 `ADB_AUTHORIZATION_REQUIRED`；系统安装确认页不能当作无人触碰安装
  成功。
- 每个最终 APK 的 JVM、Release R8、lintVital、Debug assemble 和设备证据必须对应同一
  冻结源码；旧 APK 或旧 Host 身份不向后继提交自动继承。

## 开发环境与提交纪律

- Android SDK 与 Gradle cache 的开发机位置是本机配置，不写进产品文档或源码。
  `local.properties` 只留本机且不得提交。
- 标准调试变体为 `nonRoot_gameDebug`。常用门：

  ```powershell
  .\gradlew.bat :app:testNonRoot_gameDebugUnitTest
  .\gradlew.bat :app:assembleNonRoot_gameRelease
  .\gradlew.bat :app:lintVitalNonRoot_gameRelease
  .\gradlew.bat :app:assembleNonRoot_gameDebug
  ```

- 共享 checkout 中先检查 `git status`/`git diff`；构建和 ADB 串行；只精确 stage 自己的
  allowlist，禁止 `git add .`。
- 结构迁移、行为修改和协议/存储变更应分开提交。没有明确授权时，前端不得修改 Host
  wire、pairing crypto、RTSP/GameStream/input dispatch、repository persistence 或
  Touch Layout machine authority。
