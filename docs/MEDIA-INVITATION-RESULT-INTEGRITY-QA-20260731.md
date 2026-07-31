# 媒体邀请成果事实链 QA（2026-07-31）

## 结论

本轮修复了媒体公关任务的一个 P1 状态机缺口：此前，媒体邀请仍为 `PENDING` 时，运营人员可以提交“已核验成果”，任务随即成为完成态。现在，媒体公关任务必须先登记实际已发出的邀请，且只有邀请处于 `INVITED`、`RESPONDED` 或 `ATTENDING` 时，才允许录入成果；成功提交后，同一邀请记录才会成为 `REPORTED`。该修补只约束平台内的事实链，不承诺媒体一定报道，也不表示外部媒体数据或供应商履约已经接通。

## 根因与影响

| 项目 | 修补前 | 风险 |
|---|---|---|
| 服务层 | `submitResult` 只检查任务权限、状态和供应商履约条件，没有检查媒体邀请是否实际已发。 | 可把未发生的邀请和报道结果混入客户项目记录。 |
| 数据库层 | 任务进入 `COMPLETED` 时只要求已核验成果；没有要求媒体任务具备已发邀请和报道状态。 | 直接数据库写入或未来后台旁路仍可能绕过应用层。 |
| 成果写入顺序 | 任务状态先更新为完成，再处理成果与媒体邀请状态。 | 无法以同一事务证明完成态所需事实已经同时成立。 |

## 修补内容

| 层级 | 修补 |
|---|---|
| 服务层 | `WorkflowService.submitResult` 对 `MEDIA_PR` 增加 `MEDIA_INVITATION_REQUIRED` 门禁；邀请未登记为已发时返回冲突，不生成成果、不改写邀请事实。 |
| 写入顺序 | `WorkflowRepository.submitResult` 先写已核验成果，再把真实已发的邀请更新为 `REPORTED`，最后关闭任务；任一步失败都会随事务回滚。 |
| 数据库迁移 | 新增 `database/37-media-pr-result-integrity.sql`。它先核验 schema 36 追加式台账及存量完成态事实；发现完成任务缺少已核验成果，或媒体任务缺少带邀请时间的 `REPORTED` 记录即停止。脚本不会补造邀请、报道、成果或验收事实。 |
| 终态保护 | `enforce_publish_task_terminal_integrity()` 在任务首次进入 `COMPLETED` 时要求已核验成果；`MEDIA_PR` 还必须有带 `invited_at` 的 `REPORTED` 邀请。 |
| 运行态 | 健康检查提高到 `schemaVersion=37`、`apiContractVersion=winpress-v4.2.26-20260731`，同时核验 36 基线和 37 前向台账记录。 |
| 文档与脚本 | 本机、生产 Compose、冷启动、备份恢复与 P0/P1 回归均纳入迁移 37 和媒体成果事实链检查。 |

## 本机迁移记录

当前本机演示库在迁移前完成只读预检：

- `terminal_without_verified_result = 0`
- `media_terminal_without_reported_invitation = 0`

随后已执行第 37 次迁移。迁移前保留 PostgreSQL 自定义格式备份，文件位于受控本机工作区 `artifacts/backups/`，SHA-256 为 `273DCF380E767CD9F920BA8C65D5381DFC0A53C61C9BF6D5B4BD6E704DBE9526`。该备份仅用于本机回退，不应作为源码包、生产数据或对外材料分发。

运行态复核结果：应用、数据库和结构均为 `UP`；结构版本为 `37`；接口合同为 `winpress-v4.2.26-20260731`；台账同时存在 `36 / BASELINE` 与 `37 / FORWARD` 记录。

## 回归证据

| 检查 | 结果 |
|---|---|
| `frontend\\npm.cmd run check` | 通过：Vue TypeScript 与格式检查通过。 |
| `frontend\\npm.cmd run build` | 通过：Vite 生产构建完成，1766 个模块完成转换。 |
| `backend\\mvnw.cmd test` | 通过：154 项测试，0 failed / 0 error / 0 skipped；新增“未邀请成果拒绝”和“已邀请成果可提交”单元回归。 |
| `scripts\\verify-clean-local-db.ps1` | 通过：干净隔离库加载至迁移 37；52 张公开表、22,364 条静态渠道、22,363 条静态报价；媒体成果数据库门禁已执行。静态种子完整性不代表外部数据授权。 |
| `scripts\\verify-cold-start.ps1` | 通过：干净临时库验证迁移台账与直接数据库违规写入回滚拒绝。 |
| `scripts\\verify-local-p0p1.ps1` | 通过：运行态返回 `37 / v4.2.26`；未登记已发邀请时提交媒体成果被拒绝，拒绝后邀请仍为待发且没有伪造时间；登记邀请后继续按受控流程推进。 |
| `scripts\\verify-local-database-backup-restore.ps1` | 通过：隔离容器恢复当前本机演示库，52 张公开表、四项独立服务、迁移台账与成果事实链完整。 |
| `scripts\\verify-production-compose-boundaries.ps1` | 通过：生产编排不自动导入演示数据、未验收媒体目录或报价。 |
| `scripts\\verify-production-cold-start.ps1` | 通过：隔离生产编排以空业务库、回环端口和显式 HTTPS CORS 启动到 schema 37；临时容器、卷、镜像与凭据均已清理。 |
| `frontend\\npx.cmd playwright test` | 通过：64/64；覆盖 1440×900 与 390×844 的营销路由、案例、角色导航、发布会、媒体邀请、直编筛选、任务及订单边界。 |

## 回退与未验收边界

- 迁移 37 是前向事实门禁，不提供自动降级脚本。若需回退本机演示库，应在隔离环境中用迁移前备份恢复；不得以删除 `REPORTED`、成果或台账记录的方式伪造回退。
- 真实媒体邀请、记者检索、报道链接、媒体数据许可、供应商订单、报价、回执、对账、异常重试与服务等级，仍须获得书面授权、字段映射、沙箱/生产联调和验收证据后才可对外表述为可用。
- 本机通过不等于生产已完成主体、法律文本、TLS/域名切换、监控告警、备份恢复演练和最小权限网络验收。相应上线门禁继续保持待确认，不能以演示数据或本机凭据替代。
- 历史 `WRITING_AND_PUBLISHING` 记录保持只读待审核，不会被本迁移自动拆分、映射、归档或删除。
