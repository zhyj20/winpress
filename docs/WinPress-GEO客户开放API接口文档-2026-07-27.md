# WinPress 云发布 GEO 服务端联动接口

版本：v1.1 实现核对版  
核对日期：2026-07-29  
适用对象：牛媒信源 GEO 平台后端与云发布后端的联合开发、测试和运维人员

> 当前状态：三项服务端接口已在源码和本机环境实现；生产域名、共享密钥、回调地址、调用额度和联合验收尚未确认。本文档不是已经开通的客户凭据，也不表示生产接口已经上线。

## 1. 接口边界

本接口用于牛媒信源 GEO 平台把已经确认的客户需求交给云发布履约。它不是浏览器接口，也不是面向普通客户公开申请的 API。

已实现的能力：

- 查询四项服务的客户报价；
- 获取当前可选的直编发稿渠道及客户价；
- 提交云采写、媒体邀请、直编发稿或新闻发布会订单；
- 为已接收订单生成云发布项目、任务或发稿计划；
- 通过签名回执和回调事件返回受理结果与履约状态。

未实现或尚未验收的能力：

- `X-API-Key` 客户开放平台；
- `/openapi/v1/media`、文件上传、订单查询和结果查询等公开接口；
- 生产域名、生产共享密钥、正式回调地址和合同调用额度；
- 外部媒体目录、供应商实时联动及生产价格有效性。

上述未验收能力不得作为已上线功能向客户承诺。

## 2. 地址与传输

| 环境 | Base URL | 状态 |
|---|---|---|
| 本机联调 | `http://127.0.0.1:8192/api/v1` | 可验证 |
| 生产规划 | `https://winpress.cn/api/v1` | 待部署与联合验收 |

所有业务请求均为 `POST application/json`。生产环境只允许 HTTPS。接口只接受服务端调用，签名密钥不得写入 Vue 前端、浏览器存储、日志或客户可下载文件。

## 3. 签名断言

请求体中的 `assertion` 是 HMAC-SHA256（`HS256`）签名的紧凑 JWT。共享密钥长度不得少于 32 字节。

通用声明：

| 声明 | 说明 |
|---|---|
| `iss` | GEO 平台签发方；默认 `niumedia-platform`。 |
| `aud` | 订单为 `winpress-commercial-federated-orders`；报价为 `winpress-commercial-federated-quotes`。 |
| `direction` | 订单为 `geo_to_winpress_order`；报价为 `geo_to_winpress_quote`。 |
| `iat` / `exp` | 有效期不超过 120 秒；服务端允许的未来时钟偏差不超过 30 秒。 |
| `jti` | 每次调用唯一；订单断言会做重放保护。 |

订单断言还必须携带：

`tenant_id`、`organization_id`、`brand_id`、`project_id`、`order_id`、`service_type`、`event_id`、`snapshot_hash`。

报价断言还必须携带：

`quote_request_id`、`tenant_id`、`organization_id`、`brand_id`、`project_id`、`service_type`。

断言内的身份字段必须与请求对象逐项一致。订单中的 `snapshot_hash` 为快照 JSON 递归按字段名排序、保留数组顺序后的 SHA-256 十六进制值。

## 4. 通用响应

成功：

```json
{
  "success": true,
  "code": "OK",
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-07-29T10:00:00+08:00"
}
```

失败：

```json
{
  "success": false,
  "code": "FEDERATION_ASSERTION_INVALID",
  "message": "联邦签名无效",
  "data": null,
  "timestamp": "2026-07-29T10:00:00+08:00"
}
```

默认限流为同一服务来源每分钟 120 次，部署时可在 10 至 10,000 次之间配置。超限返回 HTTP 429。

## 5. 查询服务报价

`POST /integrations/geo/quotes`

请求包：

```json
{
  "assertion": "SIGNED_ASSERTION_FROM_GEO_SERVER",
  "request": {
    "quote_request_id": "quote-20260729-001",
    "tenant_id": "tenant-a",
    "organization_id": "org-a",
    "brand_id": "brand-a",
    "project_id": "project-a",
    "service_type": "ONSITE_WRITING",
    "service_details": {
      "service_days": 1,
      "writer_count": 1
    }
  }
}
```

服务类型与报价方式：

| `service_type` | 报价结果 |
|---|---|
| `ONSITE_WRITING` | 按当前云采写客户单价 × 天数 × 写手人数计算。 |
| `DIRECT_PUBLISHING` | 按 1 至 30 个当前有效渠道客户价合计。 |
| `MEDIA_PR` | 返回 `MANUAL_QUOTE_REQUIRED`。 |
| `NEWS_CONFERENCE` | 返回 `MANUAL_QUOTE_REQUIRED`。 |

直编发稿的 `service_details.channel_selections` 每项须包含 `channel_id`，不得重复。成功响应的 `data` 包含 `quote` 与云发布签发的 `receipt`。

## 6. 获取直编发稿可选渠道

`POST /integrations/geo/catalog/direct-publishing-offers`

请求包：

```json
{
  "assertion": "SIGNED_ASSERTION_FROM_GEO_SERVER",
  "request": {
    "quote_request_id": "catalog-20260729-001",
    "tenant_id": "tenant-a",
    "organization_id": "org-a",
    "brand_id": "brand-a",
    "project_id": "project-a",
    "service_type": "DIRECT_PUBLISHING",
    "limit": 40
  }
}
```

`limit` 为 1 至 100。系统只返回状态有效且客户报价仍在有效期内的渠道，字段限于：

- `channel_id`
- `channel_name`
- `customer_amount`
- `currency`
- `valid_until`

不返回供应商、成本价、毛利、上游订单号、内部备注、令牌或密钥。生产环境在外部媒体目录和价格完成验收前可能返回空目录，不能用本机演示数据替代。

## 7. 提交联邦订单

`POST /integrations/geo/orders`

请求包：

```json
{
  "assertion": "SIGNED_ASSERTION_FROM_GEO_SERVER",
  "snapshot": {
    "contract_version": "1.0",
    "event_type": "WinpressFederatedOrderRequested",
    "event_id": "event-20260729-001",
    "tenant_id": "tenant-a",
    "organization_id": "org-a",
    "organization_name": "示例组织",
    "brand_id": "brand-a",
    "project_id": "project-a",
    "order_id": "order-20260729-001",
    "confirmed_by": "customer-user-a",
    "service_type": "NEWS_CONFERENCE",
    "title": "新品发布会",
    "objective": "",
    "service_details": {
      "contact_name": "项目联系人",
      "contact_mobile": "13800000000"
    }
  }
}
```

四项服务必须分别提交，不接受“现场采写 + 媒体邀请”等组合服务类型。

| 服务 | `service_type` | 进入云发布后的对象 |
|---|---|---|
| 云采写 | `ONSITE_WRITING` | 需求、项目、采写任务、待匹配写手记录 |
| 媒体邀请 | `MEDIA_PR` | 需求、项目、媒体邀请工作范围 |
| 直编发稿 | `DIRECT_PUBLISHING` | 需求、项目、已核定稿件、发布计划、渠道任务 |
| 新闻发布会 | `NEWS_CONFERENCE` | 需求、项目、发布会项目及九项履约清单 |

共同必填字段：

`contract_version`、`event_type`、`event_id`、`tenant_id`、`organization_id`、`brand_id`、`project_id`、`order_id`、`confirmed_by`、`service_type`、`title`、`service_details`。

条件要求：

- 云采写可传 `service_days`、`writer_count`、`event_location`，写手按就近可用原则进入匹配；
- 新闻发布会的 `service_details.contact_name` 和 `contact_mobile` 必填，其余项目资料可以后补；
- 直编发稿必须携带 `authorization_reference`、当前有效的 `channel_selections`，以及 `delivery_package.immutable_snapshot.approved_content.content`；
- 直编发稿使用创建时的客户报价快照，价格失效时拒绝创建；
- `supplier`、`cost`、`margin`、`upstream`、`secret`、`token`、`api_key` 等内部字段会被递归拒绝。

成功响应的 `data` 包含：

- `receipt`：云发布签发的受理 JWT；
- `idempotent`：是否返回已有受理结果；
- `order_id`；
- `winpress_requirement_id`；
- `winpress_project_id`。

同一 `order_id` 与同一快照重复提交会返回原受理结果；同一 `order_id` 使用不同快照返回 HTTP 409。

## 8. 回调事件

启用回调后，云发布向受控配置的 GEO 回调地址发送：

```json
{
  "assertion": "SIGNED_EVENT_FROM_WINPRESS_SERVER"
}
```

事件断言：

- `iss` 为云发布签发方，默认 `winpress-commercial`；
- `aud` 为 `niumedia-platform-federated-orders`；
- `direction` 为 `winpress_to_geo_order_event`；
- 业务字段包括平台订单标识、四级身份标识、服务类型、快照哈希、云发布项目与任务标识、当前状态和下一步事项。

GEO 回调必须返回：

```json
{
  "accepted": true
}
```

回调使用持久化 outbox、租约和重试；当前最多尝试 5 次。生产回调地址、TLS、网络白名单和告警仍需联合验收。

## 9. 错误码

| 错误码 | HTTP | 说明 |
|---|---:|---|
| `FEDERATION_UNAVAILABLE` | 503 | 联动未启用或密钥不符合要求。 |
| `FEDERATION_ASSERTION_INVALID` | 401 | 断言格式、签名、签发方、受众、方向或有效期无效。 |
| `FEDERATION_ORDER_INVALID` | 400 | 订单快照缺字段、服务类型无效或含内部字段。 |
| `FEDERATION_ORDER_UNAUTHORIZED` | 401 | 断言与快照身份或哈希不一致。 |
| `FEDERATION_ORDER_CONFLICT` | 409 | 幂等冲突、报价失效或无法创建履约对象。 |
| `FEDERATION_QUOTE_INVALID` | 400 | 报价参数或渠道选择无效。 |
| `FEDERATION_QUOTE_UNAUTHORIZED` | 401 | 报价断言与请求身份不一致。 |
| `FEDERATION_QUOTE_CONFLICT` | 409 | 报价缺失、失效或币种不一致。 |
| `FEDERATION_RATE_LIMITED` | 429 | 服务端调用频率超过配置上限。 |
| `INTERNAL_ERROR` | 500 | 未预期的服务错误。 |

## 10. 部署与验收

联动默认关闭。生产启用前必须依次完成：

1. 备份数据库并确认回滚点；
2. 设置独立的生产共享密钥、双方签发方、实例标识、HTTPS 回调地址和限流值；
3. 在受控维护窗口执行独立 `winpress_federation` schema 迁移；
4. 验证签名、身份隔离、快照哈希、重放保护、幂等冲突、报价失效、内部字段拒绝和 429 限流；
5. 验证四项服务分别创建正确的需求、项目、任务和订单对象；
6. 验证回调重试、死信处理、监控和告警；
7. 双方书面确认后再开放生产流量。

未完成上述步骤时，文档状态必须保持“待确认”，不得把本机通过等同于生产上线。
