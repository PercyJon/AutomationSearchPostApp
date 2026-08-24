# CHANGELOG

本文件记录每次改动的日期、版本、修改内容、已验证项、未解决问题与已知限制（交接规范 §12）。不含真实密码、Token 或私密地址。

---

## [未发布] 2026-08-24 —— M6-B3 节点观察事件增量签名

### 修改内容

- 新增 [`NodeObservationSignaturePolicy.kt`](app/src/main/java/com/example/douyinautomation/automation/NodeObservationSignaturePolicy.kt)：按照既有节点顺序逐项累积 `text`、描述、边界、选中和可见状态的哈希，不再为每个无障碍事件构造整棵节点树的临时拼接字符串。
- [`DouyinAccessibilityService.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinAccessibilityService.kt) 仅改用该增量签名；页面检测、OCR 签名、最终去重条件、控制器和安全动作链路保持不变。

### 已验证项

- 新增 `NodeObservationSignaturePolicyTest`，覆盖相同快照、既有全部语义/几何字段变更及节点顺序。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：任务停止态安装后，回到桌面并仅将抖音带到前台以产生观察事件；服务 Bound/Enabled、Crashed 为空，未出现 `node_inspection_failed` 或任何任务/消息动作。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、坐标或手势；节点边界仅保留为原有签名字段，不构成几何阈值。
- 节点优先、OCR 辅助、页面确认和私信安全门保持不变。

### 交接记录

- [`2026-08-24-m6-node-observation-signature.md`](docs/2026-08-24-m6-node-observation-signature.md)

---

## [未发布] 2026-08-24 —— M6-B2 OCR 预处理审计（无源码改动）

### 审计结论

- 确认 OCR 性能项已由既有提交 `7614f38` 完成：`MlKitOcrEngine` 先按 `OcrRegion` 裁剪，再只对裁剪后仍小于 720 的边放大 1.5×；全屏 1080×2412 输入不满足放大条件，不会出现全屏 1.5× 放大。
- 用户结果、主页头部、操作区、消息输入区和 Toast 已使用对应局部区域；仅未知页、视频右侧栏/当前主页确认和人工诊断保留 `FULL`，且不做全屏放大。

### 已验证项

- 审计 `MlKitOcrEngine` 预处理顺序、调用覆盖、截图区域回映射和提交历史；未发现需要重做的功能或安全缺口。
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 通过（本轮无源码修改）。
- **真机 OnePlus NE2210 / b33aa309**：服务正常 Bound/Enabled，当前没有启动任务；审计未触发 OCR、页面动作或消息动作。

### 几何与兼容性检查

- 沿用已有屏幕比例区域裁剪；未新增 Android 几何尺寸、固定 px、坐标或手势。

### 交接记录

- [`2026-08-24-m6-b2-ocr-preprocessing-audit.md`](docs/2026-08-24-m6-b2-ocr-preprocessing-audit.md)

---

## [未发布] 2026-08-24 —— M6-A6 受保护窗口纯节点树降级

### 修改内容

- [`ScreenshotCapture.kt`](app/src/main/java/com/example/douyinautomation/automation/ScreenshotCapture.kt) 将截图失败码归类为 `ScreenshotCaptureFailureKind`；只有 `secure_window` 会显式进入纯节点树降级并记录 `screenshot_pure_node_tree_fallback`。
- 连续受保护窗口只记录一次；截图成功后重置降级状态。既有调用方继续使用原节点快照或按既有安全门停止，不会以 OCR 缺失为理由放宽页面、节点、几何或点击条件。
- 不接入 `MediaProjection`，不新增屏幕录制授权、截图绕过或坐标动作。

### 已验证项

- 新增 `ScreenshotNodeOnlyFallbackPolicyTest`，覆盖受保护窗口专属降级、非受保护错误排除、连续降级去重与恢复后重新记录。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：更新后服务 Bound/Enabled、Crashed 为空；未出现任务启动、检查点恢复、截图失败、空白探测或消息发送。当前页面不是受保护窗口，`secure_window` 分支由单测覆盖。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、坐标、手势或权限。
- 节点优先、OCR 辅助、页面确认、私信安全探测和稳定任务链路均保持不变。

### 交接记录

- [`2026-08-24-m6-screenshot-node-only-fallback.md`](docs/2026-08-24-m6-screenshot-node-only-fallback.md)

---

## [未发布] 2026-08-24 —— M6-A3 节点树截断可见告警

### 修改内容

- [`NodeTreeInspector.kt`](app/src/main/java/com/example/douyinautomation/automation/NodeTreeInspector.kt) 新增带元数据的检查结果：在既有深度、节点数或时间预算停止时保留截断原因和已收集节点数；原 `inspect()` 的 `ScreenContext` 契约、节点顺序和上限不变。
- [`DouyinAccessibilityService.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinAccessibilityService.kt) 仅在一次连续截断的首次记录 `node_tree_truncated` 告警；完整快照会重新开启告警资格，避免事件风暴污染诊断缓冲区。

### 已验证项

- 新增 `NodeTreeTraversalBudgetPolicyTest`，覆盖深度/节点数/截止时间的既有停止顺序，以及连续截断去重告警。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：更新 APK 后无障碍服务为 Bound/Enabled、Crashed 为空，未出现节点读取错误。当前抖音用户页没有达到遍历上限，因而没有生成截断告警；未启动任务、恢复检查点、执行页面动作或触发消息探测。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、坐标、点击或手势；既有 400 节点/32 层/500ms 遍历预算不变。
- 截断元数据只用于诊断，不能放宽页面识别、OCR、节点选择或点击安全门。

### 交接记录

- [`2026-08-24-m6-node-tree-truncation-warning.md`](docs/2026-08-24-m6-node-tree-truncation-warning.md)

---

## [未发布] 2026-08-24 —— M6-A1 手势时长设备适配

### 修改内容

- 新增 [`GestureTimingPolicy.kt`](app/src/main/java/com/example/douyinautomation/automation/GestureTimingPolicy.kt)：以已验收 120Hz 设备的 60ms 点击 / 260ms 默认滑动为基准，按实际刷新率为低刷新率设备增加手势时长；高刷新率和无效读取均不缩短基准值。
- [`GestureEngine.kt`](app/src/main/java/com/example/douyinautomation/automation/GestureEngine.kt) 仅在调用方没有显式指定滑动时长时使用该策略；既有节点优先、归一化坐标、页面确认、OCR 与安全门保持原样。
- 修复首轮真机发现的服务启动崩溃：无障碍服务不再访问非可视 Context 的 `display`，而通过 `DisplayManager` 查询默认屏幕；查询异常时安全回退至已验证基准时长。

### 已验证项

- 新增 `GestureTimingPolicyTest`，覆盖 120Hz 基准、90Hz/60Hz 放宽、快速显示下限和 null/0/NaN 回退。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：1080×2412、120.00001Hz。安装后服务已绑定且没有 crash；日志记录 `tap_duration_ms=60`、`default_swipe_duration_ms=260`。停止态本地队列没有启动，未出现任务恢复、空白探测或消息发送。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px 或坐标逻辑；时长根据刷新率动态计算，不涉及 dp 尺寸。
- 显式时长的评论翻页、直播退出和用户列表翻页保持不变；稳定链路的节点、OCR、页面状态和消息安全规则不变。

### 交接记录

- [`2026-08-24-m6-gesture-timing-adaptation.md`](docs/2026-08-24-m6-gesture-timing-adaptation.md)

---

## [未发布] 2026-08-24 —— P2-I 本地队列终态路由策略

### 修改内容

- 在 [`TaskQueue.kt`](app/src/main/java/com/example/douyinautomation/automation/TaskQueue.kt) 增加 `LocalTaskQueueTerminalPolicy`，将任务终态后的三种既有去向收拢为纯决策：有后续冻结任务则延后启动下一项；无后续且存在本地队列则持久化完成态；无队列则按普通任务结束。
- [`DouyinNavigationController.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt) 继续负责阶段/失败发布、延时调度、任务启动与队列持久化；不改变完成、失败、到达上限后的现有顺序和动作。

### 已验证项

- `TaskQueueTest` 新增终态路由的完整组合覆盖：有后续任务时优先进入下一项；仅最后一项完成本地队列；无会话的单任务只结束自身。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309 烟测**：更新 APK 后无障碍服务重新连接；既有停止态记录未触发 `task_started` 或自动恢复，确认本轮终态路由抽取没有破坏停止态保护。完整多任务“中间项进入下一项、最后一项回记录页”的行为沿用已完成的 P0 真机验收，本轮未改动其调度动作。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、像素常量、坐标或手势参数；无需新增 dp 归一化逻辑。
- 节点、OCR、消息探测、队列执行顺序和稳定终态动作均保持不变。

### 交接记录

- [`2026-08-24-p2-terminal-queue-routing-policy.md`](docs/2026-08-24-p2-terminal-queue-routing-policy.md)

---

## [未发布] 2026-08-24 —— P2-H 已保存检查点恢复路由策略

### 修改内容

- 在 [`AutomationModels.kt`](app/src/main/java/com/example/douyinautomation/automation/AutomationModels.kt) 增加 `SavedTaskResumePolicy`：将“已保存检查点是否可原地复用当前已验证页面、对应恢复阶段为何”的无副作用决策从 `DouyinNavigationController` 抽出。
- 原有语义保持不变：仅 HOME、搜索入口、搜索结果、用户结果四类已验证导航页可原地续接；用户主页、私信页、未知/风险页及页面缺失均回到冻结的初始流程；队列明确要求重启时同样不得原地续接。
- 控制器仍负责窗口读取、页面识别、状态发布、启动应用和后续动作；本轮不改动 OCR、节点点击、手势、队列顺序、消息提交或终态行为。

### 已验证项

- 新增 `SavedTaskResumePolicyTest`：遍历全部 `PageKind`，锁定仅四类既有导航页可复用；覆盖强制初始重启和可见页面缺失两条拒绝路径。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：受控 B 端安全探测任务暂停后，当前前台不属于抖音；恢复日志记录 `queue_type=B_END_PRIVATE_MESSAGE`、`visible_page=OUTSIDE_TARGET`、`restart_from_initial=true`，随后出现 `saved_task_resume_requested` 与新的 `task_started`，确认从冻结检查点的初始流程重新建立页面状态，而非复用后台页面。
- 真机恢复后的受控任务在进入消息动作前即停止；日志未出现空白探测提交或真实文本发送。服务重连后保持停止态，不会自动恢复该任务。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、像素常量或点击坐标；无需新增 dp 归一化逻辑。
- 不修改稳定的节点、OCR、手势和队列推进链路。

### 交接记录

- [`2026-08-24-p2-saved-task-resume-policy.md`](docs/2026-08-24-p2-saved-task-resume-policy.md)

---

## [未发布] 2026-08-24 —— P0 多任务队列真机追加验收（无代码改动）

### 修改内容

- 无源码修改。本轮仅补充多任务队列在真实设备上的配置、串行执行、暂停恢复与安全空白探测验收记录。

### 已验证项（真机 OnePlus NE2210 / b33aa309）

- **任务配置**：B 端队列使用“红木沙发 / 佛山 / 厂 / 12”和“真皮沙发家具 / 广东 / 公司, 厂 / 10”；英文逗号被正确拆为两个屏蔽词。评论搜索队列使用“室内设计师 / 留空 / 2×5”及“designer / 不错 / 3×5”；中文字段均由 ADB 粘贴录入。
- **B 端队列串行执行**：第一项达到处理上限后自动进入第二项；第二项在下一结果页没有可验证的稳定续接锚点时按既有保护策略结束，未猜测行、坐标或滚动目标。
- **评论搜索队列串行执行**：首项在切换下一视频的后置验证未通过时安全结束；随后自动启动第二项，第二项在没有可验证评论入口的视频上按既有策略跳过并完成，最终回到记录页。
- **暂停、离页、恢复**：B 端队列在悬浮窗暂停时保留 `1/12` 检查点；暂停后当前前台不再是抖音，点击恢复后从初始搜索流程重新建立页面状态，处理进度续至 `6/12`，未重置已处理计数。附加复测随后由操作者停止，避免重复处理。
- **安全空白探测**：本轮所有私信动作只提交单个空格；平台出现“不能发送空白消息”提示即记录安全结果，未发送真实文本。
- **设备恢复**：手机重新连接后，系统无障碍服务处于关闭状态；确认无其他已启用服务后恢复本项目服务，应用“开始任务”按钮恢复可用。

### 已知限制

- 抖音页面在下一结果页或下一视频缺少结构化续接证据时，任务会停止或跳过，而不会通过固定坐标、猜测列表行或点击未知控件继续。

---

## [未发布] 2026-08-23 —— P0 多任务队列、M5 评论匹配优化、安全空白探测回归与 P2 导航拆分

### 修改内容

- P0 多任务队列：待办页按“B端用户私信”“评论区私信”分栏展示并分别多选；仅允许同一类型串行执行，评论队列只纳入“搜索指定用户”，当前用户主页任务保持原路径且不进入队列。
- 新增通用持久化队列会话，保存任务顺序、当前索引、运行/暂停/停止/完成状态和暂停检查点；B端私信与搜索指定用户评论私信复用同一队列契约。前一任务完成、失败或达到上限后自动进入下一任务，最后一项结束才回记录页。
- 悬浮窗显示“队列 N/M”，并使用同一按钮在“暂停/恢复”之间切换。暂停会保留当前任务及后续任务；恢复时只有当前活动窗口确属抖音且页面与暂停阶段匹配才复用页面，否则从初始流程重建，同时沿用已处理身份指纹和计数，不重复处理。
- 评论匹配条件新增“任一/全部”模式；默认“任一”使任意一个匹配词命中即可进入候选集。任务快照、ADB 预置、持久化记录与界面均使用同一配置，旧记录兼容默认值。
- 评论运行记录新增不含原始评论文本的统计：已读取正文数、命中正文数、可安全处理候选数；任务详情页可直接查看这些聚合结果。
- 修复“首个可见头像属于不匹配评论”时后续匹配评论被错误阻断的问题：只有明确验证首行同时具备昵称与正文、且正文不匹配时，才允许处理同一视口中的后续匹配候选；结构不完整或仅 OCR 证据仍保持终止，不会猜测点击。
- P2 导航职责拆分：新增 [`UserResultIdentityMatcher.kt`](app/src/main/java/com/example/douyinautomation/automation/UserResultIdentityMatcher.kt)，收拢用户搜索结果身份确认；新增 [`DouyinWindowContextReader.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinWindowContextReader.kt)，收拢活动窗口优先、可见目标窗口兜底与节点回收。现有导航状态机和动作顺序未改动。
- P2 继续拆分启动页 HOME 归一化策略：新增 [`InitialHomeSurfacePolicy.kt`](app/src/main/java/com/example/douyinautomation/automation/InitialHomeSurfacePolicy.kt)，使启动观察与有界恢复对“未知页 + 无临时遮罩 + 已验证搜索入口”使用同一纯判定；不包含节点点击、手势或消息动作。
- P2 继续收拢 OCR 证据适配：新增 [`OcrTextBlockMapper.kt`](app/src/main/java/com/example/douyinautomation/automation/OcrTextBlockMapper.kt)，让无障碍服务和导航控制器复用相同的 OCR 边界空值与排序规则；新增 [`UserResultsViewportFingerprint.kt`](app/src/main/java/com/example/douyinautomation/automation/UserResultsViewportFingerprint.kt)，让用户结果 OCR 缓存键成为基于屏幕比例的可测试纯函数。
- P2-F 收拢用户名称解析的来源优先级与 OCR 门控：新增 [`DisplayNameResolutionPolicy.kt`](app/src/main/java/com/example/douyinautomation/automation/DisplayNameResolutionPolicy.kt)，保持主页和私信页的“无障碍优先、有限 OCR 回退”语义一致，不改变各页面候选解析器或私信动作。
- P2-G1 从控制器抽取用户标签安全资格策略：新增 [`UserTabCandidatePolicy.kt`](app/src/main/java/com/example/douyinautomation/automation/UserTabCandidatePolicy.kt)，保持“精确标签、完整可见、分类栏内、紧凑高度”的节点与屏幕比例校验，拒绝把内容容器误作用户分类标签。
- P2-G2 抽取搜索入口选择优先级：新增 [`SearchEntrySelectionPolicy.kt`](app/src/main/java/com/example/douyinautomation/automation/SearchEntrySelectionPolicy.kt)，明确语义节点优先、结构节点次之、两者均失败才使用既有受限比例兜底；后置确认与暂停逻辑保持不变。
- A1 用户结果行几何归一化：结构行与 OCR 行高度、OCR 卡片间距、关注标签宽度及双帧稳定性阈值均改为按实际屏幕尺寸计算；新增 [`OcrUserResultRowGeometry.kt`](app/src/main/java/com/example/douyinautomation/automation/OcrUserResultRowGeometry.kt)，删除未使用的历史固定行高常量，不改变页面跳转、点击、手势或安全证据顺序。
- P3 稳定去重与恢复：搜索用户和评论用户的去重、检查点和记录关联改用 SHA-256 身份指纹，历史检查点和记录保留只读兼容；评论候选去重指纹随任务检查点持久化，进程恢复不会再次尝试同一候选；当前主页评论任务的空搜索词检查点也可安全保存和恢复。
- B 端多页结果恢复：新增 [`UserResultsAnchorContinuationPolicy.kt`](app/src/main/java/com/example/douyinautomation/automation/UserResultsAnchorContinuationPolicy.kt)。翻页后只要“上一位已处理用户”的唯一身份锚点在两个稳定视口中保持相同，即可严格从该行之后继续；同屏某个无关行暂时无法解析身份时不再导致整任务超时。下一行仍由既有身份、OCR 与几何安全门验证后才能打开。
- 屏蔽关键词输入规则收拢为 [`BlockedKeywordInputParser`](app/src/main/java/com/example/douyinautomation/automation/TaskDomain.kt)：英文逗号、中文全角逗号与换行都拆为独立规则，并新增单测防止回退。

### 已验证项（真机 OnePlus NE2210 / b33aa309）

- **P0 多任务队列真机闭环**：连续选择 2 个 B端任务后，悬浮窗依次显示“队列 1/2”“队列 2/2”；每项处理上限 5，前一任务达到上限后自动启动后一任务，全部结束后自动回记录页。两项共 10 个处理结果中，首项暂停时的 1 个在途候选按“已暂停”记录；其余 9 次仅执行单个空格的安全探测，均收到平台“不能发送空白消息”拒绝。未发送真实内容。
- **P0 暂停/恢复真机验证**：暂停后悬浮窗显示“恢复”并保留队列进度；恢复判定记录 `visible_page=OUTSIDE_TARGET`、`restart_from_initial=true`，不会把后台旧抖音页面当作前台页面。恢复后由既有初始归位流程继续，检查点中的已处理身份仍被保留。
- P0 单元测试：`TaskQueueTest` 覆盖同类队列校验、评论当前用户主页排除、FIFO 暂停恢复、完成状态、页面不匹配及前台非目标页重启、评论搜索队列强制初始恢复。
- **非干跑空白安全闭环，10 位评论用户**：搜索 `designer`，匹配词“不错”“漂亮”，任一匹配，1 个视频、每视频上限 10。10 位候选均从已验证的评论左侧头像进入；私信页仅提交单个空格，均收到平台空白消息拒绝，记录为 `BLANK_PROBE_VERIFIED`；任务完成 `10 / 0 / 0`。未发送真实内容，未执行点赞操作。
- P2-B 真机干跑回归：从 `designer` 搜索、用户主页、第一条视频到评论面板，安全选中匹配候选后以 `comment_dry_run_candidate_selected` 完成；未进入评论用户主页、私信或空白探测。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- A1 真机干跑：用户结果页在结构行节点不足时执行 OCR 回退；首卡缺少可验证的关注标识，任务按安全门终止，未进入用户主页、私信或空白探测。
- P3 真机干跑：`designer` → 用户标签 → OCR 身份指纹 → 用户主页 → 首条视频 → 评论面板 → 首位安全候选确认 → `COMPLETED`；未点击评论候选头像、未进入评论用户主页、私信或发送步骤。
- **B 端多页真机验收**：搜索“红木沙发”，屏蔽词“公司，厂”，最多 15 位。两次正常翻页后任务以用户上限完成：15 个结果中 11 位 `BLANK_PROBE_VERIFIED`、4 位 `FILTERED_BY_KEYWORD`、重复 0、失败 0。所有私信页仅提交单个空格并获平台“不能发送空白消息”拒绝；未发送真实内容。表单同时验证英文逗号、中文全角逗号均能拆分为独立屏蔽词。

### 交接记录

- [`2026-08-23-p1-keyword-match-mode.md`](docs/2026-08-23-p1-keyword-match-mode.md)
- [`2026-08-23-p1-match-statistics.md`](docs/2026-08-23-p1-match-statistics.md)
- [`2026-08-23-comment-non-dry-run-regression.md`](docs/2026-08-23-comment-non-dry-run-regression.md)
- [`2026-08-23-comment-non-dry-run-10-user-validation.md`](docs/2026-08-23-comment-non-dry-run-10-user-validation.md)
- [`2026-08-23-p2-navigation-identity-extraction.md`](docs/2026-08-23-p2-navigation-identity-extraction.md)
- [`2026-08-23-p2-window-context-reader.md`](docs/2026-08-23-p2-window-context-reader.md)
- [`2026-08-23-p2-initial-home-policy.md`](docs/2026-08-23-p2-initial-home-policy.md)
- [`2026-08-23-p2-ocr-text-block-mapper.md`](docs/2026-08-23-p2-ocr-text-block-mapper.md)
- [`2026-08-23-p2-user-results-viewport-fingerprint.md`](docs/2026-08-23-p2-user-results-viewport-fingerprint.md)
- [`2026-08-23-p2-display-name-resolution-policy.md`](docs/2026-08-23-p2-display-name-resolution-policy.md)
- [`2026-08-23-p2-user-tab-candidate-policy.md`](docs/2026-08-23-p2-user-tab-candidate-policy.md)
- [`2026-08-23-p2-search-entry-selection-policy.md`](docs/2026-08-23-p2-search-entry-selection-policy.md)
- [`2026-08-23-a1-user-result-row-geometry-normalization.md`](docs/2026-08-23-a1-user-result-row-geometry-normalization.md)
- [`2026-08-23-p3-stable-identity-and-comment-resume.md`](docs/2026-08-23-p3-stable-identity-and-comment-resume.md)

### 已知限制

- 空白探测的成功标准是平台明确拒绝空白消息；任何页面、节点或提示证据不足时均不会发送真实内容，并按既有安全策略停止或恢复。

## [未发布] 2026-08-22 —— M4.5 多视频评论轨道、OCR 回退与安全恢复

### 修改内容

- [`CommentPrivateMessageRuntime.kt`](app/src/main/java/com/example/douyinautomation/automation/CommentPrivateMessageRuntime.kt)：
  - 搜索结果进入的用户主页先出现空壳、作品网格稍后才渲染时，新增 12 秒有界主页重读；只在结构化首条视频入口出现后点击，避免闲置到原始 60 秒看门狗。
  - 多视频切换增加直接后置探测与前一视频语义指纹，解决抖音滑到下一条后不派发 accessibility 事件导致的等待超时。
  - 对「视频存在、评论栏的无障碍节点却全为越界/不可见」的情况，最多采集两次全屏 OCR 上下文；仍使用严格的评论轨道识别，未证实则在两次负向证据后快速跳过该视频，而不猜测坐标。
  - 从评论用户主页/私信页返回时复用同一受限 OCR 恢复路径，优先重开已验证的评论面板，避免将视频误判为评论页后额外 BACK 回到用户主页。
- [`CommentSurfaceSignals.kt`](app/src/main/java/com/example/douyinautomation/automation/CommentSurfaceSignals.kt)：当语义评论按钮和可用轨道节点都失效时，只有同时识别出右侧等距的「点赞、评论、收藏、分享」四个数值计数，才推导第二项评论气泡的紧凑点击区域；单个数字、地点卡、视频标题或字幕均不能触发操作。
- [`CommentCandidateExtractor.kt`](app/src/main/java/com/example/douyinautomation/automation/CommentCandidateExtractor.kt)：收紧评论面板判断。视频页内嵌的“期待你的评论”、地点卡及装饰性左侧图片不再构成评论区证据；需有评论数量/标签页头，或多条结构化评论行加重复“回复”证据。
- [`DouyinNavigationController.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt)：为评论运行时注入受限 OCR 截图回调，确保定时探测和事件驱动路径使用同一份 OCR 证据。
- 新增单元覆盖：OCR 四计数轨道只选第二个评论气泡；视频页内嵌评论提示与左侧装饰图不得误判为评论面板。

### 已验证项（真机 OnePlus NE2210 / b33aa309）

- **3 视频完整闭环**：`designer` → 用户搜索结果第一个用户（两次 OCR 稳定确认，未滚动结果列表）→ 该用户第一条视频，`maxVideos=3`、每视频首位评论用户上限为 1。最终 `comment_runtime_terminal [outcome=COMPLETED]`。
- 第 1 条视频：只点击首条评论用户的左侧头像；进入私信后仅提交一个空格，收到原生空白消息拒绝，记录为 `BLANK_PROBE_VERIFIED`，没有发送真实消息。
- 第 2 条视频：真实命中 `source=ocr_fallback` 打开失真右侧评论栏，再只点击首位评论用户左侧头像；私信不可用，安全记录为 `PRIVATE_MESSAGE_UNAVAILABLE`。
- 第 3 条视频：两次 OCR 均未形成完整四计数评论轨道，记录 `comment_next_video_skipped_no_safe_entry [observations=2]` 后直接完成，不超时、不猜测点击。
- 全程没有点赞/心形操作；无 force-stop；无障碍服务在任务结束后仍保持 `target_enabled=true`。`./gradlew testDebugUnitTest lintDebug assembleDebug`、`git diff --check` 均通过。

### 已知限制

- OCR 轨道回退刻意要求完整且等距的四个右侧计数；证据不足时会跳过该视频。这是为避免把点赞、收藏、地点或任意数字误当作评论按钮的安全约束。

---

## [未发布] 2026-08-22 —— 当前用户主页闭环与评论入口文案

### 修改内容

- [`HomeScreen.kt`](app/src/main/java/com/example/douyinautomation/ui/HomeScreen.kt)：`FunctionEntryCard` 改为由调用方显式提供操作文案，修复“评论私信”入口可用却错误显示“敬请期待”的问题；两个可用入口现在均显示“立即创建”。

### 已验证项（真机 OnePlus NE2210 / b33aa309）

- **挂起 → 手动导航 → 恢复**：当前用户主页模式先创建为“已挂起”，悬浮窗只显示“恢复”且未自动响应人工导航；在抖音手动进入 `designer` 搜索结果的第一个用户主页后点击恢复。任务从该主页的第一条视频开始，完成 1 位首序评论用户处理；该用户无可用私信入口，安全记录为 `PRIVATE_MESSAGE_UNAVAILABLE`，无点赞、无真实消息。
- **当前用户主页 → 立即开始**：在同一已验证的用户主页回到应用点击“立即开始”。`TargetAppLauncher` 将抖音任务带回前台且保留此主页（未回到搜索或首页），任务完成第一条视频的 1 位首序评论用户。私信页只提交单个空格并收到抖音原生空白消息拒绝，记录为 `BLANK_PROBE_VERIFIED`；无真实内容发送、无点赞操作，任务 `COMPLETED`（处理 1、失败 0）。

### 已知限制

- “当前用户主页”模式以操作者当前可见、经结构检测确认的抖音用户主页为唯一入口；用户主页之外点击恢复会保持挂起，不会猜测或跳转到其他页面。

---

## [未发布] 2026-08-22 —— 评论首行、空评论面板与空格探测稳健性

### 修改内容

- [`CommentSurfaceSignals.kt`](app/src/main/java/com/example/douyinautomation/automation/CommentSurfaceSignals.kt)：把「评论 0 + 作者发布动态 + 空状态」纳入空评论终态判断。若“去评论”按钮没有进入无障碍树，只要同时存在 `评论 0`、`发布了作品` 与「期待你的评论/发条评论表达你的想法」空状态，仍会直接关闭面板并进入下一个视频；不会把作者动态当作评论用户。
- [`CommentCandidateExtractor.kt`](app/src/main/java/com/example/douyinautomation/automation/CommentCandidateExtractor.kt)：
  - 排除右侧点赞/踩操作栏中的独立数字计数，避免它进入作者—正文配对；
  - 将昵称至正文的配对间距限制为同一评论行内的 32–96px，避免下一位用户昵称跨行“认领”上一条没有句末标点的正文；
  - 保留单字符昵称（如 `1`）作为合法首位评论用户。头像仍须是左侧固定轨道的可验证节点，右侧心形从不具备候选资格。
- [`CommentPrivateMessageRuntime.kt`](app/src/main/java/com/example/douyinautomation/automation/CommentPrivateMessageRuntime.kt)：
  - 空格探测在点击“发送”**之前**开始监听原生 transient 提示，避免“不能发送空白消息”在同一事件轮次出现而漏判；
  - 头像动作报告成功但页面仍被确认是评论面板时，只对同一可验证头像进行一次有界重试；未知页、用户主页、私信页均不会重试，且绝不退化为点击昵称、正文或点赞控件。
- [`NodeTreeInspector.kt`](app/src/main/java/com/example/douyinautomation/automation/NodeTreeInspector.kt)：私有节点诊断补充资源 id 与可见性，便于定位无障碍树差异；不写入 logcat。

### 已验证项（真机 OnePlus NE2210 / b33aa309）

- 干跑：目标严格为 `designer`；首个候选头像为 `(48, 1139)`，对应首位昵称 `1`，`candidate_count=4`，以 `comment_dry_run_candidate_selected` 正常结束，未点击头像、私信或点赞。
- 单用户真实链路：仅点击上述左侧头像；确认没有点赞动作，并在不能确认私信入口时从用户页返回原评论区。该首位用户页没有可见或可访问的纸飞机/“发私信”入口，因此正确记为 `PRIVATE_MESSAGE_UNAVAILABLE`，未尝试或发送空格，也没有跳过到下一位用户。
- **8 人完整闭环复测**：`designer` → 搜索结果第一个用户 → 第一条视频 → `maxVideos=1` / `maxUsersPerVideo=8`。最终任务 `COMPLETED`，`latest_records=8`：7 位 `BLANK_PROBE_VERIFIED`（进入私信、提交单个空格、收到抖音原生空白消息拒绝）、1 位 `PRIVATE_MESSAGE_UNAVAILABLE`；所有用户均从评论区左侧头像进入，并在每次处理后恢复评论区。首个视口后仅滚动一次加载后续候选；未出现头像打开失败或点赞动作。
- 自动化验证：`./gradlew testDebugUnitTest lintDebug assembleDebug` 与 `git diff --check` 均通过。

### 已知限制

- 首位评论用户是否允许私信由抖音账号权限决定；无入口时按安全策略记录为 `PRIVATE_MESSAGE_UNAVAILABLE` 后继续既定候选顺序，不会跳过首位改选其他人。

---

## [未发布] 2026-08-22 —— M3 有界真机回归与匹配词过滤（5/10/20 条 + 关键词 + 分支回归）

### 修改内容

- [`CommentPrivateMessageRuntime.kt`](app/src/main/java/com/example/douyinautomation/automation/CommentPrivateMessageRuntime.kt)：
  - **修复 cap=20 列表耗尽时超时**：根因是抖音在评论列表滚到底部后停止发出 accessibility 事件（RecyclerView 复用只改写视口，不再产生 content-change 事件），观测驱动的 `emptyScrollCount` 永远不会被触发，只有 12 秒分页看门狗（`armTimeout`）会超时。修复方案：在每次 `scrollCommentPanel` 之后增加**有界直连轮询循环**（`POST_SCROLL_POLL_ATTEMPTS = 3` × `POST_SCROLL_POLL_INTERVAL_MS = 600L`），直接重读 `currentContext()` 而不是等待下一次事件——命中 `CommentPanelEndDetector` 结束标记即 `advanceAfterVideo`；连续 3 次轮询都无新增候选则 `advanceAfterVideo("评论区连续滚动后无新增评论用户")`；否则回落到 `armTimeout` 看门狗。
  - 新增 `staleScrollCount`（连续指纹不变滚动计数）与 `MAX_STALE_SCROLLS = 2`、`MAX_EMPTY_SCROLLS = 3` 常量。
  - 新增 `avatarTargetDiagnostics` / `commentAvatarRowCount` 诊断：用左侧头像行数作为「评论面板仍打开」的文本无关签名（滚动后头部与「回复」标记会滚出 accessibility 树）。
  - 评论区恢复：放宽 `returnToCommentSurface` 的 `commentButton` 判断——抖音沉浸播放器保留首页底部导航，重开的视频页常被判为 `HOME` 而非 `UNKNOWN`，现在在任意非嵌套页面命中已验证的右侧评论气泡节点即可重开面板，避免「无法返回评论区」终态。
  - 空白消息安全探针增强：新增 `probingBlankMessage` / `blankRejectionObserved` 状态、`onTransientAccessibilityText` 入口（捕获 toast 携带的「不能发送空白消息」文本）、隐私作用域的 `saveNodeDiagnostic`（写入私有 `diagnostics/nodes` 目录而非 logcat）。
- [`CommentCandidateExtractor.kt`](app/src/main/java/com/example/douyinautomation/automation/CommentCandidateExtractor.kt)：新增头像重定位诊断快照与 `commentAvatarRowCount`（按 `AVATAR_ROW_BAND = 60` 分带统计左侧头像行数）。
- [`MainActivity.kt`](app/src/main/java/com/example/douyinautomation/MainActivity.kt)：新增 `CommentRegressionPreset` 数据类与 `EXTRA_COMMENT_TARGET_USER` / `EXTRA_COMMENT_MATCH_KEYWORDS` / `EXTRA_COMMENT_MAX_VIDEOS` / `EXTRA_COMMENT_MAX_USERS` / `EXTRA_COMMENT_SKIP_PINNED` 五个 ADB extras（仅在 `OPEN_COMMENT_P0=true` 时读取），使 M3 有界回归、关键词过滤、跳过置顶、多视频全部可从 ADB 驱动。
- [`HomeScreen.kt`](app/src/main/java/com/example/douyinautomation/ui/HomeScreen.kt)：新增 `commentRegressionPreset` 参数与 `appliedPresetToken` 闩锁，确保预置参数先写入 `rememberSaveable` 表单状态、再在自启动协程内用**实时委托读**构建快照（`buildPreparedSnapshot()`），避免 5/10/20 边界被固定种子「1」覆盖。
- [`DouyinNavigationController.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt)：将 `isSingleTargetCommentProbe()` 泛化为 `isSearchTargetProfileCommentTask()`——所有 `SEARCH_TARGET_PROFILE` 评论任务均共享「搜索只选一个主页、绝不滑动结果列表」的语义，不再受 `maxUsers==1` 固定约束。
- [`DouyinAccessibilityService.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinAccessibilityService.kt)：在空白消息探针等待阶段把 transient 文本事件转投给运行时。

### 已验证项（真机 OnePlus NE2210 / b33aa309）

- **M3.2 有界回归 5/10/20 条（目标：人民日报，无关键词）**：
  - cap=5 → `已完成当前视频的评论用户上限探测` → `comment_runtime_terminal [outcome=COMPLETED]`（上限停止，ledger 7）。
  - cap=10 → `评论区连续滚动后无新增评论用户` → COMPLETED（列表在第 9 条耗尽）。
  - cap=20 → `评论区连续滚动后无新增评论用户` → COMPLETED（列表在第 8 条耗尽，`comment_post_scroll_empty [poll=1/2/3]` → `comment_video_batch_completed`）。
  - 三者均 **COMPLETED 无超时**（此前 cap=20 因列表耗尽会 12 秒超时 FAILED，本版本已修复）。
- **M3.3 匹配词过滤**：
  - 负向（无匹配测试词）：首个视口 `candidate_count=0`，3 次空轮询 → COMPLETED，证明过滤生效（同目标不过滤可得 3-4 候选）。
  - 正向（`COMMENT_MATCH_KEYWORDS=的`）：首个视口 0 候选 → 滚动后 `candidate_count=3` → `comment_blank_probe_rejection_transient [signals=1]` → `已完成当前视频的评论用户上限探测` → COMPLETED。
- **M3.4 分支回归**（直播/私密/无作品/置顶/0 评论）：决策逻辑由 [`CommentEntryStateMachineTest.kt`](app/src/test/java/com/example/douyinautomation/automation/CommentEntryStateMachineTest.kt) 与 [`CommentSurfaceSignalsTest.kt`](app/src/test/java/com/example/douyinautomation/automation/CommentSurfaceSignalsTest.kt) 覆盖（直播间退出/下滑跳过、私密与空主页 SKIP_PROFILE、作品 Tab 切换、置顶磁贴跳过、0 评论面板终态）；0 候选真机路径经 cap=20 列表耗尽验证。全量 `./gradlew testDebugUnitTest lintDebug assembleDebug` → **BUILD SUCCESSFUL**。

### 未解决问题

- M4 评论功能增强：关键词筛选 UI、多视频切换、跳过置顶开关、当前用户主页模式 + A4 `tryLock` 丢帧修复。
- `DouyinNavigationController.kt` 约 5000 行，待分拆（用户指定优先级）。

### 版本

- `0.3.4-mobile-login`（versionCode 14）

---

## [未发布] 2026-08-22 —— M2 P0 三项卡点修复（调试任务竞态 / 启动不在首页 / 评论区结构误识别）

### 修改内容

- [`MainActivity.kt`](app/src/main/java/com/example/douyinautomation/MainActivity.kt) 修复 M2.1「调试任务未真正触发」在二次投递场景的残留缺陷：
  - `commentP0LaunchNonce` 由实例字段迁移到 `companion object`（`@Volatile` 静态），`recreate()` 后不再归零。
  - `onCreate` 仅在 `openCommentP0 && commentP0LaunchNonce == 0` 时赋初始 token=1，避免进程冷启动重复赋值。
  - `onNewIntent` 的 `OPEN_COMMENT_P0` 分支 `commentP0LaunchNonce += 1` 后 `recreate()`，使重投递意图获得单调递增的启动令牌。
  - 新增 `p0_launch_oncreate` / `p0_launch_new_intent` 诊断日志。
- [`HomeScreen.kt`](app/src/main/java/com/example/douyinautomation/ui/HomeScreen.kt)：
  - `AppHomeScreen` 新增 `commentP0LaunchToken` 参数，并用 `LaunchedEffect(commentP0LaunchToken)` 在重投递时把 `section` 强制拉回 `HOME`、再把 `showCommentTask` 置 true——修复根因：上一次 `onClose()`（经 `openRecordsTab()` 的 recreate）会把 `section` 持久化为 RECORDS，导致 `CommentTaskScreen` 根本未被组合。
  - `CommentTaskScreen` 自启动闩锁键改为 `autoStartToken`，`autoStartTriggeredForToken` 用 `rememberSaveable` 记录已触发的令牌，令牌变化即重新武装。
  - 新增 `p0_launch_show_comment_effect` / `p0_autostart_effect` 诊断日志（含 canRun/ready/errors/snapshot 快照）。
- [`DouyinNavigationController.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt) 修复 M2.2 恢复循环卡死在 SEARCH_ENTRY：
  - `recoverInitialSurface()` 循环分支与最终探针分支的 `PageKind.SEARCH_ENTRY` 现在无条件 `enterKeyword(current)` 并 `return`，不再受 `requireHome` 约束；因为从聚焦的搜索框按 BACK 只会收起键盘、永不离开该页，会耗尽后退预算。

### 已验证项（真机 OnePlus NE2210 / b33aa309）

- 冷启动 P0 闭环：`task_started` → 搜索/用户标签/用户结果 → `p0_first_user_ocr_probe [stable=true]` → `user_profile_postcondition` → `comment_runtime_started_at_profile_handoff` → `comment_leading_row_skipped [candidate_count=2]`（首条作者评论被排除）→ `comment_runtime_terminal [outcome=COMPLETED]` → `comment_task_completed`。
- M2.1 重投递闭环（无 force-stop、无重开无障碍）：对已运行实例再次 `am start --ez OPEN_COMMENT_P0 true` → `p0_launch_new_intent [nonce=2]` → `p0_autostart_effect [canrun=true, triggeredfor=0]` → `task_started` → `comment_leading_row_skipped` → `comment_runtime_terminal COMPLETED` → `comment_task_completed`。
- M2.3：`comment_leading_row_skipped — 首条头像对应的是被排除的行（如作者本人评论），跳过并处理下一条有效评论 [candidate_count=2, first_avatar_top=1139, first_candidate_top=1386]`。
- 构建：`./gradlew testDebugUnitTest lintDebug assembleDebug` → **BUILD SUCCESSFUL**（46s，55 tasks）。

### 未解决问题

- M3 有界真机回归（5/10/20 条）+ 匹配词过滤 + 分支回归（直播/私密/无作品/置顶/0评论）。
- `DouyinNavigationController.kt` 已约 5000 行，待 P0 闭环后分拆（用户指定优先级）。

### 版本

- `0.3.4-mobile-login`（versionCode 14）

---

## [未发布] 2026-08-21 —— M0 环境核验与基线回归

### 修改内容

- 正式接管开发，无代码改动。
- 建立 `CHANGELOG.md` 作为变更记录载体。

### 已验证项

- 构建：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → **BUILD SUCCESSFUL**（14s，55 tasks）。
- 单元测试：**177 个**全部通过（28 个测试结果文件）。
- Lint：**38 个 issue**（37 Warning + 1 Hint，无 Error），`abortOnError=true` 下通过。
- APK 产物：`app/build/outputs/apk/debug/app-debug.apk`（约 108.6 MB）。
- 环境：
  - JDK：默认 Temurin 21（另有 JDK 17 可用，`jvmTarget=17` 兼容）。
  - Android SDK：`android-35` 平台 + `build-tools 35.0.0` 齐全（compileSdk 35）。
  - ADB：36.0.0，设备 `b33aa309` 在线（OnePlus NE2210，Android 16 / SDK 36，满足 minSdk 30）。

### 未解决问题

- 待 M1 推进：P0 评论私信真实闭环（含 A5 页面类型评估）。

### 已知限制

- 尚未执行真机 instrumented 测试（README 警示 `connectedDebugAndroidTest` 会卸载应用，改用 `adb install -r` + 显式 `am instrument`）。

### 版本

- `0.3.4-mobile-login`（versionCode 14）

---

## [未发布] 2026-08-21 —— M1-A5 页面类型评估（无代码改动）

### 评估结论

原优化项 A5 认为「`PageDetector` 缺少 `VIDEO_SURFACE` / `COMMENT_SURFACE` 页面类型，状态机依赖 `UNKNOWN` 兜底」存在稳定性风险。经读取当前代码（Codex 并行更新后的版本），结论修正为：**该问题已被等效方案解决，无需新增 `PageKind`**。

### 依据

- [`CommentEntryStateMachine.kt`](app/src/main/java/com/example/douyinautomation/automation/CommentEntryStateMachine.kt:188) 的 `CommentEntrySignalDetector.observe()` 不再仅依赖 `PageDetector` 分类，而是通过结构化信号判断视频页与评论面板：
  - `hasVideoSurface = page == UNKNOWN && (commentButton != null || (hasVideoMarker && 大尺寸可见节点))`
  - `commentSurface = CommentSurfaceDetector.detect(context)`
  - `hasCommentEntry = VideoCommentButtonDetector.find(context) != null`
- 状态机在 [`CommentEntryStateMachine.kt:84`](app/src/main/java/com/example/douyinautomation/automation/CommentEntryStateMachine.kt:84) 仅当「既无视频面、又无评论面板、又无作品排序目标」时才暂停，确定性分支已由结构信号接管。

### 后续动作

- A5 从「M1 前置阻塞项」降级为「观察项」，不再阻塞 P0 闭环。
- 进入 M1 前仍会在真机复核 `VideoCommentButtonDetector` / `CommentSurfaceDetector` 对目标机型（OnePlus NE2210）的有效性。

### 已知限制

- 未修改任何代码；本评估仅基于静态阅读。

---

## [未发布] 2026-08-21 —— M1 前代码现状确认（无代码改动）

### 确认结论

交接文档 §10 的两个 P0 卡点在当前代码（Codex 并行更新后）中**已修复**，无需重复开发。

### 卡点 1：调试任务未触发（§10.1 竞态）——已修复

- [`AutomationStore.kt:85`](app/src/main/java/com/example/douyinautomation/automation/AutomationStore.kt:85) 新增 `serviceCommandReady`，与 `serviceConnected` 区分，仅在无障碍服务订阅命令后才置位。
- [`HomeScreen.kt:918`](app/src/main/java/com/example/douyinautomation/ui/HomeScreen.kt:918) 的 `canRun = state.serviceCommandReady && errors.isEmpty() && snapshot != null`，`LaunchedEffect(autoStartP0, canRun)` 等服务真正就绪后才触发，消除 Compose 初始化与服务连接间的竞态。

### 卡点 2：启动时抖音不在首页（§10.2 无节点盲后退）——已修复

- [`DouyinNavigationController.kt:4837`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt:4837) 的 `scheduleInitialObservation()` 在 `currentWindowContext()` 与 `recentInitialTargetContext()` 均为 null 时，不再直接 `return`，而是执行 `recoverInitialSurface(initialContext = null, requireHome = true)`。
- 盲后退次数由 [`MAX_INITIAL_BLIND_BACK_ACTIONS = 4`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt:4746) 封顶，满足交接文档「有限次数、不无限循环」要求。

### 用户新指示（纳入计划）

- `DouyinNavigationController.kt` 即将超过 5000 行，**M0 测试闭环完成后优先分拆该文件**，再进行后续开发。已将 C2 拆分优先级提前（原 M7 → 提前至 P0 闭环后）。

### 后续动作

- 进入真机 P0 闭环前，先复核 `VideoCommentButtonDetector` / `CommentSurfaceDetector` 在目标机型（OnePlus NE2210）的有效性。
- 真机闭环需实际驱动设备，执行前将安装 APK 并确认无障碍服务状态。

### 已知限制

- 未修改任何代码；本确认仅基于静态阅读。

---

## [未发布] 2026-08-22 —— M1 P0 评论私信真机闭环打通

### 修改内容

- [`OcrUserResultRowDetector.kt`](app/src/main/java/com/example/douyinautomation/automation/OcrUserResultRowDetector.kt) 修复真机 OCR 兜底拒绝首个用户卡片的根因：
  - `isFollowLabel` 接受被 ML Kit/Vision 截断为单个「关」字形的关注标签。
  - `MIN_FOLLOW_WIDTH_PX` 由 48 下调至 32。
  - 新增宽 follow 块兜底：ML Kit 将右侧关注胶囊与留白合并为单个更宽 TextBlock 时，严格的宽度上限会误拒；现允许在卡片垂直带内携带关注信号的更宽右侧块。
  - 新增 `OcrUserResultRowFailure` 枚举与 `OcrUserResultRowAnalysis.failureReason` / `followDiagnostics` 字段，用于真机失败阶段定位。
- [`DouyinNavigationController.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt) 在 `p0_first_user_ocr_probe` 日志中追加 `failure_reason` 与 `follow_diagnostics` 属性。
- [`OcrUserResultRowDetectorTest.kt`](app/src/test/java/com/example/douyinautomation/automation/OcrUserResultRowDetectorTest.kt) 新增两个用例：截断「关」字形、宽 follow 块嵌入关注信号。

### 已验证项（真机 OnePlus NE2210 / b33aa309）

- 前段链路：`task_started` → 搜索 → 用户标签 → `user_tab_postcondition [USER_RESULTS confidence=0.82]`。
- OCR 兜底：`p0_first_user_ocr_probe [matched=true, sample=1]` → `[matched=true, sample=2, stable=true]` → `p0_first_user_ocr_geometry_fallback [row_height=254]`。
- 后段链路：`user_profile_postcondition` → `comment_private_message_entry [route=node_click, success=true]` → `task_user_finished [outcome=MESSAGE_SEND_FAILED]`（空白消息探针被抖音拦截，符合安全预期）→ `comment_surface_restored [back_attempts=2]` → `comment_runtime_terminal [outcome=COMPLETED]` → `comment_task_completed`。
- 构建：`./gradlew testDebugUnitTest lintDebug assembleDebug` → **BUILD SUCCESSFUL**。

### 未解决问题

- M2 三项卡点修复（调试任务竞态 / 启动不在首页 / 评论区结构误识别）。
- `DouyinNavigationController.kt` 已约 5000 行，待 P0 闭环后分拆（用户指定优先级）。

### 版本

- `0.3.4-mobile-login`（versionCode 14）
