# Douyin Automation POC — M1

当前冻结版本：`0.1.0-m1` (`v0.1.0-m1`)

这是一个 Android 自动化可行性验证原型，使用 Kotlin、Jetpack Compose、Android AccessibilityService、ML Kit OCR 和标准 ADB 流程，最低支持 Android 11（API 30）。项目只使用普通 Android 应用能力，不依赖 Root、`su`、`adb root` 或系统提权。

## 当前实现范围

- 用户主动授权的无障碍服务，仅处理抖音窗口（`com.ss.android.ugc.aweme`）
- 节点树快照、私有 Node dump、截图和 ML Kit 中文 OCR 诊断
- PageDetector / Selector：节点文本、content description、结构、Bounds、归一化区域和有限坐标兜底
- 搜索流程：打开抖音 → 输入指定关键词 → 只点击右上角“搜索”（忽略下拉候选）
- 用户流程：定位“用户”标签、识别用户行、跳过“回关”用户、点击名称区域进入主页（不点击头像）
- 私信入口兼容完整“发私信”按钮和纸飞机图标，打开私信页有超时和人工接管
- 消息发送：Start 时填写非空消息会在私信页验证成功后自动发送一次；留空则只验证私信页，也可使用显式发送按钮
- 消息发送结果校验：成功、权限失败/红色感叹号、模糊失败文案和超时均有处理
- 系统横幅/临时遮挡检测：等待抖音窗口恢复后重试，不穿透系统覆盖层操作
- 验证码、登录失效、风险控制和未知页面一律暂停并等待人工处理，不实施破解或绕过

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
```

不要在日常真机上运行 `:app:connectedDebugAndroidTest`；Gradle UTP 的清理阶段可能卸载应用。真机验证使用 `adb install -r` 和显式 `adb shell am instrument`，并需要明确设置 `CONFIRM_DEVICE_TEST=1`。

## 已验证链路

在 Android 设备上已验证：

```text
启动抖音
  → 搜索“是小瑜瑜呀~”
  → 用户标签
  → 用户主页
  → 私信页
  → 自动发送一条测试消息
  → 发送结果确认
```

当前代码通过 Debug 构建、Android Lint 和 JVM 单元测试。截图、OCR、Node dump、Logcat 和 APK 迭代文件均保存在本地 `outputs/`、`artifacts/` 或 `work/`，不进入版本控制。
