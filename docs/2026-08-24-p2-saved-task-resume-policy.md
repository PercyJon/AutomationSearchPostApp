# P2-H：已保存检查点恢复路由策略

日期：2026-08-24
范围：仅抽取已保存检查点的原地恢复/初始重启判定；不改变自动化动作。

## 修改前记录

- 当前现象：已保存任务恢复时，“当前页面可否原地续接”和“应进入哪个等待阶段”的映射直接写在 `DouyinNavigationController` 中，和窗口读取、页面识别、启动应用及动作调度混在一起。
- 稳定复现步骤：
  1. 暂停 B 端队列；
  2. 当前前台保持抖音且页面与暂停阶段相符时恢复；
  3. 或切换到非抖音应用后恢复。
- 预期行为：仅已验证的早期导航页可复用；页面不足以证明安全续接时，一律从冻结任务的初始流程重新建立状态；已处理身份和计数保持在检查点中。
- 实际差异：行为已具备，但该纯映射不能独立覆盖并容易在后续控制器拆分时与队列恢复规则发生偏离。

## 本轮根因假设

恢复页面到阶段的纯映射内嵌于有副作用的控制器路径，缺少独立的完整页面矩阵测试。将其抽为无 Android 依赖的策略，可在不改变动作链路的前提下锁定既有安全语义。

## 最小修改

- 在 `AutomationModels.kt` 增加 `SavedTaskResumePolicy.phaseForInPlaceResume(...)`。
- `DouyinNavigationController.resumeSavedTask(...)` 改为消费该策略结果；窗口上下文、检测结果、相位发布与应用启动仍由原控制器执行。
- 新增 `SavedTaskResumePolicyTest`：
  - 遍历全部 `PageKind`，只允许 HOME、SEARCH_ENTRY、SEARCH_RESULTS、USER_RESULTS 原地续接；
  - `forceInitialRestart=true` 时四类安全页也必须重启；
  - `visiblePage=null` 时必须重启。

## 结果

- 结果：改善。恢复路由成为可独立单测的纯策略；页面许可集合和阶段映射与修改前保持一致。
- 自动化验证：
  - `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 通过；
  - `git diff --check` 通过。
- 真机验证：OnePlus NE2210（`b33aa309`）。受控 B 端安全探测任务先进入暂停；前台切换为非抖音窗口后触发恢复，日志确认：
  - `task_queue_resume_requested`：`visible_page=OUTSIDE_TARGET`、`restart_from_initial=true`；
  - `saved_task_resume_requested`；
  - 新一轮 `task_started`，并依次重新确认初始页面、搜索入口和用户标签。

恢复后在消息动作之前由测试控制停止。没有出现空白探测提交，也没有发送真实文本。停止态已由任务记录保护，服务恢复连接不会自动续跑。

## 几何检查

- 本轮没有新增或修改 Android 几何尺寸、固定像素、比例坐标或手势参数。
- 因此没有 dp 归一化新增项；现有几何安全门保持不变。

## 后续建议

继续 P2 时，以同样方式评估控制器中下一个真正独立且可纯化的策略边界；终态发布、队列推进和动作调度仍保持现有稳定实现，除非先有可复现问题和最小验证方案。
