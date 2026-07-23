# Artemis TouchKit 发布手册

本文档用于维护 `LitchiCore/moonlight-android` 的 TouchKit 发行版。目标是让每个公开 APK
都能追溯到明确提交、使用同一签名连续升级，并且不会覆盖原版 Artemis。

## 当前发行身份

- 应用名称：`Artemis TouchKit`
- Release 包名：`com.litchicore.artemis.touchkit.noir`
- Debug 包名：`com.litchicore.artemis.touchkit.noirdebug`
- 当前公开测试线：`20.2.6-touchkit-beta.x`
- 标签规则：`v` + `versionName`，例如 `v20.2.6-touchkit-beta.4`

Debug 与 Release 是两个独立应用，Android 不会自动迁移它们的数据。由 Debug 切换到
Release 前，应先在 **设置 > 云游戏操作 > 展开 > 导入与导出布局** 中导出布局，再在
Release 版中导入。主机配对信息和完整应用设置目前不会随布局文件迁移。

## 一次性创建签名密钥

公开 Release 不应使用 Android Debug Key。Debug Key 可能在重装开发环境后变化，使用它
发布会导致后续 APK 无法覆盖升级。

在 PowerShell 中从仓库根目录运行：

```powershell
.\scripts\create-touchkit-release-keystore.ps1
```

脚本默认在 `%USERPROFILE%\.artemis-touchkit` 创建：

- `artemis-touchkit-release.p12`：长期 Release 私钥；
- `signing.properties`：Gradle 使用的密钥路径、别名和随机密码。

脚本不会覆盖已有密钥，也不会把密码打印到终端。Windows 下会收紧目录权限，只允许当前
用户访问。Gradle 默认读取这个目录；在另一台电脑恢复时，也可以设置环境变量
`ARTEMIS_TOUCHKIT_SIGNING_PROPERTIES` 指向恢复后的 `signing.properties`。

### 必须备份什么

应把整个 `.artemis-touchkit` 目录备份到至少两个可信位置，例如加密移动硬盘和加密密码库
附件。不要只备份 `.p12` 而遗漏密码文件，也不要把任一文件提交到 Git、上传到 Release 或
发送到聊天群。

私钥一旦丢失，已经安装公开版的用户将无法直接升级到新签名的 APK，只能卸载重装并丢失
未导出的本地数据。

## 版本规则

每次发布都必须同时修改 `app/build.gradle` 中的两个字段：

- `versionName`：用户看到的版本，例如 `20.2.6-touchkit-beta.4`；
- `versionCode`：Android 比较升级顺序的整数，每次发布必须递增。

版本阶段：

1. `beta.x`：真实设备测试和界面调整；
2. `rc.1`：功能冻结，只修阻断发布的问题；
3. `20.2.6-touchkit.1`：首个稳定版。

Git 标签必须在 `versionName` 前加 `v`，并指向生成 APK 的同一个提交。

## 发布前构建与测试

先确认工作区和提交：

```powershell
git status -sb
git log -1 --oneline
```

运行单元测试并构建签名 Release：

```powershell
.\gradlew.bat testNonRoot_gameDebugUnitTest assembleNonRoot_gameRelease --no-daemon
```

如果没有签名配置，Release 构建会直接失败，不会悄悄生成一个无法安装的未签名 APK。

APK 位于 `app\build\outputs\apk\nonRoot_game\release\`。公开测试优先提供：

- `app-nonRoot_game-arm64-v8a-release.apk`：绝大多数现代手机和平板；
- 如有明确需求，再提供其他 ABI 或 universal APK。

## 校验 APK

使用 Android SDK 中最新的 `apksigner` 检查签名：

```powershell
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { 'D:\Android\Sdk' }
$buildTools = Get-ChildItem (Join-Path $sdkRoot 'build-tools') -Directory |
  Sort-Object { [version]$_.Name } -Descending |
  Select-Object -First 1
& (Join-Path $buildTools.FullName 'apksigner.bat') `
  verify --verbose --print-certs `
  app\build\outputs\apk\nonRoot_game\release\app-nonRoot_game-arm64-v8a-release.apk
```

输出至少应包含 `Verifies`、`Number of signers: 1`，并且证书 SHA-256 指纹应与第一次
公开版记录一致。对 minSdk 21 的 APK，同时具有 v1 和 v2 签名是正常结果。

计算并记录 SHA-256：

```powershell
Get-FileHash `
  app\build\outputs\apk\nonRoot_game\release\app-nonRoot_game-arm64-v8a-release.apk `
  -Algorithm SHA256
```

Release 页面必须写出完整 SHA-256。下载者可以据此确认文件没有损坏或被替换。

## 实机验收

每次发布至少在一台真实设备运行同一个 Release APK；涉及屏幕比例或跨设备迁移的改动，
还应增加一台不同尺寸的手机或平板：

1. 全新安装后能够启动、配对并进入虚拟桌面；
2. 导入原神或战雷示例布局，位置和尺寸基本正确；
3. 左手按住鼠标键，右手在空白处滑动或轻点，指针不跳到边缘；
4. 摇杆松手后方向立即释放；
5. 组合键、按键轮盘、定时长按和软键盘均能触发；
6. 导出布局，再导入为新布局，不覆盖原布局；
7. 从旧 Release 执行 `adb install -r` 升级后，主机和布局仍然保留。

已有公开版本后，第 7 项是强制检查。

## 创建 GitHub Pre-release

建议流程：

1. 确认发布提交已经推送，测试结果和实机验收记录完整；
2. 在生成 APK 的同一提交上创建版本标签；
3. 新建 GitHub Release 并勾选 **Set as a pre-release**；
4. 上传 arm64 Release APK；
5. 写明版本、提交、SHA-256、主要功能、已知限制和布局迁移方法；
6. 确认远端标签、分支和 Release 资产对应同一提交，并复核 GitHub 显示的资产摘要。

不要把本地 Debug APK改名后上传。文件名中的 `release`、应用内版本、Git 标签和 Release
标题必须相互对应。

## Obtainium 更新

应用内 Obtainium 链接指向 `LitchiCore/moonlight-android`，并只匹配 arm64 Release APK。
因此公开资源应保持文件名：

```text
app-nonRoot_game-arm64-v8a-release.apk
```

Release 标签使用 `v版本号`。改变标签或 APK 命名规则前，应同步修改 `app/build.gradle`
中的 `obtainium_app_url` 并实际测试一次订阅和更新。

## 回滚与紧急处理

- 发现普通问题：将 Release 标记为 Pre-release，修复后递增 `versionCode` 发布下一 Beta；
- 发现严重崩溃或输入失控：从 Release 页面撤下 APK并在说明顶部标记停止使用；
- 不要复用已经公开的标签或覆盖同名 APK，这会破坏哈希记录；
- Android 通常不允许直接降级。需要回滚时，应基于旧代码生成更高 `versionCode` 的修复版；
- 涉及签名密钥泄露时立即停止发布。普通 GitHub APK 分发无法无缝更换签名，应明确通知
  用户导出布局、卸载并重装。

## 发布完成记录

每次发布至少记录：

- Git 标签和完整提交 SHA；
- `versionName` 与 `versionCode`；
- APK 文件名、大小和 SHA-256；
- 签名证书 SHA-256 指纹；
- 手机和平板的系统版本及验收结果；
- 已知问题和下一版本计划。
