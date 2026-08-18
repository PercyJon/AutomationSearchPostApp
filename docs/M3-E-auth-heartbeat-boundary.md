# M3-E 授权与 heartbeat 边界

## 已实现

- `SecureAuthStore` 使用 Android Keystore 的 AES-GCM 加密保存 endpoint、license token 和设备标识。
- 加密配置只存在 App 私有 SharedPreferences；日志和 UI 不展示 token 原文。
- `HeartbeatGateway` 定义后端验证接口，`HeartbeatRequest` 只发送匿名设备标识 hash、App 版本和平台。
- `HeartbeatCoordinator` 支持立即验证与周期验证，状态分为：未配置、已验证、授权拒绝、暂时不可用。
- 设置页显示 heartbeat 状态和“立即验证 heartbeat”入口。

## 当前边界

当前仓库没有绑定生产后端 URL、鉴权字段协议或网络客户端。默认网关会明确返回“后端 heartbeat 尚未配置”，不会伪造验证成功，也不会阻塞本地空白消息安全探测。

接入后端时只需提供 `HeartbeatGateway` 实现，并通过安全配置写入 endpoint/token；不需要修改自动化状态机。真实授权拒绝、过期和服务不可用时，后续产品策略再决定是否暂停新任务。
