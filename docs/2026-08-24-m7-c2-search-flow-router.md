# M7-C2a：搜索阶段路由拆分

## 问题记录

- 当前错误现象：`DouyinNavigationController` 同时维护搜索阶段的“页面类型 → 下一步”分流、节点动作、OCR、手势、超时恢复、用户选择和私信安全闭环；仅查看搜索阶段分流也必须穿过大型控制器。
- 稳定复现：在 `WAITING_FOR_HOME`、`WAITING_FOR_SEARCH_ENTRY`、`WAITING_FOR_SEARCH_RESULTS`、`WAITING_FOR_USER_RESULTS` 四个阶段检查某个页面类型会触发什么后续步骤时，决策与副作用混在同一 `onScreenObserved` 分支。
- 当前预期行为：可无副作用地验证搜索阶段路由；控制器仍独占动作执行、锁、OCR、节点/几何校验和安全暂停。
- 实际行为差异：原行为正确，但路由难以独立覆盖，后续拆分控制器时容易误改一个页面类型对应的稳定动作。

## 单一根因与最小修改

唯一根因假设：搜索阶段的纯路由和副作用混合在控制器中，造成职责边界不清。

- 新增 `SearchFlowRouter`：仅表达“阶段 + 页面类型 → 既有路由”的纯函数。
- `DouyinNavigationController` 继续执行原方法：`openSearch`、`recoverInitialSurface`、`enterKeyword`、`reuseSearchResultsQueryField`、`selectUserTab`、`selectVisibleUser`；调用顺序、参数和提前返回与旧分支一致。
- 没有移动关键词输入、搜索提交、节点点击、OCR、坐标、手势、超时恢复或私信逻辑。

## 稳定链路影响说明

- 修改原因：为 C2 的分阶段拆分建立可测试的搜索流边界。
- 影响范围：仅四个等待阶段的页面分流表达；实际动作仍由原控制器处理。
- 风险：路由映射缺项或错误会导致错误动作或安全停止。
- 验证方案：路由表单测覆盖首页恢复、搜索入口、搜索结果、用户结果及无关页面；完整构建；真机停止态服务回归。

## 验证

- `SearchFlowRouterTest`：通过。覆盖首页的受限恢复分流、搜索入口/结果/用户结果分流、以及不应由搜索流处理的页面。
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`：通过。
- `git diff --check`：通过。

## 真机验证

- 设备：OnePlus NE2210，Android 16，序列号 `b33aa309`。
- 路径：安装 Debug APK → 保持任务停止 → 检查无障碍服务状态与诊断日志。
- 结果：改善。服务为 Bound/Enabled，Crashed 为空；服务连接、刷新率手势配置和 OCR 初始化日志正常。
- 安全结果：未启动或恢复任务，未执行搜索、页面内点击、滑动、空白消息探测或消息发送。

## 几何与 dp 归一化检查

- 未新增或修改 Android 几何尺寸、固定 px、归一化坐标或手势。
- 节点优先、OCR 辅助、几何验证、页面确认和消息安全门保持不变。

## 后续范围

- 这是 C2 的第一个可验证边界；用户选择、私信和恢复流仍在控制器内，C2 在开发计划中保持未完成。
