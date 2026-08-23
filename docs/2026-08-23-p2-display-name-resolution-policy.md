# 2026-08-23：P2-F 用户名称解析来源策略抽取

本记录遵循项目根目录 `AGENTS.md` 的小步修改与同路径验证要求。

## 当前差异

- 现象：主页和私信页都遵循无障碍优先、OCR 回退的名称来源顺序，但控制器内的 OCR 门控条件分散。
- 稳定复现：进入用户主页时，OCR 仅用于 OCR 来源、截断列表名称或无障碍名称不稳定的情况；进入私信页时，有可用无障碍名称即停止，不以 OCR 覆盖。
- 预期：两个页面保留各自的候选解析器与证据要求，同时由同一纯策略表达来源优先级和 OCR 门控。
- 实际：门控条件嵌在控制器流程中，后续调整容易造成主页与私信页的来源优先级不一致。

## 本轮唯一根因假设

名称解析的来源决策与页面动作编排混合，导致相同的无障碍优先安全规则无法独立测试。

## 最小修改与验证

- 新增 `DisplayNameResolutionPolicy`，只抽取截断检测、主页 OCR 门控和私信 OCR 门控。
- 不修改 `ProfileDisplayNameResolver`、`DirectMessageDisplayNameResolver`、节点读取、截图、持久化、点击、手势或消息动作。
- 风险：名称不确定时可能错误开启或跳过 OCR。
- 验证：来源优先级、截断名称、主页 OCR 门控和私信无障碍优先的单测；完整 Debug 单测、Lint、构建；真机干跑回归。

## 验证结果

- 定向测试：`DisplayNameResolutionPolicyTest`、主页名称解析与私信名称解析既有测试通过。
- 完整检查：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 通过，`git diff --check` 无输出。
- 真机：OnePlus NE2210（Android 16 / SDK 36，设备序列号 `b33aa309`）；`designer`、匹配词“不错”、任一匹配、1 个视频 / 1 位用户、干跑。
- 路径与结果：用户结果身份 OCR → 目标主页 → 首个作品 → 评论面板 → 首位可操作候选，`comment_runtime_terminal [outcome=COMPLETED]`。未进入评论用户主页、私信页或空白消息探测。
- 几何检查：未新增 Android 几何尺寸、固定 px、坐标点击或手势逻辑，不涉及新增 dp 归一化项。
