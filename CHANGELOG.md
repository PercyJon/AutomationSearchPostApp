# CHANGELOG

本文件记录每次改动的日期、版本、修改内容、已验证项、未解决问题与已知限制（交接规范 §12）。不含真实密码、Token 或私密地址。

---

## [未发布] 2026-08-22 —— M2 P0 三项卡点修复（调试任务竞态 / 启动不在首页 / 评论区结构误识别）

### 修改内容

- [`MainActivity.kt`](app/src/main/java/com/example/douyinautomation/MainActivity.kt) 修复 M2.1「调试任务未真正触发」在二次投递场景的残留缺陷：
  - `commentP0LaunchNonce` 由实例字段迁移到 `companion object`（`@Volatile` 静态），`recreate()` 后不再归零。
  - `onCreate` 仅在 `openCommentP0 && commentP0LaunchNonce == 0` 时赋初始 token=1，避免进程冷启动重复赋值。
  - `onNewIntent` 的 `OPEN_COMMENT_P0` 分支 `commentP0LaunchNonce += 1` 后 `recreate()`，使重投递意图获得单调递增的启动令牌。
  - 新增 `p0_launch_oncreate` / `p0_launch_new_intent` 诊断日志。
- [`HomeScreen.kt`](app/src/main/java/com/example/douyinautomation/ui/HomeScreen.kt)：
  - `AppHomeScreen` 新增 `commentP0LaunchToken` 参数，并用 `LaunchedEffect(commentP0LaunchToken)` 在重投递时把 `section` 强制拉回 `HOME`、再把 `showCommentTask` 置 true——修复根因：上一次 `onClose()`（经 `openRecordsTab()` 的 recreate）会把 `section` 持久化为 RECORDS，导致 `CommentTaskScreen` 根本未被组合。
  - `CommentTaskScreen` 自启动闩锁键改为 `autoStartToken`，`autoStartTriggeredForToken` 用 `rememberSaveable` 记录已触发的令牌，令牌变化即重新武装。
  - 新增 `p0_launch_show_comment_effect` / `p0_autostart_effect` 诊断日志（含 canRun/ready/errors/snapshot 快照）。
- [`DouyinNavigationController.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt) 修复 M2.2 恢复循环卡死在 SEARCH_ENTRY：
  - `recoverInitialSurface()` 循环分支与最终探针分支的 `PageKind.SEARCH_ENTRY` 现在无条件 `enterKeyword(current)` 并 `return`，不再受 `requireHome` 约束；因为从聚焦的搜索框按 BACK 只会收起键盘、永不离开该页，会耗尽后退预算。

### 已验证项（真机 OnePlus NE2210 / b33aa309）

- 冷启动 P0 闭环：`task_started` → 搜索/用户标签/用户结果 → `p0_first_user_ocr_probe [stable=true]` → `user_profile_postcondition` → `comment_runtime_started_at_profile_handoff` → `comment_leading_row_skipped [candidate_count=2]`（首条作者评论被排除）→ `comment_runtime_terminal [outcome=COMPLETED]` → `comment_task_completed`。
- M2.1 重投递闭环（无 force-stop、无重开无障碍）：对已运行实例再次 `am start --ez OPEN_COMMENT_P0 true` → `p0_launch_new_intent [nonce=2]` → `p0_autostart_effect [canrun=true, triggeredfor=0]` → `task_started` → `comment_leading_row_skipped` → `comment_runtime_terminal COMPLETED` → `comment_task_completed`。
- M2.3：`comment_leading_row_skipped — 首条头像对应的是被排除的行（如作者本人评论），跳过并处理下一条有效评论 [candidate_count=2, first_avatar_top=1139, first_candidate_top=1386]`。
- 构建：`./gradlew testDebugUnitTest lintDebug assembleDebug` → **BUILD SUCCESSFUL**（46s，55 tasks）。

### 未解决问题

- M3 有界真机回归（5/10/20 条）+ 匹配词过滤 + 分支回归（直播/私密/无作品/置顶/0评论）。
- `DouyinNavigationController.kt` 已约 5000 行，待 P0 闭环后分拆（用户指定优先级）。

### 版本

- `0.3.4-mobile-login`（versionCode 14）

---

## [未发布] 2026-08-21 —— M0 环境核验与基线回归

### 修改内容

- 正式接管开发，无代码改动。
- 建立 `CHANGELOG.md` 作为变更记录载体。

### 已验证项

- 构建：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → **BUILD SUCCESSFUL**（14s，55 tasks）。
- 单元测试：**177 个**全部通过（28 个测试结果文件）。
- Lint：**38 个 issue**（37 Warning + 1 Hint，无 Error），`abortOnError=true` 下通过。
- APK 产物：`app/build/outputs/apk/debug/app-debug.apk`（约 108.6 MB）。
- 环境：
  - JDK：默认 Temurin 21（另有 JDK 17 可用，`jvmTarget=17` 兼容）。
  - Android SDK：`android-35` 平台 + `build-tools 35.0.0` 齐全（compileSdk 35）。
  - ADB：36.0.0，设备 `b33aa309` 在线（OnePlus NE2210，Android 16 / SDK 36，满足 minSdk 30）。

### 未解决问题

- 待 M1 推进：P0 评论私信真实闭环（含 A5 页面类型评估）。

### 已知限制

- 尚未执行真机 instrumented 测试（README 警示 `connectedDebugAndroidTest` 会卸载应用，改用 `adb install -r` + 显式 `am instrument`）。

### 版本

- `0.3.4-mobile-login`（versionCode 14）

---

## [未发布] 2026-08-21 —— M1-A5 页面类型评估（无代码改动）

### 评估结论

原优化项 A5 认为「`PageDetector` 缺少 `VIDEO_SURFACE` / `COMMENT_SURFACE` 页面类型，状态机依赖 `UNKNOWN` 兜底」存在稳定性风险。经读取当前代码（Codex 并行更新后的版本），结论修正为：**该问题已被等效方案解决，无需新增 `PageKind`**。

### 依据

- [`CommentEntryStateMachine.kt`](app/src/main/java/com/example/douyinautomation/automation/CommentEntryStateMachine.kt:188) 的 `CommentEntrySignalDetector.observe()` 不再仅依赖 `PageDetector` 分类，而是通过结构化信号判断视频页与评论面板：
  - `hasVideoSurface = page == UNKNOWN && (commentButton != null || (hasVideoMarker && 大尺寸可见节点))`
  - `commentSurface = CommentSurfaceDetector.detect(context)`
  - `hasCommentEntry = VideoCommentButtonDetector.find(context) != null`
- 状态机在 [`CommentEntryStateMachine.kt:84`](app/src/main/java/com/example/douyinautomation/automation/CommentEntryStateMachine.kt:84) 仅当「既无视频面、又无评论面板、又无作品排序目标」时才暂停，确定性分支已由结构信号接管。

### 后续动作

- A5 从「M1 前置阻塞项」降级为「观察项」，不再阻塞 P0 闭环。
- 进入 M1 前仍会在真机复核 `VideoCommentButtonDetector` / `CommentSurfaceDetector` 对目标机型（OnePlus NE2210）的有效性。

### 已知限制

- 未修改任何代码；本评估仅基于静态阅读。

---

## [未发布] 2026-08-21 —— M1 前代码现状确认（无代码改动）

### 确认结论

交接文档 §10 的两个 P0 卡点在当前代码（Codex 并行更新后）中**已修复**，无需重复开发。

### 卡点 1：调试任务未触发（§10.1 竞态）——已修复

- [`AutomationStore.kt:85`](app/src/main/java/com/example/douyinautomation/automation/AutomationStore.kt:85) 新增 `serviceCommandReady`，与 `serviceConnected` 区分，仅在无障碍服务订阅命令后才置位。
- [`HomeScreen.kt:918`](app/src/main/java/com/example/douyinautomation/ui/HomeScreen.kt:918) 的 `canRun = state.serviceCommandReady && errors.isEmpty() && snapshot != null`，`LaunchedEffect(autoStartP0, canRun)` 等服务真正就绪后才触发，消除 Compose 初始化与服务连接间的竞态。

### 卡点 2：启动时抖音不在首页（§10.2 无节点盲后退）——已修复

- [`DouyinNavigationController.kt:4837`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt:4837) 的 `scheduleInitialObservation()` 在 `currentWindowContext()` 与 `recentInitialTargetContext()` 均为 null 时，不再直接 `return`，而是执行 `recoverInitialSurface(initialContext = null, requireHome = true)`。
- 盲后退次数由 [`MAX_INITIAL_BLIND_BACK_ACTIONS = 4`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt:4746) 封顶，满足交接文档「有限次数、不无限循环」要求。

### 用户新指示（纳入计划）

- `DouyinNavigationController.kt` 即将超过 5000 行，**M0 测试闭环完成后优先分拆该文件**，再进行后续开发。已将 C2 拆分优先级提前（原 M7 → 提前至 P0 闭环后）。

### 后续动作

- 进入真机 P0 闭环前，先复核 `VideoCommentButtonDetector` / `CommentSurfaceDetector` 在目标机型（OnePlus NE2210）的有效性。
- 真机闭环需实际驱动设备，执行前将安装 APK 并确认无障碍服务状态。

### 已知限制

- 未修改任何代码；本确认仅基于静态阅读。

---

## [未发布] 2026-08-22 —— M1 P0 评论私信真机闭环打通

### 修改内容

- [`OcrUserResultRowDetector.kt`](app/src/main/java/com/example/douyinautomation/automation/OcrUserResultRowDetector.kt) 修复真机 OCR 兜底拒绝首个用户卡片的根因：
  - `isFollowLabel` 接受被 ML Kit/Vision 截断为单个「关」字形的关注标签。
  - `MIN_FOLLOW_WIDTH_PX` 由 48 下调至 32。
  - 新增宽 follow 块兜底：ML Kit 将右侧关注胶囊与留白合并为单个更宽 TextBlock 时，严格的宽度上限会误拒；现允许在卡片垂直带内携带关注信号的更宽右侧块。
  - 新增 `OcrUserResultRowFailure` 枚举与 `OcrUserResultRowAnalysis.failureReason` / `followDiagnostics` 字段，用于真机失败阶段定位。
- [`DouyinNavigationController.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt) 在 `p0_first_user_ocr_probe` 日志中追加 `failure_reason` 与 `follow_diagnostics` 属性。
- [`OcrUserResultRowDetectorTest.kt`](app/src/test/java/com/example/douyinautomation/automation/OcrUserResultRowDetectorTest.kt) 新增两个用例：截断「关」字形、宽 follow 块嵌入关注信号。

### 已验证项（真机 OnePlus NE2210 / b33aa309）

- 前段链路：`task_started` → 搜索 → 用户标签 → `user_tab_postcondition [USER_RESULTS confidence=0.82]`。
- OCR 兜底：`p0_first_user_ocr_probe [matched=true, sample=1]` → `[matched=true, sample=2, stable=true]` → `p0_first_user_ocr_geometry_fallback [row_height=254]`。
- 后段链路：`user_profile_postcondition` → `comment_private_message_entry [route=node_click, success=true]` → `task_user_finished [outcome=MESSAGE_SEND_FAILED]`（空白消息探针被抖音拦截，符合安全预期）→ `comment_surface_restored [back_attempts=2]` → `comment_runtime_terminal [outcome=COMPLETED]` → `comment_task_completed`。
- 构建：`./gradlew testDebugUnitTest lintDebug assembleDebug` → **BUILD SUCCESSFUL**。

### 未解决问题

- M2 三项卡点修复（调试任务竞态 / 启动不在首页 / 评论区结构误识别）。
- `DouyinNavigationController.kt` 已约 5000 行，待 P0 闭环后分拆（用户指定优先级）。

### 版本

- `0.3.4-mobile-login`（versionCode 14）
