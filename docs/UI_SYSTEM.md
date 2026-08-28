# UI 设计系统

色值和圆角只出现在 `ui/theme` 与 `ui/components`。业务页只选组件，不写新的 hex / 圆角。

## 参考图

| 文件 | 用途 |
|---|---|
| `docs/ui-references/pic1-form.png` | 表单与可交互：分段页签、淡色圆角输入、选择芯片、下拉芯片、底部主按钮 |
| `docs/ui-references/pic2-sheet.png` | 辅助：标题/副标题、同行双按钮、开关 |
| `docs/ui-references/pic3-icon-actions.png` | 图标+文字：堆叠磁贴、横向胶囊 |
| `docs/ui-references/pic4-history-layout.png` | **只学布局**：数据摘要、筛选胶囊、历史卡片。禁止学黑金配色 |
| `docs/ui-references/home-hero-layout.png` | **只学布局**：首页顶部色块 + 底部圆角 + 统计卡片覆盖。禁止学参考图里的色值 |
| `docs/ui-references/app1-login-layout.png` | 登录页结构：问候 → 输入 → 主按钮。控件风格仍用上面组件 |
| `docs/ui-references/app2-empty.png` | 空状态 / 默认占位：图标 + 说明 + 底部操作 |
| `docs/ui-references/app4-detail.png` | **只学布局**：任务详情中间键值排版 |
| `docs/ui-references/app5-record-lines.png` | **只学布局**：用户处理结果用下划线分行，不用色块卡片 |

## 色板

一条主蓝 `#585EE8`（pic2「提交」），其余都是它的变淡：

- 主按钮 / 选中页签：`AutomationBlue`
- 芯片选中底：`AutomationBlueSoft`
- 输入底：`AutomationField`
- 输入描边：`AutomationFieldBorder`
- 页面底：`AutomationPage`
- 登录渐变仅用于登录画布：`AutomationLoginTop/Mid/Bottom`

## 组件

| 组件 | 参考 |
|---|---|
| `AppSegmentedTab` | pic1 顶栏页签 |
| `AppLabeledField` / `AppSectionTitle` | pic1 字段标签与加粗分节标题 |
| `AppChoiceChip` | pic1 智能扩写 / 下拉 |
| `AppPrimaryButton` | pic1 底部胶囊主按钮 |
| `AppButtonRow` | pic2 同行次按钮 + 主按钮 |
| `AppSheetHeader` | pic2 / pic3 标题副标题关闭 |
| `AppIconTile` / `AppPillAction` | pic3 |
| `AppHeroStats` / `AppFilterPills` / `AppHistoryRow` | pic4 布局 |
| `AppLoginCanvas` / `AppLoginMark` / `AppLoginField` / `AppLoginButton` | app1 登录画布与控件 |
| `AppPageHero` | 首页顶部色块 + 覆盖统计卡 |
| `AppColorBlock` | 横向 4:3 功能色块 |
| `AppEmptyState` | app2 空状态 |
| `AppDetailHero` / `AppKeyValueGroups` | app4 任务详情 |
| `AppUnderlineTabs` / `AppStatusBadge` / `AppRecordLine` | app5 处理结果列表 |
| `AppNoticeRow` | 我的页权限提示条 |
| `AppRadio` | 营销内容单选 |

默认密度偏紧：控件高约 40.dp，正文字号 13–14.sp，字重默认 Regular，不要为层次去加粗。

预览入口：我的 → 组件预览。先改组件，再改业务页。
