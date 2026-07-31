# WinPress 云发布

企业新闻传播服务平台，覆盖云采写、媒体邀请、直编发稿、新闻发布会和项目管理。原有媒体邀请平台完整保留；“云采写”既可嵌入传播项目，也可作为会议活动现场采写产品直接下单，平台按就近原则匹配写手，基础服务价为 980 元/人/天。

业务边界：云采写写手受客户委托采集事实素材、撰写和修改稿件，但不以媒体记者身份到场，也不参与媒体邀约或决定发布。媒体是否出席、采访和报道，由受邀媒体自主判断。媒体邀请、发布、差旅和其他增项不计入云采写基础价。

## 当前验收口径

当前唯一验收基准为 [产品需求文档 V4.2](docs/PRODUCT-PRD-V4.2.md) 与 [全局产品与代码审查](docs/WINPRESS-GLOBAL-PRODUCT-CODE-AUDIT-20260728.md)。本机可重复运行证据以 [P0—P1 执行与回归记录](docs/P0-P1-EXECUTION-QA-20260728.md)、[发布治理与上线边界 QA 记录](docs/RELEASE-GOVERNANCE-QA-20260731.md)、[认证与附件边界 QA 记录](docs/AUTH-AND-FILE-BOUNDARY-QA-20260731.md)、[供应商履约闭环 QA 记录](docs/SUPPLIER-FULFILLMENT-CLOSURE-QA-20260731.md) 和 [发布会统筹事项状态完整性 QA 记录](docs/CONFERENCE-WORK-ITEM-STATE-INTEGRITY-QA-20260731.md) 为准。历史 V4.1 报告、旧端口、演示数据和旧站截图仅作追溯，不能用于证明当前代码、外部媒体数据、供应商履约、法律信息或生产部署已经验收。

## 技术栈

- 前端：Vue 3、Vite、TypeScript、Pinia、Vue Router
- 后端：Java 17、Spring Boot 3、Spring JDBC
- 数据库：PostgreSQL 17
- 会话：Redis 7；账号权限变更使用会话代次统一撤销旧令牌
- 文件：本地存储适配器，默认目录 `storage/uploads`；默认上限 20 MB（`20 × 1024 × 1024` 字节），入库失败自动清理、校验文件实际格式，受权下载强制附件处理且禁止缓存

## 开放 API 整合

此前独立开发的 API 工具已作为云发布的受控接入模块整合：客户系统可在完成接入审批后读取服务目录、查询可对客展示的直编渠道、创建四项独立服务需求并查询其项目受理状态。API 创建的数据仍进入现有项目、任务和订单记录，不另建业务后台。

平台运营通过“开放 API”管理接入应用、客户归属、范围、限流、验收凭据、一次性访问密钥和脱敏请求摘要；供应商接口配置仍由“接口管理”单独维护。原始密钥、供应商、成本、上游数据和请求正文不会显示给客户或写入接口日志。外部媒体数据与真实供应商履约在授权、沙箱和验收完成前仍为待确认能力。

接口说明见 [docs/API.md](docs/API.md)，迁移与上线边界见 [docs/MIGRATION.md](docs/MIGRATION.md) 和 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。

## 目录

```text
frontend/    Vue 3 前端源码
backend/     Spring Boot 后端源码
database/    建表、种子数据、媒体数据、迁移和历史恢复快照
docs/        接口、数据库、部署、账号与审查报告
release/     已构建的前端文件和后端 JAR
storage/     本地文件存储目录
```

## Docker 部署模式

环境要求：Docker Desktop。仓库提供两套互不混用的 Compose 定义；先执行端口预检，再选择其中一套。两套编排均使用独立容器、卷和本机回环端口，不接管旧 WinPress 或其他网站容器。

### 本机演示与验收

本机演示栈可加载测试账号、案例和演示任务，仅用于开发、验收与截图核对：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/rebuild-local-demo.ps1
```

公开 GitHub 源码默认只挂载字段表头样例，不携带任何媒体目录、报价、供应商成本或客户原件；因此可完成结构性启动与界面开发，但不会把它误表述为可用的外部媒体能力。获得书面授权的本机数据只能在受控 `.env` 中设置 `WINPRESS_MEDIA_CHANNELS_CSV` 与 `WINPRESS_MEDIA_QUOTES_CSV` 后使用，两个文件均不得提交。公开镜像的完整边界见 [docs/GITHUB-PUBLIC-SOURCE-BOUNDARY.md](docs/GITHUB-PUBLIC-SOURCE-BOUNDARY.md)。

该入口会先预检端口、在临时源码副本中测试并打包后端、再重建前后端镜像并等待健康检查。后端镜像只复制 `backend/target-local/` 中的受控 JAR；因此不要直接用 `docker compose ... --build` 代替此入口，否则可能把上一次准备的 JAR 当作当前后端源码部署。

本机演示的默认访问地址为：

- 首页：`http://127.0.0.1:5217`
- 后端：`http://127.0.0.1:8192`
- 本机接口文档：`http://127.0.0.1:8192/swagger-ui/index.html`
- PostgreSQL：`127.0.0.1:55434`
- Redis：`127.0.0.1:6382`

新建本机演示数据卷会依次加载 `schema.sql`、`seed.sql`、案例数据、媒体导入与公开字段规范化脚本、迁移 `13` 至 `41`；其中 `17-local-demo-writing-assignment.sql` 只补齐固定的本机演示派单，不能用于生产。迁移 `34` 不种入开放 API 应用或密钥；迁移 `35` 只建立逐项证据门禁、供应商订单履约凭据约束和历史组合服务保护，不会把外部能力标记为已验收；迁移 `36` 只建立已核验结构的追加式台账基线；迁移 `37` 要求媒体任务在录入报道成果前已有已发邀请事实；迁移 `38` 把一份云采写订单拆为可追溯的写手名额，并在数据库层拒绝同一写手的重叠已确认档期。迁移 `39` 在写手配置了服务半径时要求平台人工录入可核验的服务距离，并拒绝超出半径的活跃派单；它不会定位、推算距离或填补历史数据。迁移 `40` 不会修复或推断任何历史完成时间；发现发布会事项与完成时间矛盾时会停止等待人工核验。迁移 `41` 仅固化发布会候选名单的推进顺序与终态保护；它不补造邀请、回复、到场、联系人或其他历史履约事实。

需要确认当前源码可在干净数据库中重建时，运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-clean-local-db.ps1
```

该回归使用不开放端口、运行结束自动清理的临时 PostgreSQL 容器；它只核验数据库结构和受控静态种子导入，不表示外部媒体数据、供应商履约或生产环境已验收。

需要确认当前本机演示库能够从二进制备份恢复时，运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-local-database-backup-restore.ps1
```

该脚本只接受本项目 `docker-compose.local-demo.yml` 启动的健康 PostgreSQL 容器。它将备份恢复到无宿主机端口、无持久化数据卷的临时容器，比对关键业务汇总和结构约束后自动删除全部临时文件；不会启动生产编排，也不包含上传文件恢复，不能作为生产备份验收。

上传文件本体使用独立数据卷，可继续运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-local-upload-backup-restore.ps1
```

该脚本只接受当前本机演示后端挂载的上传卷，恢复到临时 Docker 卷并比对文件哈希、所有者、权限、大小和目录集合；不打印文件名或内容，完成后删除归档和临时卷。它不替代生产加密、异地副本、保留周期和恢复时限验收。

需要确认账号停用或角色调整后，即使 Redis 会话索引缺失，变更前的令牌也不会在账号恢复后重新生效，可运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-local-session-revocation.ps1
```

该脚本仅允许访问本机回环地址，临时使用一个隔离演示账号完成“停用—恢复—旧令牌拒绝—新登录成功”回归，并在退出前恢复原角色与状态；不会打印账号、密码、令牌或用户标识。

### 生产部署

生产编排不提供回退凭据，不加载 `seed.sql`、品牌案例或迁移 `17`，也不会生成测试账号、客户项目、供应商或演示订单。先从 `.env.example` 创建受保护的 `.env`，由部署负责人填入独立数据库、Redis、端口和 CORS 配置，再启动：

```powershell
Copy-Item .env.example .env
# 在受控方式下填写 .env；不要将其提交到版本库。
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-production-readiness.ps1 -EnvFile .env
docker compose --env-file .env -f docker-compose.production.yml up -d --build
```

生产首建会加载 `schema.sql`、`02-production-access-control.sql` 与迁移 `13` 至 `16`、`18` 至 `41`。未完成数据许可、字段审查和对客报价验收的外部媒体目录、报价文件及其规范化脚本不会进入生产 Compose；在获得书面确认前，客户页面只能显示“暂不可用”和人工补充路径，不能把本机静态目录当作生产媒体能力。生产前端显式以非演示模式编译；只有本机演示编排才会显示“本机演示环境”提示，示例与测试数据不得被当作生产服务、媒体资源或履约结果。生产后端固定关闭 Swagger 与 OpenAPI 公开入口，CORS 只接受部署文件中明确列出的 HTTPS 正式来源，不会额外放行 localhost 或 HTTP 域名。`02-production-access-control.sql` 只创建角色、权限和对客服务价目；首个运营账号须由受控的管理员开通流程创建，不能使用测试账号替代。开放 API 应用、密钥和生产验收记录只可在正式审批完成后由平台运营创建，不能由种子或部署文件带入。

提交生产部署前，可先运行一次自动清理的生产等价空库验收：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-production-cold-start.ps1
```

该脚本拒绝接管任何既有 `winpress-production-*` 容器，使用自动避让端口、随机临时凭据、独立数据卷和独立质检镜像标签启动真实生产编排。它验证结构版本、接口合同、空业务数据、角色权限、980 元/人天公开价、Swagger/OpenAPI 关闭、CORS 只接受显式 HTTPS 来源、外部联动关闭、上传目录保护与仅本机端口绑定，随后删除容器、卷、质检镜像及临时凭据。它是可复现的发布前证据，不会创建正式生产账号，也不替代域名、TLS、备份、监控、外部授权和真实履约验收。

正式生产空库健康且上线审批完成后，由部署负责人在服务器交互式创建首个平台管理员：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bootstrap-production-admin.ps1 -ConfirmCreate
```

脚本只接受 `docker-compose.production.yml` 启动的健康生产 PostgreSQL，密码以隐藏输入读取，不提供默认账号，不输出联系方式、密码、令牌或数据库凭据。首次成功会写入脱敏审计记录；数据库曾存在平台管理员后会永久拒绝再次执行。后续账号应由已认证的平台管理员维护，不能用该脚本绕过账号恢复或审批。可先运行 `scripts/verify-production-admin-bootstrap.ps1`，在自动清理的生产等价空库中验证创建、登录、权限、审计及重复执行拒绝；该验证只创建合成账号，不创建真实生产账号。

生产发布前还应执行一次非空的数据库与附件成对恢复演练：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-production-backup-restore.ps1
```

该脚本固定拒绝真实生产实例，只在独立冷启动栈中通过正式接口注册一个合成客户，分别创建新闻发布会、云采写、媒体邀请和直编发稿四个独立项目，并以同一活动关联查看；随后核验四类客户任务、四条稳定订单、云采写 980 元/人/天、其他服务未报价状态和客户字段边界，再上传一个合成附件。PostgreSQL 自定义备份与附件卷归档会分别恢复到无网络数据库容器和未暴露文件卷，项目、任务、订单、九项发布会清单、附件元数据、文件哈希、权限与大小一致后才通过并删除全部资源。它证明当前生产编排的四服务非空数据和附件可以成对恢复，但不替代真实生产备份策略、加密、异地副本、RPO/RTO、灾备切换或恢复审批。Redis 只保存会话和短期限流状态，不属于本演练的权威业务数据。

存量数据库还应按顺序执行：

- `database/05-separate-writing-from-media-pr.sql`
- `database/06-onsite-writing-product.sql`
- `database/07-news-conference-replaces-owned-channel.sql`
- `database/08-niumedia-media-discovery.sql`
- `database/09-pricing-management.sql`
- `database/11-v4_1-product-core.sql`
- `database/12-media-invitation-without-manuscript.sql`
- `database/13-conference-progressive-intake.sql`
- `database/14-supplier-orders-and-conference-workbench.sql`
- `database/15-niumedia-reporter-and-multi-invitation.sql`
- `database/16-media-partnership-inquiry.sql`
- `database/18-service-intake-tasks.sql`
- `database/19-activity-project-linkage.sql`
- `database/20-direct-manuscript-source.sql`
- `database/21-manual-media-invitation-pending-verification.sql`
- `database/22-service-intake-title-integrity.sql`
- `database/23-settlement-transaction-ledger.sql`
- `database/24-requirement-idempotency.sql`
- `database/25-task-acceptance-integrity.sql`
- `database/26-channel-quote-integrity.sql`
- `database/27-media-invitation-progress-integrity.sql`
- `database/28-publish-task-terminal-integrity.sql`
- `database/29-publish-plan-idempotency.sql`
- `database/30-settlement-transaction-idempotency.sql`
- `database/31-batch-quote-adjustment-idempotency.sql`
- `database/32-publish-plan-service-integrity.sql`
- `database/33-supplier-api-connections.sql`
- `database/34-open-api-management.sql`
- `database/35-release-governance-and-evidence.sql`
- `database/36-schema-migration-ledger.sql`
- `database/37-media-pr-result-integrity.sql`
- `database/38-writing-assignment-slot-schedule-integrity.sql`
- `database/39-writing-assignment-radius-integrity.sql`
- `database/40-conference-work-item-state-integrity.sql`
- `database/41-conference-media-candidate-state-integrity.sql`

`39-writing-assignment-radius-integrity.sql` 仅把已经配置的写手服务半径变为活跃派单的数据库门槛：半径非负，活跃名额必须有人工核验距离且不得超出半径；下调半径若会使既有活跃名额越界，数据库会拒绝。脚本先扫描存量矛盾，发现缺距离或超半径的活跃记录即停止，不定位、不推算、不补填距离，也不改写写手、订单、价格、排班或历史记录。

`40-conference-work-item-state-integrity.sql` 固化新闻发布会统筹事项的完成时间、合法状态迁移和终态保护。已完成事项与已完成发布会项目不能重新打开；项目完成前全部统筹事项必须完成。脚本发现历史状态与完成时间矛盾或完成项目仍有未完成事项时会停止，不补写完成时间、事项、项目或履约事实。

`41-conference-media-candidate-state-integrity.sql` 固化发布会候选名单从“候选”到“待邀约确认”“已邀请”再到回复、到场、婉拒或不再推进的顺序。结果状态需要留存跟进说明，终态不可被普通更新改写；脚本先检查存量时间线和说明是否自洽，发现矛盾即停止，不补写邀请、回复、到场、联系人或履约事实。

`10-brand-case-demo-data.sql` 与 `17-local-demo-writing-assignment.sql` 仅用于本机演示，不能作为存量或生产迁移执行。`18-service-intake-tasks.sql` 则是生产迁移：它为媒体邀请与直编发稿建立可追溯的服务受理任务，不改写既有价格、供应商映射或历史执行记录。`19-activity-project-linkage.sql` 只建立同一活动下的可选项目关联，不会合并需求、任务、订单、价格或历史记录。`20-direct-manuscript-source.sql` 只为直编发稿的客户定稿副本保留来源关联，复制后的稿件、项目、任务和订单仍相互独立。`21-manual-media-invitation-pending-verification.sql` 只允许人工补充的媒体邀请名单在项目核验前暂不绑定执行渠道；它不导入外部目录、不产生供应商订单、不确认价格，也不代表已向媒体发出邀请。`22-service-intake-title-integrity.sql` 仅修复已被问号替换的服务受理标题，并阻止空标题或纯问号标题继续写入；它不会推测原始媒体、渠道、供应商或价格。`23-settlement-transaction-ledger.sql` 建立有凭据的收款、退款、账务调整与核销台账；它不会生成交易数据，也不会把结算状态自动当作付款事实。`24-requirement-idempotency.sql` 为客户下单建立请求标识和内容摘要约束；同一客户组织以同一标识重试只返回原项目，不会重复生成需求、项目、任务或订单。`25-task-acceptance-integrity.sql` 固化发布任务、媒体邀请和成果链接状态约束，并阻止同一任务重复登记同一成果链接；客户验收必须由后端在已核验成果存在时完成，验收后任务不可回退或重复提交成果。`26-channel-quote-integrity.sql` 为直编渠道客户价建立有效期、成本关系和状态约束，并保证同一渠道最多只有一条有效报价；调价服务同时锁定渠道行，避免并发首次报价产生价格分叉。`27-media-invitation-progress-integrity.sql` 将媒体邀请事实同步到通用任务、发布计划和项目状态：拒绝或不再推进使用独立终态，不伪造成发布成果；已确认计划对应的服务受理阶段同时闭合。迁移只对齐已有事实，不生成媒体联系、回复、到场、报道、价格或供应商数据。`28-publish-task-terminal-integrity.sql` 在数据库层阻止已完成、客户已验收或媒体不再推进的任务被任何后台旁路重新打开；客户验收还必须对应已核验成果。它不生成任务、成果、供应商订单或履约事实。`29-publish-plan-idempotency.sql` 为发布计划保存建立请求标识和内容摘要约束；同一客户以同一标识重试只返回原计划，不会重复生成计划、媒体选择、任务、订单、价格或供应商事实。`30-settlement-transaction-idempotency.sql` 为收款、退款、账务调整与核销登记增加请求标识和内容摘要约束；相同标识与相同内容的重试返回原交易，相同标识不能改作另一笔交易，历史交易保持空值。`31-batch-quote-adjustment-idempotency.sql` 为批量按比例调价建立请求批次和调价明细关联；相同管理员使用同一标识和相同内容重试只返回原调价结果，不会在已调价格上再次计算，迁移本身不生成或改写任何报价。`32-publish-plan-service-integrity.sql` 阻止媒体邀请或直编发稿计划被写入其他服务项目，也阻止后续变更项目或需求服务类型绕过边界；历史错配记录原样保留并从客户查询中隔离，不能据此生成新的任务或订单。`33-supplier-api-connections.sql` 建立管理员专属的接口配置、上线验收和历史组合审核台账；凭据只保存环境变量名，未取得授权、沙箱与生产证据时接口不能启用，历史组合记录不会被自动拆分、迁移、归档或删除。`34-open-api-management.sql` 建立客户开放 API 接入应用、仅保存哈希的访问密钥、受理回执与脱敏请求摘要；它不迁入旧工具数据、不生成测试密钥，也不把外部媒体或供应商能力当作已验收服务。`35-release-governance-and-evidence.sql` 将五项总关卡拆为 28 项可核验材料，并在数据库层要求外部媒体连接、供应商订单回执与历史组合处理决定具备相应证据；存量供应商订单统一保留为“履约未确认”，不伪造成真实外部提交。`36-schema-migration-ledger.sql` 仅为已核验的 schema 35 建立追加式迁移台账基线，不回填或伪造旧脚本记录，也不写入业务、凭据或外部履约事实。`37-media-pr-result-integrity.sql` 追加媒体报道成果的事实链门禁：终态任务必须已有已核验成果；媒体任务还必须先有已发送邀请并由结果写入“已报道”。如存量记录不满足条件，迁移会停止并等待逐条业务核验，不会补造邀请、报道或验收事实。`38-writing-assignment-slot-schedule-integrity.sql` 将云采写派单改为逐写手名额和实际服务时段；同一写手确认两个重叠时段时由数据库直接拒绝，父派单在所有名额确认前保持未满额状态。对于没有真实写手、时间或多人名额映射依据的存量记录，迁移会停止，不会补造排班、自动拆单、改价、归档或删除。

本机演示编排中的回退密码仅供本机验收。生产编排要求显式环境变量，测试账号不会进入生产数据库或公开前端路径。

## 源码方式启动

如需调试，可先启动本机演示栈中的 PostgreSQL 和 Redis：

```powershell
docker compose -f docker-compose.local-demo.yml up -d postgres redis
```

启动后端：

```powershell
cd backend
./mvnw.cmd spring-boot:run
```

启动前端：

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

如需避开存量数据库和 Redis，可启动 V4.1 隔离质检环境：

```powershell
docker compose -f docker-compose.v4_1-qa.yml up -d
```

隔离环境使用 PostgreSQL `55433`、Redis `6381`，只供本机测试。生产环境不得沿用其中的账号和密码。完整连接方式见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。

源码调试默认使用前端 `5217`、后端 `8192`。发现端口已被占用时会中止启动并显示占用进程，不会自动跳转到其他端口。

## 本机测试账号

本机种子包含平台运营、发布运营和客户三类测试身份。用户名、权限和本机密码只记录在
[docs/TEST-ACCOUNTS.md](docs/TEST-ACCOUNTS.md)，不在 README、前端页面、静态资源或构建产物中公开。生产编排不导入这些账号。

如本机旧演示卷的账号无法登录，只能运行文档所列的本机重置脚本；脚本固定拒绝生产或其他 Compose 目标，并会同步所有受控本机演示账号后撤销旧会话。

另有四个隔离的本地案例账号，用于查看采写、媒体邀请、新闻发布会和直编发稿项目。案例名称、项目及金额只用于内部演示，未取得客户授权不得作为对外业绩展示。

生产部署必须使用独立的数据库和 Redis 凭据，并经受控流程创建首个运营账号；不得复制本机测试账号或密码。

## 验证

```powershell
cd backend
./mvnw.cmd test

cd ../frontend
npm.cmd run check
npm.cmd run build
npm.cmd run test:e2e

cd ..
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-recovery-traceability.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-local-settlement-idempotency.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-local-batch-quote-idempotency.ps1
```

代码格式、质量检查与提交约定见 [`docs/CODE-STYLE.md`](docs/CODE-STYLE.md)。

完整部署步骤见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)，接口定义见 [docs/API.md](docs/API.md)，本轮 P0—P1 验收见 [docs/P0-P1-EXECUTION-QA-20260728.md](docs/P0-P1-EXECUTION-QA-20260728.md)，发布治理与上线边界见 [docs/RELEASE-GOVERNANCE-QA-20260731.md](docs/RELEASE-GOVERNANCE-QA-20260731.md)，发布会统筹事项状态回归见 [docs/CONFERENCE-WORK-ITEM-STATE-INTEGRITY-QA-20260731.md](docs/CONFERENCE-WORK-ITEM-STATE-INTEGRITY-QA-20260731.md)，媒体邀请成果事实链回归见 [docs/MEDIA-INVITATION-RESULT-INTEGRITY-QA-20260731.md](docs/MEDIA-INVITATION-RESULT-INTEGRITY-QA-20260731.md)，全局复查结果见 [docs/WINPRESS-GLOBAL-PRODUCT-CODE-AUDIT-20260728.md](docs/WINPRESS-GLOBAL-PRODUCT-CODE-AUDIT-20260728.md)，近 60 天决策补充闭环见 [docs/RECOVERY-SUPPLEMENT-QA-20260730.md](docs/RECOVERY-SUPPLEMENT-QA-20260730.md)，旧站公开内容吸收边界见 [docs/OLD-SITE-CONTENT-INTEGRATION-AUDIT-20260730.md](docs/OLD-SITE-CONTENT-INTEGRATION-AUDIT-20260730.md)，案例资料公开边界见 [docs/CASE-MATERIAL-PUBLICATION-BOUNDARY-20260730.md](docs/CASE-MATERIAL-PUBLICATION-BOUNDARY-20260730.md)，当前源码包范围见 [docs/SOURCE-PACKAGE-MANIFEST-20260728.md](docs/SOURCE-PACKAGE-MANIFEST-20260728.md)。
