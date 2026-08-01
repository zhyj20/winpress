# 生产等价冷启动与备份恢复复核（2026-08-02）

适用范围：当前源码、`docker-compose.production.yml`、隔离 Docker 项目
`winpress-production-cold-start`。本记录是本机隔离验证证据，不构成真实生产上线、外部能力或商业主体的验收结论。

## 执行结果

| 检查 | 结果 | 核验要点 |
| --- | --- | --- |
| 生产 Compose 数据边界 | 通过 | 不自动加载测试账号、演示订单、未验收媒体目录或报价目录。 |
| 生产等价空库冷启动 | 通过 | 源码构建隔离后端和前端镜像；结构 `41`、接口合同 `winpress-v4.2.30-20260731`、空业务数据、私有 API 文档、显式 HTTPS CORS、集成保护、`980 CNY / PERSON_DAY` 公开采写价及仅回环端口均通过。 |
| 非空数据库与附件成对恢复 | 通过 | 在隔离栈中建立 1 个合成客户、4 项独立服务项目和 1 个合成附件；恢复到无网络、无宿主机端口的临时 PostgreSQL 及未挂载应用的临时上传卷。 |
| 恢复一致性 | 通过 | 组织 1、需求 4、项目 4、云采写派单 1、服务受理任务 2、发布会工作项 9、附件 1；数据库记录与附件路径、SHA-256、大小、所有权和权限清单一致。 |
| 资源回收与本机隔离 | 通过 | 复核后 `winpress-production-*` 容器和卷、恢复状态文件及暂存目录均为 0；本机演示后端健康检查和首页均返回 HTTP 200。 |

## 执行命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-production-compose-boundaries.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-production-cold-start.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-production-backup-restore.ps1
```

恢复脚本最终输出为 `Result: PASS`、`DatabaseAndUploadMatch: True`、
`ExternalMediaOrSupplierData: not included`、`RealProductionAcceptance: not asserted`。

## 安全与数据边界

- 只使用临时端口、临时凭据、临时卷和合成客户/附件；不读取或写入本机演示数据，更不接触真实生产数据。
- 演练不导入媒体目录、渠道报价、供应商、供应商订单，也不调用牛媒、GEO 或其他外部接口。
- 不输出合成账户密码、令牌、数据库凭据、文件正文或生产配置值。
- 先前因非交互执行通道截断而未取得终态输出的问题，已改用保持会话方式复跑；本记录仅以本次完整 `PASS` 输出为证据。

## 仍未覆盖的生产门禁

本次不证明真实预发布或生产环境的网络隔离、备份加密、异地副本、保留周期、RPO/RTO、TLS、域名切换、监控告警、生产迁移、主体信息、外部媒体授权、供应商实单或法律文本已经验收。上述事项仍须由有权主体在受控预发布/生产环境中单独确认。
