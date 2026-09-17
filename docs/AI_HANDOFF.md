# AI 交接文档

> 快照时间：2026-09-17  
> 本文描述当前可继续开发的仓库状态。不要把密码、Bearer Token、管理端 JWT、原始 Android ID 写入日志、截图或新文档。

## 1. 这是什么项目

Android 端抖音自动化获客 App（可行性验证 / 生产试用中）。技术栈：

- Kotlin、Jetpack Compose、AccessibilityService、ML Kit 中文 OCR
- 最低 Android 11（API 30），`applicationId` 为 `com.example.douyinautomation`
- 当前版本：`versionCode 16` / `versionName 0.3.6-device-logs`
- 后端是独立 FastAPI Admin 仓库里的 `module_automation` 插件，不在本 Git 仓库内

自动化点击必须走：无障碍节点 → OCR + 几何校验 → 页面状态确认 → 最后才允许安全兜底。禁止直接点固定坐标，禁止误触点赞、收藏、分享、地址、AI 解析、验证码或风控页。

开发规范见仓库根目录 [`AGENTS.md`](../AGENTS.md)。UI 色/间距/组件只改 `ui/theme` 和 `ui/components`，见 [`docs/UI_SYSTEM.md`](UI_SYSTEM.md)。

## 2. 仓库与分支（交接当时）

| 项 | 值 |
| --- | --- |
| Android 仓库 | `/Users/mac/Documents/Codex/2026-08-17/referenced-chatgpt-conversation-this-is-an` |
| GitHub | `https://github.com/PercyJon/AutomationSearchPostApp.git` |
| 交接前功能分支 | `codex/account-device-ui` |
| 交接动作 | 已把该分支 **fast-forward 合并进本地 `main`** |
| 后端仓库 | `/Users/mac/Documents/abb/all/project202608plus/获客系统/AutomationSearchPost-FastapiAdmin` |
| 后端模块 | `backend/app/plugin/module_automation/` |

合并后请在 `main` 上继续开发。若其他人要从 GitHub 拉代码，还需要把本地 `main` **推送到 `origin/main`**（本轮只做了本地合并，没有 push）。

不要提交：`app/release/`、`node_modules/`、`local.properties`、APK、日志、截图里的 token。

## 3. 当前产品形态

1. 底部三栏：首页 / 待办 / 我的。记录、设置、诊断、账号设备在「我的」。
2. 未登录只显示账号 + 密码登录页，**不再显示「服务地址」输入框**。
3. 服务地址由构建参数注入，当前 `gradle.properties` 为：

   ```properties
   automationApiEndpoint=http://hk.sxjjerp.com:9999/
   ```

   注意是 **http 不是 https**。`LoginEndpointPolicy` 允许该主机明文 HTTP；`network_security_config.xml` 已对该域名开放 cleartext。回环 `127.0.0.1` / `localhost` 仍可用于 `adb reverse` 本地调试。
4. 本机只加密保存移动端 license 和 `ANDROID_ID` 的 SHA-256。密码和管理端 JWT 不落盘。
5. 一个后台账号同一时刻应只绑定一台设备：同设备重新登录轮换授权，不同设备冲突；退出时先请求服务端 logout，成功或 401/403 才清本地会话。
6. 登录成功后延迟 1000ms 再首次 heartbeat，避免签发事务未提交就被判授权无效。

## 4. 2026-09-17 刚完成的登录修复（必须知道）

### 现象

真机用账号密码登录，页面提示 **「验证码不能为空」**。

### 根因

当前后端是 FastapiAdmin，共享登录策略开启了 **滑块验证码**：

1. `GET /api/v1/system/auth/captcha/get` → `{ enable, key, img_base }`
2. 滑块模式 `img_base` 为空，需 `POST /api/v1/system/auth/captcha/slider/complete`
3. 再 `POST /api/v1/automation/mobile/login`，带 `username` / `password` / `device_id_hash` / `captcha_key` / `captcha`

旧客户端只发账号密码，所以被拒绝。

### 已改代码

- `AutomationHttpClient.login()` 提交前自动取验证码并完成滑块，再带 `captcha_key` 登录。
- 登录页仍然只有账号、密码，不展示验证码控件。
- 若后端改成 **图片验证码**（`img_base` 非空），当前 App 会明确报「移动端暂不支持」，不会盲填。
- 仪器测试：`AutomationHttpClientInstrumentedTest.mobileLoginCompletesSliderCaptchaBeforePostingCredentials`

### 验证

- 对当前服务器用同一协议验证：错误密码变为「账号或密码错误」；正确账号返回「移动端登录成功」。
- **当时电脑没有连接安卓设备**，没有在 App UI 上再点一次登录。下一位请重新编译安装后，在真机走一遍登录。

相关文件：

- `app/src/main/java/com/example/douyinautomation/ui/LoginScreen.kt`
- `app/src/main/java/com/example/douyinautomation/automation/LoginEndpointPolicy.kt`
- `app/src/main/java/com/example/douyinautomation/automation/AutomationHttpClient.kt`
- `app/src/main/res/xml/network_security_config.xml`
- `gradle.properties`

## 5. 代码地图

| 范围 | 位置 |
| --- | --- |
| 登录门控 / 首页 / 我的 | `app/src/main/java/com/example/douyinautomation/ui/` |
| 认证、心跳、HTTP | `automation/AuthModels.kt`、`SecureAuthStore.kt`、`AutomationHttpClient.kt`、`LoginEndpointPolicy.kt` |
| 无障碍与导航状态机 | `DouyinAccessibilityService.kt`、`DouyinNavigationController.kt` |
| 页面识别 | `PageDetector.kt`；OCR 只做局部裁剪，不能交给全局分类器直接点击 |
| 任务与记录 | `AutomationStore.kt`、远程任务 `RemoteTaskSyncQueue.kt` |
| 营销话术 | `MarketingContentStore.kt` |
| 设备日志 | `DeviceLogStore.kt`、`DeviceLogUploader.kt` → `POST /automation/mobile/device-logs` |
| 应用内更新 | `AppUpdateController.kt` |
| 评论入口 / 换视频 | `CommentPrivateMessageRuntime.kt`、`CommentEntryStateMachine.kt`、`NextVideoAdvancePolicy.kt` |
| 构建注入地址 | `app/build.gradle.kts` 的 `AUTOMATION_API_ENDPOINT` |

后端关键入口：

- 移动端登录/退出：`backend/app/plugin/module_automation/mobile/controller.py`
- 账号设备绑定：`service.py` 的 `rotate_mobile_license` / `logout_mobile_license`
- 管理端解绑：`GET /automation/admin/account-devices`、`POST /automation/admin/account-devices/{license_id}/unbind`
- OpenAPI：`http://<host>:9999/docs`

## 6. 如何构建与安装

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

`gradle.properties` 已带服务地址，不必再传 `-PautomationApiEndpoint`，除非要临时改成回环：

```bash
./gradlew :app:assembleDebug -PautomationApiEndpoint=http://127.0.0.1:8001/
adb reverse tcp:8001 tcp:8001
```

辅助脚本：

```bash
scripts/check-device.sh <adb-serial>
scripts/build-test.sh
scripts/install-debug.sh <adb-serial>
scripts/capture-device-screen.sh <adb-serial>
scripts/ensure-accessibility.sh <adb-serial>
```

不要日常跑 `:app:connectedDebugAndroidTest`：Gradle UTP 清理可能卸载 App。真机用 `adb install -r`，instrumentation 仅在 `CONFIRM_DEVICE_TEST=1` 时显式执行。

真机自动化任务启动前：

1. `adb shell am force-stop com.ss.android.ugc.aweme`
2. 再开本次任务，避免复用上一页残留
3. 每 30 秒看日志；出现 `COMPLETED` / `FAILED` / `poc_paused_for_manual_handoff` 立即接管，不要空等

## 7. 功能分支上已合入 main 的主要能力（相对旧 origin/main）

这些提交原先只在 `codex/account-device-ui`，合并后都在本地 `main`：

- 统一登录、设备哈希、服务端 logout、一账号一设备
- 首页/待办/我的信息架构与蓝色 UI
- B 端私信、评论获客、营销话术、App 内更新、设备运行日志上传
- 评论换视频、悬浮窗触摸穿透、OCR/模板排除自有 overlay
- 2026-09-17：固定 HTTP 服务地址、去掉登录页地址框、登录前完成滑块验证码

更细的阶段记录在 [`CHANGELOG.md`](../CHANGELOG.md) 和 `docs/2026-08-25-*.md`、`docs/2026-08-24-*.md`。

## 8. 尚未解决 / 接手后优先注意

1. **登录真机回归。** 协议已通，App UI 在本机未再装包点一次。装上当前 `main` 后用后台账号登录，确认进入首页且「我的」heartbeat 为已授权。
2. **图片验证码未做 UI。** 若后台把滑块改成图片验证码，需要补输入框，或让后台对移动端关闭图片验证码。
3. **评论动作栏双锚点回退从未被真机实点。** 更高优先级的无障碍入口通常先命中。不要为了测它去放宽点赞/收藏安全规则。
4. **第三作品评论入口自动隐藏。** 2026-08-25 评论换片在第三作品动作栏验证超时处止损过；后续有一次 4 视频跳过私信的 `COMPLETED`。再改前先做最小诊断，不要无目的重构导航。
5. **Release 未签名。** `assembleRelease` 能出包，但不能当发布包。
6. **签名变更会换 `ANDROID_ID` 摘要。** 生产证书必须连续；换签后要后台解绑再登录。
7. **几何约束。** 新尺寸必须 dp 或屏幕比例；禁止新的固定 px 行高。历史债：`StructuralUserRowDetector` / `DouyinNavigationController` / `OcrUserResultRowDetector` 仍有 px 常量，见 `AGENTS.md` 第 5 节，不要在无关修复里顺手大改。

## 9. 必须遵守的修复纪律

1. 每轮只允许一个根因假设、最小改动、同一复现步骤、记录改善/无变化/变差。
2. 同一问题连续五轮未解决：停止第六次改代码，列出五轮证据和最多三个剩余假设，等确认。
3. 无障碍 / 跳转 / 点击 / 手势 / 状态恢复必须真机验证，记录设备、路径、日志、结果。
4. 除非明确授权，不启动抖音、不保存/启动任务、不发送真实私信。

## 10. 建议的下一步（一次只做一件）

1. 连接真机，安装当前 `main` 的 Debug APK，验证登录 → 首页 → 我的 heartbeat。
2. 若登录仍失败，先看页面原文：是验证码、设备已绑定，还是网络/明文 HTTP 被拦。
3. 需要给其他人远程协作时，再 `git push origin main`（需仓库写权限）。
4. 不要在未确认登录可用前，开始改评论导航或发版。

## 快速入口

- 开发规范：[`AGENTS.md`](../AGENTS.md)
- 项目说明：[`README.md`](../README.md)
- 变更记录：[`CHANGELOG.md`](../CHANGELOG.md)
- UI 系统：[`UI_SYSTEM.md`](UI_SYSTEM.md)
- 账号退出同步：[`2026-08-25-account-device-logout-sync.md`](2026-08-25-account-device-logout-sync.md)
- 评论双锚点：[`2026-08-24-p0-action-rail-dual-anchor-fallback.md`](2026-08-24-p0-action-rail-dual-anchor-fallback.md)
