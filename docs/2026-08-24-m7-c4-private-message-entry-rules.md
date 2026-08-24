# 2026-08-24：M7-C4 私信入口规则远程配置

本记录遵循项目根目录 `AGENTS.md` 的小步修改与同路径验证要求。

## 当前差异

- 现象：B 端私信、评论私信与主页纸飞机兜底都各自硬编码“客服、咨询、购物车、商品”等拒绝文案和私信语义文案。
- 稳定复现：候选节点同时含私信与咨询/客服/商品语义时，三条路径都应拒绝；候选只有明确的私信或纸飞机语义时才可继续到既有页面确认。
- 预期：三条路径使用一套可版本化、可通过 HTTPS JSON 下发的规则，网络失败仍回退本地安全基线，远程内容不能扩大自动点击范围。
- 实际：相同文本规则分散，修改或应对抖音文案变更时容易遗漏某一流程，且没有本地缓存的远程配置通道。

## 本轮唯一根因假设

私信入口的文本安全判定没有单一策略和配置来源，导致规则复制且无法安全地版本化下发。

## 最小修改与验证方案

- 新增私信入口规则目录、JSON 缓存、HTTPS 获取和内存策略；控制器、评论运行器与图标兜底仅改为调用同一策略。
- 内置拒绝词不可移除；远程规则仅可新增拒绝词或缩小既有允许词，远程新允许词不会单独打开新的点击路径。无授权、网络失败或非法 JSON 均使用内置/缓存的更保守规则。
- 不改变选择器、节点读取、OCR、几何、点击、手势、页面确认、消息发送或任务恢复行为。
- 风险：规则合并错误可能误放行或误拦截入口。
- 验证：默认等价、远程拒绝词叠加、远程允许词收缩、非法远程词不放宽的单测；HTTP JSON 契约编译；完整 Debug 单测、Lint、构建和真机停止态安装回归。

## 远程 JSON 契约

- 路径：`GET /api/v1/automation/mobile/private-message-entry-rules`，沿用现有 HTTPS、Bearer 授权和 `{ "success": true, "data": ... }` 响应封装。
- `data`：`version`、可选 `updated_at`、`blocked_terms`、`selector_allowed_terms`、`icon_allowed_terms`。
- `blocked_terms` 会追加到对应路线的内置拒绝词；两个 `*_allowed_terms` 若非空，仅与内置路线允许词取交集。未知、过长或空白文本被丢弃；网络/授权/JSON 失败使用私有缓存，再回退内置基线。

## 验证结果

- 定向 JVM：新增 `PrivateMessageEntryRulePolicyTest` 与 `PrivateMessageEntryRuleRepositoryTest`，覆盖默认等价、远程拒绝叠加、远程允许词收缩但不放宽、图标兜底的“购物”拒绝、缓存与离线回退；既有 `ProfileMessageEntryFallbackTest` 通过。
- Android 契约：新增 `AutomationHttpClientInstrumentedTest` 的规则目录响应解析与 Bearer 请求头检查；`:app:compileDebugAndroidTestKotlin` 通过。
- 完整检查：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin` 通过，`git diff --check` 无输出。
- 真机：OnePlus NE2210（Android 16，序列号 `b33aa309`）成功安装 Debug 包、启动至首页，包版本 `0.3.4-mobile-login`；无障碍服务保持禁用、未启动任务、未操作抖音或发送消息，`Crashed services` 为空。
- 几何检查：未新增或修改 Android 几何尺寸、固定 px、坐标点击或手势时长；不涉及新增 dp 归一化项。节点优先、OCR→几何验证→页面确认和消息安全门保持不变。
