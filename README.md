# Douyin Automation POC — M3

当前开发版本：`0.3.4-mobile-login`

M1 冻结版本仍保留在标签 `v0.1.0-m1`。

这是一个 Android 自动化可行性验证原型，使用 Kotlin、Jetpack Compose、Android AccessibilityService、ML Kit OCR 和标准 ADB 流程，最低支持 Android 11（API 30）。项目只使用普通 Android 应用能力，不依赖 Root、`su`、`adb root` 或系统提权。

## 当前实现范围

- 用户主动授权的无障碍服务，仅处理抖音窗口（`com.ss.android.ugc.aweme`）
- 节点树快照、私有 Node dump、截图和 ML Kit 中文 OCR 诊断
- PageDetector / Selector：节点文本、content description、结构、Bounds、归一化区域和有限坐标兜底
- 搜索流程：打开抖音 → 输入指定关键词 → 只点击右上角“搜索”（忽略下拉候选）
- 用户流程：定位“用户”标签、识别用户行、跳过“回关”用户、点击名称区域进入主页（不点击头像）
- 私信入口兼容完整“发私信”按钮和纸飞机图标，打开私信页有超时和人工接管
- M2 安全探测：Start 不发送真实文案；进入私信页后只提交一个 ASCII 空格
- 空白消息结果校验：识别“不能发送空白消息”等模糊提示后，确认本用户链路可提交并继续下一位
- 多用户推进：返回用户结果页、有限滚动并选择下一行；回关用户、私信失败、超时和空白提示均有独立日志
- 真实消息发送：保留底层显式 API 供后续审核，不由 M2 UI 暴露，避免测试误发内容
- 系统横幅/临时遮挡检测：等待抖音窗口恢复后重试，不穿透系统覆盖层操作
- 验证码、登录失效、风险控制和未知页面一律暂停并等待人工处理，不实施破解或绕过
- M3-A/B 初步任务模型：地区+关键词组合、预设词、本地预设源和屏蔽关键词评估
- 正常任务工作台首页：任务配置、最终搜索词预览、地区、屏蔽词和任务/记录/设置导航
- 任务历史摘要：持久化任务状态、冻结搜索词/地区/屏蔽词/执行模式及处理统计；不保存 OCR 原文
- 基础授权边界：Android Keystore 加密本地授权配置、heartbeat 请求/响应模型、周期验证状态
- Android HTTP Gateway：Bearer 授权、heartbeat、搜索预设/地区/屏蔽词目录、任务领取、进度、分页断点和用户结果同步契约
- 远程任务工作台：读取后台待执行任务、绑定数字任务 ID、本地执行与异步断点/用户记录上报；默认隐藏并停止轮询，可在设置中开启；网络失败不阻塞自动化
- 远程断点续跑：领取任务时读取后台进度，按最后用户身份和已处理身份台账定位后从下一条继续；缺少可验证台账时安全暂停
- 远程规则绑定：地区前缀和屏蔽关键词目录可选，多选结果冻结到任务快照并随远程任务下发
- 任务状态写回：领取、暂停、继续、完成、失败和停止状态异步写回后台，网络失败不阻塞本地执行
- 任务结束回跳：无论完成、失败、停止还是人工暂停，自动打开 App 的“记录”页
- 处理记录详情：记录用户显示名/稳定账号标识、开始/结束时间、模拟消息内容、处理结果和失败说明，可点击查看
- 独立任务详情页：从“记录”列表进入后展示任务状态、时间、统计和全部用户处理结果，不再使用弹窗承载详情
- 账号帮助行兼容：将“找不到想要的账号？告诉我们”视为结果列表中的辅助行；仅在没有真实用户行时作为有界结束信号
- 私信入口安全门：节点优先，纸飞机/私信语义校验；拒绝“联系客服/咨询/购物车”等同区域误匹配，不再使用私信入口 OCR 直接点击或无语义坐标点击
- OCR 降级：ML Kit 使用用户列表、主页操作区、消息区等局部裁剪并放大预处理；服务层按页面签名缓存短期结果；OCR 页面需要连续两帧一致后才能驱动状态机
- 启动页面兼容：抖音恢复旧搜索结果时复用顶部 EditText 并重新校验任务关键词；已知用户列表/主页/聊天页有界返回；开屏广告只等待不点击，未知页面安全暂停并保留诊断
- 启动 watchdog：从视频页、开屏广告或冷启动回到可搜索页面时最多等待 30 秒；用户行、主页和私信步骤仍使用较短超时
- 图像识别扩展点：保留 `ImageMatcher` 作为 OpenCV/模板匹配注入接口；在未安装模板和未达到置信度前不会替代节点操作或冒险点击
- 预设词远程接口：远程优先、本地缓存和内置预设回退；设置页可录入 HTTPS 后端与授权 Token
- 移动端账号登录：设置页输入后台用户名和密码，经 HTTPS 调用 `/automation/mobile/login` 获取设备绑定移动授权；密码和管理端 JWT 不落盘，登录后自动刷新 heartbeat 与远程任务；账号用户名与显示名称分开保存，重启后仍可直接再次登录
- 远程任务操作提示：任务面板支持手动刷新、最近刷新时间、空列表和后台状态显示，便于核对任务是否已下发到设备
- 设备摘要：heartbeat 只发送 Android ID 的 SHA-256 摘要，不发送原始设备标识
- M3 UI Redesign：蓝色企业级 Automation Dashboard、独立新建任务页、状态 Badge/指标卡、紧凑处理记录与任务详情、分组设置页和中文开发诊断页；保留原有状态机、任务数据和后台接口

## 构建与测试

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

本机开发辅助脚本：

```bash
scripts/check-device.sh <adb-serial>
scripts/build-test.sh
scripts/install-debug.sh <adb-serial>
scripts/capture-device-screen.sh <adb-serial>
scripts/ensure-accessibility.sh <adb-serial>
```

开发机可以显式尝试 `ALLOW_ADB_ACCESSIBILITY=1 scripts/ensure-accessibility.sh <adb-serial>`；
脚本只使用 ADB shell，不使用 Root，失败时会打开系统设置等待人工确认。正式 App 不包含该能力。

不要在日常真机上运行 `:app:connectedDebugAndroidTest`；Gradle UTP 的清理阶段可能卸载应用。真机验证使用 `adb install -r` 和显式 `adb shell am instrument`，并需要明确设置 `CONFIRM_DEVICE_TEST=1`。

## 已验证链路

在 Android 设备上已验证：

```text
启动抖音
  → 搜索“是小瑜瑜呀~”
  → 用户标签
  → 用户主页
  → 私信页
  → 输入一个空格并点击发送
  → 识别“不能发送空白消息”
  → 返回用户列表并进入下一位
```

当前代码通过 Debug 构建、Android Lint 和 JVM 单元测试。截图、OCR、Node dump、Logcat 和 APK 迭代文件均保存在本地 `outputs/`、`artifacts/` 或 `work/`，不进入版本控制。

## 下一阶段设计

M2.5 产品板块、任务模型、地区组合、预设词接口和关键词屏蔽规则见：
[`docs/M2.5-product-and-task-design.md`](docs/M2.5-product-and-task-design.md)

M3-D/E 记录、预设缓存和授权 heartbeat 边界见：
[`docs/M3-D-records-and-preset-source.md`](docs/M3-D-records-and-preset-source.md)

M3-F Android HTTP Gateway、后端 B2/B3 接口映射和测试策略见：
[`docs/M3-F-http-gateway.md`](docs/M3-F-http-gateway.md)

M3-G 远程任务会话、异步断点/记录同步和安全边界见：
[`docs/M3-G-remote-task-session.md`](docs/M3-G-remote-task-session.md)

M3-H 远程任务断点定位和精确续跑见：
[`docs/M3-H-remote-resume.md`](docs/M3-H-remote-resume.md)

M3-I 远程地区、屏蔽关键词目录与任务快照绑定见：
[`docs/M3-I-remote-rule-binding.md`](docs/M3-I-remote-rule-binding.md)

M3-J 远程任务生命周期状态写回见：
[`docs/M3-J-remote-status-writeback.md`](docs/M3-J-remote-status-writeback.md)

本轮 M3-K 稳定性改造与验收边界见：
[`docs/M3-K-stability-and-records.md`](docs/M3-K-stability-and-records.md)

M3-P0 任务状态闭环、冻结参数详情和远程任务刷新见：
[`docs/M3-P0-final-closure.md`](docs/M3-P0-final-closure.md)

M3.5 任务配置持久化、记录筛选、历史任务复用、失败/暂停任务安全重试和无障碍服务重绑续跑已并入当前版本；真机回归期间不使用会抢占无障碍通道的 `uiautomator dump`。

M3-P2 真机回归补充：顶部用户标签候选增加紧凑顶部标签带约束；翻页时若上一条锚点被裁出，则按已处理身份台账选择首个未处理用户，避免半页加载造成重复或跳过。

M3-UI1 视觉改版：见 `app/src/main/java/com/example/douyinautomation/ui/theme/` 的 Design System；本轮只调整 Compose UI 与版本展示，不修改 Accessibility、PageDetector、Selector、OCR、任务规则或后台协议。

M3-Recovery 私信流程容错：见 [`docs/M3-recovery-per-user-failure.md`](docs/M3-recovery-per-user-failure.md)。私信输入、发送确认和进入私信页均采用有限重试与超时；可判定的单用户阻塞记录为失败并自动跳过，只有验证码、登录、风控或无法恢复到结果列表的任务级故障才停止人工处理。

M3-ProfileName 记录名称修复：用户主页名称采用无障碍节点优先、局部 OCR 兜底；资源 ID、控件类名、店铺/认证标签及资料操作菜单文本不会写入用户名称。详情页对历史记录做展示层清洗（行号、截断标记、已知 UI 后缀），稳定复合身份键仍仅用于去重。已通过真机安装、详情页回归和 JVM 单元测试验证。

M3-ProfileConfidence 名称可信度优化：主页标题采用连续两帧无障碍节点确认；列表识别来自 OCR 或主页节点不稳定时，才执行一次主页标题局部中文 OCR；增加保守的常见地名 OCR 混淆校正（例如“抗州”→“杭州”），避免全屏重复 OCR。

M3-IdentityNoise 账号身份修复：过滤“背景图片”“用户头像”等头像/封面无障碍描述，优先使用用户名称文本节点；当一行只有通用图片描述时不创建用户记录；主页名称回填同样排除图片类噪声，避免错误账号名进入任务详情。

M3-ProfileAnchor 主页名称锚点修复：主页名称解析优先绑定圆形头像右侧区域，并用“店铺账号/抖音号”所在行作为第二锚点；若账号标签缺失，则选择头像右侧字号最大的名称；顶部“求更新/搜索”等操作按钮不会再被识别为账号名称。

M3-ProfileControlNoise 控件噪声修复：将“筛选”“按钮”等顶部控件描述加入名称黑名单，并同步应用于主页解析、稳定身份去重和历史详情展示，避免瞬时页面状态把筛选按钮记录成用户名称。

M3-DirectMessageIdentity 私信页名称兜底：在完成私信页安全探测前，优先从大圆形头像下方的名称节点回填用户名称；头像带“+”关注标记、顶部关注提示或头像大小变化时仍按头像几何关系识别，节点缺失才使用私信页局部 OCR。

M3-IdentityCanonicalization 重复记录修复：身份比对增加繁简/OCR 常见字形规范化与编辑距离匹配，兼容公司名漏字和相邻帧字体差异，减少同一用户被重复处理和重复记录。

M3-RemoteTaskVisibility 远程任务显示策略：任务页默认隐藏后台远程任务并停止轮询；设置页可单独开启显示和刷新，手机本地新建任务流程不受影响。

M3-TaskFormCompact 新建任务页优化：顶部仅保留无障碍服务状态；配置卡片改为紧凑布局；搜索词和屏蔽词预设改为换行并联动输入框；任务名称可留空并自动按“搜索词-时间”生成；移除最终搜索词预览、手动保存/清除和底部返回按钮。

M3-TaskFormCompact-2 新建任务页密度优化：输入框改用可控内容内边距，避免短字段内容被裁切；预设搜索词、地区规则和屏蔽词改为紧凑按钮，缩小字体、控件高度及换行间距，减少默认交互组件留白；已在真机安装后复核布局和无障碍状态。

M3-UI2 参考移动端搜索/表单风格：统一白底、浅灰表面、黑灰文字和克制蓝色操作；任务配置输入改为“标题 + 内容 + 细分隔线”表单行，预设搜索词使用浅灰黑字胶囊并自然横向排列、自动换行；新建任务页隐藏底部导航，设置页字段同步使用同一表单样式。

M3-TaskQueue 多任务运行：新建任务支持“保存”与“立即开始”；任务页以“待办任务”展示本地保存配置，支持勾选多个任务并按 FIFO 顺序执行。单个任务完成或可恢复失败后自动进入下一个，批次结束后再打开记录页；新建任务入口固定在任务页底部导航上方。通用顺序队列位于 `automation/TaskQueue.kt`。

M3-UI3 首页与待办导航：底部导航调整为“首页 / 待办 / 记录 / 设置”；首页改为总览、功能入口和今日待办，待办任务完整列表移动到独立“待办”页；B端私信入口打开新建任务表单，评论私信入口仅展示占位卡片。首页统计使用本地任务历史与客户处理记录计算，暂无任务时进度显示 100%。

多任务验收样例：依次保存并勾选“佛山红木家具”（上限 5）、“佛山沙发家具”（上限 5）、“红木家具”（上限 10），点击“开始选中任务”后按上述顺序逐个运行；测试模式为安全空消息探测，不发送真实私信。该参数顺序由 `MultiTaskScenarioTest` 固定回归。

本轮真机回归：以上三条任务已通过 ADB 调试夹具写入本地待办并按 5/5/10 顺序完整执行；三条记录均在最终记录页显示“已完成”。修复了批次切换时旧任务状态被新任务的 `LAUNCHING_TARGET` 阶段覆盖、导致已完成记录重新显示“执行中”的问题。单用户“发送失败”仅表示抖音账号私信权限限制，批次仍会继续并完成。
