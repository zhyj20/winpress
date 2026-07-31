# WinPress REST API

基础路径：`/api/v1`

除健康检查、登录、注册和签名的 GEO 服务端联动接口外，普通业务接口均需携带：

```http
Authorization: Bearer <session-token>
```

统一响应：

```json
{
  "success": true,
  "code": "OK",
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-07-15T10:00:00+08:00"
}
```

## 登录与账号

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| `POST` | `/auth/register` | 公开 | 注册客户账号，必填用户名、单位、联系人、手机号和邮箱 |
| `POST` | `/auth/login` | 公开 | 登录并取得 Redis 会话令牌；连续失败会按来源进行共享短时冷却 |
| `GET` | `/auth/me` | 已登录 | 当前账号、角色和权限 |
| `POST` | `/auth/logout` | 已登录 | 退出并撤销令牌 |

## 客户与项目

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| `GET` | `/dashboard` | 已登录 | 按角色返回项目、任务和成果数量 |
| `GET` | `/work-items` | 已登录 | 返回与传播看板“需要我处理”同口径的发布、云采写、发布会与授权工单待办 |
| `GET` | `/task-records` | 已登录 | 返回角色范围内的云采写、媒体邀请、直编发稿和发布会历史任务记录；不含供应商、成本或内部订单字段 |
| `GET` | `/order-records` | 已登录 | 返回角色范围内云采写、媒体邀请、直编发稿和新闻发布会的客户订单记录；支持 `serviceType`、`status`、分页；不含供应商、成本或上游标识 |
| `GET` | `/settlement-records` | 客户 | 返回当前客户、当前组织四项独立服务的现行结算记录；支持 `status`、分页；不返回供应商、成本、运营人、上游标识或内部对账信息 |
| `GET` | `/settlement-archive-records` | 客户 | 返回当前客户、当前组织旧版组合服务的只读结算归档；支持 `status`、分页；归档记录不计入当前待结账单 |
| `GET` | `/transaction-records` | 客户 | 返回当前客户、当前组织四项独立服务中已有凭据的收款、退款、调整和核销记录；支持 `transactionType`、`status`、分页 |
| `GET` | `/transaction-archive-records` | 客户 | 返回旧版组合服务交易事实的只读归档；不计入现行四服务交易台账，也不能继续登记或作废 |
| `POST` | `/requirements` | 客户 | 携带 `Idempotency-Key` 创建需求并生成项目 |
| `GET` | `/requirements` | 客户、平台运营 | 客户仅查看本人需求；平台运营可用于核查；发布运营从已授权项目查看履约信息 |
| `GET` | `/projects` | 已登录 | 分页、状态、关键词和 `serviceType` 筛选；客户仅获取项目名称、服务状态、稿件/任务/成果汇总及自身可核验字段 |
| `GET` | `/customer/approved-manuscripts` | 客户 | 返回当前客户、当前组织内可复制到新直编发稿项目的已确认稿件版本；不返回稿件正文、内部来源或供应商字段 |
| `GET` | `/projects/{projectId}` | 已登录 | 需求、发布会统筹清单、稿件版本、任务、文件、成果、监测和结算；客户响应不含负责人、内部预算、供应商、成本、上游候选标识、内部备注或运营执行信息 |
| `PATCH` | `/projects/{projectId}/conference` | 项目客户、发布运营、平台运营 | 分次补充发布会时间、地点、嘉宾、议程、场地、媒体方向和传播目标 |
| `POST` | `/files` | 已登录 | 上传项目材料，必须携带 `projectId`，单文件不超过 20 MB |
| `GET` | `/files/{fileNo}` | 已登录且具备项目范围 | 下载项目材料；后端再次校验项目归属并以附件方式返回 |

客户只能访问本组织项目；发布运营只能访问分配给自己的项目；平台运营可访问全部项目。上传接口不返回上传目录、物理存储键或公开文件 URL；`/files/` 不是静态下载路径。

`GET /settlement-records` 只展示四项独立服务已有结算记录的项目、应结金额、已确认金额、币种、到期/确认时间、发票信息和状态。旧版组合服务结算由 `GET /settlement-archive-records` 单独返回，仅作追溯，不作为当前待付款项，也不能在现行后台修改状态、发票或交易记录。

`GET /transaction-records` 与现行结算使用同一四服务边界；旧组合服务已有交易事实只由 `GET /transaction-archive-records` 返回。两个交易接口均采用客户字段白名单，不返回内部说明、登记人、作废人、作废原因、供应商、成本或上游标识。上述客户接口均不提供在线支付、退款、开票或外部对账操作；这些事项必须以双方确认的凭据和另行验收的流程为准。

`POST /requirements` 支持以下客户服务类型：

- `ONSITE_WRITING`：云采写现场服务。
- `MEDIA_PR`：媒体邀请。
- `DIRECT_PUBLISHING`：客户已有稿件，进入直编发稿流程。
- `NEWS_CONFERENCE`：创建新闻发布会项目，统一记录会务信息、媒体方向、现场素材和会后传播安排。

每次首次下单必须携带 16—80 字符的 `Idempotency-Key` 请求头，建议使用 UUID：

```http
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

网络超时后应使用原请求头和原请求体重试。同一客户组织、同一请求标识和同一内容只返回首次创建的项目，不会重复建立需求、项目、任务或订单；同一标识改用另一份请求内容会返回 `409 IDEMPOTENCY_KEY_REUSED`。缺少或格式错误分别返回 `400 IDEMPOTENCY_KEY_REQUIRED`、`400 INVALID_IDEMPOTENCY_KEY`。请求标识只用于下单防重，不能替代登录、权限或项目范围校验。

现场采写订单必须提交 `eventTime`、`eventLocation`、`serviceDays`、`writerCount`、`onsiteContactName` 和 `onsiteContactMobile`。后端固定使用 980 元/人/天计算 `estimatedAmount`，不接受客户端传入单价。媒体邀请须另行下单，不计入现场采写基础金额。

新闻发布会项目只要求提交 `title`、`conferenceContactName` 和 `conferenceContactMobile`。时间、地点、类型、形式、议程、场地和媒体方向均可在项目建立后逐步补充。创建后自动生成 9 项统筹清单：项目范围、议程与嘉宾、场地与动线、新闻材料、拟邀媒体、邀请与到场、现场接待与采写、会后发布、成果核验与复盘。媒体邀请、现场采写和直编发稿仍按各自服务范围单独确认。

`ONSITE_WRITING_AND_MEDIA_PR` 与 `INTEGRATED_PROJECT` 不再接受普通需求创建。API 接入属于企业级商务咨询和技术评估，不是日常自助服务类型。

`POST /requirements` 可选传入同一客户、同一组织且已可访问项目的 `relatedProjectId`，把一项独立服务关联到既有活动。关联只用于项目视图归组：每项服务仍分别生成需求、项目、任务和订单，分别计价；无权、跨组织、已失效或伪造的关联项目会被拒绝。

直编发稿可选成对传入 `sourceManuscriptId`、`sourceManuscriptVersionId`，仅可选择上述客户范围内、当前处于已确认状态的版本。系统会将该版本复制到新建的直编发稿项目，并保留受控来源关联；不会共享稿件记录，也不会合并原项目的任务、订单、价格或结算。未传来源时，客户仍可在新直编发稿项目中自行提交已有定稿。

## 公开商务咨询

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| `POST` | `/public/inquiries` | 公开 | 提交服务咨询、API 接入、媒体合作申请或商务合作意向 |

请求必须包含咨询类型、公司名称、联系人、有效中国大陆手机号、具体说明及隐私政策同意状态。API 接入和媒体合作申请只进入商务审核，不生成普通服务订单或供应商账号。平台会在 5 分钟内拦截相同手机号、咨询类型和内容的重复提交。

## 开放 API（受控接入）

本机 Swagger 仅用于受控验收：`/swagger-ui/index.html`。其中健康检查不需要密钥；六个客户业务端点使用 `X-WinPress-API-Key`；`/admin/open-api/**` 仍使用平台管理员登录会话。三类认证方式在文档中分别标注，避免把客户 API Key 误用于后台管理接口。

独立开发的 API 工具已迁入云发布的项目、任务和订单体系，基础路径为：

```text
/api/v1/open-api/v1
```

这组接口面向已完成商务确认、客户归属绑定和技术验收的企业系统；不是公开注册接口，也不是供应商履约接口。平台运营在“开放 API”管理页登记应用、限定范围、留存授权与验收依据后，才可以签发密钥。原始密钥只在签发时显示一次，平台不保存或回显其明文。

```http
X-WinPress-API-Key: <受控访问密钥>
```

| 方法 | 路径 | 所需范围 | 用途 |
|---|---|---|---|
| `GET` | `/api/v1/open-api/health` | 无 | 返回开放接口可用性与外部媒体数据的验收状态 |
| `GET` | `/api/v1/open-api/v1/services` | `SERVICE_CATALOG` | 读取四项可受理服务 |
| `GET` | `/api/v1/open-api/v1/direct-publishing/channels` | `DIRECT_CHANNEL_CATALOG` | 读取当前可对客展示的直编渠道目录 |
| `GET` | `/api/v1/open-api/v1/direct-publishing/taxonomy` | `DIRECT_CHANNEL_CATALOG` | 读取直编渠道筛选字典 |
| `POST` | `/api/v1/open-api/v1/requirements` | `REQUIREMENT_CREATE` | 创建一项独立服务需求，并进入标准项目链路 |
| `GET` | `/api/v1/open-api/v1/requirements` | `PROJECT_READ` | 查询本应用创建的受理记录 |
| `GET` | `/api/v1/open-api/v1/requirements/{externalRequestId}` | `PROJECT_READ` | 查询一项受理记录及对应项目状态 |

`POST /requirements` 必须提供调用方自己的 `external_request_id`。同一接入应用以相同标识和相同内容重复调用，只返回第一次受理的项目；相同标识不能换作另一份内容。接口只接受服务受理所需字段，拒绝供应商、成本、上游、密钥和内部备注等字段。它不会创建组合服务，也不会绕过标准的客户权限、项目、任务和订单记录。

成功受理与重复重放均使用平台统一响应信封；首次受理的 `idempotent_replay` 为 `false`，同标识、同内容重试为 `true`。回执只返回调用方可追溯的外部请求标识、服务类型、需求号、项目号、项目名称、项目状态和受理时间，不返回内部项目 ID、客户账号、供应商、成本、原始请求或密钥信息。

```json
{
  "success": true,
  "code": "OK",
  "data": {
    "external_request_id": "client-request-20260730-001",
    "service_type": "NEWS_CONFERENCE",
    "requirement_no": "REQ-...",
    "project_no": "PRJ-...",
    "project_name": "活动名称",
    "status": "PLANNING",
    "idempotent_replay": false
  }
}
```

常见受控错误包括 `OPEN_API_UNAUTHORIZED`（密钥无效、过期或未启用）、`OPEN_API_SCOPE_DENIED`（未获授范围）、`OPEN_API_RATE_LIMITED`（超过登记限额）、`INVALID_OPEN_API_REQUEST`（字段或必填规则不满足）与 `OPEN_API_IDEMPOTENCY_CONFLICT`（同一外部标识对应不同内容）。调用方应使用相同的 `external_request_id` 与同一业务内容重试，不能用内部字段补写或修改已受理请求。

新闻发布会的最小请求示例如下；其他三类服务仍执行各自在客户下单页面明确的必填规则：

```json
{
  "external_request_id": "crm-20260730-conference-001",
  "service_type": "NEWS_CONFERENCE",
  "title": "年度战略发布会",
  "conference_contact_name": "项目联系人",
  "conference_contact_mobile": "13800000000"
}
```

直编渠道目录可使用 `keyword`、`region`、`category`、`publish_form`、`min_price`、`max_price`、`max_days`、`link_support`、`link_type`、`news_source`、`entry_level`、`special_industry`、`weekend_policy`、`sort`、`page` 与 `page_size` 筛选。目录只提供当时可对客展示的参考信息，不构成媒体库存、价格有效性、排期或发布承诺。

开放 API 不提供媒体邀请或记者检索的第三方实时数据。外部媒体数据、供应商真实下单、回执、对账与异常重试均须在取得书面授权、字段映射、沙箱联调和验收记录后另行开通；在此之前，接口与页面只能显示待确认或人工补充路径。

## 稿件

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| `POST` | `/operator/projects/{projectId}/manuscripts` | 发布运营、平台运营 | 提交首稿或新版本 |
| `POST` | `/projects/{projectId}/customer-manuscripts` | 项目客户，仅直编发稿项目 | 提交客户已有定稿并直接标记为已确认版本 |
| `GET` | `/customer/approved-manuscripts` | 客户 | 读取可复制至新直编发稿项目的客户已确认版本清单 |
| `PATCH` | `/operator/projects/{projectId}/conference-work-items/{itemId}` | 发布运营、平台运营 | 更新发布会统筹事项状态与说明；请求须带当前读取到的 `expectedStatus`，已完成事项不可重新打开 |
| `POST` | `/manuscripts/{manuscriptId}/review` | 客户 | `APPROVE` 确认定稿，`RETURN` 退回修改 |

客户确认的版本写入 `approved_version_id`。发布计划只能引用该版本。

发布会统筹事项仅允许按 `待处理 → 进行中 / 需补充 / 受阻 / 已完成`，以及 `进行中、需补充、受阻` 之间的前进处理路径更新；不能退回待处理，`COMPLETED` 为终态。`NEEDS_INFO`、`BLOCKED` 必须填写说明。服务端行锁和 `expectedStatus` 会拒绝并发覆盖，返回 `CONFERENCE_WORK_ITEM_STATE_CHANGED` 时应刷新后重新核对。数据库也会拒绝绕过接口的非法回退或把未完成事项标记为完成的发布会项目。

客户已有稿件可在新直编发稿项目创建时从客户已确认版本复制，也可在项目建立后通过上述接口提交；两种方式都只作用于独立直编发稿项目，不会开放给现场采写、媒体邀请或新闻发布会项目，也不会替代云采写的撰写与修改流程。

## 渠道与发布

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| `GET` | `/channels` | 已登录 | 按类型、关键词、地区、分类、发布形式、价格区间、时效、链接要求和排序分页筛选渠道；目录用于方案与预算参考，不是实时媒体库存或发布承诺 |
| `POST` | `/projects/{projectId}/publish-plans` | 客户 | 使用必填请求头 `Idempotency-Key` 保存直编发稿或媒体邀请计划，状态为 `WAITING_CONFIRMATION`，不生成任务；相同标识和相同内容的重试返回原计划，相同标识改传其他内容返回冲突 |
| `GET` | `/projects/{projectId}/publish-plans` | 已登录 | 查看角色范围内的发布计划、金额快照和确认状态 |
| `POST` | `/publish-plans/{planNo}/confirm` | 客户 | 按公开计划编号确认计划并生成项目任务，计划状态进入 `CONFIRMED`，不表示媒体或渠道已经开始执行；响应只返回公开 `taskNos`，重复提交返回同一批公开任务单号，不重复下单。渠道可用性、稿件要求、价格与排期仍由平台核验 |
| `GET` | `/publish-tasks` | 已登录 | 角色范围内的发布任务；客户仅获取公开任务单号、项目、渠道、计划/实际时间、状态和更新时间，不返回内部任务主键 |
| `GET` | `/publish-tasks/{taskId}` | 发布运营、平台运营 | 内部执行任务详情 |
| `GET` | `/customer/publish-tasks/{taskNo}` | 客户 | 按公开任务单号读取客户范围内的任务详情；不返回稿件内部标识、执行备注、异常原因、负责人、供应商、成本或上游字段 |
| `PATCH` | `/operator/publish-tasks/{taskId}` | 发布运营、平台运营 | 更新执行中、需补充或异常状态；已完成或客户已验收的任务不可回退 |
| `PATCH` | `/operator/publish-tasks/{taskId}/media-invitation` | 发布运营、平台运营 | 仅在实际沟通发生后登记媒体邀请状态；状态变更必须附简要事实说明，已完成或已验收任务不可继续改写，不向客户返回该内部协同记录 |
| `POST` | `/operator/publish-tasks/{taskId}/results` | 发布运营、平台运营 | 回填可访问的 HTTP/HTTPS 成果链接并完成任务；每项任务只能完成一次，不能在客户验收后补写或替换结果 |
| `POST` | `/publish-tasks/{taskNo}/accept` | 客户 | 按公开任务单号验收已有核验成果的已完成任务；重复验收幂等返回 `CLIENT_ACCEPTED`，验收后不可回退 |

渠道类型：

- `MEDIA_PR`：向媒体和记者发送活动或议题邀请；媒体决定是否出席、采访和报道。
- `DIRECT_PUBLISHING`：按媒体、栏目、客户服务价和时效提交发布。

媒体邀请可以直接使用活动简报、新闻点和拟邀方向创建，`manuscriptId` 与 `versionId` 可同时不传。直编发稿必须绑定客户已确认的稿件版本。当前版本不提供线上排他邀约或稿件锁定下单；如存在另行约定的历史安排，由项目负责人依约处理，不作为客户自助入口。历史自有渠道记录仅归档保留，不向客户开放，也不能新建。

稿件编号必须成对提交：不能只传 `manuscriptId` 或只传 `versionId`。媒体邀请确认后，执行反馈进入任务管理；是否出席、采访或报道由媒体自主决定。成果提交、任务完成和客户验收均采用任务锁与条件更新，避免重复请求或并发操作制造两份结果、回退客户验收或覆盖已经确认的事实。

兼容路径 `POST /projects/{projectId}/publish-plan` 仍可保存计划，但已弃用，并且同样要求 `Idempotency-Key`；新前端和第三方接入应使用复数路径。发布任务的唯一可靠生成入口是计划确认接口。

## 云采写派单

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| `GET` | `/writing-assignments` | 发布运营、平台运营 | 查看本人或角色范围内的云采写派单；客户通过项目、任务记录和订单记录查看自身服务进度 |
| `GET` | `/admin/writers` | 平台运营 | 查看可派单写手、服务城市和可用状态 |
| `POST` | `/admin/writing-assignments/{assignmentId}/offer` | 平台运营 | 按就近优先规则向写手发出任务，保存距离、单价与金额快照 |
| `POST` | `/writing-assignments/{assignmentId}/respond` | 被派写手 | `ACCEPT` 接单或 `DECLINE` 拒单；拒单后回到待匹配状态 |

现场采写订单创建后生成 `WAITING_MATCH` 派单。980 元/人/天来自服务价目表并在下单、派单时保存快照；后续调价不追改历史订单。写手资料、履约评价、距离和内部调度信息不属于客户可见字段。

## 媒体候选检索与发布会拟邀名单

- `GET /api/v1/media-discovery/status`：仅返回客户当前可用的媒体、记者和筛选字典能力，以及通用的暂不可用状态；不返回 Token、上游地址、限流周期、运行配置、验收状态或供应信息。
- `GET /api/v1/media-discovery/taxonomy`：返回省市、媒体属性和媒体形态筛选项。
- `GET /api/v1/media-discovery`：检索媒体或记者候选。`target` 为 `MEDIA` 或 `REPORTER`；支持 `keyword`、`name`、`province`、`city`、`medium_type`、`media_type`、`mp_types`、`mp_type_group`、`reporter_type`、`platform`、`sort`、`field`、`workflow`、`page`、`pageSize`。检索记者时必须传入先前媒体检索结果返回的 `media_ref`（即候选的短时 `candidateKey`），不能直接传入上游媒体编号。
- `POST /api/v1/projects/{projectId}/conference-media-candidates`：将单个检索结果加入新闻发布会拟邀名单，保留用于兼容已有调用方。
- `POST /api/v1/projects/{projectId}/conference-media-candidates/batch`：一次加入 1 至 100 个发布会媒体或记者候选；重复候选不会重复写入。
- `PATCH /api/v1/operator/projects/{projectId}/conference-media-candidates/{candidateId}`：运营人员更新候选状态：`CANDIDATE`、`READY_TO_INVITE`、`INVITED`、`RESPONDED`、`DECLINED`、`ATTENDING`、`NOT_PROCEEDING`。

统一候选字段包括短时 `candidateKey`、`candidateType`、`displayName`、`reporterName`、媒体属性、省市、形态、分类、标签、综合分、报道数量、受众量和更新时间。上游媒体与记者编号、供应商处理备注，以及可能指向上游服务的图片 URL 不向浏览器返回；提交候选时服务端会解析该短时引用。媒体邀请计划可以包含多个不同候选，每个对象分别生成计划项和邀请任务；直编渠道仍按同一计划内渠道唯一处理。

媒体邀请任务可带入候选公开属性；联系人未知时允许留空，由项目执行阶段核实。任何候选结果都不返回上游 Token、接口地址、供应商信息、原始价格或内部规则。候选不能直接视为到场、采访或报道承诺。

未验收资料库不可用时，客户可人工补充媒体或记者名称。此类计划项的执行渠道为空、状态为“待项目核验”；保存或确认计划不会自动发出邀请、建立供应商订单、生成客户价格或记录媒体已确认参与。实际联系与回复只能由获授权项目人员在事实发生后记录。

## 平台运营（仅 `PLATFORM_ADMIN`）

以下 `/admin/*` 接口仅供平台管理员在受控后台使用。客户与发布运营账号不能访问报价成本、供应商、供应商订单、内部结算或操作日志；这些信息也不得经客户项目、订单、渠道目录或页面间接返回。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/admin/operators` | 可分配的写手或服务运营账号 |
| `PATCH` | `/admin/projects/{projectId}/assign` | 分配项目和未分配任务 |
| `GET` | `/admin/channels` | 分页查看和维护渠道资料 |
| `POST` | `/admin/channels` | 新增渠道和报价 |
| `PUT` | `/admin/channels/{channelId}` | 调整渠道资料和可用状态；报价调整使用独立报价接口 |
| `GET` | `/admin/pricing` | 按渠道资料、渠道状态和报价状态分页查看直编报价 |
| `GET` | `/admin/pricing/summary` | 获取有效、临期、失效和待报价数量 |
| `GET` | `/admin/pricing/compare?channelIds=1,2` | 对 2 至 5 个直编渠道比较客户服务价、时效和有效期 |
| `POST` | `/admin/pricing/quotes` | 单渠道人工调价，可同时维护成本价与客户服务价；客户服务价不得低于成本价 |
| `POST` | `/admin/pricing/adjustments` | 携带必填请求头 `Idempotency-Key`，对明确选择的直编渠道按比例批量调价，最多 200 项 |
| `GET` | `/admin/pricing/{channelId}/adjustments` | 查看该渠道最近 30 条人工调价记录 |
| `GET` | `/admin/suppliers` | 分页查看供应商及渠道、在途订单数量 |
| `GET` | `/admin/suppliers/options` | 获取可分配的供应商选项；传入 `channelId` 时仅返回已关联该渠道且状态可用的供应商，用于供应商订单分配 |
| `POST` | `/admin/suppliers` | 新建供应商 |
| `PUT` | `/admin/suppliers/{supplierId}` | 更新供应商资料与可用状态 |
| `GET` | `/admin/supplier-channels` | 查看供应商与直编渠道的执行关系 |
| `POST` | `/admin/supplier-channels` | 建立或更新供应商渠道关系 |
| `GET` | `/admin/supplier-orders` | 分页查看供应商订单、客户价、成本价和执行状态 |
| `GET` | `/admin/supplier-orders/summary` | 获取待提交、执行中、异常和毛利概览 |
| `GET` | `/admin/supplier-orders/{supplierOrderId}/history` | 查看该供应商订单的状态轨迹、时间、操作人和履约说明；不向客户或发布运营开放 |
| `PATCH` | `/admin/supplier-orders/{supplierOrderId}` | 更新供应商订单号、状态、备注或异常原因；重试时必须清空本次上游订单号和履约凭据，既有事实保留在状态轨迹中 |
| `GET` | `/admin/inquiries` | 分页查看商务咨询 |
| `PATCH` | `/admin/inquiries/{inquiryId}` | 记录已联系、处理说明或关闭咨询 |
| `GET` | `/admin/settlements` | 结算列表；旧版组合服务记录带只读归档标记 |
| `PATCH` | `/admin/settlements/{id}` | 更新现行四项服务的结算状态和发票信息；历史组合记录拒绝修改 |
| `GET` | `/admin/settlement-transactions` | 按结算单、交易类型和状态查询收款、退款、调整与核销台账 |
| `POST` | `/admin/settlements/{id}/transactions` | 携带必填请求头 `Idempotency-Key` 登记一笔有凭据的结算交易 |
| `POST` | `/admin/settlement-transactions/{transactionId}/void` | 填写原因后作废交易；原始记录和作废事实均保留 |
| `GET` | `/admin/users` | 分页查看账号 |
| `GET` | `/admin/roles` | 角色与权限清单 |
| `PATCH` | `/admin/users/{id}` | 变更角色或停用账号，并撤销原会话 |
| `GET` | `/admin/operation-logs` | 关键操作日志 |
| `GET` | `/admin/open-api` | 查看接入应用、密钥元数据、访问摘要与客户可选账号；不返回密钥明文或请求正文 |
| `POST` | `/admin/open-api/applications` | 登记开放 API 接入应用与验收状态 |
| `PUT` | `/admin/open-api/applications/{id}` | 更新应用范围、状态和验收依据 |
| `POST` | `/admin/open-api/applications/{id}/keys` | 为已启用应用签发一次性显示的访问密钥 |
| `POST` | `/admin/open-api/keys/{id}/revoke` | 撤销访问密钥 |

结算交易请求标识使用与客户下单相同的 16—80 字符格式，建议使用 UUID。网络超时后必须用原请求头和原请求体重试：同一结算单、同一标识和同一内容返回首次登记的交易，不会再次改变实收、退款或调整金额；同一标识携带不同金额、类型、时间或凭据会返回 `409 IDEMPOTENCY_KEY_REUSED`。请求标识不能替代管理员权限、结算单锁定、金额上限和凭据校验。

批量调价也必须使用 16—80 字符的请求标识。网络超时后应原样重发请求头和请求体；服务端会返回第一次完成的批次结果，不会以新价格为基数再次计算。渠道编号顺序不影响同一请求的识别；调价比例、截止时间、对客条款或原因发生变化时，不得沿用原标识，否则返回 `409 IDEMPOTENCY_KEY_REUSED`。批次状态或明细不完整时返回冲突并停止继续调价，不能用客户端重试补写价格事实。

供应商订单是平台内部履约台账，不构成已完成真实供应商联动的声明。直编发稿任务已关联供应商时，平台只能在该供应商订单处于 `COMPLETED` 且留有履约凭据后登记发布成果。异常重试回到 `PENDING_SUBMISSION` 时，当前订单号、凭据、提交说明和提交时间会从当前记录清除；上一尝试的受控说明仍留在管理员状态轨迹中。非接口履约不得填写上游订单号；真实接口订单号、报价、回执和对账仍须经逐家授权、字段映射、沙箱联调和验收后使用。

## GEO 服务端联动

以下接口只供受信任的 GEO 后端调用，不使用客户会话，也不提供浏览器侧 API Key：

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/integrations/geo/quotes` | 查询四项服务的客户报价或人工报价状态 |
| `POST` | `/integrations/geo/catalog/direct-publishing-offers` | 返回当前有效的直编渠道客户价目录 |
| `POST` | `/integrations/geo/orders` | 分别受理云采写、媒体邀请、直编发稿或新闻发布会订单 |

请求体携带短期 `HS256` 签名断言。订单接口同时校验租户、组织、品牌、项目、订单、事件、服务类型和快照哈希，并通过 JTI 与订单快照实现重放保护和幂等受理。接口按服务来源限流，默认每分钟 120 次；超限返回 `FEDERATION_RATE_LIMITED`。

联动默认关闭；生产域名、共享密钥、回调地址和调用额度尚待联合验收。完整契约见 `docs/WinPress-GEO客户开放API接口文档-2026-07-27.md` 与 `docs/winpress-geo-openapi-2026-07-27.yaml`。旧版设想中的 `/openapi/v1`、`X-API-Key`、公开媒体检索、文件上传和订单查询接口并未实现，不能作为生产能力使用。

## 对客字段边界

`GET /channels` 只返回渠道名称、分类、地区、发布形式、预计时效、客户服务价、报价有效期和对客说明。以下字段不会返回：

- 内部成本和毛利
- 数据来源和上游资源标识
- 供应商信息、密钥、令牌和原始响应
- 内部备注

## 主要错误码

| 错误码 | 含义 |
|---|---|
| `UNAUTHORIZED` / `SESSION_EXPIRED` | 未登录或会话过期 |
| `LOGIN_RATE_LIMITED` | 连续登录失败过多，当前来源处于短时冷却 |
| `FEDERATION_RATE_LIMITED` | GEO 服务端调用超过当前来源的每分钟限额 |
| `FEDERATION_UNAVAILABLE` | GEO 联动未启用或生产凭据尚未配置 |
| `FEDERATION_ASSERTION_INVALID` | GEO 联动断言的签名、受众、方向或有效期无效 |
| `FORBIDDEN` | 无权访问该项目或任务 |
| `VALIDATION_ERROR` | 表单字段缺失或格式错误 |
| `INVALID_REQUEST_PARAMETER` | 路径或查询参数类型不正确，例如本应为数字的项目编号无效 |
| `EVENT_TIME_REQUIRED` / `EVENT_LOCATION_REQUIRED` | 现场采写订单缺少日期或地点 |
| `INVALID_SERVICE_DAYS` / `INVALID_WRITER_COUNT` | 现场采写天数或写手人数超出范围 |
| `ARCHIVED_SETTLEMENT_READ_ONLY` | 旧版组合服务结算仅供查阅，不能在当前系统中修改 |
| `IDEMPOTENCY_KEY_REQUIRED` / `INVALID_IDEMPOTENCY_KEY` | 下单、保存发布计划、批量调价或登记结算交易缺少有效请求标识 |
| `IDEMPOTENCY_KEY_REUSED` | 同一请求标识被改用于另一份需求、计划、调价批次或结算交易内容 |
| `BATCH_ADJUSTMENT_INCOMPLETE` / `BATCH_ADJUSTMENT_STATE_UNAVAILABLE` | 批量调价批次或明细状态不完整，系统停止继续改价并要求运营核查 |
| `SETTLEMENT_TRANSACTION_EVIDENCE_REQUIRED` | 结算交易未填写凭据编号或客户可见说明 |
| `SETTLEMENT_NOT_CONFIRMED` / `SETTLEMENT_ALREADY_CLOSED` | 当前结算状态不允许登记该类交易 |
| `PAYMENT_EXCEEDS_OUTSTANDING` / `REFUND_EXCEEDS_PAID` / `ADJUSTMENT_EXCEEDS_OUTSTANDING` | 交易金额超过当前可登记范围 |
| `ONSITE_CONTACT_REQUIRED` / `ONSITE_MOBILE_INVALID` | 现场联系人信息缺失或手机号无效 |
| `CONFERENCE_CONTACT_REQUIRED` / `CONFERENCE_MOBILE_INVALID` | 新闻发布会缺少联系人或手机号格式不正确 |
| `INVALID_CONFERENCE_TYPE` / `INVALID_CONFERENCE_FORMAT` | 新闻发布会类型或举办形式不正确 |
| `MANUSCRIPT_NOT_APPROVED` | 稿件尚未确认定稿 |
| `MANUSCRIPT_REFERENCE_INCOMPLETE` | 只提交了稿件或版本编号之一，两者必须同时提交或同时省略 |
| `SOURCE_MANUSCRIPT_REFERENCE_INCOMPLETE` | 创建直编发稿项目时只提交了来源稿件或来源版本之一 |
| `SOURCE_MANUSCRIPT_NOT_APPLICABLE` | 非直编发稿服务尝试使用客户已确认稿件来源 |
| `SOURCE_MANUSCRIPT_NOT_AVAILABLE` | 所选来源不属于当前客户组织、并非当前已确认版本，或已不可用 |
| `DIRECT_PROJECT_REQUIRED` | 直编发稿计划未使用独立直编发稿项目 |
| `MEDIA_PR_EXCLUSIVE_NOT_AVAILABLE` | 当前版本不提供线上排他邀约或稿件锁定下单 |
| `MEDIA_INVITATION_NOTE_REQUIRED` | 媒体邀请实际沟通状态变更未附事实说明 |
| `MANUSCRIPT_LOCKED` | 当前稿件版本存在未完成的既有发布安排 |
| `PLAN_NOT_CONFIRMABLE` | 发布计划当前状态不能确认 |
| `PLAN_PRICE_EXPIRED` | 计划中的报价已经失效，须刷新后重新创建 |
| `PRICE_UNAVAILABLE` | 媒体暂无有效客户报价 |
| `PRICE_BELOW_COST` | 人工调价后的客户服务价低于成本价 |
| `INVALID_SUPPLIER_ORDER_TRANSITION` | 供应商订单状态跳转不符合执行顺序 |
| `SUPPLIER_ORDER_EXCEPTION_REQUIRED` | 将供应商订单标记为异常时未填写原因 |
| `SUPPLIER_ORDER_PENDING_CONTEXT_INVALID` | 异常重试回到待提交时仍携带上一次的订单号、履约凭据或提交上下文 |
| `SUPPLIER_EXTERNAL_ORDER_MODE_INVALID` | 人工履约或未确认履约错误填写了仅接口回执可用的上游订单号 |
| `SUPPLIER_FULFILLMENT_REQUIRED` | 已关联供应商的直编任务尚未完成履约登记或缺少凭据，不能提交客户可见成果 |
| `INQUIRY_MOBILE_INVALID` | 商务咨询手机号格式不正确 |
| `PRIVACY_CONSENT_REQUIRED` | 商务咨询未同意隐私政策 |
| `MEDIA_DISCOVERY_NOT_CONFIGURED` | 媒体数据服务尚未完成配置 |
| `MEDIA_DISCOVERY_AUTH_FAILED` | 服务端授权已失效 |
| `MEDIA_DISCOVERY_LIMIT_REACHED` | 媒体资料库正在限流；可等待冷却或人工补充拟邀对象 |
| `MEDIA_DISCOVERY_UPSTREAM_UNAVAILABLE` / `MEDIA_DISCOVERY_RESPONSE_INVALID` | 媒体数据服务暂时不可用或返回结构异常 |
| `INVALID_MEDIA_DISCOVERY_TARGET` | `target` 不是 `MEDIA` 或 `REPORTER` |
| `MEDIA_CANDIDATE_EXISTS` | 该媒体已在发布会拟邀名单中 |
| `TASK_NOT_ACCEPTABLE` | 任务尚未完成，不能验收 |
| `TASK_RESULT_REQUIRED` | 已完成任务缺少可核验成果，暂不能验收 |
| `TASK_FINALIZED` | 任务已经完成或由客户验收，不能回退到执行状态 |
| `TASK_ALREADY_ACCEPTED` | 客户已验收，不能再次提交成果 |
| `TASK_RESULT_ALREADY_SUBMITTED` | 该任务已经存在核验成果，不能重复提交 |
| `TASK_STATE_CHANGED` | 任务状态已被其他请求更新，客户端应刷新后再处理 |
| `INVALID_RESULT_URL` | 成果链接不是带有效主机名的 HTTP/HTTPS 地址 |
| `RESULT_TIME_INVALID` | 成果发布时间明显晚于服务器当前时间 |
| `FILE_TYPE_NOT_ALLOWED` | 上传文件类型不支持 |

本机演示可使用 Swagger UI：`/swagger-ui/index.html`（`/swagger-ui.html` 会重定向到该地址）。生产 Compose 固定关闭 Swagger 与 `/v3/api-docs`，这些地址在生产环境返回 `404`；对客 API 交付应使用经审批的独立契约文档，不能公开内部接口浏览器。
