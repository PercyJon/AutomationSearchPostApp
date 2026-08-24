# 2026-08-25 统一登录会话与设备指纹安全存储

## 阶段目标

在不重建既有任务、无障碍或页面导航结构的前提下，完成 App 的账号密码登录入口、会话门控，以及本机设备标识由原始 Android ID 向 SHA-256 设备哈希的迁移。

## 改造基线

| 项目 | 改造前 | 本阶段结果 |
| --- | --- | --- |
| 入口 | 设置页混合填写服务地址、账号、密码、Token 和设备 ID | 未登录时统一进入账号 + 密码登录页 |
| 服务地址 | 操作员在 UI 手工输入 | 仅从构建参数 `-PautomationApiEndpoint=…` 注入；正常登录页不展示 |
| 本机会话 | Keystore 密文内存放原始 Android ID | 新密文仅存 `device_id_hash`；旧密文首次读取时原地迁移 |
| 页面访问 | 无会话也可直接进入主界面 | 有可用移动端授权才进入既有首页；退出本机登录后回到登录页 |
| 首次 heartbeat | 登录后立即请求 | 等待后端签发事务提交后再验证一次 |

## 登录后首个 heartbeat 修复记录

### 当前错误现象

真机首次登录可进入首页，但“我的 / 账号与设备”随即显示“授权无效”；稍后手动点击 heartbeat 验证又显示“授权有效”。

### 稳定复现步骤

1. 在统一登录页输入有效测试账号与密码。
2. 登录成功后立即打开“我的”。
3. 首次状态显示授权拒绝。
4. 等待后在“账号与授权”点击“立即验证 heartbeat”。

### 预期与实际差异

预期：登录签发的移动端授权首次 heartbeat 就显示“已验证”。

实际：第一次请求被后端以无效授权拒绝，后续同一授权可验证通过。

### 单一根因假设

后端通过请求级事务签发移动端授权；登录响应返回时，新 token 的事务提交与紧随其后的 heartbeat 存在竞争。因此它不是账号、设备哈希或 token 解析错误。

### 最小修改

只调整 `AuthStore.login()`：成功写入 Keystore 和内存会话后，延迟 1000ms 触发第一次 heartbeat。没有修改重试策略、自动化动作、手势、OCR、坐标或后端现有文件。

### 结果

改善：使用相同“退出登录 → 账号密码登录 → 我的”路径，首次状态直接为“已授权 / 授权有效”；随后冷启动仍能恢复会话并进入主界面。

## 修改范围

- `app/build.gradle.kts`
  - 新增构建期 `AUTOMATION_API_ENDPOINT` 字段；默认空值，不将临时服务地址写入源码。
- `app/src/main/java/com/example/douyinautomation/ui/LoginScreen.kt`
  - 新增统一登录页和由加密会话驱动的应用入口门控。
  - 仅提供账号、密码和密码显隐；密码不进入 SavedState。
- `app/src/main/java/com/example/douyinautomation/MainActivity.kt`
  - 改为从会话门控进入既有 `AppHomeScreen`。
- `app/src/main/java/com/example/douyinautomation/automation/AuthModels.kt`
  - `AuthConfig` 使用 `deviceIdHash`；新增 64 位 SHA-256 格式校验和会话 `StateFlow`。
  - heartbeat 直接发送已存哈希，不再二次哈希。
- `app/src/main/java/com/example/douyinautomation/automation/SecureAuthStore.kt`
  - 新写入只保存 `device_id_hash`；读取旧版 `device_id` 时计算哈希并立即用新密文覆盖。
- `app/src/main/java/com/example/douyinautomation/ui/HomeScreen.kt`
  - 移除设置页中服务地址、Token、原始设备 ID 和手工授权保存入口；保留账号摘要与手动 heartbeat 诊断按钮。
- `AutomationHttpClient` 与认证相关 JVM/设备测试
  - 更新为明确传递设备哈希。

## 验证结果

### 静态与构建验证

```text
git diff --check
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  -PautomationApiEndpoint=<受管 HTTPS 服务地址>
```

结果：通过。JVM 认证模型测试覆盖无效端点、缺失 token 和非 SHA-256 设备值拒绝。

### 后端连通性

- 本地健康检查：通过。
- 外部受管 HTTPS 健康检查：通过。
- 移动端账号登录：第一候选测试密码被后端拒绝；按授权尝试第二候选后登录成功。密码未记录在日志、文档或本机会话。

### 真机验证

| 项目 | 结果 |
| --- | --- |
| 设备 | OnePlus NE2210 / `b33aa309` / Android 16 |
| 登录页 | 账号、密码、显隐按钮和首次设备绑定说明可见 |
| 登录失败 | 后端“账号或密码错误”可在密码不泄露的情况下显示 |
| 登录成功 | 成功进入既有首页；系统密码管理器提示时选择“不保存” |
| heartbeat | 修复后首次显示“已授权 / 授权有效” |
| 退出 | “我的 → 退出登录”清除本机密文并返回统一登录页 |
| 冷启动 | 已登录会话可从新格式密文恢复并进入首页 |
| 自动化安全 | 未启动任务、未打开或操作抖音、未输入或发送私信；完成后停止 App 并回到桌面 |

## 几何与安全检查

- 新页面尺寸、圆角、控件最小高度和间距均为 Compose `dp`；未新增 px 常量。
- 没有新增 OCR、图像匹配、屏幕比例点击、手势时长或无障碍动作。
- 密码仅在提交期间驻留在内存；移动端 Bearer token 不写入日志；原始 Android ID 仅在登录时短暂读取，或为一次性迁移旧密文而读取，之后不会被新格式持久化或发送。

## 后续与阻塞记录

1. 后端当前工作区仍含预先未提交的移动端登录改动，覆盖账号—设备绑定所需的 controller/schema/service/test 文件；本阶段没有修改、暂存或提交这些文件。
2. 当前后端的 `rotate_mobile_license()` 只撤销“同一账号 + 同一设备”的旧授权，尚未满足“一个账号只允许一台设备、后台可解绑”的最终产品规则。该项将在可追溯的后端干净基线建立后实施。
3. 当前退出登录仅清除本机会话；服务端撤销接口与后台设备解绑管理将在后端绑定阶段一并接入。
4. 下一独立阶段：在不改动自动化运行链的前提下，收紧 B 端私信和评论私信表单的文案与控件比例，并统一其视觉风格。
