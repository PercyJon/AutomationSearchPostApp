# CHANGELOG

本文件记录每次改动的日期、版本、修改内容、已验证项、未解决问题与已知限制（交接规范 §12）。不含真实密码、Token 或私密地址。

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
