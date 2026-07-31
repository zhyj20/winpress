# 工程交接说明

## 产品边界

WinPress 云发布同时保留媒体邀请平台和内容采写服务，两者可以在同一活动下关联查看，但必须分别下单、执行、计价和留档，不能互相替代。“云采写”支持客户直接下单会议活动现场采写，按 `980 元 × 写手人数 × 服务天数`计算基础金额；平台优先匹配活动所在地及周边、档期和写作能力合适的写手。

云采写写手可按客户委托到场记录会议进程、采集事实素材并完成稿件，但不以受邀媒体记者身份到场。媒体邀请、采访协调和发布由独立流程执行，媒体是否出席、采访或报道由媒体自主决定。云发布的发布渠道分为两类：

1. 记者邀约：确定记者和媒体后发起采访报道邀请，记录邀约、反馈和报道成果。
2. 直编发稿：按媒体、地区、价格和时效选择渠道，提交后复核稿件、栏目和排期。
新闻发布会是独立项目能力：客户先确认活动主题、时间地点、会务联系人、媒体目标和当前准备情况；V4.1 新建项目自动生成项目范围、议程嘉宾、场地动线、新闻材料、拟邀媒体、邀请到场、现场接待采写、会后发布、成果复盘 9 项统筹清单。媒体邀约、现场采写和直编发稿仍按各自服务范围和费用单独确认。

直编发稿采用两阶段确认：客户先保存发布计划，确认前不生成任务；客户确认后系统按计划项生成任务，重复确认不重复下单。媒体邀请与直编发稿可以并行；当前客户入口不提供排他邀约或稿件锁定下单，既有约定仅由项目负责人依约处理。

两类渠道任务、发布会统筹清单与云采写任务分别执行，在订单、项目、稿件、任务、成果、监测和结算层统一管理。客户自有渠道管理属于牛媒信源 GEO 的渠道管理流程，不在云发布配置或执行。

## 工程结构

| 目录 | 交接内容 |
|---|---|
| `frontend/` | Vue 3 页面、路由、Pinia 会话、REST 请求封装和响应式样式 |
| `backend/` | Spring Boot 控制器、服务、数据访问、权限、会话、异常和文件存储 |
| `database/` | PostgreSQL 建表、种子数据、媒体导入、数据规范化、迁移和历史恢复快照 |
| `docs/` | API、数据库、部署、迁移、测试账号和验收报告 |
| `release/` | 可直接部署的前端静态文件和后端 JAR |

后端基础包名为 `com.winpress.commercial`，接口统一使用 `/api/v1` 前缀。前端生产环境通过同域 `/api/v1` 访问后端。

## 核心业务规则

- 客户只能访问自己的组织、需求、项目和任务。
- 发布运营只能访问已分配的项目和任务。
- 平台运营可管理全局项目、渠道、报价、账号、结算和日志。
- 直编发稿必须使用客户确认的稿件版本；媒体邀请可以从活动简报直接开始。
- 云采写现场服务的单价由后端固定为 980 元/人/天，客户端只提交人数和天数，不能修改单价。
- 同一活动另行下单媒体邀请时，云采写基础金额仍只计算采写服务；媒体邀请、交通住宿、摄影摄像、超时和额外稿件分别确认。
- 当前客户流程不创建稿件锁；既有有效安排由项目负责人核验，避免与新的直编计划冲突。
- 新闻发布会项目创建后生成统筹清单；服务运营或平台运营可按项目分配与更新事项状态。事项更新必须携带当前状态，已完成事项为终态，不能重新打开；状态完整性回归见 [`CONFERENCE-WORK-ITEM-STATE-INTEGRITY-QA-20260731.md`](CONFERENCE-WORK-ITEM-STATE-INTEGRITY-QA-20260731.md)。
- 直编订单保存报价编号、单价和有效期，后续调价不修改历史订单。
- 客户渠道接口只返回客户服务价和公开字段；内部成本仅在平台运营接口返回。
- 关键写操作写入 `operation_log`。

## 数据与接口整合

- 生产域名：`winpress.cn`、`winpress.waykey.net`。
- PostgreSQL 可部署在与其他产品共用的数据库集群中，WinPress 使用独立数据库 `winpress_commercial`。
- Redis 会话键由后端统一管理，生产环境应使用独立密码和实例或键空间。
- 媒体导入以 `channel_no` 为稳定业务键，以 `quote_no` 为报价业务键。
- 与牛媒信源整体平台整合时，建议保留 WinPress 的模块边界和 `/api/v1` 契约；统一身份中心可替换 `SessionService`，不改变业务服务层。
- 记者 API、媒体同步和站外监测适配器应写入内部来源字段，不扩大客户接口字段。
- 独立 API 工具已迁入 `/api/v1/open-api/v1/*`；平台运营在 `/admin/open-api` 管理客户接入应用、验收状态、一次性访问密钥和脱敏访问摘要。它与供应商接口配置分离，创建的服务需求必须进入既有项目、任务和订单记录，不能形成旁路数据库或组合服务。

## 公开案例与资料边界

- 案例列表 `/cases` 为每个项目提供独立详情页；芯海科技 COMPUTEX 2026 页面为 `/cases/chipsea-computex-2026`，按“项目启动、执行过程、展会现场、报道成果”展示。媒体邀请脱敏样本保留在独立路由，但只说明服务类别与公开边界，不展示操作过程、素材、名单或项目成果。
- 项目方案、WBS、新闻手册和采访提纲只用于还原执行节奏和资料边界，不作为对外成果、传播数量、合同履约或云发布归因的证明。对外成果只列可追溯的公开页面，并标明公开页面、项目归档或受限站点等证据状态。
- 两个案例详情页与 `case-media` 目录均保持 `noindex`；原 DOCX、PDF、PPTX、原图、预算、联系人、媒体沟通记录、采访原文和未公开经营信息不得复制至 `frontend/public` 或提供下载。取消索引或正式营销使用前，必须取得项目主体、肖像与媒体截图权利方的书面确认。
- 芯海科技页面的报告清单、来源状态和本机验收证据见 [`CHIPSEA-COMPUTEX-CASE-QA-20260731.md`](CHIPSEA-COMPUTEX-CASE-QA-20260731.md)。其中“公开页面数”只表示页面留存，不表示独立原创篇数或传播效果承诺。

## 主要入口

| 能力 | 后端入口 | 前端入口 |
|---|---|---|
| 登录注册 | `AuthController` | `LoginView`、`RegisterView` |
| 需求与项目 | `WorkflowController` | `RequirementCreateView`、`ProjectsView` |
| 稿件版本与审核 | `WorkflowController` | `ProjectDetailView` |
| 渠道选择与发布计划 | `WorkflowController` | `ChannelsView` |
| 发布执行与成果 | `OperatorController` | `TasksView` |
| 渠道、报价、供应商与订单 | `AdminController` | `ChannelAdminView`、`PricingAdminView`、`SuppliersView` |
| 供应商接口与上线门禁 | `IntegrationAdminController` | `IntegrationAdminView` |
| 客户开放 API 管理 | `OpenApiAdminController`、`OpenApiClientController` | `OpenApiAdminView` |
| 商务咨询 | `PublicController`、`AdminController` | `ContactView`、`InquiriesAdminView` |
| 账号权限、结算、审计 | `AdminController` | `UsersView`、`SettlementsView`、`AuditView` |

## 交付检查

1. 按 `docs/DEPLOYMENT.md` 配置 PostgreSQL、Redis、域名和上传目录。
2. 本机演示库按 `schema.sql`、`seed.sql`、`10-brand-case-demo-data.sql`、媒体导入、公开字段规范化及迁移 13—41 初始化；存量库按 `docs/MIGRATION.md` 依次执行缺失的生产迁移（不执行仅限本机演示的迁移 10、17）。迁移 38 会拒绝缺少真实写手、服务时段或多人名额事实的存量活跃派单；迁移 39 会拒绝写手半径已配置但缺人工核验距离、或超出半径的活跃名额；迁移 40 会拒绝发布会事项状态与完成时间矛盾、以及完成项目仍有未完成事项的存量记录；迁移 41 会拒绝候选名单状态、邀请／回复时间线或结果说明互相矛盾的存量记录，均必须先由业务确认后再继续。
3. 部署 `release/backend/winpress-commercial-1.0.0.jar`。
4. 将 `release/frontend/` 配置为 Nginx 站点根目录。
5. 使用三类基础测试账号和四个品牌案例账号完成 `docs/TEST-ACCOUNTS.md` 中的角色与数据隔离路径。
6. 在本机演示环境分别运行 `scripts/verify-local-database-backup-restore.ps1` 与 `scripts/verify-local-upload-backup-restore.ps1`，确认数据库和上传文件卷都能在隔离容器/临时卷恢复；两项本机结果均不替代生产验收。
7. 上线前替换所有默认密码；不需要品牌案例时停用对应账号，并分别配置数据库和上传目录备份、访问控制、保留周期及生产恢复演练。
8. 以 `docs/RELEASE-GOVERNANCE-QA-20260731.md` 核对 28 项必备证据；供应商和外部媒体的当前运行判定另见 `docs/INTEGRATION-RUNTIME-READINESS-QA-20260731.md`。五个门禁未全部通过前，不得把本机配置、演示数据或隔离演练表述为真实外部能力或生产验收。

> 存量数据库升级时，必须执行 `07-news-conference-replaces-owned-channel.sql`、`13-conference-progressive-intake.sql` 和 `14-supplier-orders-and-conference-workbench.sql`。迁移 07 会归档旧自有渠道，迁移 13、14 才会补齐三项建项、阶段工作台、供应商订单和商务咨询。
