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
| Display capability | `AndroidStreamDisplayCapabilityProbe` 与 `StreamDisplayPolicy` | Display modes 只产生设备最大值、16:9档位和FPS能力原因；推荐不写选择，Host resolution authority不变 |
| Input | `InputSelectionCoordinator` | UI 展示输入模式、稳定设备选择和布局摘要，不按设备名或易变 deviceId 猜测 |
| Settings | application owners 的组合投影 | Settings route 不创建第二份偏好或 Host state |

串流触摸路径由 application 层投影为 closed `EffectiveStreamingTouchMode`：
`DIRECT_TOUCH`、`ABSOLUTE_POINTER` 或 `TRACKPAD`。该投影用于验收时同时记录用户公开选择
与实际生效链路，不把 `mouse_mode_list` 或内部布尔偏好泄漏给 UI。direct touch 的 Android
pointer edge 逐字映射为 Host touch DOWN/UP/MOVE/CANCEL；pointer 模式的 tap release 使用
async main handler，避免释放边被串流帧同步屏障延迟。viewport mapper 以实际视频 View 的
平移、缩放和尺寸归一化并裁剪黑边输入；旋转只消费旋转后 View 尺寸，不按 DPI 猜坐标。

`InputSelectionCoordinator` 与 `LigasePreferences` 是本机输入配置的唯一 owner。全局 profile
只使用 closed 的 overlay 与 cloud-touch mode；单游戏覆盖只按 Host Sync 提供的 canonical
lowercase app UUID 读写，不按名称、Steam App ID 或 launch ID 猜测。launch plan 携带已解析的
typed profile，`Game` 不再重新读取另一份全局触控选择。observe 权限只投影 `HIDDEN` 且只读，
不会清除或改写用户原有全局/单游戏偏好；离线本机全局设置仍可编辑。

分辨率与帧率保持两条正交 authority：global/app resolution 仍由 Host Sync revision writer
拥有，Android 只用本机 display modes 投影设备最大、最佳16:9、720p/1080p/1440p/2160p
及明确能力原因；FPS 是 Android 本机 launch 设置，支持跟随显示与固定30/60/90/120档。
推荐只作标记，不自动写偏好；launch intent 携带已解析的精确 FPS，`Game` 不二次猜测。

Library 的 Host identity/binding/cover consumer 由 `AndroidSyncV1StrictCodec`、
`HostLayoutBindingResolver` 与 `HostAppAssetRepository` 分责：codec 在进入 session 前执行
closed wire 校验；resolver 只把 Host exact binding 与已验证的本机 v3 revision 对齐；
appasset repository 只产出 current、rejected 或已验证 stale cover。三者都不写 Host 字段、
不建立本地 identity authority，也不让 presentation 接触 raw JSON、路径或未验证图片字节。
`HostVerifiedCoverLoader` 由选中 Host 的 coordinator 唯一创建和关闭，以 canonical app UUID
与 expected SHA 作为 generation/cache key，合并同 key 请求，并在进程重启读缓存时重新计算
实际 SHA 与 PNG 边界。有 cover authority 的卡片只能走该 loader；legacy
`CachedAppAssetLoader/getBoxArt` 不得并行或回退。
Library 卡片只消费这条链路产生的安全 immutable projection：Steam 身份仅显示
Host 已验证的 provider 与 canonical 十进制 App ID；布局只显示 closed binding state。
`RESOLVED` 仅表示“本机布局已就绪”，UI 不从 UUID、文件、名称或 revision 反推额外详情。

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
- 编辑器的“添加键盘按键”由前端真实键盘浮层收集多选结果，并仅向 application
  owner 提交一次 `Set<InputCode>`；中心堆叠、canonical 顺序、UUID、z-order 与
  journal 原子性仍由 v3 editor session 唯一负责。编辑器通过 canonical
  `InputCode` 投影真实键名，未知 code 只显示通用安全标签；可选功能说明写回既有
  typed `Appearance.description`，不建立 UI sidecar。Host 下载、publish 与
  runtime/export/share 不在此能力内。

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
    圆形控件在拖动 resize handle 的本地预览阶段即保持等宽等高，不等待提交后纠正。
   形状值由v3 schema与strict codec约束，前端只更新typed properties。
11. 浮层分成互斥的“控件属性”和“布局设置”。控件属性仅消费当前选中控件的typed
    payload、说明、形状、层级与删除动作；布局设置只承载新增、批量键盘、
    保存与保留草稿。两者共用唯一Activity ViewModel/Session，不创建第二store。
12. 单控件不透明度已从协议与UI移除；布局设置只通过Session的布局级typed action
    提交全局不透明度，canvas从root draft投影统一合成全部控件。拖动滑杆仅本地预览，
    释放时提交一次，失败回到authoritative readback；前端不得循环修改element。
    focus轮廓和缩放手柄属于编辑器chrome，保持可见。
13. 属性层用共享安全键名投影显示`W`、`Space`等可读名称，不展示numeric InputCode；
    canonical identity仍由typed model持有。文字字号按控件实际短边在安全上下限内
    缩放，空间不足时单行省略。
14. COMBO与RADIAL只在控件属性浮层编辑。组合键与轮盘动作的按键选择复用真实键盘
    picker，但确认只调用一次对应payload typed action，不创建canvas element。
    轮盘动作由backend生成并返回稳定actionId；前端按actionId执行标签、组合键、排序
    与删除，失败保留当前编辑上下文。布局设置的新建入口分别一次提交完整Combo chord
    或包含2至16项的Radial request；element/action ID、位置和默认值全部由Session
    policy拥有。运行态仍由typed gate明确为不可用。

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
- `HostEndpointCoordinator` 继续唯一消费 `ComputerManagerService` 的LAN discovery更新；
  首页配对候选只是ONLINE+NOT_PAIRED canonical UUID的安全投影，按UUID/已验证证书去重，
  不按名称或地址建立身份，也不绕过 `HostPairingCoordinator` 的确认与Host审批。
- `StreamMenuEntryPolicy` 冻结Ligase会话的非持久菜单入口与legacy隔离；
  `StreamClipboardPolicy` 是双向剪贴板类型/UTF-8 64KiB边界的唯一Android owner。
  `GameMenu`仅发起显式确认，`Game`只在policy接受后调用既有NvHTTP剪贴板ABI。
- Library pull-to-refresh indicator只消费Material theme token，不持有独立明暗色或刷新状态。
- 网络测试与码率建议通过closed typed UI state投影。Host path/quality machine contract未冻结时，
  production固定为`HOST_CONTRACT_NOT_READY`，不发网络请求、不选择路径，也不写码率或网络设置。
- Device Presence v1 heartbeat由application coordinator单独调度：前台选中且已认证Host，或实际
  串流Activity的证书绑定Host，均只形成一个generation target；立即发送后按单调5秒tick、
  single-flight执行。切换、后台、onStop与销毁只失效generation，不发送final heartbeat，也不
  创建后台service。Android仅投影本机请求ACK/失败，不把响应解释为Host presence真值。
- Resolution Dialog使用受window、safe drawing与IME约束的全高容器；标题和底部actions固定，
  preset、能力原因、自定义字段与错误信息位于单一可滚动内容区。响应式布局不改变A2 policy。

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
