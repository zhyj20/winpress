# 部署说明

## 域名

- 主域名：`winpress.cn`
- 别名：`winpress.waykey.net`

两个域名指向同一套前端和后端。生产环境通过 `WINPRESS_DB_URL` 连接公司 PostgreSQL 集群；WinPress 使用独立数据库 `winpress_commercial`。Redis 使用独立密码和键前缀。

登录失败冷却和会话均使用 Redis：连续失败的计数不落入业务数据库，也不会按账号名向客户端暴露任何存在性信息。Redis 临时不可用时，后端保留单实例短时保护，运维应同时处理 Redis 告警。

随附前端 Nginx 是应用 API 的第一跳边界：它会以本次连接的远端地址覆盖 `X-Forwarded-For`，而不会附加客户端自行提交的转发链。Spring 会据此识别登录失败限流来源，避免攻击者伪造请求头绕过限流。若正式 TLS/CDN/WAF 代理位于此前，必须由该受信任代理先按其固定网段解析并净化真实客户端地址，再只向 WinPress 前端转发；不得把互联网客户端提交的 `X-Forwarded-For` 原样传入，也不得直接公开后端 `8092` 端口。

## 环境变量

```text
BACKEND_PORT=8092
WINPRESS_DB_URL=jdbc:postgresql://postgres-host:5432/winpress_commercial
WINPRESS_DB_USERNAME=winpress
WINPRESS_DB_PASSWORD=<strong-password>
WINPRESS_REDIS_HOST=redis-host
WINPRESS_REDIS_PORT=6379
WINPRESS_REDIS_PASSWORD=<strong-password>
WINPRESS_SESSION_TTL_HOURS=12
WINPRESS_STORAGE_PATH=/srv/winpress/storage/uploads
WINPRESS_STORAGE_MAX_FILE_BYTES=20971520
WINPRESS_CORS_ORIGINS=https://winpress.cn,https://winpress.waykey.net
WINPRESS_API_DOCS_ENABLED=false
WINPRESS_NIUMEDIA_BASE_URL=https://api.media.beer/v1
WINPRESS_NIUMEDIA_TOKEN=<authorized-token-or-empty>
WINPRESS_NIUMEDIA_MEDIA_SEARCH_PATH=/media/search
WINPRESS_NIUMEDIA_REPORTER_SEARCH_PATH=/reporter/search
WINPRESS_NIUMEDIA_REGION_PATH=/region
WINPRESS_NIUMEDIA_MEDIA_TYPES_PATH=/media/types
WINPRESS_NIUMEDIA_MEDIA_FORMS_PATH=/media/mp_types
WINPRESS_NIUMEDIA_REQUEST_TIMEOUT_SECONDS=15
WINPRESS_NIUMEDIA_SEARCH_CACHE_SECONDS=300
WINPRESS_NIUMEDIA_TAXONOMY_CACHE_SECONDS=86400
WINPRESS_NIUMEDIA_MIN_REQUEST_INTERVAL_MILLIS=500
WINPRESS_NIUMEDIA_RATE_LIMIT_COOLDOWN_SECONDS=60
WINPRESS_LOGIN_MAX_FAILURES=8
WINPRESS_LOGIN_FAILURE_WINDOW_SECONDS=900
WINPRESS_LOGIN_COOLDOWN_SECONDS=300
WINPRESS_FEDERATION_ENABLED=false
WINPRESS_FEDERATION_MIGRATE_ON_START=false
WINPRESS_FEDERATION_SHARED_SECRET=<at-least-32-byte-production-secret-or-empty>
WINPRESS_FEDERATION_PLATFORM_ISSUER=niumedia-platform
WINPRESS_FEDERATION_WINPRESS_ISSUER=winpress-commercial
WINPRESS_FEDERATION_SOURCE_INSTANCE_ID=production-a
WINPRESS_FEDERATION_GEO_CALLBACK_URL=<approved-https-callback-or-empty>
WINPRESS_FEDERATION_CALLBACK_TIMEOUT_SECONDS=15
WINPRESS_FEDERATION_MAX_REQUESTS_PER_MINUTE=120
```

`WINPRESS_STORAGE_MAX_FILE_BYTES` 必须与反向代理的请求体限制及 Spring 的 `spring.servlet.multipart.max-file-size` 一并评审。当前源码默认值为 `20 × 1024 × 1024` 字节；上传请求会先由框架限制，再由存储层按实际输入流复核，不能只依赖客户端上报的文件大小。生产环境如调整该值，应同步完成超限响应、磁盘配额、备份和恢复回归。

生产 Compose 读取 `.env` 中的必填变量，不为数据库、Redis、端口或 CORS 使用回退值。`.env.example` 只能作为字段清单，部署负责人必须在受控环境中填入独立值并将 `.env` 排除在版本控制和交付包之外。`WINPRESS_API_DOCS_ENABLED` 在生产环境必须明确保持 `false`；生产 Compose 还会在容器层固定为 `false`，不能通过环境文件公开 Swagger 或 OpenAPI。

GEO 服务端联动默认关闭。首次启用前须备份数据库，在受控维护窗口将 `WINPRESS_FEDERATION_MIGRATE_ON_START=true` 启动一次，确认独立 `winpress_federation` schema 迁移成功后再恢复为 `false`。共享密钥不得少于 32 字节，不得进入 Vue、浏览器、源码包或日志。生产域名、密钥、HTTPS 回调、网络白名单、限流额度、回调重试和双方契约未联合验收前，`WINPRESS_FEDERATION_ENABLED` 必须保持 `false`。

## 数据库初始化与数据边界

### 生产首建

生产数据库不导入 `seed.sql`、品牌案例、迁移 `17`、本机测试账号、未验收的媒体目录或报价文件。新库依次执行：

```powershell
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/schema.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/02-production-access-control.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/13-conference-progressive-intake.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/14-supplier-orders-and-conference-workbench.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/15-niumedia-reporter-and-multi-invitation.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/16-media-partnership-inquiry.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/18-service-intake-tasks.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/19-activity-project-linkage.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/20-direct-manuscript-source.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/21-manual-media-invitation-pending-verification.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/22-service-intake-title-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/23-settlement-transaction-ledger.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/24-requirement-idempotency.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/25-task-acceptance-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/26-channel-quote-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/27-media-invitation-progress-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/28-publish-task-terminal-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/29-publish-plan-idempotency.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/30-settlement-transaction-idempotency.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/31-batch-quote-adjustment-idempotency.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/32-publish-plan-service-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/33-supplier-api-connections.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/34-open-api-management.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/35-release-governance-and-evidence.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/36-schema-migration-ledger.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/37-media-pr-result-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/38-writing-assignment-slot-schedule-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/39-writing-assignment-radius-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/40-conference-work-item-state-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/41-conference-media-candidate-state-integrity.sql
```

`02-production-access-control.sql` 只创建角色、权限和对客服务价目，不创建可登录账号、业务项目、供应商或演示订单。首个可登录平台管理员必须使用下文的一次性受控开通工具创建，不得复制本机测试账号。

### 外部媒体目录与报价：待确认后再启用

`03-media-import.sql`、`04-normalize-public-channel-data.sql`、`media_channels.csv` 与 `media_quotes.csv` 不再随生产 Compose 自动导入。它们代表待审查的静态输入，不代表已验收的实时媒体、报价或供应商履约能力。

只有在完成数据来源与许可确认、对客字段审查、价格有效期核验、备份点记录和书面上线批准后，才能由受控部署流程导入经批准的数据副本。未完成前，直编发稿仍可独立创建项目、提交客户定稿并进入服务受理；客户不能在线选择未经验证的渠道。生产 Compose 边界可先执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-production-compose-boundaries.ps1
```

### 本机演示与验收

仅在隔离的本机测试库中，才按以下顺序加载种子和案例数据：`schema.sql`、`seed.sql`、`10-brand-case-demo-data.sql`、媒体导入/公开字段规范化脚本、迁移 `13` 至 `41`。迁移 `17-local-demo-writing-assignment.sql` 只补齐固定演示派单，禁止进入生产库；前端会在本机演示编排中显示环境提示，明确示例与测试数据不代表生产服务、媒体资源或履约结果。迁移 `34` 不产生应用或密钥，迁移 `35` 不替代外部授权和真实验收，迁移 `36` 不回填历史迁移或业务事实，迁移 `37` 不会为存量记录补造已发邀请、报道或成果事实，迁移 `38` 不会为历史多人派单虚构写手名额或服务档期，迁移 `39` 不会定位、推算或补写写手距离，迁移 `40` 不会推断或补写历史完成时间，迁移 `41` 不会补造候选名单的邀请、回复、到场、联系人或履约事实。

如需验证源码在干净数据库中的可重建性，可运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-clean-local-db.ps1
```

该脚本会在无宿主机端口映射的临时 PostgreSQL 容器中加载与本机演示相同的初始化结构，核验四服务关键结构、活动关联与稿件来源字段。公开源码默认使用仅含表头的 CSV 样例，验证导入结构而不制造媒体数据；受控本机环境可通过参数或 `WINPRESS_MEDIA_CHANNELS_CSV`、`WINPRESS_MEDIA_QUOTES_CSV` 提供已批准的本地输入，脚本才核对其渠道/报价行数。受控账号说明存在时才核验 bcrypt 哈希；公开克隆不会要求或伪造测试账号。结束后自动删除临时容器和暂存目录；它不连接当前数据卷、不修改生产库，也不证明外部媒体数据、供应商履约或实时接口已经验收。

如需验证当前本机演示数据卷的 PostgreSQL 二进制备份确实可恢复，可运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-local-database-backup-restore.ps1
```

该脚本会先校验来源容器确由本项目 `docker-compose.local-demo.yml` 启动，拒绝生产或其他项目容器；再生成 PostgreSQL 自定义格式备份，恢复到无宿主机端口、无持久化数据卷的临时容器，并比对四项服务类型、关键业务表汇总和迁移 `22` 的标题完整性约束。校验结束后会删除临时容器、容器内备份和宿主机暂存文件。它只验证数据库，不包含 `file_asset` 对应的上传文件内容，也不替代生产库备份、上传目录恢复、加密、保留周期、异地副本或生产恢复时限验收。

上传文件本体存放在独立 Docker 卷中，本机演示可继续运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-local-upload-backup-restore.ps1
```

该脚本从当前本机演示后端的 `/app/storage/uploads` 挂载反查唯一上传卷，并核对本项目 Compose 标签；生产或其他项目卷会被拒绝。临时 `tar.gz` 只恢复到隔离 Docker 卷，脚本比较文件哈希、数字所有者、权限、大小和目录集合，不输出文件名或内容；结束后自动删除归档、临时卷和暂存目录。数据库与上传卷恢复必须成对验证，但这两个本机脚本仍不覆盖生产加密、密钥托管、异地副本、保留周期、恢复时限或灾备切换。

## 本机 Docker 完整部署

本机完整栈使用独立 Compose 项目名，不接管旧 WinPress、牛媒信源或其他网站容器，并且只允许使用本机演示编排：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/rebuild-local-demo.ps1
```

本机演示编排的后端镜像使用 `backend/Dockerfile.local`，只复制由重建脚本在临时副本中完成测试和打包后生成的 JAR。重建脚本会先执行端口预检，并在 Compose 启动后等待前后端健康；这样不会因本机已有 Java 进程锁定 `backend/target/`、Docker 内 Maven 仓库出现短暂网络握手异常，或遗漏准备步骤而混入旧产物。前端构建同时显式传入 `VITE_LOCAL_DEMO=true`；`target-local/` 是本机构建产物，不进入源码包，也不能当作生产交付件。

生产编排仍使用 `backend/Dockerfile` 从源码构建；前端显式以 `VITE_LOCAL_DEMO=false` 编译，因而不会显示本机演示提示。该 Dockerfile 对 Maven 下载做有限重试，但生产镜像构建、迁移和发布必须由预发布环境另行验收。本机演示镜像健康不代表生产部署已验收。

如果复用的本机数据卷仍保留旧版演示账号哈希，可在确认目标为本机测试库后执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/reset-local-test-accounts.ps1 -Force
```

该脚本从 `docs/TEST-ACCOUNTS.md` 读取本机演示账号，重置对应密码并撤销这三类账号的旧会话；不会打印密码，禁止用于生产数据库。

生产部署使用独立编排。先由部署负责人创建受保护的 `.env`，再执行：

```powershell
Copy-Item .env.example .env
# 在受控方式下填写 .env；不要将其提交到版本库。
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-production-readiness.ps1 -EnvFile .env
docker compose --env-file .env -f docker-compose.production.yml up -d --build
```

生产预检不会启动容器、连接数据库或打印凭据。它拒绝 `.env.example`、占位符、启用 Swagger/OpenAPI、非 HTTPS 的本地 CORS、重复端口和已被占用的宿主机端口。启用 GEO 联邦时，还会拒绝不足 32 字节的共享密钥、默认或未规范化的实例标识、非公开 HTTPS 回调、无效发行方、越界的回调超时与请求限额，以及“联邦关闭但迁移开启”的冲突配置。只有通过预检且具备书面上线批准后，部署负责人才能执行 Compose 命令。下列地址只代表本机演示默认映射，不能据此推断生产端口。

### 生产等价空库冷启动验收

在正式部署前，建议先运行自动清理的生产等价验收：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-production-cold-start.ps1
```

脚本会先拒绝任何既有 `winpress-production-*` 容器，避免触碰真实生产实例；随后自动选择四个未占用的本机端口，生成只存在于系统临时目录的随机凭据，并使用独立项目、数据卷和 `production-cold-start` 镜像标签构建真实生产 Compose。验收范围包括：

- 后端健康、数据库健康、结构版本 `41` 和接口合同 `winpress-v4.2.30-20260731`；
- 账号、组织、需求、项目、媒体渠道、报价、供应商、供应商订单与商务咨询均为零；
- 仅存在 3 个角色、10 项权限和一条 `ONSITE_WRITING` 公开价 `980 CNY / PERSON_DAY`；
- 牛媒媒体接口、GEO 联动及迁移保持关闭，临时共享密钥为空；
- `/files/` 与 `/test-accounts.html` 返回 `404`，媒体状态接口要求登录；
- PostgreSQL、Redis、后端和前端都只绑定 `127.0.0.1`；
- 生产前端构建中不存在“本机演示环境”或“选择测试身份”标记。

默认模式完成后自动删除隔离容器、数据卷、质检镜像和临时凭据。只有需要短时人工页面检查时才使用 `-KeepRunning`；检查后必须立即运行同一脚本的 `-CleanupOnly`。该验收不创建生产账号，不连接外部媒体或 GEO，不替代生产域名、TLS、备份、监控、网络策略、法律信息、授权合同和真实供应商履约验收。

### 首个平台管理员一次性开通

生产空库健康、生产 Compose 归属已核实且上线审批完成后，由部署负责人在服务器交互式执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bootstrap-production-admin.ps1 -ConfirmCreate
```

该工具具有以下硬边界：

- 只接受容器名 `winpress-production-postgres`、Compose 项目 `winpress-commercial-production`、服务 `postgres`，且配置来源必须包含 `docker-compose.production.yml`；
- 目标容器必须处于运行且健康状态；本机演示、其他 Compose 项目和第二个管理员开通都会被拒绝；
- 组织名称、姓名、手机号、登录邮箱由部署负责人输入，密码以 `SecureString` 隐藏读取，须为 12—64 位并同时包含大写、小写、数字和符号；
- 密码不进入命令行历史、源码、输出或审计；脚本也不打印联系方式、数据库凭据、令牌或密码哈希；
- 成功时只创建一个 `PLATFORM` 组织、一个 `PLATFORM_ADMIN` 用户及其角色关系，并写入一条 `BOOTSTRAP_PLATFORM_ADMIN` 脱敏审计；不会创建客户、项目、媒体目录、报价、供应商或业务订单；
- 只要数据库曾存在 `PLATFORM_ADMIN` 角色关系，即使该账号后来停用，也不允许再次使用开通工具。后续管理员维护走已认证的账号与权限管理；账号丢失须按单独审批的恢复流程处理。

在正式执行前，可用下列自动化验收验证工具自身：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-production-admin-bootstrap.ps1
```

验收脚本只在独立生产等价空库中使用合成联系方式和内存密码，实际调用登录、本人信息、管理员账号列表及退出接口，检查 10 项权限、bcrypt 成本、审计脱敏和第二次执行拒绝，随后删除隔离容器、卷、镜像与临时凭据。它不创建真实生产管理员，也不替代生产责任人身份核验、密码托管、多因素认证、域名、TLS、备份、监控或发布审批。

### 生产等价数据库与附件成对恢复

空库冷启动只能证明新环境可以初始化，不能证明已有项目和附件能够从备份恢复。发布前运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-production-backup-restore.ps1
```

该脚本首先调用生产等价冷启动，并再次核验所有来源容器和附件卷都属于 `winpress-production-cold-start`；如果本机存在任何正式命名容器，冷启动前置检查会直接拒绝，因而不会把真实生产数据当作测试源。演练步骤为：

1. 通过正式注册接口建立一个合成客户，通过正式需求接口分别建立新闻发布会、云采写、媒体邀请和直编发稿四个独立项目，并用活动关联而不合并订单；
2. 核验客户可查的四类任务、四条稳定订单、云采写 `980 CNY / PERSON_DAY` 和其余三项未报价状态；同时确认客户响应不含供应商、成本、上游标识或令牌，未授权媒体目录仍为空；
3. 核验发布会项目只凭标题、会务联系人和联系电话即可生成九项工作清单，并通过受保护文件接口上传、下载一个合成文本附件；
4. 在业务写入停止后生成 PostgreSQL 自定义格式备份及附件 `tar.gz`，同时生成目录、文件哈希、数字所有者、权限和大小清单；
5. 将数据库恢复到 `--network none`、无宿主机端口、临时文件系统的 PostgreSQL 容器，将附件恢复到未挂载应用、未暴露宿主机的临时 Docker 卷；
6. 比对源库和恢复库的结构、角色权限、公开价格、四类需求、四个项目、采写派单、两项受理任务、发布会九项清单和附件记录；再交叉核对恢复库中的 `storage_key`、SHA-256、大小与恢复文件；
7. 删除数据库备份、附件归档、临时数据库、临时文件卷、冷启动栈、质检镜像、状态文件和所有临时凭据。

演练不会导入媒体目录、报价、供应商或供应商订单，也不会连接牛媒或 GEO。它属于当前源码的生产等价恢复证据，不代表真实生产备份已配置。正式上线仍需确认备份加密、保留周期、异地副本、访问审批、RPO/RTO、定时执行、告警和灾备切换。Redis 当前只保存登录会话、登录失败窗口和短期接口限流状态，不作为权威业务台账；Redis 丢失会使会话失效并重置短期限流窗口，但不得导致 PostgreSQL 业务记录或附件事实丢失。

端口分配：

- 首页：`http://127.0.0.1:5217`
- 后端：`http://127.0.0.1:8192`
- 本机演示 Swagger：`http://127.0.0.1:8192/swagger-ui/index.html`
- PostgreSQL：`127.0.0.1:55434`
- Redis：`127.0.0.1:6382`

容器内后端仍监听 `8092`；`8192` 是仅用于本机访问的宿主机映射端口。上述 Swagger 地址只由 `docker-compose.local-demo.yml` 显式开启；生产 Compose 下 `/swagger-ui/index.html`、`/swagger-ui.html` 与 `/v3/api-docs` 必须返回 `404`。

## 升级已有数据库

已运行过旧版本的数据库，请按顺序执行增量脚本。`07` 会将历史“自有渠道”记录归档为只读历史数据，并新增新闻发布会项目与统筹清单；不会删除已有项目记录。

```powershell
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/05-separate-writing-from-media-pr.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/06-onsite-writing-product.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/07-news-conference-replaces-owned-channel.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/08-niumedia-media-discovery.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/09-pricing-management.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/10-brand-case-demo-data.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/11-v4_1-product-core.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/12-media-invitation-without-manuscript.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/13-conference-progressive-intake.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/14-supplier-orders-and-conference-workbench.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/15-niumedia-reporter-and-multi-invitation.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/16-media-partnership-inquiry.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/18-service-intake-tasks.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/19-activity-project-linkage.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/20-direct-manuscript-source.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/21-manual-media-invitation-pending-verification.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/22-service-intake-title-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/23-settlement-transaction-ledger.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/24-requirement-idempotency.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/25-task-acceptance-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/26-channel-quote-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/27-media-invitation-progress-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/28-publish-task-terminal-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/29-publish-plan-idempotency.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/30-settlement-transaction-idempotency.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/31-batch-quote-adjustment-idempotency.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/32-publish-plan-service-integrity.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/33-supplier-api-connections.sql
psql -v ON_ERROR_STOP=1 -d winpress_commercial -f database/34-open-api-management.sql
```

迁移 `18-service-intake-tasks.sql` 为既有 `MEDIA_PR` 与 `DIRECT_PUBLISHING` 项目补建一条服务受理任务，状态和负责人可继续在项目任务记录中追踪；它不变更既有价格、供应商关系、上游数据或历史执行结果。迁移 `19-activity-project-linkage.sql` 为同一活动下的独立服务增加可选根项目关联；每项服务继续保留独立需求、项目、任务、订单与计价。迁移 `20-direct-manuscript-source.sql` 为客户已确认稿件复制到新直编项目保留来源关联；复制不会共享稿件、合并订单或继承源项目的价格和供应商信息。迁移 `21-manual-media-invitation-pending-verification.sql` 仅允许人工补充的媒体邀请名单在项目核验前暂不绑定执行渠道；它不导入目录、报价或供应商，也不代表已发出媒体邀请。迁移 `22-service-intake-title-integrity.sql` 只修复已经变成纯问号的服务受理标题，并拒绝新的空标题或纯问号标题；它不会补写媒体、渠道、供应商或价格。迁移 `23-settlement-transaction-ledger.sql` 建立有凭据的收款、退款、贷项、借项与核销记录；不会补写历史交易，也不会把结算状态改动视为到账。迁移 `24-requirement-idempotency.sql` 为客户下单建立请求标识及唯一约束；历史记录保持空值，新请求重试不会重复创建业务记录。迁移 `25-task-acceptance-integrity.sql` 固化任务、媒体邀请和成果链接状态约束，并阻止同一任务重复登记同一成果链接；客户验收必须有已核验成果，验收后任务不可回退或再次提交成果。迁移 `26-channel-quote-integrity.sql` 验证客户价、成本关系、有效期和报价状态，并保证同一渠道只有一条有效报价；它不会改写既有报价或确认外部供应商履约。迁移 `27-media-invitation-progress-integrity.sql` 只把已有媒体邀约事实与任务、计划、项目和服务受理状态对齐；拒绝或不推进不会成为发布成果，也不会产生联系、报道、价格或供应商数据。迁移 `28-publish-task-terminal-integrity.sql` 阻止已完成、已验收或不再推进的任务被供应商订单及其他后台旁路回退；安装前仅校验存量，不补写成果或履约事实。迁移 `29-publish-plan-idempotency.sql` 为发布计划保存增加请求标识和内容摘要约束；历史计划保持空值，网络重试只返回原计划，不会重复生成计划、任务、订单、价格或供应商事实。迁移 `30-settlement-transaction-idempotency.sql` 为结算交易登记增加请求标识和内容摘要约束；历史交易保持空值，网络重试只返回原交易，不会重复改变实收、退款或调整金额。迁移 `31-batch-quote-adjustment-idempotency.sql` 为批量调价建立可核验批次；相同请求重试返回原调价明细，不会再次按比例计算，迁移本身不生成或改写任何报价。迁移 `32-publish-plan-service-integrity.sql` 阻止媒体邀请或直编发稿计划绑定到其他服务项目，并保护计划、项目和需求的后续归属变更；历史错配记录不被篡改，但客户侧不可见且不能继续确认。迁移 `33-supplier-api-connections.sql` 建立管理员接口配置、上线验收门禁和历史组合审核结构；只保存凭据环境变量名，五项上线门禁默认待验收，历史组合服务只登记待人工审核，不自动拆单、迁移、归档或删除。迁移 `34-open-api-management.sql` 将受控的客户系统接入纳入平台管理；它不导入旧工具密钥或数据库，不生成应用或访问密钥，也不将外部媒体或供应商能力宣称为实时可用。迁移 `35-release-governance-and-evidence.sql` 将五项关卡拆为 28 项必备材料，并要求外部检索、供应商履约和历史组合变更同时满足证据与数据库事实；它不会替代授权、合同、生产凭据或业务签字。

迁移 `36-schema-migration-ledger.sql` 只为已经通过结构核验的 schema 35 建立追加式基线，不回填旧迁移、业务记录、供应商、凭据或外部履约事实。健康检查必须同时确认该表、版本 36 基线记录和追加式保护触发器。

迁移 `37-media-pr-result-integrity.sql` 追加媒体成果事实链门禁：发布任务进入完成态前必须已有已核验成果；媒体公关任务还必须先有带邀请时间的已发邀请，并在结果提交时写入“已报道”。安装前若发现存量事实矛盾即停止，等待逐条业务核验；脚本不补写邀请、报道、成果或验收事实。

迁移 `38-writing-assignment-slot-schedule-integrity.sql` 建立云采写逐写手名额与服务时段。数据库会拒绝同一写手的重叠已确认档期；一份多人订单只有全部名额确认后才进入已接单状态。安装前若发现活跃旧派单缺少真实写手、时段，或已确认多人记录没有可靠的名额映射，脚本会停止，须先形成业务映射清单；不得自动拆单、补造写手、排班或履约事实。

迁移 `39-writing-assignment-radius-integrity.sql` 建立写手服务半径与活跃名额距离的一致性门禁。写手已配置半径时，待接单或已接单名额必须有人工核验的非负服务距离且不超过半径；下调半径若会使已有活跃名额越界，数据库会拒绝。安装前会先发现缺距离或越界的活跃历史记录并停止，等待业务核验；不得自动定位、推算、补写距离或改动排班、价格、订单和履约事实。

迁移 `40-conference-work-item-state-integrity.sql` 使新闻发布会统筹事项的完成时间、状态迁移和项目完成态可由数据库复核。它不会从状态推算完成时间，也不会改写历史项目或事项；如果发现完成时间与状态矛盾，或完成项目尚有未完成事项，脚本会停止，必须先根据可核验的业务记录人工处置。

迁移 `41-conference-media-candidate-state-integrity.sql` 使发布会候选名单的推进顺序、邀请／回复时间线和终态可由数据库复核。它不会从候选状态推断邀请、回复或到场时间，也不会改写历史联系人或履约事实；如果发现状态、时间线或结果说明矛盾，脚本会停止，必须先根据可核验的业务记录人工处置。

历史快照仅供追溯，不作为当前恢复基线：

```powershell
psql -d winpress_commercial -f database/winpress_full.sql
```

`winpress_full.sql` 是历史恢复快照，未作为本轮 P0/P1 的最终可复现生产基线。生产环境应按结构脚本与迁移脚本受控初始化；在干净验收库验证迁移 `18` 至 `41` 后，再导出新的受控恢复包并记录校验值。

## 本机 V4.1 隔离质检环境

为避免与其他网站的 PostgreSQL、Redis 和历史 WinPress 实例混用，本源码提供只用于本机验证的隔离编排：

```powershell
docker compose -f docker-compose.v4_1-qa.yml up -d
```

质检端口：PostgreSQL `55433`、Redis `6381`。启动后端时显式设置：

```powershell
$env:WINPRESS_DB_URL='jdbc:postgresql://127.0.0.1:55433/winpress_commercial'
$env:WINPRESS_DB_USERNAME='winpress_v41'
$env:WINPRESS_DB_PASSWORD='<docker-compose.v4_1-qa.yml 中的本机质检密码>'
$env:WINPRESS_REDIS_HOST='127.0.0.1'
$env:WINPRESS_REDIS_PORT='6381'
$env:WINPRESS_REDIS_PASSWORD='<docker-compose.v4_1-qa.yml 中的本机质检密码>'
$env:BACKEND_PORT='8092'
java -jar backend/target/winpress-commercial-1.0.0.jar
```

`docker-compose.v4_1-qa.yml` 中的账号只允许用于本机隔离测试，禁止复制到生产。当前本机存量库 `55432` 已完成 V4.1 结构迁移，但其历史运行凭据与现有编排配置不一致；在凭据归属人完成校正前，8092 质检后端连接的是 `55433` 隔离库，不能据此宣称生产连接已经验收。

## 媒体数据授权接入

`WINPRESS_NIUMEDIA_TOKEN` 仅由后端读取，不能写入 `VITE_*` 变量、前端代码、浏览器 Cookie 或日志。未提供有效授权令牌时，媒体、记者和筛选字典能力必须显示为“待配置/不可用”，不能以演示数据、公开额度或缓存内容声称已实时联通。`WINPRESS_NIUMEDIA_MIN_REQUEST_INTERVAL_MILLIS` 与 `WINPRESS_NIUMEDIA_RATE_LIMIT_COOLDOWN_SECONDS` 仅用于授权完成后的服务端限流与冷却控制。正式上线前仍须确认授权范围、商业额度、数据许可、缓存期限和人工复核责任。详见 `docs/NIUMEDIA-INTEGRATION.md`。

## 构建

```powershell
cd backend
./mvnw.cmd clean package

cd ../frontend
npm.cmd ci
npm.cmd run build
```

构建结果：

- 后端：`backend/target/winpress-commercial-1.0.0.jar`
- 前端：`frontend/dist/`

## 启动后端

```powershell
java -jar backend/target/winpress-commercial-1.0.0.jar
```

文件目录应由运行账号独占写权限。数据库、Redis 和上传目录不得通过 Web 服务器直接列目录。

## Nginx

```nginx
server {
    listen 443 ssl http2;
    server_name winpress.cn winpress.waykey.net;

    root /srv/winpress/frontend;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8092;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 上传目录不能作为静态资源或反向代理目录暴露。
    location ^~ /files/ {
        return 404;
    }
}
```

配置 TLS 证书后，将 HTTP 全部重定向到 HTTPS。

项目附件只能通过受保护的 `GET /api/v1/files/{fileNo}` 下载。请求必须携带已登录会话，后端按项目范围复核权限并以附件方式返回内容；不得生成或传播直接指向上传目录的 URL。

## 上线检查

1. 确认使用生产 Compose 与受保护的 `.env`，且生产库未导入测试账号、案例、迁移 `17` 或演示订单。
2. 确认 PostgreSQL 仅对应用服务器开放。
3. 确认 Redis 启用密码且不暴露公网端口。
4. 确认 `WINPRESS_CORS_ORIGINS` 只含正式域名。
5. 检查上传目录权限、容量和备份策略。
6. 验证 `/files/` 直接访问返回拒绝；以两个不同客户身份验证 `/api/v1/files/{fileNo}` 仅能下载各自项目材料。
7. 访问 `/api/v1/health`，仅在 `status=UP`、`database=UP`、`schemaStatus=UP` 时继续发布；再在受控数据库会话中核验 `schema_migration_ledger` 的迁移 `41`、脚本名和发布合同。公共健康接口不得回显结构版本、接口合同、构建提交或构建时间；若返回通用升级状态，先在受控环境核验并补齐数据库结构，禁止以源码版本号替代实际检查。
8. 在新生产库只执行一次受控首个平台管理员开通，核对 `PLATFORM_ADMIN`、10 项权限和 `BOOTSTRAP_PLATFORM_ADMIN` 脱敏审计；禁止复制测试账号或二次运行开通工具。
9. 对四项独立服务分别验证“下单 → 项目 → 任务 → 订单记录”的角色范围与状态。
10. 媒体数据未完成授权时验证状态为待配置/不可用；完成授权后再进行单独的供应商与数据许可验收。
11. 配置 PostgreSQL 与附件存储的成对备份、加密、保留周期和异地副本，并完成真实预发布恢复演练；确认 Redis 仅承载可重建的会话与短期保护状态。
12. 保存发布计划时验证缺少 `Idempotency-Key` 被拒绝、相同标识和相同内容返回原计划、相同标识改传其他内容发生冲突，并确认任务数不变；点击确认后才生成任务，重复确认不重复下单。
13. 登记结算交易时验证缺少 `Idempotency-Key` 被拒绝、原请求重试返回同一交易号、同一标识改传另一金额发生冲突，并确认实收、退款和调整汇总只变化一次。
14. 批量调价时验证缺少 `Idempotency-Key` 被拒绝、原请求重试返回同一批报价、同一标识改传另一比例发生冲突，并确认每个渠道只新增一条有效报价和一条批次明细。
15. 检查客户接口和页面不含供应商、上游来源、内部成本、令牌、原始媒体标识或运营备注。
16. 如启用 GEO 联动，验证签名、身份隔离、快照哈希、JTI 重放保护、订单幂等冲突、报价失效、内部字段拒绝和 429 限流。
17. 验证 GEO 回调只发送签名事件，失败重试与死信可监控，并确认回调地址使用已批准的 HTTPS 域名。
18. 在管理员“接口管理”逐项核对 28 项必备证据；任何一项未核验、外部生产连接不满足约束或历史组合记录仍待决定时，相应总关卡必须保持待验收。
19. 对供应商订单验证“履约未确认”不能进入已提交状态；人工/API 提交必须附可追溯凭据，API 提交还须有外部订单号和已验收生产连接。

## 既有数据库升级

Compose 对数据库初始化脚本的挂载只会在新建数据卷时执行；已有生产库不会因重建应用容器而自动补齐迁移。上线前必须在备份、维护窗口和受控环境验证后，由数据库管理员按顺序执行：

`database/21-manual-media-invitation-pending-verification.sql`、`database/22-service-intake-title-integrity.sql`、`database/23-settlement-transaction-ledger.sql`、`database/24-requirement-idempotency.sql`、`database/25-task-acceptance-integrity.sql`、`database/26-channel-quote-integrity.sql`、`database/27-media-invitation-progress-integrity.sql`、`database/28-publish-task-terminal-integrity.sql`、`database/29-publish-plan-idempotency.sql`、`database/30-settlement-transaction-idempotency.sql`、`database/31-batch-quote-adjustment-idempotency.sql`、`database/32-publish-plan-service-integrity.sql`、`database/33-supplier-api-connections.sql`、`database/34-open-api-management.sql`、`database/35-release-governance-and-evidence.sql`、`database/36-schema-migration-ledger.sql`、`database/37-media-pr-result-integrity.sql`、`database/38-writing-assignment-slot-schedule-integrity.sql`、`database/39-writing-assignment-radius-integrity.sql`、`database/40-conference-work-item-state-integrity.sql` 和 `database/41-conference-media-candidate-state-integrity.sql`。

完成后，公共健康接口必须返回 `schemaStatus=UP`；数据库管理员须在受控会话中核验 `schema_migration_ledger` 已记录迁移 `41`。迁移 40 遇到历史状态与完成时间矛盾时会停止；迁移 41 遇到候选状态、邀请／回复时间线或结果说明矛盾时会停止。数据库管理员不得用当前时间、推测值或空泛备注补齐，须由业务方先提供可核验依据。

迁移 21 只允许人工媒体邀请名单在“待项目核验”阶段不绑定执行渠道。它不导入媒体目录、报价或供应商，不生成供应商订单，也不使外部媒体数据变成已验收能力。迁移 22 只修复纯问号的服务受理标题并建立标题完整性约束，不会恢复或外推任何原始业务资料。迁移 23 只建立交易事实台账与凭据约束，不生成收款、退款、调整或核销记录；存量交易如需补录，必须逐笔核验后由管理员登记。迁移 24 只增加客户需求请求标识和摘要约束，不为历史需求伪造标识，也不改写项目、任务、订单、价格或履约状态。迁移 25 只建立状态和成果链接完整性约束，不生成成果或客户验收记录；运行时仍须通过服务端锁与权限校验完成验收。迁移 26 只验证并约束渠道报价，不调整金额、不选择供应商，也不把静态报价解释为实时可履约价格。迁移 27 只对齐已有邀约进度与任务终态，并关闭已完成的服务受理阶段；它不生成联系、回复、到场或报道事实。迁移 28 只建立任务终态保护并校验已验收任务具备核验成果，不生成任何新业务事实。迁移 29 只建立发布计划请求幂等约束，不为历史计划伪造标识，也不生成媒体选择、任务、订单、价格、供应商或履约事实。迁移 30 只建立结算交易请求幂等约束，不为历史交易伪造标识，也不生成收款、退款、调整、核销、发票或履约事实。迁移 31 只建立批量调价请求批次、摘要、状态和明细关联，不为历史调价伪造批次，不新增或改写客户价、成本价、供应商和外部渠道数据。迁移 32 只建立发布计划与服务类型的一致性保护，不改写历史错配记录，也不新增任务、订单、媒体、价格或供应商事实。迁移 33 只建立接口配置、验收门禁和历史组合审核记录；它不保存密钥、不发起外部请求、不启用真实履约，也不改写历史业务记录。迁移 34 只建立开放 API 应用、密钥哈希、受理回执和访问摘要结构；它不导入旧工具数据、不签发密钥、不发起第三方请求，也不改写既有项目、任务、订单或历史组合服务。迁移 35 只建立逐项证据、履约凭据和历史组合变更保护；它不证明外部授权已经取得，也不自动改变任何历史业务决定。迁移 36 只建立追加式结构台账基线，不回填旧迁移或业务事实。迁移 37 只验证媒体成果事实链；如完成态媒体任务缺少已核验成果、已发邀请或已报道记录，脚本会停止，不会补造或改写任何业务事实。迁移 38 只把已有真实写手信息映射为名额并新增档期冲突保护；当活跃派单缺少真实写手、服务时间，或历史多人接单无法无损还原时，脚本会停止，禁止自动补写、拆分或删除。迁移 39 将已配置的写手服务半径落实为活跃名额约束；半径已配置时，活跃名额必须具有人工核验距离且不得越界。它不定位、不推算、不填补历史距离，也不自动修改订单、价格、档期或履约事实。
