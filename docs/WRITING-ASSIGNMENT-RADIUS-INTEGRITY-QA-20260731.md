# 云采写写手服务半径派单完整性 QA

日期：2026-07-31  
环境：本机 Docker 演示栈（前端 `5217`、后端 `8192`、PostgreSQL `55434`）  
范围：写手服务半径、人工服务距离、云采写派单、数据库迁移与运行时健康状态。

## 缺陷与影响

写手档案已有 `service_radius_km`，但此前派单只把 `distance_km` 作为参考字段。已配置半径的写手可能被派往距离未核验或超出半径的服务地点，前端、服务端和数据库也没有统一保护。

## 修复内容

1. 新增迁移 `39-writing-assignment-radius-integrity.sql`。
2. 写手半径必须为非负数；写手半径非空时，活跃 `OFFERED`、`ACCEPTED` 名额必须具有人工核验的非负 `distance_km`，且不超过半径。
3. 平台派单服务为“缺少距离”和“超出服务半径”返回独立业务错误；写手派单页面同步显示半径、必填距离和可用上限。
4. 下调写手半径若会使既有活跃名额越界，数据库拒绝变更。
5. 迁移只检查并保护既有事实：历史活跃名额缺距离或越界时停止迁移，绝不自动定位、推算、补写、拆单、改价、归档或删除。

## 数据库执行记录

- 迁移前创建了本机 PostgreSQL 自定义格式备份：`artifacts/database-backups/winpress-pre-schema39-20260731-093110.dump`。
- 备份 SHA-256：`99616b65b3e54692f54645316702c923da6a28af79d943ab4f4363bc994d4a7d`。
- 迁移执行成功；运行库追加台账为 `39 | 39-writing-assignment-radius-integrity.sql | winpress-v4.2.28-20260731 | FORWARD`。
- 此备份与本机演示数据有关，不得复制到生产环境或作为生产恢复证据。

## 回归结果

| 项目 | 结果 | 证据 |
|---|---|---|
| 后端定向单元测试 | 通过 | `WorkflowServiceTest, HealthControllerTest` 覆盖缺距离、超半径与运行时结构检查。 |
| 后端全量测试与打包 | 通过 | `backend\\mvnw.cmd -q test`、`backend\\mvnw.cmd -q -DskipTests package` 均成功。 |
| 前端类型、格式与生产构建 | 通过 | `npm.cmd run check`、`npm.cmd run build`；1,766 个模块完成构建。 |
| Compose 本机重建 | 通过 | 受控脚本刷新后端 JAR、后端与前端镜像；四个本机容器健康。 |
| 运行时健康 | 通过 | `/api/v1/health` 返回 `schemaVersion=39`、`apiContractVersion=winpress-v4.2.28-20260731`、`schemaStatus=UP`。 |
| 本机 P0/P1 接口与权限回归 | 通过 | `verify-local-p0p1.ps1` 全部通过；客户、运营、管理员权限和四服务账本边界保持。 |
| 干净数据库冷初始化 | 通过 | `verify-clean-local-db.ps1`：四服务结构、迁移、22,364 条静态渠道与 22,363 条静态报价完整性通过；不主张外部数据可用。 |
| 本机数据库备份恢复 | 通过 | `verify-local-database-backup-restore.ps1`：隔离临时容器恢复、关键汇总匹配、结构台账通过。 |
| 生产 Compose 数据边界 | 通过 | `verify-production-compose-boundaries.ps1`：不自动装载演示夹具或未验收媒体目录。 |
| 页面复测 | 通过 | `/about` 在 `1440×900` 与 `390×844` 视口无页面横向溢出、无控制台 warning/error；本轮关于页不影响派单权限或写手操作入口。 |

## 未覆盖的边界

- 本修复不是地理定位能力，未验证地图、地址解析、路程计算或“自动就近”调度。
- 外部写手服务范围、真实地址、交通成本、合同、排期和实际履约仍需在生产预发布环境按授权与合同验收。
- 本机演示数据、测试账户、静态渠道和报价不等同于生产数据、供应商报价或真实可履约资源。
