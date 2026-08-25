# AI 交接文档

> 快照时间：2026-08-25（以本文件创建前后的实际 Git、源码与既有测试记录为准）  
> 本文只描述可追溯的当前状态，不含密码、Bearer Token、私有服务地址或用户数据。

## 1. 当前目标与已确认的需求

### 项目目标

这是一个最低 Android 11 的抖音自动化可行性验证 App。Android 端采用 Kotlin、Jetpack Compose、AccessibilityService、ML Kit OCR 和受限图像回退；后端采用 FastAPI 的 `module_automation` 插件。自动化动作必须在用户授权的无障碍服务内执行，并保留安全暂停、空白消息探测和页面后置确认。

当前已确认的产品方向如下：

1. App 已从“记录 / 设置”底部入口收敛为“首页 / 待办 / 我的”；记录、设置、诊断与账号/设备操作位于“我的”。
2. 使用统一的账号 + 密码登录页；移动端只保存加密后的会话和 `ANDROID_ID` 的 SHA-256 摘要，不保存密码、管理端 JWT 或原始设备 ID。
3. 一个后台账号同一时刻只能绑定一台 Android 设备：同设备重新登录轮换授权，不同设备登录返回冲突；管理员可通过后台 API 查询和解绑绑定；移动端退出应释放服务端绑定。
4. B 端私信和评论区私信表单采用紧凑、uView 风格的蓝色 primary 视觉。评论任务只保留“搜索指定用户”入口。
5. 评论区右侧入口遵循安全优先级：无障碍节点 → 评论气泡受限模板 → OCR 数字栏几何 → 点赞/收藏“双锚点”视觉回退。任何视觉或 OCR 结果均不能直接授权点击，必须再经过几何、页面状态和评论面板后验。

### 当前技术结构

| 范围 | 实际位置 / 设计 |
| --- | --- |
| Android UI 与会话 | `app/src/main/java/com/example/douyinautomation/ui/`，Compose；`LoginScreen.kt` 负责会话门控，`MyPage.kt` 承载账号状态与退出。 |
| Android 自动化 | `automation/` 下的状态机、PageDetector、Selector、OCR、受限图像匹配、任务/记录存储与 HTTP Gateway。 |
| Android 认证与同步 | `AuthModels.kt`、`SecureAuthStore.kt`、`AutomationHttpClient.kt`；会话保存在 Android Keystore 加密存储中。 |
| 后端自动化模块 | `/Users/mac/Documents/abb/all/project202608plus/获客系统/AutomationSearchPost-FastapiAdmin/backend/app/plugin/module_automation/`，按 controller / service / schema / model 分层。 |
| 后端设备绑定 | 移动端登录与退出位于 `mobile/controller.py`；管理员查询/解绑位于 `admin/controller.py`；并发与授权轮换规则位于 `service.py`。 |

## 2. 已完成的改动、涉及文件与关键设计决定

### Android：账户中心、登录、表单与服务端退出

以下提交均已在 Android 远端分支中：

| 提交 | 已完成内容 | 关键文件 |
| --- | --- | --- |
| `d2faf69` | 新增“我的”账户中心及三项底部导航，记录/设置迁入列表型菜单。 | `ui/HomeScreen.kt`、`ui/MyPage.kt` |
| `2e74a9e` | 统一账号密码登录、加密会话门控和设备摘要迁移。服务地址只允许构建参数注入。 | `ui/LoginScreen.kt`、`MainActivity.kt`、`automation/AuthModels.kt`、`automation/SecureAuthStore.kt`、`app/build.gradle.kts` |
| `611383e` | B 端/评论表单横向紧凑布局与入口收敛。 | `ui/HomeScreen.kt`、`ui/MyPage.kt`、`docs/2026-08-25-task-form-horizontal-layout.md` |
| `c44560b` | 移动端退出同步服务端解绑。 | `automation/AuthModels.kt`、`automation/AutomationHttpClient.kt`、`ui/HomeScreen.kt`、`ui/MyPage.kt`、`docs/2026-08-25-account-device-logout-sync.md` |

当前最重要的退出设计为：

- `AutomationHttpClient.logout()` 调用 `POST /automation/mobile/logout`。
- `AuthStore.logout()` 先请求服务端；服务端成功或已返回 401/403 时才清除本地 Keystore 会话。
- 网络/传输错误时保留本机会话，以便用户重试，避免 UI 显示“已退出”但服务端绑定仍存在。
- 登录成功后延迟 1000ms 再首次 heartbeat，规避登录签发事务提交与 heartbeat 的竞争。

### 后端：一账号一设备、后台解绑和移动端退出

后端已提交并推送的提交为 `e48c73e feat(automation): enforce account device binding`。涉及文件：

- `backend/app/plugin/module_automation/service.py`
  - `rotate_mobile_license(...)` 以锁读取当前账号的所有有效绑定；存在不同 `device_id_hash` 时返回 `409`。
  - 同设备登录撤销旧授权并签发新 token；数据库只保存 token 摘要。
  - `logout_mobile_license(...)` 撤销当前授权并释放绑定；无其他有效绑定时相应物理设备标为离线。
- `backend/app/plugin/module_automation/mobile/controller.py`
  - 已接入 `POST /automation/mobile/login` 与 `POST /automation/mobile/logout`。
- `backend/app/plugin/module_automation/admin/controller.py`
  - 已接入 `GET /automation/admin/account-devices` 与 `POST /automation/admin/account-devices/{license_id}/unbind`。
- `backend/app/plugin/module_automation/schema.py`、`backend/tests/test_api_module_automation.py`、`backend/README.md`、`backend/docs/2026-08-25-account-device-binding.md`。

关键决定：未新增表、迁移或更改原有 `/admin/devices` 的物理设备语义；账号—设备管理通过已有有效 License 记录实现。当前“后台可管理”已具备 API 能力，**本次变更中没有发现相应 Web 管理页面**。

### P0：评论区右侧动作栏

`193db41` 及 [`docs/2026-08-24-p0-action-rail-dual-anchor-fallback.md`](2026-08-24-p0-action-rail-dual-anchor-fallback.md) 记录了受限双锚点回退：

- 仅将点赞和收藏作为独立 Alpha 感知模板锚点；分享图标不参与证据链。
- 必须同栏、同尺度、间距符合屏幕比例，且连续两帧稳定，才在两者之间推导评论槽位。
- 该候选的优先级低于现有无障碍、评论气泡模板和 OCR 数字栏；点击后仍要确认评论面板。
- 现有真机路径中双锚点已被检测到，但因 OCR 更高优先级先命中，尚未发生过 `source=dual_anchor_fallback` 的真实点击验证。

## 3. 当前未提交 diff、分支与提交状态

### Android 仓库

- 路径：`/Users/mac/Documents/Codex/2026-08-17/referenced-chatgpt-conversation-this-is-an`
- 分支：`codex/account-device-ui`
- `HEAD`：`c44560b feat(auth): sync mobile logout with server`
- 上游：`origin/codex/account-device-ui`；快照时 ahead/behind 为 `0/0`。
- 最近五次提交：
  1. `c44560b feat(auth): sync mobile logout with server`
  2. `2e9116a docs: record b33aa309 e2e smoke`
  3. `611383e feat(ui): align task form rows`
  4. `2b2c619 docs: record release build status`
  5. `166d241 docs: record android regression`

在创建本文前，实际未提交状态为：

```text
 D App开发交接需求文档.md
?? AGENTS.md
```

- 上述删除和未跟踪文件都不是本轮创建的，不得恢复、暂存或提交，除非文件所有者另行明确授权。
- 本文创建后，`docs/AI_HANDOFF.md` 会成为额外的未跟踪**文档**文件；本轮没有业务代码 diff，也没有暂存内容。

### 后端仓库

- 路径：`/Users/mac/Documents/abb/all/project202608plus/获客系统/AutomationSearchPost-FastapiAdmin`
- 分支：`main`
- `HEAD`：`e48c73e feat(automation): enforce account device binding`
- 上游：`origin/main`；快照时 ahead/behind 为 `0/0`，工作区干净。
- 最近五次提交：`e48c73e`、`7b828f2`、`dc0920e`、`a14bf44`、`dea4a63`。

## 4. 已运行的测试、结果与复现步骤

下面的“通过”来自当前工作区内的阶段记录与已提交文档；**本次交接整理未重新运行构建或真机动作**。当前工作区仍可见 lint、JVM 测试报告和 Debug/Release APK 构建产物，不能仅以产物存在代替重新执行。

| 验证 | 记录结果 | 可复现命令 / 路径 |
| --- | --- | --- |
| Android 静态、JVM、lint、Debug 构建 | 通过；仅记录既有 Material/图标弃用警告。 | `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PautomationApiEndpoint=<受管 HTTPS 地址>`；见 `docs/2026-08-25-task-form-horizontal-layout.md` 与 `docs/2026-08-25-account-device-logout-sync.md`。 |
| Android 设备测试 | `connectedDebugAndroidTest`：5/5 通过。仅覆盖 manifest、服务声明、HTTP 桩和 Alpha 图像匹配。 | 仅在明确授权且设置 `CONFIRM_DEVICE_TEST=1` 后运行显式 instrumentation；日常不运行 Gradle connected test，以免 UTP 卸载 App。见 `docs/2026-08-25-android-regression.md`。 |
| Android Release 构建 | 通过，产物为未签名的 `app-release-unsigned.apk`，未安装或发布。 | `./gradlew :app:assembleRelease -PautomationApiEndpoint=<受管 HTTPS 地址>`；发布仍需签名密钥和发布授权。 |
| 后端 API | `backend/tests/test_api_module_automation.py`：`7 passed`。 | 在后端目录执行 `ENVIRONMENT=dev .venv/bin/python -m pytest tests/test_api_module_automation.py -q`。覆盖同设备轮换、跨设备 409、后台解绑、旧 token heartbeat 403、移动端 logout。 |
| 服务可达性 | 本地与受管 HTTPS 的 OpenAPI 文档均返回 HTTP 200。 | 启动：`ENVIRONMENT=dev .venv/bin/python -m uvicorn main:create_app --factory --host 127.0.0.1 --port 8001`；再检查本地 `/api/v1/docs` 与当前受管 HTTPS `/api/v1/docs`。 |

### 已完成的真机复现

设备为 OnePlus NE2210，序列号 `b33aa309`，Android 16，分辨率 1080×2412。

1. **账户/绑定闭环（通过）**：后台解绑测试遗留绑定 → Android 账号密码登录 → 读取搜索预设、地区、屏蔽词 → heartbeat → “我的”退出。服务端 logout 返回 200，App 回到登录页，有效账号—设备绑定数为 0。全程未打开抖音、创建或执行任务。见 `docs/2026-08-25-account-device-logout-sync.md`。
2. **UI 非执行型冒烟（通过）**：首页 → 我的 → 账号与设备 → heartbeat → 任务记录 → 待办 → B 端私信 → 评论私信，确认横向表单、标题、选择态、服务状态和系统返回；未保存、挂起或启动任务。见 `docs/2026-08-25-b33aa309-mobile-e2e-smoke.md`。
3. **评论自然路径（通过，但双锚点分支未实点）**：`designer × 3 × 1` 空白消息安全探测多次完成。2026-08-25 任务 `designer-20260825-104801` 已处理 3 / 跳过 0 / 失败 0；启动门在调起弹窗消失后判为 `HOME`（213 节点）。评论入口由更高优先级无障碍路径打开，双锚点探测 `like_available=false`，仍无 `source=dual_anchor_fallback`。见 `docs/2026-08-25-waiting-for-home-unknown-diagnosis.md` 与 `docs/2026-08-24-p0-action-rail-dual-anchor-fallback.md`。
4. **后台设备管理页（通过）**：超管打开管理端「自动化管理 → 设备管理」，可见相关内容且有一条记录，操作者确认一切正常。未展示 token、密码或原始设备 ID。见 `backend/docs/2026-08-25-automation-device-admin-page.md`。

## 5. 已排除的假设、尚未解决的问题与日志证据

### 已排除 / 已修复

| 问题 | 已排除或确认的原因 | 结果 |
| --- | --- | --- |
| App 退出后后台仍认为设备在线 | 根因不是 Keystore 清除失败，而是此前根本没有服务端 logout 请求。 | 已通过移动端 logout + 后端撤销修复并真机通过。 |
| 同设备重新登录需被拒绝 | 不是预期；同设备应轮换授权。真正缺失的是同账号、不同设备的冲突检查。 | 已实现全有效绑定锁查询；跨设备返回 409，解绑后可登录。 |
| 首次登录 heartbeat 立即无效 | 不属于设备哈希或 token 解析错误；记录表明是登录签发事务提交与紧随 heartbeat 的竞争。 | Android 登录后延迟 1000ms，真机首次 heartbeat 已验证有效。 |
| 双锚点日志曾缺失 | 不是模板必然不匹配；诊断证明一部分采样帧动作栏尚未显现，显现帧可以检测到两个锚点。 | 已改善；但仍未实测由该低优先级回退执行点击。 |

### 尚未解决 / 仅完成部分验证

1. **稳定可见的抖音首页仍可能被判为 `UNKNOWN`（17:12 / 17:40，17:53 已改善）。** 评论任务导航带 OCR 已把可见推荐首页分类为 `HOME` 并进入搜索；3×2 空白探测完成。仍不得把 OCR 块交给全局 PageDetector。
2. **双锚点实际回退点击尚无真机证据。** 2026-08-25 同一 `designer × 3 × 1` 路径完成 3 位空白探测；评论面板由更高优先级无障碍入口打开，过渡探测为 `like_available=false`，仍无 `source=dual_anchor_fallback`。这不是功能失败，而是低优先级分支的前置条件未出现。
3. **Release 发布尚未完成。** Release APK 已成功构建但未签名；不得将未签名产物作为发布包。
4. **非首页启动：评论面板残留。** 方案一已接线，但 2026-08-25 17:13 真机为**无变化**：视觉评论面板打开，无障碍树 `MAXIMUM_DEPTH` 截断为 149 节点且无面板 chrome，评论任务跳过 OCR，`CommentSurfaceDetector` 未命中，30 秒暂停。下一轮建议只做启动阶段有界评论面板 OCR 确认，仍只 BACK。见 [`2026-08-25-nested-comment-launch-recovery.md`](2026-08-25-nested-comment-launch-recovery.md)。

### 相关日志与记录位置

- 启动 `UNKNOWN`：[`2026-08-25-waiting-for-home-unknown-diagnosis.md`](2026-08-25-waiting-for-home-unknown-diagnosis.md)；历史证据见 `docs/2026-08-24-p0-comment-next-video-and-transition-latency.md`。
- 评论启动恢复、第二视频与 OCR 安全边界：`docs/2026-08-24-comment-search-startup-unknown-recovery.md`。
- 双锚点探测 / 候选 / 优先级日志：`docs/2026-08-24-p0-action-rail-dual-anchor-fallback.md`。
- 账号绑定 / 退出服务端日志和 API 测试：`backend/docs/2026-08-25-account-device-binding.md`、`docs/2026-08-25-account-device-logout-sync.md`。
- 后台设备管理页：`backend/docs/2026-08-25-automation-device-admin-page.md`。
- 评论面板残留启动恢复：[`2026-08-25-nested-comment-launch-recovery.md`](2026-08-25-nested-comment-launch-recovery.md)。

## 6. 接下来 1–3 个最小步骤与验收标准

按最小、可验证、互不混杂的顺序建议如下；每一步开始前均须先建立“现象—复现—预期—实际—单一根因假设”记录。

1. **真机验收加长后的启动 BACK。**
   - 前置：抖音停在打开的评论面板。
   - 验收：确认面板后 BACK 不再把抖音退出；到达首页或搜索栏后继续搜。日志两次 `global_back` 间隔约 2 秒，且不应连按满 5 次仍为 UNKNOWN。

2. **评论任务启动首页有界 OCR（真机已进入搜索）。**
   - 前置：`waiting_for_home_unknown [cause=OCR_NOT_TRIGGERED]` 且首页已稳定可见。
   - 已做：`CommentLaunchHomeOcrPolicy` 仅把顶部/底部导航带 OCR 分类为 `HOME`；搜索仍走节点顺序。
   - 验收：2026-08-25 17:53 同一 3×2 条件进入搜索。后续 `COMPLETED` 的 6 次探测实际都在同一条视频上，见下一项。

3. **换视频用评论区首屏行位置确认（进行中）。**
   - 上一轮指纹确认会把同一列表续翻当成新视频，指纹未变时再滑会跳过已到达的视频。
   - 已做：打开评论不依赖指纹；首屏行比例确认后才清人数；续翻只再滑一次；上滑恢复 `0.84→0.28 / 520ms`。
   - 缩短路径：OPEN_COMMENT_P0 默认跳过私信。主页 BACK 后按 B 端 200ms+150ms×4 等待，避免第二次 BACK 落到作品页。
   - 关面板后连续 3 帧稳定播放器才上滑，闪断不再额外 BACK。
   - 2026-08-25 19:55：用户确认第二个作品已显示，评论首行仍为 0.571；因此首行位置不能否决可见的换片。现改为两次相同且不同于上滑前的关闭播放器指纹确认；首行位置仅作指纹未确认时的保守回退。
   - 验收：同一条件「室内设计师 / 3 视频 / 每视频 2 评论」。日志应有 `changed_stable=true`、`source=stable_changed_player_fingerprint`、三次每视频 2 人处理；无额外 BACK 关闭已确认的新作品评论区。
   - **止损 2026-08-25 21:40**：首作品与第二作品各处理 2 人；第二作品已由 `changed_stable=true` 确认并进入 `video_index=1`。第三作品在安全评论入口自动隐藏后，首次中性画布点按成功但未得到新鲜入口，动作栏验证超时。累计换片修复超过五轮，停止继续改动。
   - 已排除：3×2 预设、跳过私信、第二作品未换片、以评论首行 0.571 判旧视频。
   - 恢复前最小诊断：第三作品入口可见与自动隐藏前后各记录无文本节点几何摘要，确认中性媒体画布为何不可重新定位；待确认后再改动。
   - **验证通过 2026-08-25 22:21**：改为「室内设计师 / 4 视频 / 每视频 1 评论 / 跳过私信」以缩短单作品循环。首作品使用“作品”标签下方网格锚点；节点缺标签时 OCR 仅定位标签，店铺/商品/橱窗图块排除。日志确认 `video_index=1/2/3`，最终 `video_count=4`、`COMPLETED`；4 位均为 `PROFILE_OPENED`，未进入私信。
   - **悬浮窗 2026-08-25 22:56**：用户结果首行“关注”锚点曾被应用自有蓝色悬浮窗覆盖，三条 `designer` 重试均处理 0 人并安全失败。现悬浮窗保持显示但执行期触摸穿透，服务发布实时边界；OCR/视觉模板动态排除该边界，不依赖当前样式或固定坐标。`designer` 用户行验证已通过，后续首作品动作栏为独立待验问题。

4. **仅当节点、评论模板和 OCR 均无法形成评论入口时，再验收双锚点实点击。**
   - 动作：不调整阈值、时序或优先级；只在右侧动作栏清晰可见且更高优先级入口都失败的真实视频上走既有空白消息安全路径。
   - 验收：连续两帧稳定、`source=dual_anchor_fallback`、评论面板后验成功；无点赞/收藏/分享或真实私信。

## 7. 必须遵守的项目规则与风险点

### 强制规则

1. 每轮修复只允许一个根因假设和最小修改范围；用同一复现步骤验证并记录“改善 / 无变化 / 变差”。同一问题连续五轮未解决时，停止第六次修改，列出五轮证据、最多三个剩余假设和一个最小诊断动作，等待确认。
2. 所有新增 Android 几何尺寸必须使用 Compose `dp` 或 `dp(value) = value * density`；点击和手势只能使用 0–1 的屏幕比例坐标。禁止将 dp 尺寸判断与比例坐标混用，也禁止新增固定 px 尺寸、距离、行高或裸坐标点击。
3. 自动点击顺序必须为：Accessibility 节点 → OCR 结果 + 几何验证 → 页面状态确认 → 最后的安全兜底。OCR/模板结果不能直接点击；不得误触点赞、收藏、分享、地址、AI 解析、验证码或风控页面。
4. 涉及无障碍流程、页面跳转、点击、手势或状态恢复必须真机验证，并记录设备、路径、日志和结果。除非明确授权，不启动抖音、不保存/启动任务、不发送真实消息。
5. 每个阶段完成后需要输出修改内容、修改文件、测试/真机结果、新增几何常量检查、dp 归一化检查和下一步，并在通过后提交、推送。

### 当前风险

- Android 8+ 的 `ANDROID_ID` 与应用签名/用户作用域相关。生产必须保持签名证书连续；变更签名后会产生新摘要，应先后台解绑再重新登录，不能误判为同一设备。
- 后端 token 仅可在签发响应中返回；日志、文档、截图和提交中不得写入 token、密码、管理端 JWT、原始 Android ID 或私有服务地址。
- 受管 HTTPS 地址仅通过 Gradle `-PautomationApiEndpoint=...` 注入，不能硬编码到源码或提交到配置。
- `:app:connectedDebugAndroidTest` 的 Gradle UTP 清理可能卸载 App；日常真机回归优先 `adb install -r` 和经明确许可的显式 instrumentation。
- 当前 Android 工作区已有 `App开发交接需求文档.md` 删除与 `AGENTS.md` 未跟踪状态，均为外部既有改动；继续开发时不得擅自覆盖或混入提交。
- 图像模板匹配只是受限回退，不是通用 OpenCV/多机型图像识别承诺。多机型验证前不得扩大其用途到登录、验证码、风控或任意 UI。

## 快速入口

- Android 开发规范：[`AGENTS.md`](../AGENTS.md)
- Android 项目概览：[`README.md`](../README.md)
- Android 变更记录：[`CHANGELOG.md`](../CHANGELOG.md)
- 账号退出同步：[`2026-08-25-account-device-logout-sync.md`](2026-08-25-account-device-logout-sync.md)
- 真机 UI 冒烟：[`2026-08-25-b33aa309-mobile-e2e-smoke.md`](2026-08-25-b33aa309-mobile-e2e-smoke.md)
- P0 双锚点：[`2026-08-24-p0-action-rail-dual-anchor-fallback.md`](2026-08-24-p0-action-rail-dual-anchor-fallback.md)
- 后端绑定记录：`/Users/mac/Documents/abb/all/project202608plus/获客系统/AutomationSearchPost-FastapiAdmin/backend/docs/2026-08-25-account-device-binding.md`
