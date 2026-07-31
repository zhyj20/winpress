# 外部集成运行就绪边界 QA（2026-07-31）

## 本轮目的

本轮审查供应商接口、外部媒体数据适配器和上线验收关卡之间的状态一致性。目标是避免把数据库中的“已登记”或“已批准”误写成实际可履约、可检索或已接通的外部能力。

本记录不包含供应商名称、合同、令牌、上游地址、客户资料或外部响应原文。

## 修补内容

| 风险 | 原行为 | 修补后 |
|---|---|---|
| 接口概览误判 | 只要凭据存在且连接验收字段完整，就可能被统计为“配置齐备”；停用状态、检索范围、履约回执与对账资料没有统一纳入。 | `configurationReady` 改为统一依据完整阻断项计算：正式授权、沙箱、生产批准、运行环境凭据、数据范围/检索路径、下单/状态/回调/对账/SLA 与启用状态均需满足。 |
| 验收关卡状态 | 可在数据库连接字段完整但当前部署凭据缺失时通过外部数据或供应商履约关卡。 | 标记为通过时，必须存在当前运行环境确实可用的生产连接；不会只依据历史数据库字段。 |
| 供应商 API 回执 | 供应商订单登记为 `API` 回执时，只依据静态连接与数据库验收状态。 | 现在同时要求：对应供应商的生产连接完整且启用、运行时凭据存在、`SUPPLIER_FULFILLMENT` 总关卡为 `PASSED`、该关卡没有未核验必备证据。任一项撤回，接口回执登记被拒绝。 |
| 旧判断入口 | 数据访问层仍保留可绕过运行时凭据和总关卡的静态“就绪”方法。 | 删除未使用的静态判断入口，履约判断统一通过 `IntegrationAdminService` 的运行时规则。 |

## 对客与管理边界

- 管理端只保存凭据环境变量名称，不保存或回显令牌；配置检查不向供应商发起网络请求。
- 客户与服务运营角色无法访问供应商接口管理；客户接口不会返回供应商、成本、上游地址、令牌或内部验收备注。
- 当前未提供正式授权、范围确认、令牌、限流和逐家联调证据的外部媒体适配器维持“暂不可用”和人工补充路径。不得以本机配置、演示数据或本轮测试对外宣称实时媒体/记者数据或真实供应商履约已经接通。

## 本机验证证据

在 `E:\Codex\Projects\软件开发与运维\.winpress-media-integration-20260726` 执行：

```powershell
cd backend
.\mvnw.cmd -q test

cd ..
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\prepare-local-docker-backend.ps1
docker compose -f docker-compose.local-demo.yml build backend
docker compose -f docker-compose.local-demo.yml up -d --no-deps backend
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-local-p0p1.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-local-database-backup-restore.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-production-compose-boundaries.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-production-cold-start.ps1
```

结果：

- 后端 Maven 测试通过，新增单元回归覆盖“缺少运行凭据”“总关卡未通过”“必备证据未核验”三种供应商 API 履约拒绝条件，以及所有前置条件齐备时的受控放行条件。
- 本机健康接口返回：应用、数据库与结构均为 `UP`；结构版本 `36`；接口契约 `winpress-v4.2.25-20260731`。
- `verify-local-p0p1.ps1` 通过：客户与服务运营不能访问接口配置；平台管理员可访问脱敏概览；概览不返回凭据值；外部适配器在未验收时不可用，并保留人工补充路径。
- `verify-local-database-backup-restore.ps1` 通过：在无主机端口、无持久卷的临时容器中恢复并核对 `52` 张公开业务表、四项独立服务、开放 API 治理和结构迁移账本。
- 生产 Compose 边界检查与隔离生产冷启动均通过。冷启动使用空数据库、随机临时凭据、回环端口和显式 HTTPS CORS；验证后自动删除临时容器、卷、镜像与凭据。
- 前端 `npm.cmd run check`、`npm.cmd run build` 通过；Playwright 桌面端与移动端共 `44/44` 通过。此次服务端规则不改变对客页面或路由。

## 未验证事项与上线前门禁

以下事项未由本轮本机 QA 证明，仍需主体方、供应商和生产环境分别完成：

1. 取得每个外部数据源和履约供应商的正式授权、字段范围、令牌、限流策略、合同/SLA 与数据处理边界。
2. 在隔离预发布环境逐家完成真实检索、报价、下单、回执、超时、重试、对账和异常处理联调，并将证据逐项登记后再通过对应关卡。
3. 完成生产 TLS、域名、最小权限网络、监控告警、备份恢复和迁移演练；本机冷启动不替代生产验收。
4. 法律主体、备案、联系信息、隐私与服务条款、案例授权、生产凭据及客户资料保留策略，必须经权利主体核验后发布。

## 回滚边界

本轮没有迁移数据库、没有变更客户业务记录、没有写入外部平台，也没有新增真实供应商凭据。若需要回退，仅回退本轮 Java 服务与单元测试代码；不得通过删除或自动拆分历史 `WRITING_AND_PUBLISHING` 记录来回退。

## 2026-07-31 补充：开放 API 服务目录名称一致性

- 开放 API 是已受控的企业系统接入能力，不是客户日常服务下单入口；营销页继续只提供咨询申请，管理员通过“开放 API”页面管理应用、访问范围、密钥、日志和验收材料；
- 接口服务目录中的 `NEWS_CONFERENCE` 名称已从“新闻发布会”统一为“举办新闻发布会”，与客户操作台、项目筛选和订单筛选一致；
- 新增 `OpenApiClientServiceTest.serviceCatalogUsesTheSameFormalConferenceServiceNameAsTheConsole`，确保该名称不会因服务端目录变更再次漂移；
- Maven Surefire `148/148`、前端类型/格式/生产构建均通过。当前外部媒体和供应商适配器仍为待验收状态，以上名称和界面管理能力不构成真实外部接通。
