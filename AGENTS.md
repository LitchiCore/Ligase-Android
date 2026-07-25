# Ligase Android 仓库协作规则

本文件适用于整个 Android 仓库。

## 从实时状态开始

- 修改文件前，必须读取本文件、`REQUEST.md`、相关正式文档，并检查当前分支、
  `git status`、完整 diff 与未跟踪文件。
- 当前 checkout 可能由多个任务共享。所有未知修改和未跟踪文件都视为其他 owner
  的工作；先冻结精确文件 allowlist，发生文件或 hunk 重叠时先协调。
- 除非用户明确要求，不得改用隔离 worktree。

## 工具链与设备窗口

- 使用本机已配置的 D 盘 Android SDK 与 Gradle user home。不得把开发机绝对路径
  写入提交的源码或文档。
- 同一时间只允许一个任务运行 Gradle、安装 APK 或操作 ADB。协作时必须明确宣布
  占用窗口，并在完成后立即释放。
- 优先使用已连接的真实设备，不以模拟器替代。设备、Host、驱动、权限或安装条件
  不可用时，必须精确报告 `BLOCKED` 或 `SKIP`；不得通过伪造 UI state、注入
  fixture/数据库或更换产品路径冒充通过。

## 架构边界

- `LigaseActivity` 负责 Android lifecycle 与 legacy composition bridge；
  `app/root` 负责 feature route 组合；`app/navigation` 是产品导航状态的唯一 owner。
  Feature UI 只消费 immutable presentation state 与 typed actions。
- Compose 不得为了绕过缺失的 application seam 而新建第二套 store、ViewModel、
  repository、session owner 或 policy。应先报告所需的 typed state/action contract。
- TouchKit v1 runtime/persistence 与 Touch Layout v2/v3 的 catalog、journal、
  generation repository、editor workspace 必须隔离。禁止双写、按名称或路径猜身份，
  也不得把新布局写回 legacy SharedPreferences。
- 前端任务未经明确授权，不得修改 pairing crypto、Host wire DTO、
  GameStream/RTSP、input dispatch、repository persistence 或 machine-readable
  layout contract。
- 正式协议/schema 及其 machine-readable 资产是字段权威。应用代码与前端文档只做
  链接和投影，不得复制第二份 authority。

## 跨仓库协作流程

- 协调根存在 `AUTHORITY.md` 时，它是 Android、Host、Layouts Web 的 owner 与
  字段位置索引。跨仓库任务开始前必须读取；发现路径过期或 ownership 冲突时报告
  协调任务。该文件由协调任务统一维护，仓库任务不得并发修改。它不替代本仓库的
  schema、API、测试或 owner 文档。
- 领域 owner 提交审议前，必须形成精确 allowlist、稳定文件大小与 SHA-256、
  machine validation 结果及明确排除项。审议资产保持未提交并标记为 `REVIEW`；
  发布新快照即使全部旧 SHA 失效。
- 每个受影响消费端都必须针对同一固定快照独立执行只读复核，并明确返回
  `ACCEPT` 或 `NEEDS_REVISION`。沉默、旧版 ACCEPT、单元测试通过或协调摘要都不
  构成接受。
- 任一端返回 `NEEDS_REVISION` 后，快照退回 owner。owner 只能修订审议资产，
  发布全新 SHA，并重新走所有必要审议。所有必要 reviewer 接受前，禁止生产实现、
  stage authority、兼容兜底或通过其他路径绕过。
- 所有必要 reviewer 均 ACCEPT 后，仍须等待协调任务明确授权，才能精确 stage、
  commit、push machine authority 并回读 remote SHA。生产实现必须获得单独范围，
  按可编译的依赖顺序分块提交。
- 前后端可直接沟通 typed seam 和阻塞，但 review verdict、授权需求、commit SHA、
  runtime mutation 与最终证据都必须同时回报协调任务。`PEER_ALREADY_NOTIFIED`
  只表示已通知，不代表获得更大授权。
- 共享 checkout 中不得 stage、revert、format 或修复 peer WIP。Gradle 与 ADB
  窗口必须显式预约并及时释放；source owner 宣布冻结后，最终门必须基于同一冻结源
  重跑。
- 跨仓库变更必须留在各自 owner 仓库并独立提交。消费者依赖新 authority 或 typed
  API 时，provider commit 必须先到达。报告中必须区分 `REVIEW`、`FROZEN`、
  `TRANSITIONAL`、已实现、已真机验证与阻塞状态。

## 安全与证据

- 未获当前任务明确授权，不得清除设备数据、删除已配对电脑、执行配对、启动或停止
  Host、发起串流、修改 Host 设置或改变用户偏好。
- 自动测试、编译、R8、lint 与 assemble 只证明对应自动门，不能证明真实配对、
  网络写入、串流、TalkBack 语音、process-death 持久化或手机/平板 UI。
- 设备证据必须标明精确冻结源码/APK，并明确区分已完成、阻塞和跳过的检查。

## Git、文档与回报

- 每个任务 final 与 commit handoff 必须声明 `DOC_IMPACT=UPDATED|NONE` 并说明
  理由。用户行为或文案、架构 owner/依赖方向、storage/schema/protocol、
  Activity/lifecycle、权限/安全、构建安装说明或设备验收步骤发生变化时，必须在同一
  提交更新 owner 文档，或先冻结文档契约。通常只有纯机械重构和仅强化既有行为的
  测试可以使用 `NONE`。
- 文档应链接 machine authority，不复制其内容。不得把 planned 工作描述成已实现，
  也不得把临时 SHA 或测试计数写进稳定架构文档。
- 保留所有无关共享修改。只能精确 stage 已批准文件；禁止 `git add .` 和宽泛暂存。
- 结构移动、行为变化、协议/storage 变化与纯证据文档应使用不同的聚焦提交。
- commit/push 前运行与风险相称的测试、`git diff --check`、allowlist 复核及
  secret/绝对路径扫描。push 后回读 remote SHA，并报告本任务 scope 是否 clean。
- 每个阶段按结构化格式回报：阶段/结论、精确文件范围、验证证据、阻塞或后续契约、
  是否需要外部动作、commit/remote 状态、`DOC_IMPACT` 及 peer 是否已通知。
