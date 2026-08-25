# 2026-08-25：评论任务从打开的评论面板启动时走有界 BACK

## 修复前记录

- **错误现象**：评论任务从非首页启动时，若抖音停在上一次留下的评论面板，`WAITING_FOR_HOME` 连续观测为 `UNKNOWN`，30 秒后安全暂停，已处理 0。
- **稳定复现步骤**：OnePlus NE2210（ADB `b33aa309`）上一次「室内设计师」评论任务结束后停在评论区；再以 `OPEN_COMMENT_P0` 启动「室内设计师 / 5 视频 / 每视频 1 评论 / 跳过置顶开启」。
- **预期行为**：确认是评论面板后，用既有有界 `GLOBAL_ACTION_BACK` 退出嵌套层；到达首页或仍带可编辑搜索栏的页面后，继续既有搜索流程。不得 force-stop、清栈或命令抖音跳转首页。
- **实际行为差异**：任务 `室内设计师-20260825-144430` 诊断为 `waiting_for_home_unknown [cause=OCR_NOT_TRIGGERED, ocr_skipped=comment_task, nodes=400]`，随后 `poc_paused_for_manual_handoff`。截图为打开的评论面板（「447条评论」+ 底部输入框），不是未分类首页。

## 本轮唯一根因假设

启动阶段把所有 `UNKNOWN` 都当成「可能是自绘首页」而延后 BACK。评论面板已被既有 `CommentSurfaceDetector` 识别，却进不了 `recoverInitialSurface`。

## 最小修改范围

1. 新增纯策略 `NestedLaunchSurfacePolicy`：仅当 `WAITING_FOR_HOME` 且评论面板已确认时，允许走既有有界 BACK；面板仍打开时禁止点搜索（搜索图标可能露在面板上方）。
2. `onScreenObserved` 在 UNKNOWN 延后恢复之前检查该策略，命中则调用既有 `recoverInitialSurface`。
3. `recoverInitialSurface` 在面板仍打开时只 BACK；面板关闭后仍按 HOME → 打开搜索，或 `SEARCH_ENTRY` / 带输入框的 `SEARCH_RESULTS` 直接继续搜。

不修改全局 `PageDetector`、不启用评论任务启动 OCR、不用视频动作栏当作嵌套证据（首页信息流也有评论按钮）。

## 几何与安全检查

- 未新增固定 px、点击坐标或手势时长。
- 仍只使用无障碍全局 BACK；到达可搜索页后走既有节点搜索入口。
- OCR/模板结果不授权点击。

## 第 2 轮最小修改（OCR 确认面板）

本轮唯一根因假设：17:13 无障碍树被 `MAXIMUM_DEPTH` 截断，节点版 `CommentSurfaceDetector` 看不到面板；评论任务又跳过启动 OCR。超时诊断 OCR 已有约 30 个文本块，足够既有 OCR 分支确认面板。

最小修改：

1. `WAITING_FOR_HOME` + `UNKNOWN` + 节点未确认面板时，最多 2 次 `OcrRegion.FULL` 采样，只喂给 `CommentSurfaceDetector`。
2. 这些 OCR 块不交给 `PageDetector` / `InitialHomeSurfacePolicy.normalize`，避免把面板上方搜索图标当成 HOME 去点。
3. OCR 确认后第一次恢复强制 BACK，再使用既有首页或搜索栏路径。

未改检测器阈值、未加深树遍历、未放宽 PageDetector。

## 验证

- JVM：`NestedLaunchSurfacePolicyTest` 覆盖节点面板、OCR 面板 chrome（「评论 58」+ 回复行）、首页信息流「评论」标签、OCR 探测门限。
- 完整 `testDebugUnitTest` / `lintDebug` / `assembleDebug` 与 `git diff --check` 见本轮构建。

### 真机 2026-08-25 17:13（第 1 轮，无变化）

- **设备**：OnePlus NE2210 / ADB `b33aa309` / Android 16。
- **操作路径**：安装含方案一的 Debug APK（未 force-stop 抖音）；信息流视频打开评论面板后，经 `OPEN_COMMENT_P0` 启动「室内设计师 / 视频数 1 / 评论数 1 / 跳过置顶」。
- **预期**：`initial_nested_comment_surface_recovery`，随后有界 BACK 并进入搜索。
- **实际**：无该日志。启动观测 `page=UNKNOWN, nodes=149, ocr_blocks=0, ocr_skipped=comment_task`，并有 `node_tree_truncated [reason=MAXIMUM_DEPTH, captured_node_count=149]`。17:13:43 `step_timeout WAITING_FOR_HOME`，17:13:46 `poc_paused_for_manual_handoff`。记录 `室内设计师-20260825-171311` 已暂停 0/0/0。
- **超时节点摘要**（不含评论文案）：无 EditText、无「条评论」、无「AI解析」；可点击节点 1。同时截图仍是打开的评论面板。
- **结论**：方案一的策略本身未触发，因为无障碍快照里没有评论面板 chrome。评论任务又跳过启动 OCR。这不是 BACK 预算或搜索入口的问题。

## 已排除

- 策略未接线（第 1 轮代码已安装；未命中是检测器输入为空）。
- 用视频动作栏当作嵌套证据（首页信息流也有评论按钮）。
- force-stop / 清栈 / 命令抖音跳转首页。

第 2 轮真机：需在评论面板打开时再跑同一启动路径，日志应出现 `initial_nested_comment_surface_ocr` 且 `sheet=true`，随后 `initial_nested_comment_surface_recovery` 并进入搜索。

## 第 3 轮最小修改（加长启动恢复 BACK 间隔）

- **错误现象**：OCR 已确认评论面板并开始有界 BACK，但连续返回把抖音退出到桌面。
- **稳定复现**：2026-08-25 17:25 `室内设计师 / 1×1`，`initial_nested_comment_surface_recovery` 后 17:25:45–17:25:52 共 5 次 `global_back`，每次探测仍为 `page=UNKNOWN`，随后 `抖音未能在限定时间内回到可搜索页面`。
- **本轮唯一根因假设**：启动恢复复用资料页 700ms BACK 间隔，评论面板关闭和首页树恢复来不及，下一次 BACK 打在未分类表面，直到退出抖音。
- **最小修改**：仅 `recoverInitialSurface` 使用 `INITIAL_SURFACE_RECOVERY_BACK_DELAY_MS = 2000`；`USER_PROFILE_BACK_DELAY_MS` 与 BACK 次数上限不变。该 2000ms **不得**用于评论运行时「空白探测后两步返回评论区」。
- 同轮：`RETURN_TO_COMMENT_DELAY_MS` 从 250ms 改为 120ms，只缩短评论运行时返回评论区的 BACK 间隔。
