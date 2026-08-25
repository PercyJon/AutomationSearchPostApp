# 2026-08-25：账号设备绑定与退出同步

## 修复前记录

- **错误现象**：Android 的“退出登录”只删除本机 Keystore 中的移动授权；服务端 License 仍有效，账号设备绑定不会释放。
- **稳定复现步骤**：登录移动端账号后，在“我的”点击“退出登录”；应用回到登录页，但原移动 token 仍可由服务端使用，后台无法得知本机已退出。
- **预期行为**：退出必须先通知服务端撤销当前移动授权并释放当前账号—设备绑定；只有服务端明确完成（或已拒绝过期 token）后，才删除本机密钥。
- **实际差异**：移动端 HTTP 网关没有退出接口，`AuthStore.clearConfig` 直接清空本地会话。

## 单一根因假设

退出操作缺少服务端撤销请求，因而本地会话状态和后端设备绑定状态无法同步。

## 最小修改范围

1. `AutomationHttpClient` 增加受 Bearer 保护的 `POST /automation/mobile/logout` 调用。
2. `AuthStore.logout` 先完成服务端调用，再清空 Keystore 会话；网络异常保留本机会话供重试，401/403 视为服务端已不可用后安全清除。
3. “我的”退出确认框显示同步语义，失败留在当前会话并展示可重试信息。

## 验证结果

- `./gradlew testDebugUnitTest lintDebug assembleDebug`：通过。
- 后端 `backend/tests/test_api_module_automation.py`：`7 passed`。
- **真机**：OnePlus NE2210（`b33aa309` / Android 16），安装注入受管 HTTPS 地址的 Debug APK。完成“后台解绑遗留测试绑定 → 账号密码登录 → 搜索预设/地区/屏蔽词读取 → heartbeat → 我的页退出”。服务端 `POST /automation/mobile/logout` 返回 200，应用回到登录页，后台有效账号设备绑定数为 0。
- 未启动抖音、未创建、保存、挂起或执行自动化任务；测试结束停留在本应用登录页。

## 几何与兼容性检查

- 未新增自动化几何、固定 px、点击坐标、OCR 或图像匹配规则。
- 退出对话框仅沿用现有 Compose `dp` 布局；设备身份继续使用应用作用域 Android ID 的 SHA-256 摘要，不发送原始标识。

## 已知部署注意项

- Android 8+ 的 `ANDROID_ID` 会按应用签名和用户作用域区分。生产包必须保持签名证书连续；若未来更换签名证书，设备摘要会变化，需要通过后台解绑后重新登录，不应把它误判为同一摘要。
