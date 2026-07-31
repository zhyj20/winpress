# 代码规范与质量检查

## 通用规则

- 文本文件统一使用 UTF-8 与 LF 行尾；Windows 启动脚本保留 CRLF。
- 不提交 `node_modules`、`dist`、`target`、日志、崩溃报告、上传文件或本地环境变量。
- 客户可见文案与内部运营字段分离；内部成本、来源标识和供应信息不得进入客户接口。
- 云采写单价与服务类型集中维护，前端展示金额不作为后端结算依据。

## 前端

```powershell
cd frontend
npm.cmd run format
npm.cmd run check
npm.cmd run build
```

Prettier 使用固定版本，配置位于 `frontend/.prettierrc.json`。业务常量集中在
`frontend/src/constants/`，页面不得重复实现相同的服务类型判断和金额计算。

## 后端

```powershell
cd backend
.\mvnw.cmd test
```

Java 源码遵循现有 2 空格缩进、UTF-8、LF 行尾约定。后端暂不绑定需要额外下载格式器的
Maven 插件，避免离线或受限网络环境阻塞正常构建；提交前必须通过编译与测试。

## 数据库

- 新库以 `database/schema.sql`、`database/seed.sql` 和 `database/10-brand-case-demo-data.sql` 为准。
- 存量库按 `docs/MIGRATION.md` 的顺序执行迁移，不直接改写历史脚本。
- `winpress_full.dump` 只能由完成迁移并验证后的 PostgreSQL 数据库重新生成。
