# M3-I 远程地区与屏蔽词绑定

## 变更

- 首页在授权有效时读取后台地区目录和屏蔽关键词目录；网络失败仍保留手工输入。
- 地区选择器写入规则的 `prefix`，最终搜索词由 `QueryComposer` 统一生成，例如“广东 + 红木家具”变为“广东红木家具”。
- 屏蔽词选择器支持多选并写入任务快照；运行中的任务不会因后台目录后续发布而改变。
- 后端任务新增 `blocked_keywords` JSON 快照字段，管理端创建/修改时去重，移动端任务响应原样返回。
- 远程任务优先使用后台 `region_prefix`，并使用后台 `blocked_keywords`；旧后端没有该可选字段时安全回退为空列表。

## 数据库

已有数据库需要执行 Alembic 迁移：

```bash
alembic upgrade head
```

迁移文件：`backend/app/alembic/versions/20260818_01_add_task_blocked_keywords.py`。

## 安全边界

屏蔽词只用于跳过用户结果，不会尝试修改抖音内容；地区和屏蔽词均是任务创建时的快照，执行期间不读取实时目录覆盖任务配置。

## 验证

- 后端任务接口测试覆盖屏蔽词去重、管理端输出和移动端输出。
- Android JVM 单元测试、Lint、Debug APK 和 instrumentation APK 构建通过。
