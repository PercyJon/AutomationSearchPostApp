# CHANGELOG

本文件记录每次改动的日期、版本、修改内容、已验证项、未解决问题与已知限制（交接规范 §12）。不含真实密码、Token 或私密地址。

---

## [未发布] 2026-08-25 —— 系统返回导航一致性

### 修改内容

- 为工作台的任务表单、任务详情和二级页添加系统返回映射，与已有左上返回保持一致。
- 任务详情回记录列表；表单回原工作台；记录/设置/诊断回“我的”；我的/待办回首页；首页仍采用系统默认退出。

### 已验证项

- `git diff --check` 与 `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PautomationApiEndpoint=<受管 HTTPS 服务地址>` 通过。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：使用系统返回键完成表单、详情、记录、设置、诊断、我的和待办的层级回退验证；未启动任务、调用抖音或产生诊断操作，测试后 App 已停止并返回系统桌面。

### 几何与兼容性检查

- 未新增固定 px、自动化坐标、OCR 几何或手势时长；仅增加 Compose 系统返回状态处理。

### 交接记录

- [`2026-08-25-system-back-navigation.md`](docs/2026-08-25-system-back-navigation.md)

---

## [未发布] 2026-08-25 —— 待办、任务详情与诊断页面视觉收敛

### 修改内容

- 待办底部操作区、任务记录详情和用户结果卡收敛为紧凑圆角比例。
- 开发诊断页采用标准返回箭头、统一描边卡片、紧凑输入/预设芯片/控制按钮；所有诊断与自动化动作回调保持不变。

### 已验证项

- `git diff --check` 与 `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PautomationApiEndpoint=<受管 HTTPS 服务地址>` 通过。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：验证待办、任务记录详情和开发诊断页。没有开始、暂停、继续、停止、采集、复用、重试或调用抖音；测试后 App 已停止并返回系统桌面。

### 几何与兼容性检查

- 视觉调整仅使用 Compose `dp`（50dp 输入框、46dp 按钮、34dp 芯片、12dp/8dp 圆角、92dp 诊断标签宽度）；未新增固定 px、自动化坐标、OCR 几何或手势时长。
- P0 评论入口、双锚点回退、动作节奏和无障碍状态机未修改。

### 交接记录

- [`2026-08-25-operational-pages-visual-refinement.md`](docs/2026-08-25-operational-pages-visual-refinement.md)

---

## [未发布] 2026-08-25 —— 设置与任务记录页面视觉收敛

### 修改内容

- 设置/任务记录顶部栏改为明确页面名称，移除页内及副标题的重复层级。
- 设置卡片、输入框、按钮、任务记录筛选项和记录卡统一为紧凑的圆角矩形比例；仅压缩展示文案，不改变设置读写、授权、任务数据或导航回调。

### 已验证项

- `git diff --check` 与 `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PautomationApiEndpoint=<受管 HTTPS 服务地址>` 通过。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：验证“我的 → 自动化设置 → 返回 → 我的 → 任务记录”，并滚动检查悬浮窗、远程任务与开发者选项。没有写入设置、验证 heartbeat、打开权限页、启动/重试任务或调用抖音；测试结束后 App 已停止并返回系统桌面。

### 几何与兼容性检查

- 视觉调整仅使用 Compose `dp`（50dp 输入框、46dp 按钮、34dp 筛选项、12dp/8dp 圆角）；未新增固定 px、自动化坐标、OCR 几何或手势时长。
- P0 评论入口、双锚点回退、动作节奏和无障碍状态机未修改。

### 交接记录

- [`2026-08-25-settings-records-visual-refinement.md`](docs/2026-08-25-settings-records-visual-refinement.md)

---

## [未发布] 2026-08-25 —— B 端与评论私信任务表单视觉收敛

### 修改内容

- 移除两张任务表单中的静态辅助小字和重复页内标题，仅保留字段标签、相关控件、必要校验和操作按钮。
- 输入框统一为紧凑的浅色圆角描边样式；预设词、选择项和操作按钮收敛为更接近 uView 的矩形比例与圆角。
- 保留原有字段、默认值、校验、保存、挂起、启动和全部自动化逻辑；未改动抖音识别、手势、OCR、图像匹配或网络行为。

### 已验证项

- `git diff --check` 与 `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PautomationApiEndpoint=<受管 HTTPS 服务地址>` 通过。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：验证“首页 → B 端私信 → 左上返回 → 首页 → 评论私信”。两页的字段、选择项、开关、校验和按钮显示正常；未保存或启动任务，测试后 App 已停止并返回系统桌面。

### 几何与兼容性检查

- 仅使用 Compose `dp` 调整视觉尺寸（50dp 输入框、46dp 按钮、34dp 选择项、8dp 圆角、1dp 描边）；未新增固定 px、自动化坐标或设备相关手势时长。
- P0 评论入口、双锚点回退、动作节奏和无障碍状态机未修改。

### 已记录后续项

- 任务表单的系统返回键会退出 App，左上返回按钮可正常回首页；该导航一致性问题未纳入本次视觉收敛范围。

### 交接记录

- [`2026-08-25-task-form-visual-refinement.md`](docs/2026-08-25-task-form-visual-refinement.md)

---

## [未发布] 2026-08-25 —— 统一登录会话与设备指纹安全存储

### 修改内容

- 新增账号 + 密码统一登录页；无可用移动端授权时不进入既有主界面。
- 服务地址改为构建参数注入，普通操作界面不再显示服务地址、Token 或设备 ID 输入。
- 本机新的 Keystore 密文只保存 SHA-256 设备哈希；旧版含原始设备 ID 的密文会在首次读取时迁移覆盖。
- 登录后等待后端授权签发事务提交，再执行首次 heartbeat，修复首次瞬时“授权无效”。
- 设置页收敛为账号与授权摘要和诊断验证；退出登录仍只清除本机密文。

### 已验证项

- `git diff --check` 与 `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PautomationApiEndpoint=<受管 HTTPS 服务地址>` 通过。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：验证登录失败提示、登录成功、首次 heartbeat“已授权”、退出回登录页和冷启动会话恢复；系统密码管理提示选择“不保存”。测试期间没有启动任务或操作抖音。

### 几何与兼容性检查

- 新 UI 全部使用 Compose `dp`；没有新增 px、自动化坐标、OCR、图像匹配或手势行为。
- P0 评论入口、双锚点回退、动作节奏及无障碍状态机未改动。

### 已记录的阻塞项

- 后端未提交的移动端登录基线仍与“一账号一设备、后台解绑”实现文件重叠；该项保持记录并暂不覆盖。当前后端仅轮换同账号同设备授权，尚未实现跨设备阻止。

### 交接记录

- [`2026-08-25-login-session-and-device-hash.md`](docs/2026-08-25-login-session-and-device-hash.md)

---

## [未发布] 2026-08-24 —— 账户中心与三项底部导航基础

### 修改内容

- 底部导航由“首页 / 待办 / 记录 / 设置”收敛为“首页 / 待办 / 我的”。
- 新增“我的”账户中心：蓝色账户摘要、授权状态提示、账号与设备、任务记录、自动化设置、服务与诊断、远程任务设置等列表入口。
- 原“记录”“设置”保留为二级页面；从二级页面的返回按钮回到“我的”。既有 `OPEN_RECORDS` 入口和任务详情路径没有删除。
- 本阶段的退出登录仅清除本机 Keystore 加密授权；服务端撤销会在账号—设备绑定后端阶段接入。

### 已验证项

- `git diff --check && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 通过。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：安装 Debug APK 后依次验证“首页 → 我的 → 任务记录 → 返回我的 → 自动化设置 → 返回我的”；三个底部入口均可见，记录/设置不再是底部入口。
- 测试前只读检查确认历史评论任务已完成；测试期间未启动任务、未操作抖音、未输入或发送消息。验证完成后已停止本 App 并返回系统桌面。

### 几何与兼容性检查

- 全部新增 Compose 尺寸使用 `dp`；列表行最小高度为 56dp，未新增固定 px、自动化坐标、OCR 几何或设备相关手势时长。
- P0 评论入口、双锚点回退、动作间隔与无障碍状态机未修改。

### 已记录的阻塞项

- 后端工作区当前存在未提交的移动端登录改动，且与账号—设备绑定阶段的 controller/schema/service/test 文件重叠。为避免覆盖或混入未知改动，本阶段未修改后端；详情见下方交接记录。

### 交接记录

- [`2026-08-24-my-navigation-foundation.md`](docs/2026-08-24-my-navigation-foundation.md)

---

## [未发布] 2026-08-24 —— P0 右侧动作栏点赞/收藏双锚点回退

### 修改内容

- 为已有的下一视频右侧栏截图增加 Alpha 感知的点赞、收藏图标模板匹配；分享图标不参与动作栏证据或点击。
- 只有两个锚点同栏、同尺度、跨越两个连续动作槽、并在连续两帧稳定时，才按屏幕比例推导中间的评论图标候选。
- 候选严格排在既有无障碍节点、评论气泡模板和 OCR 数字栏几何之后；点击后仍需既有视频页及评论面板后置确认。
- 新增仅记录匹配可用性的诊断日志，区分“右栏尚未显现”与“点赞/收藏模板未命中”；未放宽任何阈值或既有安全门。

### 已验证项

- `CommentSurfaceSignalsTest` 覆盖未确认、反序/异尺度拒绝、两帧稳定、中点推导及既有模板/OCR 优先级；Android 设备侧 `AlphaMaskedActionIconMatcherInstrumentedTest` 验证真实 APK 资产可由 Android `AssetManager`/`Bitmap` 匹配。
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`、`git diff --check` 通过；此前同机 `:app:connectedDebugAndroidTest` 的 5 项设备测试通过。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：操作者确认系统“允许本 App 调起抖音”后，`designer × 3 × 1` 空白探测路径完整完成。第 2、3 视频均记录 like/collect 双锚点候选（约 `0.973/0.993` 与 `0.991/0.905`），但 OCR 几何先安全定位评论入口，故按优先级以 `ocr_fallback` 打开并确认评论面板；3 位用户均为 `BLANK_PROBE_VERIFIED`，没有点赞、收藏、分享、真实消息或 `dual_anchor_fallback` 点击。

### 几何与兼容性检查

- 新增的搜索区域、同栏校验、尺寸/间距和中点坐标均使用截图或屏幕比例；未新增固定 Android px、固定点击点或设备相关手势时长。
- 双锚点只能在节点、OCR 与评论模板均不足时作为最后回退；OCR 仍须经几何、视频页状态和评论面板确认，不能直接作为点击依据。

### 交接记录

- [`2026-08-24-p0-action-rail-dual-anchor-fallback.md`](docs/2026-08-24-p0-action-rail-dual-anchor-fallback.md)

---

## [未发布] 2026-08-24 —— 全局动作间隔改为操作员设置

### 修改内容

- 移除无障碍动作的固定 3 秒默认间隔；设置页“自动化服务”新增“全局动作间隔（ms，可留空）”。
- 留空即不施加全局节流；仅可保存 1000–5000ms。无效值会显示错误且不写入本地偏好。
- `GestureEngine` 在每个新动作槽位读取已保存设置，因此变更不需要重绑无障碍服务；页面确认、OCR、重试次数和其他既有等待常量未修改。

### 已验证项

- JVM 用例覆盖默认无节流、1000/5000ms 边界、非法值拒绝和运行时读取新间隔；`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 通过。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：设置页默认空值可见；999 被拒绝；1000 可保存；最终已恢复为空值且私有偏好为空。无障碍服务仍启用，未启动任务或操作抖音。

### 几何与兼容性检查

- 未新增 Android 几何、固定 px、坐标或手势时长；设置页只复用既有 Compose `dp` 布局。
- 节点优先、OCR→几何验证→页面确认及所有任务安全门保持不变。

### 交接记录

- [`2026-08-24-action-pacing-setting.md`](docs/2026-08-24-action-pacing-setting.md)

---

## [未发布] 2026-08-24 —— P0 下一视频右侧栏 OCR 时序优化

### 修改内容

- 仅将下一视频评论入口的两次 OCR 从全屏改为右侧动作栏的截图比例区域
  （横向 `0.68..1.00`、纵向 `0.36..0.96`）；不改变模板匹配的完整截图受限搜索区域。
- 保留原有无障碍节点优先、双帧视觉稳定、OCR 数字栏几何校验、页面状态确认和评论面板后置确认；OCR 仍不能直接授权点击。
- 新增 JVM 纯比例几何测试，避免 Android 本地单元测试桩的 `Rect` 实现影响区域计算回归。

### 已验证项

- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`（340 个 JVM 测试）及 `git diff --check` 通过。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：`designer × 3 × 1` 空消息安全路径完整完成。第 1→2、2→3 视频均以既有 OCR 几何回退确认评论入口并打开评论面板；3 位用户均为 `BLANK_PROBE_VERIFIED`。所有动作槽为 `wait_ms=0`，未记录点赞、收藏、分享或真实消息动作。
- 同机样本中，右侧栏 OCR 从截图请求到完成约 2.68 / 3.52 秒；修改前失败样本的两次全屏 OCR 为约 4.09 / 4.11 秒。该数值仅用于本机同路径对比，不作为多机型性能承诺。

### 几何与兼容性检查

- 新区域仅使用截图宽高比例；未新增固定 Android px、dp 尺寸、坐标点击点或设备相关手势时长。
- 已登记但尚未实施的“点赞 + 收藏双锚点插值”视觉回退，必须另起独立验证轮，并在当前时序优化结束后才可开始。

### 交接记录

- [`2026-08-24-latency-optimization-round-1.md`](docs/2026-08-24-latency-optimization-round-1.md)
- [`2026-08-24-p0-comment-next-video-and-transition-latency.md`](docs/2026-08-24-p0-comment-next-video-and-transition-latency.md)

---

## [未发布] 2026-08-24 —— P0 评论入口半透明图标受限视觉回退

### 修改内容

- 将操作者提供的评论气泡 PNG 作为应用资产；仅为 `comment_next_video_rail` 的已有私有截图增加 Alpha 感知的本地匹配。
- 匹配不比较固定白色 RGB，而是校验气泡前景与三个透明孔位的相对亮暗关系；候选尺寸与搜索区域均使用截图/屏幕比例。
- 视觉候选必须连续两帧在归一化位置和尺寸上稳定，随后仍需 `HOME/UNKNOWN` 视频页确认与既有评论面板后验，才允许作为节点/OCR 均不可用时的最后坐标回退。
- 评论区恢复仍只接受无障碍节点，明确拒绝视觉和 OCR 坐标回退。

### 已验证项

- JVM 用例覆盖首帧拒绝、双帧稳定、已确认右侧模板边界及恢复路径拒绝视觉回退；`git diff --check && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 通过。
- 使用真机保存的 1080 × 2412 视频截图离线校准：评论气泡被唯一保留，心形、收藏和分享未通过三个孔位的相对对比。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：早期启动门样本曾在 `UNKNOWN` 安全暂停。后续集成回归的 `designer × 3 × 1` 已完整完成；第 2、3 视频使用 OCR 几何回退打开评论面板，评论模板仅作为首帧候选而未单独形成点击证据。该结果证明联合入口链路可闭环，不单独宣告模板回退或多机型回归通过。

### 几何与兼容性检查

- 未新增固定 Android px、固定点击点或设备密度阈值；右侧区域、候选大小、坐标映射和双帧稳定均为屏幕比例。
- Alpha/亮度/置信度与采样数量是图像特征参数，不是 Android 布局尺寸；完整多分辨率真机回归仍是通用模板匹配发布前置条件。

### 交接记录

- [`2026-08-24-p0-comment-next-video-and-transition-latency.md`](docs/2026-08-24-p0-comment-next-video-and-transition-latency.md)

---

## [未发布] 2026-08-24 —— M8-E2 执行限额、远程任务授权与操作节流

### 修改内容

- 单任务人数上限调整为 1000；未填写使用 1000；B 端输入框限制最多 1000，并将旧的自动保存默认值 20 迁移为 1000。
- 本地串行待办队列限制为 999 项；评论私信按“视频数 × 每视频人数”计算，单任务总人数同样不得超过 1000。
- 新增当日独立用户额度账本，最多 9999 人，仅持久化匿名 SHA-256 指纹与保留时间；达到上限会暂停当前任务/队列，避免进入下一任务，次日可从检查点继续。
- 新增远程任务 ID 本机授权清单。远程任务在领取前、控制器启动时和检查点恢复时均需命中清单；本机显式创建的 UUID 任务不受影响。
- 节点点击、文本设置/提交、滚动、归一化手势、返回和启动目标应用共用 3 秒最短操作间隔。

### 已验证项

- 新增 9 项 JVM 策略测试，覆盖限额、旧草稿迁移、远程授权、队列、日额度/跨日、节流与快照入口。
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin` 与 `git diff --check` 通过；Lint 为 0 error。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：Debug 包可安装；远程授权设置项可见；保留旧草稿时 B 端用户上限显示 1000；无障碍服务保持禁用，未启动任务、未操作抖音或发送消息。

### 几何与兼容性检查

- 新增界面尺寸使用既有 Compose `dp`；未新增固定 px、OCR 几何、坐标或设备相关手势时长。
- 节点优先、OCR→几何验证→页面确认、头像排除、私信入口规则和空白消息安全门均保持不变。

### 交接记录

- [`2026-08-24-m8-e2-execution-limits-and-authorization.md`](docs/2026-08-24-m8-e2-execution-limits-and-authorization.md)

---

## [未发布] 2026-08-24 —— M8-E1 图像匹配预留通道边界

### 修改内容

- 明确 `NoOpImageMatcher` 为当前唯一允许接线的禁用实现；它不匹配图像、不保留截图、不会触发点击或替代节点链路。
- 记录未来模板匹配的独立启用门槛：脱敏模板、多尺度/ORB、dp 或比例尺寸、节点+几何+页面确认及多分辨率真机回归。

### 已验证项

- 代码审计确认 `ImageMatcher` 未被控制器、评论运行器、选择器或手势引擎调用。
- 本阶段无可执行行为改动；沿用完整 Debug 单测、Lint、构建成功结果。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、坐标或手势时长；无需新增 dp 归一化逻辑。

### 交接记录

- [`2026-08-24-m8-e1-image-matcher-reserved.md`](docs/2026-08-24-m8-e1-image-matcher-reserved.md)

---

## [未发布] 2026-08-24 —— M7-C4 私信入口安全规则远程配置

### 修改内容

- 将 B 端私信、评论私信和主页纸飞机兜底中重复的私信语义/风险文案判定收敛到 `PrivateMessageEntryRulePolicy`。
- 新增 HTTPS JSON 规则目录、私有缓存和服务绑定时的异步刷新：`/automation/mobile/private-message-entry-rules`。未配置授权、网络或 JSON 失败时使用缓存或内置安全基线。
- 远程规则只能新增拒绝词或收缩既有允许词；内置拒绝词无法移除，远程新增允许词不会单独成为新的自动点击依据。

### 已验证项

- 新增规则策略和缓存回退 JVM 测试；既有纸飞机兜底测试通过。
- 新增 Android HTTP JSON 契约测试并完成 `:app:compileDebugAndroidTestKotlin`。
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：Debug 包可安装并启动到首页，无障碍服务保持禁用、无崩溃；未启动任务、未操作抖音、未输入或发送消息。

### 几何与兼容性检查

- 未新增或修改 Android 几何尺寸、固定 px、坐标或手势时长；无需新增 dp 归一化逻辑。
- 节点优先、OCR→几何验证→页面确认、头像排除、风险暂停和空白消息安全门保持不变。

### 交接记录

- [`2026-08-24-m7-c4-private-message-entry-rules.md`](docs/2026-08-24-m7-c4-private-message-entry-rules.md)

---

## [未发布] 2026-08-24 —— M7-C3 统一显示名称解析器

### 修改内容

- 新增 `DisplayNameResolver` 作为用户主页和私信页显示名称的唯一控制器入口：统一页面表面委托、OCR 门控、无障碍优先的来源仲裁与主页二次一致确认。
- `DouyinNavigationController` 不再直接依赖 `ProfileDisplayNameResolver`、`DirectMessageDisplayNameResolver` 或自行实现主页名称确认；页面级候选提取、OCR 区域、节点/几何过滤、记录字段、点击、手势、导航和消息发送均未改变。

### 已验证项

- 新增 `DisplayNameResolverTest`；主页、私信、策略的既有名称解析测试同时通过。
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：新包安装后无障碍服务可绑定、无崩溃。发现设备中有历史恢复状态，已在任何自动化动作前停止服务；未点击抖音、未输入或发送消息，未以历史任务伪造 C3 行为验证。

### 几何与兼容性检查

- 未新增或修改 Android 几何尺寸、固定 px、坐标或手势时长；无需新增 dp 归一化逻辑。
- 节点优先、OCR→几何验证→页面确认、头像排除、风险暂停和空白消息安全门保持不变。

### 交接记录

- [`2026-08-24-m7-c3-display-name-resolver.md`](docs/2026-08-24-m7-c3-display-name-resolver.md)

---

## [未发布] 2026-08-24 —— 评论搜索恢复与悬浮窗真机回归

### 修改内容

- 修复“评论私信 → 搜索指定用户”在用户标签切换帧中读取到隐藏 `ViewPager` 子树时过早失败的问题；仅在该过渡态执行有界稳定等待，之后仍需结构验证或两次一致的 OCR/几何验证才可打开源主页。
- 修复暂停恢复的初始流程在已验证搜索结果输入框存在时，仍强制回到首页并可能超时的问题；现在会覆盖并重新提交冻结的搜索词。
- 修复评论任务将单个源主页错误持久化为通用已处理用户、导致恢复后跳过该主页的问题；评论候选仍使用原有独立去重账本。

### 已验证项

- 新增 `UserResultsViewportTransitionDetectorTest`；`./gradlew :app:testDebugUnitTest :app:assembleDebug` 通过。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：以“室内设计师”、2 视频、每视频 5 人、搜索指定用户、空白消息安全探测模式，验证用户结果→源主页→视频→评论面板；悬浮窗暂停、恢复、停止均可用。恢复后同一源主页再次经 OCR/几何验证进入，未被错误判为重复。
- 未发送真实消息；最终停止时仅已进入评论候选的私信入口，未输入或提交消息，也未运行空消息安全探测。

### 几何与兼容性检查

- 未新增固定 px、点击坐标、Android 尺寸或手势时长。过渡态检测只使用无障碍节点可见性和已有页面验证；新增延时为版本化导航等待配置，非设备几何。
- 节点优先、OCR→几何验证→页面确认、头像排除、风险暂停和空白消息安全门保持不变。

### 交接记录

- [`2026-08-24-comment-search-row-settle-repair.md`](docs/2026-08-24-comment-search-row-settle-repair.md)
- [`2026-08-24-comment-resume-search-recovery-repair.md`](docs/2026-08-24-comment-resume-search-recovery-repair.md)
- [`2026-08-24-comment-resume-source-identity-repair.md`](docs/2026-08-24-comment-resume-source-identity-repair.md)
- [`2026-08-24-pause-resume-progress-repair.md`](docs/2026-08-24-pause-resume-progress-repair.md)

---

## [未发布] 2026-08-24 —— 评论私信流程等待优化

### 修改内容

- 将启动首次观察、评论用户主页轮询、首视频/评论面板探测、头像刷新重试、评论翻页读取和下一视频结算的保守固定等待缩短为更早的有界采样。
- 冷启动尚无无障碍根节点时与 UNKNOWN 启动帧采用同一“只观察、不后退”策略，避免缩短首次等待后误触旧的盲目 BACK 恢复。
- 仅调整版本化等待配置与初始无树安全策略；不修改状态机、选择器、OCR 预算、几何/坐标、手势时长、候选筛选或消息发送逻辑。

### 已验证项

- 更新 `InitialHomeSurfacePolicyTest` 与 `TuningConstantsTest`，覆盖无树启动延后策略及新的版本化等待值。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：2 视频 × 每视频 2 人空白消息安全探测完整完成，4 位用户均为 `BLANK_PROBE_VERIFIED`，第二视频正常进入评论面板，完成后回到记录页。
- **真机翻页验证**：1 视频 × 每视频 5 人完成首屏 4 位候选、自动滚动一次、发现新候选并完成第 5 位；5 位均为 `BLANK_PROBE_VERIFIED`。
- 未发送真实消息；冷启动期间没有 `initial_context_missing_back`。

### 几何与兼容性检查

- 未新增或修改 Android 几何尺寸、固定 px、归一化坐标或手势时长；无需新增 dp 逻辑。
- 节点优先、OCR→几何验证→页面确认、风险暂停与空白消息安全门保持不变。

### 交接记录

- [`2026-08-24-comment-flow-latency-tuning.md`](docs/2026-08-24-comment-flow-latency-tuning.md)

---

## [未发布] 2026-08-24 —— 评论私信“搜索指定用户”稳定性修复与真机闭环

### 修改内容

- 启动初始表面尚未完成分类时，`UNKNOWN` 不再触发 BACK；维持既有的有界观察和超时安全暂停。
- 仅在评论私信的“用户”搜索结果 OCR 采样中保留行级文字几何，使首张自绘结果卡可将显示名、账号与关注控件作同卡验证。
- 将已验证结果卡的手势兜底收窄到名称/主页入口带，继续排除头像与关注按钮；未改变语义节点点击和用户主页后置确认。
- 第二视频详情页临时分类为 HOME 时，可在既有上滑上下文中进行至多两次只读 OCR；评论文字兜底改为受限的文字上方图标区，并保留越界拒绝、完整右侧动作列和页面确认。
- 从评论任务记录重试后保留在记录页，避免显示为 B 端任务新建表单。

### 已验证项

- 新增/更新 `InitialHomeSurfacePolicyTest`、`OcrTextBlockMapperTest`、`CommentSurfaceSignalsTest`、`TuningConstantsTest`。
- **真机 OnePlus NE2210 / b33aa309 / Android 16**：以“室内设计师”、2 视频、每视频 2 人、搜索指定用户、非干跑空白消息安全探测完成全程。首卡进入用户主页成功；两段视频各完成 2 位评论用户，共 4 次 `BLANK_PROBE_VERIFIED`；第二视频经受限 OCR 打开评论面板；任务完成后自动回记录页。
- 未发送真实消息。

### 几何与兼容性检查

- 新增/调整的评论图标、结果卡点按带均使用屏幕或行高比例；无新增固定 px，未混用 dp 尺寸与归一化坐标。
- 节点优先、OCR→几何验证→页面状态确认、风险暂停及空白消息安全门保持不变。

### 交接记录

- [`2026-08-24-comment-search-startup-unknown-recovery.md`](docs/2026-08-24-comment-search-startup-unknown-recovery.md)

---

## [未发布] 2026-08-24 —— M7-C2h 恢复阶段动作编排与 C2 完成

### 修改内容

- 新增 [`RecoveryFlow.kt`](app/src/main/java/com/example/douyinautomation/automation/RecoveryFlow.kt)，将既有超时恢复路由的广告等待、重试、恢复、跳过和暂停分派移出控制器主干。
- C2 已完成：搜索、用户选择、私信和恢复四阶段均具有纯路由及动作编排边界；控制器仍保留共享的页面读取、节点/OCR/几何校验、手势、检查点和任务安全策略。

### 已验证项

- 新增 `RecoveryFlowTest`，覆盖广告等待、搜索重试、用户选择、私信重试、失败跳过和暂停。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：安装更新后服务 Bound/Enabled、Crashed 为空；停止态没有任务、恢复、页面动作、空白探测或消息发送。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、坐标或手势；无需新增 dp 归一化逻辑。
- 启动广告仍仅等待，节点优先、OCR 辅助、几何校验、页面确认和消息安全门保持不变。

### 交接记录

- [`2026-08-24-m7-c2-recovery-flow-orchestration.md`](docs/2026-08-24-m7-c2-recovery-flow-orchestration.md)
- [`2026-08-24-m7-c2-flow-orchestration-completion.md`](docs/2026-08-24-m7-c2-flow-orchestration-completion.md)

---

## [未发布] 2026-08-24 —— M7-C2g 用户选择阶段动作编排拆分

### 修改内容

- 新增 [`UserSelectionFlow.kt`](app/src/main/java/com/example/douyinautomation/automation/UserSelectionFlow.kt)，将处理上限、远程锚点续接与账号帮助结束分派移出用户行处理主干。
- 控制器继续独占候选行身份提取、去重、屏蔽词、关注限制、OCR、几何校验、节点点击、翻页和检查点。

### 已验证项

- 新增 `UserSelectionFlowTest`，覆盖三个既有入口动作及普通候选不消费。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：安装更新后服务 Bound/Enabled、Crashed 为空；停止态没有任务、恢复、页面动作、空白探测或消息发送。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、坐标或手势；无需新增 dp 归一化逻辑。
- 候选行节点优先、OCR 辅助、几何校验、页面确认和安全兜底顺序保持不变。

### 交接记录

- [`2026-08-24-m7-c2-user-selection-flow-orchestration.md`](docs/2026-08-24-m7-c2-user-selection-flow-orchestration.md)

---

## [未发布] 2026-08-24 —— M7-C2f 搜索阶段动作编排拆分

### 修改内容

- 新增 [`SearchFlow.kt`](app/src/main/java/com/example/douyinautomation/automation/SearchFlow.kt)，将搜索路由的打开搜索、恢复、输入、复用结果、用户标签与候选选择分派移出页面观察主干。
- 控制器仍独占实际搜索、恢复、关键词输入、用户标签/候选选择及节点/OCR/几何/手势安全链路。

### 已验证项

- 新增 `SearchFlowTest`，覆盖六种既有搜索动作及无关页面不消费。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：安装更新后服务 Bound/Enabled、Crashed 为空；停止态没有任务、恢复、页面动作、空白探测或消息发送。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、坐标或手势；无需新增 dp 归一化逻辑。
- 搜索和候选选择的节点优先、OCR 辅助、几何校验和页面确认顺序保持不变。

### 交接记录

- [`2026-08-24-m7-c2-search-flow-orchestration.md`](docs/2026-08-24-m7-c2-search-flow-orchestration.md)

---

## [未发布] 2026-08-24 —— M7-C2e 私信阶段动作编排拆分

### 修改内容

- 新增 [`PrivateMessageFlow.kt`](app/src/main/java/com/example/douyinautomation/automation/PrivateMessageFlow.kt)，将既有私信路由的完成、跳过、后置确认和风险暂停分派移出页面观察主干。
- 控制器通过原有方法提供回调，仍独占私信完成、空白探测、失败记录、风险暂停、节点/OCR/几何/手势和消息安全策略。

### 已验证项

- 新增 `PrivateMessageFlowTest`，覆盖私信完成、失败、空白探测、风险、后置确认及无关页面不消费。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：安装更新后服务 Bound/Enabled、Crashed 为空；停止态没有任务、恢复、页面动作、空白探测或消息发送。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、坐标或手势；无需新增 dp 归一化逻辑。
- 空白消息探测和风险暂停安全链路保持不变。

### 交接记录

- [`2026-08-24-m7-c2-private-message-flow-orchestration.md`](docs/2026-08-24-m7-c2-private-message-flow-orchestration.md)

---

## [未发布] 2026-08-24 —— M7-C2d 恢复阶段路由拆分

### 修改内容

- 新增 [`RecoveryFlowRouter.kt`](app/src/main/java/com/example/douyinautomation/automation/RecoveryFlowRouter.kt)，将看门狗超时后的既有阶段/页面分流收敛为无副作用路由。
- [`DouyinNavigationController.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt) 保留启动广告识别与只等待策略、超时预算、恢复动作、跳过归因和暂停；不移动节点/OCR/几何/手势或私信安全链路。

### 已验证项

- 新增 `RecoveryFlowRouterTest`，覆盖启动广告、搜索、首页、用户结果、主页和不支持阶段的既有恢复出口。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：安装更新后服务 Bound/Enabled、Crashed 为空；停止态没有任务、恢复、页面动作、空白探测或消息发送。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、坐标或手势；无需新增 dp 归一化逻辑。
- 启动广告仍仅等待，节点优先、OCR 辅助、几何校验、页面确认和消息安全门保持不变。

### 交接记录

- [`2026-08-24-m7-c2-recovery-flow-router.md`](docs/2026-08-24-m7-c2-recovery-flow-router.md)

---

## [未发布] 2026-08-24 —— M7-C2c 用户选择阶段路由拆分

### 修改内容

- 新增 [`UserSelectionFlowRouter.kt`](app/src/main/java/com/example/douyinautomation/automation/UserSelectionFlowRouter.kt)，把用户结果阶段入口的既有优先级收敛为无副作用路由：处理上限、远程断点锚点、账号帮助结束和可见候选选择。
- [`DouyinNavigationController.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt) 继续执行远程续接、查询切换、完成记录、节点/OCR/几何验证、点击、手势和检查点写入；账号帮助文本的读取时机保持在原先高优先级分支之后。

### 已验证项

- 新增 `UserSelectionFlowRouterTest`，覆盖处理上限、远程续接、账号帮助、视口锚点和默认选择的优先级。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：安装更新后服务 Bound/Enabled、Crashed 为空；停止态没有任务、恢复、页面动作、空白探测或消息发送。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、坐标或手势；无需新增 dp 归一化逻辑。
- 节点优先、OCR 辅助、几何校验、页面确认和消息安全门保持不变。

### 交接记录

- [`2026-08-24-m7-c2-user-selection-flow-router.md`](docs/2026-08-24-m7-c2-user-selection-flow-router.md)

---

## [未发布] 2026-08-24 —— M7-C2b 私信阶段路由拆分

### 修改内容

- 新增 [`PrivateMessageFlowRouter.kt`](app/src/main/java/com/example/douyinautomation/automation/PrivateMessageFlowRouter.kt)，将私信页、消息结果和空白探测结果的既有页面分流收敛为无副作用路由。
- [`DouyinNavigationController.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt) 保留所有完成、跳过、后置确认和暂停方法；不移动发送、空白探测、节点/OCR/手势或风险页全局安全门。

### 已验证项

- 新增 `PrivateMessageFlowRouterTest`，覆盖私信终态、空白拒绝、私信受限、普通/探测失败、后置确认与风险映射。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：更新后服务 Bound/Enabled、Crashed 为空；停止态没有任务、恢复、私信进入、页面动作、空白探测或消息发送。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、坐标或手势；无需新增 dp 归一化逻辑。
- 节点优先、OCR 辅助、几何校验、页面确认和消息安全门保持不变。

### 交接记录

- [`2026-08-24-m7-c2-private-message-flow-router.md`](docs/2026-08-24-m7-c2-private-message-flow-router.md)

---

## [未发布] 2026-08-24 —— M7-C2a 搜索阶段路由拆分

### 修改内容

- 新增 [`SearchFlowRouter.kt`](app/src/main/java/com/example/douyinautomation/automation/SearchFlowRouter.kt)，将首页、搜索入口、搜索结果与用户结果四个等待阶段的“页面类型 → 既有下一步”收敛为无副作用路由。
- [`DouyinNavigationController.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt) 继续执行原有搜索、恢复、关键词录入、用户标签和用户行方法；不移动节点/OCR/手势/超时/私信动作。

### 已验证项

- 新增 `SearchFlowRouterTest`，覆盖首页受限恢复、搜索入口、搜索结果、用户结果和无关页面。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：更新后服务 Bound/Enabled、Crashed 为空；停止态没有任务、恢复、搜索动作、页面点击、滑动、空白探测或消息发送。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、坐标或手势；无需新增 dp 归一化逻辑。
- 节点优先、OCR 辅助、几何校验、页面确认和消息安全门保持不变。

### 交接记录

- [`2026-08-24-m7-c2-search-flow-router.md`](docs/2026-08-24-m7-c2-search-flow-router.md)

---

## [未发布] 2026-08-24 —— M7-C1 跨流程调参收敛完成

### 修改内容

- [`TuningConstants.kt`](app/src/main/java/com/example/douyinautomation/automation/TuningConstants.kt) 已形成版本 `4` 的四个只读分组：导航生命周期、导航流程、评论运行时、无障碍生命周期。
- [`DouyinNavigationController.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt)、[`CommentPrivateMessageRuntime.kt`](app/src/main/java/com/example/douyinautomation/automation/CommentPrivateMessageRuntime.kt)、[`DouyinAccessibilityService.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinAccessibilityService.kt) 的跨流程时序、重试、上限和归一化动作参数均改由该层读取；数值与行为不变。
- 单一检测器的证据系数、加密/存储/协议常量继续局部封装，不开放为远程或全局运行调参。

### 已验证项

- `TuningConstantsTest` 覆盖启动、恢复、评论私信、滚动/直播上限、消息安全和归一化手势关键值；控制器 84 个 `NavigationFlow` 引用均有配置声明。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：更新后服务 Bound/Enabled、Crashed 为空；停止态没有任务、恢复、页面动作、空白探测或消息发送。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、点击坐标或动作逻辑；既有比例坐标保持 `0..1`，未与 dp 体系混用。
- 节点优先、OCR 辅助、几何校验、页面确认和消息安全门保持不变。

### 交接记录

- [`2026-08-24-m7-c1-tuning-constants-completion.md`](docs/2026-08-24-m7-c1-tuning-constants-completion.md)

---

## [未发布] 2026-08-24 —— M7-C1b 评论运行时调参收敛

### 修改内容

- [`TuningConstants.kt`](app/src/main/java/com/example/douyinautomation/automation/TuningConstants.kt) 新增版本 `2` 的 `CommentRuntime` 分组，集中评论私信运行时的时序、重试、滚动/直播停止上限、空白探测预算及私有诊断目录。
- [`CommentPrivateMessageRuntime.kt`](app/src/main/java/com/example/douyinautomation/automation/CommentPrivateMessageRuntime.kt) 只改为读取该配置；所有原始值、循环条件、延时、页面判定与安全停止分支保持不变。

### 已验证项

- `TuningConstantsTest` 扩展关键评论安全预算覆盖。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：更新后服务 Bound/Enabled、Crashed 为空；停止态没有任务、恢复、页面动作、空白探测或消息发送。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、坐标、手势或远程调参入口；无需新增 dp 归一化逻辑。
- 评论节点/OCR/几何/页面确认和空白消息安全闭环保持不变。

### 交接记录

- [`2026-08-24-m7-c1-comment-runtime-tuning.md`](docs/2026-08-24-m7-c1-comment-runtime-tuning.md)

---

## [未发布] 2026-08-24 —— M7-C1a 导航生命周期调参收敛

### 修改内容

- 新增 [`TuningConstants.kt`](app/src/main/java/com/example/douyinautomation/automation/TuningConstants.kt) 的版本化 `NavigationLifecycle` 配置组，集中管理启动稳定等待、观察窗口、未知页 OCR 重试及基础阶段看门狗的八个既有数值。
- [`DouyinNavigationController.kt`](app/src/main/java/com/example/douyinautomation/automation/DouyinNavigationController.kt) 仅改为直接读取该配置；延时、次数、日志值、OCR 门控、页面判定、动作与安全策略不变。

### 已验证项

- 新增 `TuningConstantsTest` 锁定迁移前的八个数值与配置版本。
- 自动化验证：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 `git diff --check` 均通过。
- **真机 OnePlus NE2210 / b33aa309**：更新后服务 Bound/Enabled、Crashed 为空；停止态没有任务启动、恢复、页面动作、空白探测或消息发送。

### 几何与兼容性检查

- 未新增 Android 几何尺寸、固定 px、坐标、手势或远程调参入口；无需新增 dp 归一化逻辑。
- 节点优先、OCR 辅助、几何校验、页面确认和消息安全门保持不变。

### 交接记录

- [`2026-08-24-m7-c1-navigation-lifecycle-tuning.md`](docs/2026-08-24-m7-c1-navigation-lifecycle-tuning.md)

---

## [未发布] 2026-08-24 —— M6 双机适配验收（无源码改动）

### 已验证项

- **OnePlus NE2210 / b33aa309 / Android 16 / 1080×2412 / 480dpi / 120Hz** 与 **vivo V2217A / 10ACAA2DHF001QY / Android 13 / 1080×2400 / 480dpi / 120Hz**：当前 Debug APK 均能正常绑定并启用无障碍服务，`Crashed services` 为空。
- 两台设备均只做停止态前台观察：未启动或恢复任务，未执行抖音页面内点击、滑动、私信、空白消息探测或消息发送。vivo 保留了其原有无障碍服务，本项目服务以追加方式启用。
- OCR 量化为输入图像工作量：在 vivo 1080×2400 屏幕上，`USER_RESULTS` / `PROFILE_HEADER` / `PROFILE_ACTION` / `MESSAGE_COMPOSER` / `TOAST` 相对 `FULL` 分别减少 16% / 73% / 62% / 62% / 57% 的输入像素面积；全屏没有 1.5× 放大。

### 已知限制

- 两机的宽度、密度和刷新率相同，仅高度与 Android 版本不同；尚未覆盖低刷新率、不同 dpi 或明显不同宽高比设备。
- 当前诊断日志不记录同一固定样本的 OCR 识别毫秒数，输入面积比例不能替代跨设备固定耗时比例；如需性能 SLA，应后续增加不含 OCR 文本的耗时采样。

### 交接记录

- [`2026-08-24-m6-two-device-acceptance.md`](docs/2026-08-24-m6-two-device-acceptance.md)

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
