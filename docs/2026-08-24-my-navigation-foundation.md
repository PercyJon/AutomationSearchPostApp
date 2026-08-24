# 2026-08-24 账户中心与三项底部导航基础

## 阶段目标

在不重建现有 Compose 页面结构、任务记录页或设置页的前提下，完成“首页 / 待办 / 我的”三项底部导航，并将记录和设置迁入“我的”的列表型账户中心。

## 基线与验收预期

| 项目 | 基线 | 本阶段预期 |
| --- | --- | --- |
| 底部导航 | 首页、待办、记录、设置四项 | 首页、待办、我的三项 |
| 记录/设置 | 可从底部直接进入 | 作为“我的”的二级入口，功能不删除 |
| 账户信息 | 分散在设置页 | “我的”显示账户/授权摘要与设备入口 |
| 自动化链路 | 现有 P0、无障碍与任务状态机 | 不改变 |

## 修改范围

- `app/src/main/java/com/example/douyinautomation/ui/HomeScreen.kt`
  - 增加内部 `MY` 页面状态；保留 `RECORDS`、`SETTINGS`、`DIAGNOSTICS` 内部路由。
  - 将记录和设置入口移出底部导航；二级页面的返回目标改为“我的”。
- `app/src/main/java/com/example/douyinautomation/ui/MyPage.kt`
  - 新增账户中心，使用原创 Compose 图形与既有主题色。
  - 未登录时展示“未登录”和现有授权状态；已登录后显示本机退出入口。
  - 退出动作当前只清除本机加密授权，服务端撤销将在登录/设备绑定阶段补齐。

## 构建问题与最小修复

### 错误现象

首次执行 `./gradlew :app:compileDebugKotlin` 时，新增 `MyPage.kt` 出现四个编译错误：错误导入 `weight`、错误导入 `rememberSaveable`，以及将无接收者的可组合 lambda 直接作为 `Column` 内容传入。

### 稳定复现

执行：

```text
./gradlew :app:compileDebugKotlin
```

### 根因假设与最小修改

同一新页面中的 Compose API 导入和 `ColumnScope` lambda 类型不匹配；未涉及任何页面状态或自动化逻辑。移除错误导入、使用 `runtime.saveable.rememberSaveable`，并用 `Column { content() }` 承接列表内容。

### 结果

改善：相同编译命令通过；随后完整 JVM 测试、Lint 和 Debug 构建通过。

## 验证结果

### 静态与构建验证

```text
git diff --check
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

结果：通过。

### 真机验证

| 项目 | 结果 |
| --- | --- |
| 设备 | OnePlus NE2210 / b33aa309 / Android 16 |
| 安装 | `adb install -r` 成功 |
| 路径 | 首页 → 我的 → 任务记录 → 返回我的 → 自动化设置 → 返回我的 |
| 结果 | 三个底部菜单可见；二级页返回“我的”成功 |
| 安全边界 | 测试前确认没有正在执行任务；未调用任务启动命令、未操作抖音、未输入或发送消息 |
| 收尾 | 验证后停止 App 并回到系统桌面 |

## 几何与安全检查

- 新增 UI 间距、圆角、图标和列表高度均为 Compose `dp`；没有引入 px 常量。
- 没有新增屏幕比例坐标、OCR 区域、图像匹配、手势时长或无障碍点击逻辑。
- 现有任务记录、深链和诊断内部路径仍可用；只有底部信息架构发生变化。

## 后续阶段

1. 在隔离的后端基线上实现账号—设备绑定、登录限制、会话与服务端退出。
2. 用统一登录页替代设置页中的普通用户账号/密码/Token/设备 ID 输入。
3. 继续紧凑化 B 端私信和评论私信表单，并统一全局控件风格。

## 阻塞记录

后端仓库工作区存在预先未提交的移动端登录相关改动，覆盖：

- `mobile/controller.py`
- `schema.py`
- `service.py`
- `tests/test_api_module_automation.py`

这些文件与本阶段后续的账号—设备绑定实现重叠。为保护既有改动，本阶段没有修改、暂存、提交或推送后端文件。下一轮将先建立干净且可追溯的后端承接基线；若该基线仍无法安全确定，则继续推进不依赖它的 Android 登录 UI 与表单视觉阶段。
