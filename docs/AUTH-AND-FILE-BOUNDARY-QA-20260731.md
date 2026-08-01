# 认证、会话与附件边界 QA（2026-07-31）

## 本轮范围

本轮只审查并修补当前本机演示栈中认证、会话、项目权限和附件上传/下载的可验证边界。它不改变四项独立服务的下单、项目、任务或订单模型，不接通外部媒体数据或供应商履约。

## 审查结论与修补

| 边界 | 现状 | 本轮处理 |
|---|---|---|
| 登录与会话 | 已使用 BCrypt、Redis 失败冷却、会话代次失效及每次请求的账号有效性复核。 | 复核既有实现与回归，不改变对客登录流程。 |
| 项目附件权限 | 上传与下载均先校验当前用户对项目的查看范围。 | 增加运行时跨客户下载回归；拒绝发生在读取存储文件之前。 |
| 文件大小 | 原先只有 Spring 的 multipart 限制。 | 新增 `WINPRESS_STORAGE_MAX_FILE_BYTES`，默认 `20971520`；存储层同时检查声明大小与实际输入流，超限返回 `413 / FILE_TOO_LARGE`，并删除部分落盘文件。 |
| 文件名与类型 | 已校验扩展名与常见文件签名。 | 拒绝空值、控制字符、非法路径和超过数据库字段长度的文件名；下载时将历史或异常 MIME 类型回退为二进制附件。 |
| 下载响应 | 已经使用 `Content-Disposition: attachment`。 | 增加 `Cache-Control: no-store, private` 与 `X-Content-Type-Options: nosniff`，防止受权项目资料被缓存或按可执行内容内联解释。 |
| 框架异常 | 超出 multipart 限制可能落入通用异常。 | 为 `MaxUploadSizeExceededException` 增加统一 JSON 响应 `413 / FILE_TOO_LARGE`。 |

## 本机验证证据

在 `E:\Codex\Projects\软件开发与运维\.winpress-media-integration-20260726` 执行：

```powershell
backend\mvnw.cmd -q test
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\prepare-local-docker-backend.ps1
docker compose -f docker-compose.local-demo.yml build backend
docker compose -f docker-compose.local-demo.yml up -d --no-deps backend
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-local-p0p1.ps1
```

结果：

- 后端 Maven 测试通过。
- 本机后端健康检查返回数据库 `UP`、结构版本 `36`、接口契约 `winpress-v4.2.25-20260731`。
- 回归脚本通过附件匿名拒绝、跨客户上传拒绝、跨客户下载拒绝、项目附件元数据脱敏、附件下载私有且禁止缓存、四项独立服务和角色权限边界等检查。
- 新增单元回归覆盖异常文件名、伪报文件大小、超限输入流的部分文件清理、实际落盘大小、下载 MIME 回退、下载安全响应头、上传拒绝先于存储写入以及框架级超限响应。
- 前端 `npm.cmd run check` 与 `npm.cmd run build` 通过；Playwright 桌面端和移动端共 `44/44` 通过，覆盖营销页面、案例、接口管理、发布会日程和直编筛选等既有路径。
- `verify-local-database-backup-restore.ps1` 与 `verify-local-upload-backup-restore.ps1` 通过，分别在隔离数据库容器和隔离 Docker 卷中复原并比对结构/业务汇总与附件哈希/属性；`verify-production-compose-boundaries.ps1` 通过，确认生产编排未引入演示输入或未验收外部数据。
- `verify-production-cold-start.ps1` 通过：在空库、随机临时凭据、回环端口和显式 HTTPS CORS 下完成结构版本 `36`、接口契约 `v4.2.25`、私有接口文档、受保护集成和 980 元公开采写价格的验证，并自动删除临时容器、卷、镜像和凭据。首次演练暴露冷启动临时环境缺少附件上限变量；已补齐生成逻辑及旧状态文件的兼容清理，再次演练通过。

## 未覆盖与上线门禁

- 本地文件存储适配器不等于生产级恶意文件扫描、内容净化、加密密钥托管或对象存储生命周期策略；这些能力应在主体方确认后进入独立的生产验收。
- 生产反向代理、WAF、对象存储和备份系统的请求体限制必须与本配置一致；本机容器通过不代表生产网络、TLS、监控、告警、恢复时限或异地副本已验收。
- 客户资料、附件原文、密钥、供应商信息和外部媒体数据不得写入 QA 输出；本记录只保留机制与结果，不包含样本内容或凭据。

## 2026-08-01 公共健康信息边界

- 匿名 `GET /api/v1/health` 现只返回服务、数据库与结构就绪状态；不再回显结构版本、接口合同、构建提交、构建时间或通用版本号。
- Docker 与监控仍可使用 HTTP 状态及 `status`、`database`、`schemaStatus` 判断可用性。精确迁移版本和接口合同改由受控数据库台账、源码构建与发布证据核验，不再作为公开接口契约。
- 先将 `HealthControllerTest` 改为断言上述字段不存在，旧实现产生两项失败；删除公开回显后，专项测试通过。历史记录中出现的结构版本和接口合同属于当时本机验收快照，不代表当前公共响应。
- 运行态复测的 HTTP `200` 响应只含 `status,database,schemaStatus`，三项均为 `UP`；完整 Java 测试 `172/172`、Java 打包、Vue 类型/格式检查与生产构建均通过。关键浏览器回归覆盖媒体邀请安全降级、发布会三项首要资料和订单筛选历史同步，在桌面、平板、移动端共 `9/9` 通过。
- `verify-local-p0p1.ps1`、隔离数据库恢复、隔离上传卷恢复、干净本机数据库初始化和生产 Compose 静态边界检查均通过。全量 Playwright `108` 项在当前终端运行器中被中断且未形成最终报告，不计为本轮通过；应由 GitHub CI 或稳定预发布执行器重新运行后留存最终结果。
- 本机演示环境的生产等价冷启动仍未重新取得完整通过输出；清理已验证，但正式预发布冷启动、TLS、域名、监控和生产恢复演练仍是上线门禁。
