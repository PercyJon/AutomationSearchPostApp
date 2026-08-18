# M3-F Android HTTP Gateway

## 目标

Android 端现在可以通过一个受控的 HTTP 边界访问后端移动端接口。客户端不依赖 Root 或系统提权，授权 Token 保存在 Android Keystore 加密的私有配置中，网络请求只发送 Bearer Token，不把 Token、用户原文或 OCR 原文写入 Logcat。

## 已接入接口

所有路径默认拼接 `/api/v1`；如果配置地址已经以 `/api/v1` 结尾则不会重复拼接。

- `POST /automation/mobile/heartbeat`
- `GET /automation/mobile/search-presets`
- `GET /automation/mobile/regions`
- `GET /automation/mobile/block-keywords`
- `GET /automation/mobile/tasks`
- `POST /automation/mobile/tasks/{id}/claim`
- `GET /automation/mobile/tasks/{id}/progress`
- `POST /automation/mobile/tasks/{id}/checkpoint`
- `POST /automation/mobile/tasks/{id}/records`

响应统一按 FastAPIAdmin 的 `{success, msg, data}` 包装解析。HTTP 错误和业务失败会变成可诊断的网关异常；搜索预设采用远程优先、24 小时本地缓存、内置词回退。

## Android 设置

设置页可以录入：

1. HTTPS 后端地址；
2. 移动端授权 Token；
3. 设备标识，默认使用 Android ID。

保存时由 `SecureAuthStore` 使用 Android Keystore 加密。heartbeat 会把设备标识转换为 SHA-256 摘要后发送，服务端不会收到原始设备标识。

## B3 断点与记录

`AutomationHttpClient` 已实现任务领取、进度读取、分页断点提交和用户处理结果提交的数据模型与 JSON 映射。当前自动化控制器仍以本地检查点为实时执行主状态；下一步将把已确认的远程任务 ID 映射到本地任务会话，再启用断点/记录的异步上报。这样网络短暂不可用时不会阻塞抖音页面操作。

## 验证策略

- JVM：`./gradlew :app:testDebugUnitTest`
- 静态检查与 APK：`./gradlew :app:lintDebug :app:assembleDebug`
- HTTP/Android JSON 契约测试已编译到 `androidTest`，因为 Android 平台的 `JSONObject` 在 JVM 桩环境不可运行。
- 不在日常真机上运行 `connectedDebugAndroidTest`，避免 Gradle UTP 的卸载清理影响已安装应用。需要真机时使用显式 `adb install -r` 和经确认的 instrumentation 命令。

