# Ligase Android 客户端设计

本文档描述 `ligase/compose-client-redesign` 分支的当前产品结构和接口边界。
涉及导航、游戏库、Host 身份、偏好或验收流程的代码变更，应同步更新本文档。

## 产品结构

- 顶级壳层只有 `LigaseActivity`，产品 UI 使用 Jetpack Compose。
- 首页就是游戏库，不再先显示旧式电脑卡片页，也不以 `PcView` / `AppView`
  作为产品入口。
- 首页顶部显示当前电脑。点击后打开独立电脑管理弹窗：
  - 查看并切换所有已发现或手动添加的电脑；
  - 添加电脑；
  - 按 Host UUID 区分并移除旧电脑，删除前二次确认。
- 手机使用底部导航，横屏和平板由 Material 3 adaptive navigation 自动切换为左侧导航。
- 游戏库支持搜索、稳定排序值以及用户可选的列表/竖向海报布局。
- 每个游戏卡片右下角提供独立设置入口；该入口不触发启动，后续承载单应用
  分辨率等串流设置。
- 设置页提供串流输入、明亮/黑暗主题和语言（跟随系统、简体中文、English）。

## GameStream 现有能力

当前直接复用稳定的 Moonlight/GameStream 底层：

- 电脑发现、在线状态与配对；
- applist 的应用名称、Apollo `appUUID`、数字 `appId` 和 HDR 能力；
- `appUUID + appId` 启动、恢复和运行状态；
- GameStream box art；
- 已选择的手柄、键鼠或无外接设备输入模式会传入串流。

客户端不会拼接 Windows 路径、工作目录或命令行。

## 稳定系统入口

- `desktop` / 监控桌面：
  `78A25216-F239-45BD-B4AA-F41C814066E9`
- `virtualDesktop` / 虚拟桌面：
  `8902CB19-674A-403D-A587-41B092E900BA`

在 Host 同步 DTO 接入前，adapter 只用以上精确 UUID 补充系统类型，禁止按名称识别。
虚拟桌面仅在实际 GameStream applist 中出现时显示为可启动。

普通项目暂时只有 GameStream 展示与启动能力；`steam` / `executable` 来源、
`steamAppId`、添加时间、更新时间和最近游玩时间等待 Host 同步 API。

## Host 同步边界

客户端已经预留只含跨平台字段的 domain/DTO adapter。排序机器值固定为：

- `nameAscending`
- `nameDescending`
- `addedNewest`
- `addedOldest`
- `lastPlayedNewest`

当前排序偏好保存在本地并按 Host UUID 隔离。未来 Host API 上线后，以 Host 的
`revision` 和 `sortMode` 为权威；客户端只提交 `baseRevision + sortMode`，
不自行新增或删除主机游戏，也不虚构未上线的 REST 地址。

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
