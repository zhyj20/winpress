# 文档索引

> 当前唯一验收基准为 [`PRODUCT-PRD-V4.2.md`](PRODUCT-PRD-V4.2.md) 与 [`WINPRESS-GLOBAL-PRODUCT-CODE-AUDIT-20260728.md`](WINPRESS-GLOBAL-PRODUCT-CODE-AUDIT-20260728.md)。
> 本机运行态证据以 [`P0-P1-EXECUTION-QA-20260728.md`](P0-P1-EXECUTION-QA-20260728.md) 为准。旧版 PRD、阶段报告和历史 QA 只用于追溯，不能证明当前源码、外部数据、供应商履约或生产部署已经验收。

## 当前验收与交付

| 文档 | 内容 |
|---|---|
| `PRODUCT-PRD-V4.2.md` | 当前有效的产品定位、四项独立服务边界、核心流程与商用门槛 |
| `WINPRESS-GLOBAL-PRODUCT-CODE-AUDIT-20260728.md` | 当前全局产品与代码审查、修复项、遗留风险与验收依据 |
| `P0-P1-EXECUTION-QA-20260728.md` | 当前 P0—P1 修复、本机运行验证及外部依赖边界 |
| `SOURCE-PACKAGE-MANIFEST-20260728.md` | 当前受控源码归档范围、排除项与 SHA-256 校验方式 |
| `API.md` | REST API、鉴权、错误码与客户字段边界 |
| `DATABASE.md` | 当前 PostgreSQL 结构、数据边界、迁移和本机演示计数说明 |
| `DEPLOYMENT.md` | PostgreSQL、Redis、前后端和 Nginx 部署；生产仍须受控预发布验收 |
| `MIGRATION.md` | 存量数据迁移和媒体数据质量处理 |
| `HANDOFF.md` | 产品边界、代码模块、整合要点和交付顺序 |
| `COMMERCIAL-COPY-IA-QA-20260731.md` | 客户可见营销页标题、说明文字与旧站文章入口的商业文案收口记录 |
| `OPEN-API-DOCUMENTATION-QA-20260731.md` | 开放 API 与后台管理接口认证标注、运行时规范和回归记录 |
| `SUPPLIER-FULFILLMENT-CLOSURE-QA-20260731.md` | 供应商订单状态、异常重试、成果提交门禁与管理员履约轨迹的本机闭环回归记录 |

## 受控本机验收资料

| 文档 | 内容 |
|---|---|
| `TEST-ACCOUNTS.md` | 仅本机演示账号与角色验收路径；不进入生产或源码归档 |
| `DEPLOYMENT-QA-20260726.md` | 历史 Docker 基线；当前结果须以 P0/P1 执行记录为准 |
| `NIUMEDIA-MEDIA-SCREENING-QA-20260726.md` | 历史接口适配记录；正式授权、额度、许可与生产联调仍待确认 |

## 历史追溯（非当前验收）

| 文档 | 内容 |
|---|---|
| `PRODUCT-PRD-V4.1.md` | V4.1 阶段产品基线 |
| `TRACEABILITY-V4.1.md` | V4.1 需求追踪矩阵 |
| `TERMINOLOGY-AND-VISIBILITY-V4.1.md` | V4.1 术语与字段边界 |
| `COMMERCIAL-QA-REPORT.md` | V4.1 阶段性回归记录 |
| `COMMERCIAL-DELIVERY-QA-20260726.md` | 2026-07-26 阶段交付 QA |
| `V4.1-IMPLEMENTATION-REPORT.md` | V4.1 实施记录 |
