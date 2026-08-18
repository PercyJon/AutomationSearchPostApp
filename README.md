# Douyin Automation POC — M3

当前开发版本：`0.3.0-m3-p5`

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
- 远程任务工作台：读取后台待执行任务、绑定数字任务 ID、本地执行与异步断点/用户记录上报；网络失败不阻塞自动化
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
- 设备摘要：heartbeat 只发送 Android ID 的 SHA-256 摘要，不发送原始设备标识

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

M3.5 任务配置持久化、记录筛选、历史任务复用和无障碍服务重绑续跑已并入当前版本；真机回归期间不使用会抢占无障碍通道的 `uiautomator dump`。

M3-P2 真机回归补充：顶部用户标签候选增加紧凑顶部标签带约束；翻页时若上一条锚点被裁出，则按已处理身份台账选择首个未处理用户，避免半页加载造成重复或跳过。
