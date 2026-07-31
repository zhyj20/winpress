# 开放 API 认证文档 QA（2026-07-31）

## 范围

本轮只核验并修复 Swagger/OpenAPI 文档中的认证标注，不新增客户能力、不签发密钥、不写入业务记录、不调用外部媒体或供应商接口。

## 问题与根因

`OpenApiConfig` 曾把登录会话 `bearerAuth` 设为全局认证要求。实际运行中，客户开放接口由 `X-WinPress-API-Key` 独立校验，平台管理员接口由登录会话校验，健康检查无需认证。全局标注会使客户系统在 Swagger 中看到错误的认证方式；把健康检查标成需要 API Key 也与实际行为不一致。

## 修复

- 移除 OpenAPI 全局认证要求。
- 为六个客户业务端点单独标注 `openApiKey`：服务目录、直编渠道目录与分类、提交需求、需求列表和单条需求查询。
- 为管理员开放 API 管理端点单独标注 `bearerAuth`。
- 健康检查保留无认证状态。
- 新增 `OpenApiDocumentationSecurityTest`，防止以后把后台会话、客户 API Key 和健康检查重新混淆。

## 运行时证据

本机 Docker 部署后，从 `http://127.0.0.1:8192/v3/api-docs` 读取规范并核验：

| 路径 | 文档认证 | 实际校验 |
|---|---|---|
| `GET /api/v1/open-api/health` | 无 | HTTP 200；返回 `AVAILABLE` 与 `PENDING_AUTHORIZATION` |
| `GET /api/v1/open-api/v1/services` | `openApiKey` / `X-WinPress-API-Key` | 无密钥 HTTP 401 |
| `GET /api/v1/admin/open-api` | `bearerAuth` | 平台管理员登录会话 |

外部媒体数据状态仍为 `PENDING_AUTHORIZATION`。本次没有把未授权的媒体或记者检索包装为实时能力。

## 构建与回归

- 定向认证文档与开放接口测试：7/7 通过。
- 后端完整测试：149/149 通过。
- 前端类型检查与格式检查：通过。
- 前端生产构建：通过（1766 个模块）。
- `scripts/verify-local-p0p1.ps1`：通过；新增的运行时规范检查确认客户端、管理员端和健康检查的认证要求分别正确。
- 本机 Docker 重新构建后，前端、后端、PostgreSQL、Redis 均为 healthy。

## 修改与回滚边界

修改文件：

- `backend/src/main/java/com/winpress/commercial/config/OpenApiConfig.java`
- `backend/src/main/java/com/winpress/commercial/controller/OpenApiClientController.java`
- `backend/src/main/java/com/winpress/commercial/controller/OpenApiAdminController.java`
- `backend/src/test/java/com/winpress/commercial/config/OpenApiDocumentationSecurityTest.java`
- `scripts/verify-local-p0p1.ps1`
- `docs/API.md`
- `docs/README.md`

未修改数据库结构、迁移、客户项目、任务、订单、访问密钥或外部连接。回滚仅需恢复上述 Java 与文档变更，再重新构建后端镜像；不涉及数据卷回滚。

## 未验收边界

- 客户应用、合同、授权、沙箱与生产批准仍需由平台管理员按现有门禁逐项留存证据。
- 正式 API Key 不得写入源码、文档、测试报告或浏览器页面。
- 外部媒体数据、供应商下单、回执、对账、超时和重试仍需取得书面授权并完成联合验收。
