# M6-A6：受保护窗口截图的纯节点树降级

## 决策与问题记录

本轮采用开发计划允许的“截图失败时纯节点树降级”方案，不接入 `MediaProjection`。该方案不请求屏幕录制授权、不尝试绕过受保护窗口，也不将 OCR 缺失替换为坐标点击。

- 当前错误现象：截图接口可返回 `secure_window`，但此前只作为普通截图失败记录；诊断中无法明确区分“受保护窗口，后续不得依赖截图 OCR”。
- 稳定复现：截图失败类型为 `secure_window` 时，既有调用方会继续使用原始节点快照或安全结束，但没有明确的降级事件。
- 当前预期行为：受保护窗口首次出现时记录“纯节点树”降级；后续动作只可依赖既有无障碍节点、安全页面判定，或按既有条件停止。
- 实际行为差异：截图错误日志本身无法表达后续 OCR 证据不再可用的安全策略。

## 单一根因与最小修改

唯一根因假设：截图错误码没有抽象为可判定的失败类型和降级策略。

- `ScreenshotCaptureException` 保留 Android 错误码，并新增不含敏感内容的 `ScreenshotCaptureFailureKind`。
- 只有 `SECURE_WINDOW` 映射为 `ScreenshotNodeOnlyFallbackReason.SECURE_WINDOW`；节流、显示、访问和未知错误仍走既有错误路径，避免把可恢复问题误判为受保护窗口。
- 截图入口在连续受保护窗口的首次记录 `screenshot_pure_node_tree_fallback`；截图成功后重置该状态，避免日志洪泛。
- 现有调用方不变：服务层 OCR 回退返回原始 `ScreenContext`；控制器 OCR 辅助返回原节点快照或安全停止。节点优先、OCR 仅辅助、几何验证与点击安全门均未改变。

## 验证

- `ScreenshotNodeOnlyFallbackPolicyTest`：覆盖仅受保护窗口降级、非受保护失败不降级，以及连续降级只记录一次、截图恢复后重新记录。
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`：通过。
- `git diff --check`：通过。

## 真机验证

- 设备：OnePlus NE2210，Android 16，序列号 `b33aa309`。
- 路径：安装 Debug APK → 等待无障碍服务重连 → 检查系统 Bound/Enabled/Crashed 状态与应用日志。
- 结果：改善。服务 Bound/Enabled、Crashed 为空；未出现截图失败、任务启动、检查点恢复、空白消息探测或消息发送。当前页面未处于受保护窗口，`secure_window` 的分支由纯策略单测验证。

## 几何与 dp 归一化检查

- 未新增或修改 Android 几何尺寸、固定 px、坐标、点击或手势。
- 未新增 `MediaProjection` 权限、窗口绕过机制或屏幕录制路径。
