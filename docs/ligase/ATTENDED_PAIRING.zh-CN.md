# Ligase Android attended pairing v1

## 产品流程

当 public `serverinfo` 同时返回
`LigaseAttendedPairingVersion=1` 与非空
`LigaseAttendedPairingPath` 时，Android 只执行 attended pairing：

1. Android 建立一次性请求并显示 `XXXX-XXXX` 安全短码；
2. 用户在电脑上确认相同短码并选择“可操作”或“仅观察”；
3. Host Allow 后释放既有 GameStream `getservercert`；
4. Android 在任何 challenge 前校验 Host X.509 DER SHA-256；
5. 配对成功后使用 pinned HTTPS 读取 Sync 和
   `LigaseClientAccessMode`。

普通页面不显示 PIN、48990 或管理账号。capability 缺失时只在明确的“高级兼容
配对”确认之后进入旧 PIN 流程。

## 实现边界

- `AttendedPairingRepository` 只负责 public HTTP wire，不记录请求或响应内容。
- `AttendedPairingCoordinator` 拥有 generation、单调 deadline、最多 1 Hz poll、
  held legacy pairing、终态和 best-effort secret cleanup。
- `AttendedPairingCrypto` 使用 APK 固定 BouncyCastle lightweight X25519、
  HKDF-SHA-256 和 ChaCha20-Poly1305。
- `AttendedPairingJson` 拒绝 invalid UTF-8、duplicate/unknown property、
  非 canonical base64url 和非整数 token，并生成 RFC 8785 所需的确定性 JSON。
- 首个 `getservercert` 请求携带 canonical `ligasepairingrequestid`；后续 challenge
  沿用既有 GameStream ABI，不携带该参数。
- 用户取消立即停止常规 poll 和 held call；DELETE 204 后仅执行一次 authenticated
  status probe，不能从 204 猜测最终状态，也不能自动重放。
- token、PIN、private/shared/pairing key、nonce、plaintext、ciphertext 和证书全文
  禁止进入日志、Toast 与异常文本；自持 mutable buffer 在终态 best-effort 覆盖。

## 权限

权限由 paired HTTPS `serverinfo` 的 `LigaseClientAccessMode` 提供：

- `operate`：正常启动、Sync 写、结束会话和输入。
- `observe`：只读游戏库；启动、游戏设置写入、全局设置写入和输入发送禁用。
- unknown/custom：按 observe。
- 缺失：表示当前响应不是可用的 paired-client 权限投影，不能从 403、名称或
  permission bitmask 猜测。

Host 仍是权限修改与设备删除的唯一写入口。

## 权威测试资产

- `attended-pairing-v1-vectors.json`
  SHA-256 `7A924B4FFB9C813F9D9450D85428FE3148F3317C789715D1DE815D07CC98B3D3`
- `attended-pairing-v1-vectors.schema.json`
  SHA-256 `53F08A996123F77CAC5A459437A2F24DE2338E3172B1A7C8DB4A9CED9E000B81`

JVM 测试必须验证原始资源 SHA、119 IDs/顺序、完整 crypto positive、12 个
Android cancel case，以及 duplicate/UTF-8/base64url 严格边界。

## 真机联合门

完成状态必须分别记录：

- V2353A：Allow、Reject、timeout、用户取消 one-shot probe、Activity 重建、DER
  pin、HTTPS Sync、operate launch/input/cancel。
- AGS2-AL00：宽屏配对 UI、另一权限模式 observe、只读成功和所有写/启动/输入
  fail-closed。
- 系统旋转设置前/中/后不被 Ligase 写入；串流只使用 Activity 局部方向，退出后
  回到原系统旋转约束。

未完成的门必须标为 PARTIAL 或 BLOCKED，不能用单元测试代替真机证据。
