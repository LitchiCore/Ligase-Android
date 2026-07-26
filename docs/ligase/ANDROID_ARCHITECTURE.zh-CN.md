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

## Touch Layout v3

### v1：已退出产品路径

- Hall、Input、Root 与 Activity 不再提供 v1 布局选择、创建、编辑、预览或恢复入口。
- 产品启动也不读取 v1 layout ID、SharedPreferences profile 或旧 game store；v3 runtime
  完成前由 typed gate 明确返回不可用，不以默认布局或隐藏入口回退。
- v1 残余实现只为后续精确删除保持短期可编译，不构成产品能力，也不会迁移或双写。

### v3：唯一内容契约与本机 Creator

- v3 machine authority 位于本仓库已冻结的 schema、API review 与向量资产；应用文档只
  链接该权威，不复制字段定义。
- Core A 提供 strict JSON/JCS、cross-field 验证、canonical content 与精确cutover gate。
- Core B 提供独立的 journal、generation repository、Workspace/Activity owner、
  adaptive anchor geometry、full-overlay mapper及generation-bound gesture commit。
- `LayoutV3EditorSession`和Activity lifecycle ViewModel是编辑状态的唯一owner；Compose
  只消费immutable projection和typed actions，不读取raw、path、hash或extensions。
- journal只保护未提交草稿，不冒充正式保存；正式保存仍须完成generation原子写入及
  readback。v2 production owner已移除；v3不读写v1存储。
- committed v3 generation只能由Workspace/Repository严格枚举并投影为typed本机卡片；
  Hall不读取路径、raw、hash或文件名。卡片可继续编辑，但不宣称runtime可执行。
- 本阶段不包含真实PC键盘多选浮窗、Host下载、publish或runtime/export/share。

### 黑色横屏编辑器

v3 编辑体验采用独立横屏沉浸式黑色TouchKit画布，而不是壳层内的卡片式编辑画布。
实现遵守以下不变量：

1. NEW_V3由独立Activity在完整immersive overlay bounds稳定后创建canvas；existing
   draft只通过opaque ID恢复，Intent不携带content或路径。
2. 独立Activity lifecycle owner独占草稿；退出时flush/close，Hall重新枚举。
3. canvas与完整edge-to-edge control overlay一致；system inset、cutout和视频letterbox
   不缩小或重排坐标。
4. 位置由adaptive anchor与offset表达；绘制和逆映射只调用v3权威geometry，前端不推导
   anchor、舍入或越界规则。
5. 拖动/缩放可在 View 内逐帧 preview，但每次手势只在 pointer-up 提交一次 typed action。
6. 黑色 canvas 始终占满 Activity 可用内容区。新增、属性、层级与保存操作位于可收起的
   半透明浮层；浮层开关不得改变 canvas 测量结果或控件像素位置。
7. 浮层只拦截自身 bounds，不以透明全屏层禁用画布。点控件主体会选中并直接打开浮层；
   浮层依据控件映射后的像素中心自动放到相反侧，未被覆盖的画布仍可选择和拖动。
   Back 或浮层内的显式关闭操作负责收起浮层。
8. 右下角缩放手柄只提交 resize，视觉与命中区均受控件渲染尺寸比例限制；控件主体的
   其余区域优先用于 drag，三种形状遵循同一手势仲裁。
9. 细调位置采用方向键排布；轻点移动一个 canonical unit，长按只在 UI 内逐帧 preview，
   释放时最多提交一次typed nudge，Activity停止或组合销毁不会留下repeat job。
10. 键盘键与鼠标按钮的新建默认形状为圆形；编辑浮层可显式切换圆形、圆角矩形或矩形。
   形状值由v3 schema与strict codec约束，前端只更新typed properties。

handoff、exclusive lease、adaptive mapper与全屏浮层Activity已进入生产代码；真实手机/
平板交互仍必须以对应冻结APK的设备证据为准，自动测试不能替代真机验收。

## 设备验收矩阵

| 设备 | 角色 | 当前证据边界 |
| --- | --- | --- |
| Xiaomi M2007J17C | 手机竖屏与横屏 | 历史v2测试数据证据不再作为当前v3验收；v3门按对应冻结APK重新执行 |
| AGS2-AL00 | 宽屏/平板 | 大厅与编辑器宽屏布局、旋转、编辑、保存与 READY 轻量门通过 |
| V2353A | 历史手机 | 仅保留旧提交的 library/manual/stream 等证据；不能替代当前源码验收 |

统一限制：

- 没有授权生产来源时，不注入 fixture 来制造v3 catalog、模板或只读类型。
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
