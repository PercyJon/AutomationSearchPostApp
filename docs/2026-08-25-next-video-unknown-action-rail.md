# 2026-08-25：第二视频 UNKNOWN 动作栏识别

## 修复前记录

- **错误现象**：评论私信任务完成第一视频 3 位空白探测并成功上滑后，第二视频画面和右侧评论气泡已可见，但任务以“评论流程超时：等待下一个视频”失败。
- **稳定复现步骤**：OnePlus NE2210（Android 16 / `b33aa309`），运行“室内设计师 / 视频数 2 / 评论数 3 / 跳过置顶开启”的空白消息安全探测。
- **预期行为**：确认新视频和安全评论入口后打开评论面板，处理第二视频；任何视觉入口仍需几何和面板后验。
- **实际行为差异**：上滑后前五次探针均为 `page=UNKNOWN, changed=true, video_surface=false, comment_entry=false, ocr_blocks=0`。第六次变为 `HOME` 才启动动作栏 OCR；第二次 OCR 刚开始，旧的 18 秒“等待下一个视频”看门狗先终止任务。

## 本轮唯一根因假设

下一视频动作栏识别被 `hasVideoSurface || page == HOME` 前置门限制；已确认变化的 `UNKNOWN` 详情页不能启动既有 OCR / 模板 / 双锚点链路。同时，识别开始后仍沿用从滑动时刻计时的旧看门狗，无法保证最多两帧的受限识别完成。

## 最小修改

1. 使用 OCR 丰富前的原始节点树计算滑动前后指纹。
2. 只有 `page=UNKNOWN && changed=true` 时，额外允许进入既有最多两次动作栏识别；不修改全局 `PageDetector`，该条件不直接授权点击。
3. 首次动作栏识别前，将滑动看门狗替换为新的有界动作栏验证看门狗；后续帧不重复延长。
4. 保留节点 → 评论气泡模板 → OCR 数字栏几何 → 双锚点的入口优先级，以及点击后的评论面板确认。

## 验证结果

- JVM：`NextVideoTransitionProbePolicyTest` 覆盖变化后的 `UNKNOWN`、未变化 `UNKNOWN`、无关页面、预算耗尽和看门狗只替换一次。
- 构建：定向 JVM 测试、完整 Debug JVM 测试、`lintDebug`、`assembleDebug` 通过。
- 真机：同一 OnePlus NE2210，以“室内设计师 / 2 × 3 / 跳过置顶开启”运行空白探测。
  - 14:30:19 上滑成功。
  - 14:30:20 第一帧即记录 `comment_next_video_action_rail_watchdog_replaced [attempt=1, changed=true, page=UNKNOWN]`。
  - 同一帧受限动作栏 OCR 得到 6 个块；14:30:24 后置条件为 `comment_entry=true, video_surface=true`，以 `source=ocr_fallback` 打开评论面板。
  - 14:31:24 记录 `comment_video_batch_completed [video_count=2]`、`comment_runtime_terminal [outcome=COMPLETED]`。
  - 任务记录：已处理 6 / 跳过 0 / 失败 0；六位均为 `BLANK_PROBE_VERIFIED`。

本轮结果：**改善并解决该复现路径**。

## 几何与安全检查

- 未新增固定 px、绝对坐标、手势时长或入口阈值。
- 页面变化与诊断只使用既有节点指纹；点击仍来自既有安全入口目标。
- 未发生点赞、收藏、分享或真实消息发送。
