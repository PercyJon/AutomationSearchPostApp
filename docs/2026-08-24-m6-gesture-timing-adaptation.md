# M6-A1：手势时长设备适配

## 目标与稳定链路说明

`GestureEngine` 的默认点击和默认归一化滑动此前固定为 60ms / 260ms。该时长属于设备刷新节奏相关的参数：在刷新率较低的设备上，同一时间窗口可用的帧数更少。

- 修改原因：默认时长没有随显示刷新率调整，无法保证跨设备获得足够的手势采样窗口。
- 影响范围：仅 `GestureEngine` 的默认点击与未显式传入时长的归一化滑动；节点点击优先级、坐标比例、页面确认、OCR、几何安全门和消息动作均不变。
- 风险：时长计算来源在 `AccessibilityService` 上不可用会阻断服务启动；低刷新率上较长手势可能影响页面动画节奏。
- 验证方案：纯策略单测覆盖 120/90/60Hz、快速显示和无效读数；完整 Debug 单测、Lint、构建；OnePlus NE2210 真机安装后复验服务绑定、实际时长日志和停止态保护。

## 最小修改

- 新增 `GestureTimingPolicy`：以已验收的 120Hz、60ms 点击 / 260ms 默认滑动作为基准。90Hz、60Hz 依刷新率增加时长，并分别限制在 120ms、500ms 内；高刷新率和无效读数不缩短基准值。
- `GestureEngine` 仅在未提供滑动时长时采用策略；评论翻页、直播退出和用户列表翻页已有的显式时长保持原样。
- 服务通过 `DisplayManager` 查询默认屏幕刷新率，并记录仅含数值的 `gesture_timing_profile` 诊断事件。

## 修复记录

### 第 1 轮：服务启动崩溃（变差）

- 错误现象：安装更新并启用无障碍服务后，系统将服务列为 crashed，未输出时长配置日志。
- 稳定复现：安装 Debug APK → 启用本项目无障碍服务 → 查询 `dumpsys accessibility`。
- 预期行为：服务绑定并记录当前显示刷新率对应的配置。
- 实际差异：服务未绑定，任务不会执行。
- 单一根因假设：在非可视的 `AccessibilityService` Context 上读取 `Context.display` 被 OEM 拒绝。
- 证据：OnePlus NE2210 栈为 `UnsupportedOperationException`，定位到 `GestureEngine` 初始化的 `Context.display` 调用。

### 第 2 轮：默认屏幕查询（改善）

- 最小修复：只将刷新率读取改为 `DisplayManager.getDisplay(Display.DEFAULT_DISPLAY)`，并以 `runCatching` 在读取失败时回退到已验证基准值。
- 相同复现结果：安装 Debug APK → 服务自动重新绑定；`dumpsys accessibility` 显示 Bound、Enabled，Crashed 为空。
- 真机日志：`gesture_timing_profile [display_refresh_rate_hz=120.00001, tap_duration_ms=60, default_swipe_duration_ms=260]`。
- 安全结果：日志未出现 `task_started`、已保存任务恢复、空白消息探测或真实消息发送；持久化本地队列状态为 `STOPPED`，未被启动。

## 自动化验证

- `GestureTimingPolicyTest`：通过。覆盖已验证的 120Hz 基准、90Hz / 60Hz 放宽、240Hz 下限和 null / 0 / NaN 回退。
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`：通过。
- `git diff --check`：通过。

## 真机验证

- 设备：OnePlus NE2210，Android 16，序列号 `b33aa309`。
- 显示：1080×2412，活动刷新率 120.00001Hz。
- 路径：构建 → 安装 → 恢复已启用无障碍服务 → 检查系统绑定状态与应用诊断日志。
- 结果：改善。服务可用，且当前设备继续使用历史上已验收的 60ms / 260ms 时长；本轮没有触发任何自动化页面动作。

## 几何与 dp 归一化检查

- 本轮没有新增或修改 Android 几何尺寸、固定 px、点击坐标、屏幕比例或行高判断。
- 手势时长为基于刷新率的动态策略，不属于 dp 尺寸；没有新增固定 px。
