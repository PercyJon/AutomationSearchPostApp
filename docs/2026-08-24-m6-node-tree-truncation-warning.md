# M6-A3：节点树截断可见告警

## 问题记录

- 当前错误现象：`NodeTreeInspector` 到达 400 个节点、最大深度或 500ms 预算时直接返回已收集节点；主无障碍观察路径无法得知该快照是否完整。
- 稳定复现：遍历预算策略在深度 33、已收集 400 节点或到达截止时间时停止；此前三种情况都不会产生诊断事件。
- 当前预期行为：在不放宽任何页面、OCR、节点或点击安全条件的前提下，将实际截断原因记录为告警。
- 实际行为差异：部分节点树与完整节点树对下游表现相同，导致定位缺少树证据的问题时没有截断信号。

## 单一根因与最小修改

唯一根因假设：遍历终止原因没有随快照返回，因此服务层没有可记录的截断事实。

- `NodeTreeInspector.inspect()` 保持原有 `ScreenContext` 返回契约；新增 `inspectWithMetadata()`，只额外返回 `NodeTreeInspection` 与可选的截断原因。
- 仍使用既有停止顺序：最大深度 → 最大节点数 → 检查截止时间；已收集节点顺序、400 节点上限和所有下游页面/动作判定不变。
- 无障碍服务只在连续截断区间的首次记录 `node_tree_truncated`，随后完整快照会重新开启下一次告警资格，避免日志洪泛。
- 告警只包含原因枚举与收集节点数；不包含节点文本、OCR、搜索词、账号或消息内容。

## 验证

- `NodeTreeTraversalBudgetPolicyTest`：覆盖原停止优先级、预算内不停止，以及连续截断只记录一次、完整快照后重新告警。
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`：通过。
- `git diff --check`：通过。

## 真机验证

- 设备：OnePlus NE2210，Android 16，序列号 `b33aa309`。
- 路径：安装 Debug APK → 等待无障碍服务重连 → 查询系统 Bound/Enabled/Crashed 状态与应用诊断日志。
- 结果：改善。服务为 Bound/Enabled，Crashed 为空；没有 `node_inspection_failed`。当前停留的抖音用户页未达到遍历上限，故没有 `node_tree_truncated` 事件；这符合仅在实际截断时记录的预期。
- 安全结果：未启动任务、未恢复检查点、未执行页面点击或滑动、未触发空白消息探测或发送内容。

## 几何与 dp 归一化检查

- 未新增或修改 Android 几何尺寸、固定 px、坐标比例、点击或手势。
- 400 节点、32 层及 500ms 是既有遍历预算，并非 Android 几何尺寸；本轮仅让预算终止原因可见。
