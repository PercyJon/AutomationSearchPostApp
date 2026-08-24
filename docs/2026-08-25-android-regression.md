# Android 阶段回归记录

## 范围

- 覆盖本轮账户中心、统一登录/设备哈希、任务表单、二级页、操作页和系统返回的 Android 改动。
- 不包含后端“一账号一设备、后台解绑”实现；该项仍依赖可追溯的后端干净基线。

## 自动化验证

- `git diff --check` 通过。
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PautomationApiEndpoint=<受管 HTTPS 服务地址>` 通过。
- `./gradlew :app:connectedDebugAndroidTest` 在 **OnePlus NE2210 / b33aa309 / Android 16** 上通过：**5/5 tests**。
  - 测试只覆盖 Android manifest、无障碍服务声明、HTTP JSON/Authorization 桩和合成 Alpha 图像匹配；不启动抖音、不执行无障碍页面操作、不发送消息。

## 真机回归摘要

- 已按各阶段记录验证登录会话恢复、账户中心、B 端和评论私信表单、设置、任务记录、任务详情、诊断页和系统返回层级。
- 本次回归期间未启动、保存、暂停、继续、停止、重试或采集任何自动化任务；结束后已停止 App 并返回系统桌面。
- ColorOS 对本地测试 APK 及正式 Debug APK 显示了系统安装引导；仅完成了本项目构建产物的安装。设备当前保留的 `com.example.douyinautomation` 版本为 `0.3.4-mobile-login`，并已按受管 HTTPS 服务地址重新构建安装。

## 几何与安全检查

- 本回归不新增代码或几何常量；已完成的 UI 改动均使用 Compose `dp`。
- P0 评论入口、双锚点回退、OCR/视觉安全门、动作节奏与无障碍状态机未在本轮改动。

## 未完成且已记录的后端阻塞项

- 后端工作区存在未提交的移动端登录/controller/schema/service/test 基线改动，且与“一账号一设备、后台设备解绑”实现重叠。
- 为避免覆盖或把未知基线混入 GitHub，本轮未修改后端。后端基线被提交或授权纳入后，应实现并验证：跨设备登录拒绝、管理员设备绑定列表、按账号解绑、App 的服务端退出/解绑同步。
