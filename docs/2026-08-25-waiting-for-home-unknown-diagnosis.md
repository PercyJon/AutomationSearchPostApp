# 2026-08-25：WAITING_FOR_HOME 稳定首页 UNKNOWN 诊断

## 修复前记录

- **错误现象**：人工已确认抖音首页视频流稳定呈现（顶部导航、底部“首页”、右上搜索图标可见），评论任务启动后连续观测为 `PageKind.UNKNOWN`，最终 `WAITING_FOR_HOME` 30 秒超时。
- **稳定复现步骤**：OnePlus NE2210（Android 16，ADB `b33aa309`）手动停留在抖音首页后，经既有 `OPEN_COMMENT_P0` 入口启动 `designer × 3 × 1` 非干跑空消息安全探测。
- **预期行为**：稳定首页应被分类为 `HOME` 或至少给出可区分的拒绝原因，而不是无差别 `UNKNOWN`。
- **实际行为差异**：2026-08-24 17:12:12–17:12:33 每次启动观察均为 `nodes=71`、`ocr_blocks=0`、`page=UNKNOWN`；17:12:40 记录 `step_timeout [phase=WAITING_FOR_HOME]`。Activity 名称为 `SplashActivity`，现有分类器不以该名称判定页面。

## 本轮唯一根因假设

该首页的可见导航/放大镜由自绘或不兼容的无障碍结构提供，未同时满足“至少两个首页语义标签”或“右上可点击 ImageView/Button/ImageButton 搜索节点”；评论任务启动分支又刻意跳过初始 OCR，因此稳定视觉首页始终进不了可接受的启动证据集合。

## 最小修改范围

只增加只读诊断，不改点击、BACK、PageDetector、OCR 跳过策略、选择器阈值或手势：

1. 新增 `WaitingForHomeUnknownDiagnostics`，对同一帧输出：节点类别/数量、首页语义命中、两个搜索选择器拒绝原因、遮罩结果、OCR 是否跳过、稳定观察次数。
2. 在 `WAITING_FOR_HOME` 的初始观察与 `UNKNOWN` 延后恢复路径记录 `waiting_for_home_unknown`；日志只含类别、数量、归一化区域和拒绝原因，不含视频文案、用户信息或完整 OCR 文本。
3. 每个启动会话最多写一份无文本的几何摘要到应用私有 `diagnostics/nodes/`。

`cause` 只能是以下之一：

| cause | 含义 |
| --- | --- |
| `MISSING_NODES` | 无节点树 |
| `OVERLAY_REJECTED` | 启动广告或顶部直播横幅遮罩 |
| `STABLE_GATE_NOT_PASSED` | 已分类为可启动页，但连续稳定帧不足 |
| `OCR_NOT_TRIGGERED` | 首页语义与两个搜索选择器均不足，且 OCR 被跳过（评论任务 / 未到期 / 达上限 / 引擎缺失） |
| `MISSING_SEMANTICS` | 节点存在且 OCR 已有机会，仍无足够首页语义或搜索候选 |

## 验证结果

- JVM：`WaitingForHomeUnknownDiagnosticsTest` 覆盖上述五类 cause；`./gradlew :app:testDebugUnitTest --tests ...WaitingForHomeUnknownDiagnosticsTest :app:lintDebug :app:assembleDebug` 通过。
- `git diff --check` 通过。
- 本轮未启动抖音、未保存/启动任务、未执行 BACK/搜索/动作栏/私信点击。
- 用 17:12 既有真机签名（`nodes=71`、`ocr_blocks=0`、评论任务跳过初始 OCR）对照诊断器：与 `OCR_NOT_TRIGGERED` 一致。这不是功能修复，只说明下一步应单独验证“评论任务 WAITING_FOR_HOME UNKNOWN 是否允许有界首页 OCR”，而不是先放宽全局 PageDetector。

## 几何与安全检查

- 未新增固定 px、点击坐标或手势时长。
- 选择器区域与遮罩区域仅以 0–1 屏幕比例写入日志。
- OCR/模板结果仍不能直接授权点击；本轮没有任何动作分发变化。

## 真机验证 2026-08-25（只读诊断，未改点击）

- **设备**：OnePlus NE2210 / ADB `b33aa309` / Android 16。
- **操作路径**：先将抖音回到可见首页（顶部「推荐」、底部「首页」、右上搜索图标），覆盖安装含诊断日志的 Debug APK，登录后由既有 `OPEN_COMMENT_P0` 启动 `designer × 3 × 1` 空白消息安全探测。
- **实际 cause**：启动瞬间系统「允许打开抖音」弹窗挡住目标窗口，观测 1–3 均为 `cause=MISSING_NODES`（`nodes=0`，`ocr_skipped=comment_task`）。弹窗消失后 `initial_observation_snapshot [page=HOME, nodes=213, ocr_blocks=0, confidence=0.78]`，随后 `search_entry_opened [route=bounds_gesture]`。全程没有 `OCR_NOT_TRIGGERED` 或 `MISSING_SEMANTICS`。
- **对照 17:12**：当时是稳定首页 `nodes=71` / `page=UNKNOWN`。本轮稳定首页已是 213 个节点并由既有 `InitialHomeSurfacePolicy`（语义搜索候选，置信度 0.78）判为 `HOME`。17:12 的 `OCR_NOT_TRIGGERED` 假设**未在本轮复现**。
- **结果**：诊断器可用；启动门改善（进入搜索）。按单假设规则，**不实施**评论任务有界首页 OCR，也不放宽全局 PageDetector。若再次出现稳定首页 `nodes≈71` 且 `cause=OCR_NOT_TRIGGERED`，再单独立项。

## 下一步

仅当稳定可见首页再次被判 `UNKNOWN` 且日志为 `OCR_NOT_TRIGGERED` 时，另起一轮：仅对评论任务 `WAITING_FOR_HOME` 的 `UNKNOWN` 增加顶部/底部导航带有界 OCR 双帧确认；OCR 只能把页面归为 `HOME`，搜索入口仍走既有节点/结构/受限兜底顺序。
