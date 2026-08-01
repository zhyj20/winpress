# 外部媒体检索能力级降级 QA（2026-08-02）

## 本轮问题与根因

当外部媒体数据的上线关卡已通过、但运行环境仅登记了“媒体检索”或仅登记了“记者检索”其中一项时，服务层过去只判断“至少有一种检索能力”。另一类检索或分类资料请求会继续进入适配器，并把上游的“未配置”提示返回给客户。

这会让同一处未验收能力出现两种不一致的对客文案，且后者没有明确保留人工补充候选名单的路径，不符合 [`PRODUCT-PRD-V4.2.md`](PRODUCT-PRD-V4.2.md) 对外部媒体数据的降级边界。

## 已实施修复

- `NiumediaMediaService.search` 按本次请求的目标分别检查媒体检索或记者检索能力；
- `NiumediaMediaService.taxonomy` 单独检查分类资料能力；
- 任一目标能力、分类资料能力或外部数据治理关卡未满足时，统一返回 `MEDIA_DISCOVERY_UNAVAILABLE`，并说明“可提交需求，由项目负责人补充并核验候选名单”；
- 在拒绝前不调用上游客户端，避免产生无意义的外部请求、配置探测或不同错误口径；
- 已增加“仅媒体检索可用时请求记者”和“分类资料未配置”两项单元回归。

## 修改文件

- `backend/src/main/java/com/winpress/commercial/service/NiumediaMediaService.java`
- `backend/src/test/java/com/winpress/commercial/service/NiumediaMediaServiceTest.java`

## 数据与回滚边界

本轮没有数据库迁移、没有写入媒体、记者、供应商、项目或订单记录，也没有发起上游请求。回滚仅涉及上述 Java 服务与测试文件；不得以删除、拆分或重建历史业务记录作为回滚方式。

## 验证证据

在项目根目录 `E:\Codex\Projects\软件开发与运维\.winpress-media-integration-20260726` 执行：

```powershell
cd backend
.\mvnw.cmd -q -Dtest=NiumediaMediaServiceTest test
.\mvnw.cmd -q test
.\mvnw.cmd -q -DskipTests package

cd ..\frontend
npm.cmd run check
npm.cmd run build
npm.cmd run test:e2e
```

结果：

- 定向 `NiumediaMediaServiceTest` 通过；
- 后端 Surefire：`174` 项，`0` failure、`0` error、`0` skipped；
- 前端类型检查、格式检查和生产构建通过；
- Playwright：桌面 1440×900、平板 768px、移动 390×844 共 `117/117` 通过；其中覆盖未验收媒体资料的人工补充路径、四项独立服务、三类角色、订单与结算边界；
- 后端容器更新后，针对“未验收媒体资料时，客户媒体邀请保留人工补充与核验路径”的 Playwright 三视口回归为 `3/3` 通过；
- 本机 Docker 后端使用 `scripts/prepare-local-docker-backend.ps1` 重新生成 `target-local` 产物后再构建；容器内 `/app/app.jar` 与主机产物的 SHA-256 一致，避免用旧运行镜像替代本次验证；
- `scripts/verify-local-p0p1.ps1` 通过：匿名开放 API 被拒绝、三类角色登录及组织隔离正确、客户不能访问供应商/成本/接口配置、管理员仅见脱敏配置、外部适配器未验收时保持不可用和人工路径；
- `http://127.0.0.1:8192/api/v1/health` 返回 `200`，应用、数据库和结构状态均为 `UP`；前端 `http://127.0.0.1:5217/` 保持可访问。

## 仍未满足的上线门禁

本修复不证明牛媒或任何外部媒体数据已经获得授权、令牌可用、实时可查或可向客户展示。真实启用前仍须逐项取得书面授权、数据范围、生产凭据、限流策略、沙箱联调、异常与回执验证、生产验收记录，并由管理员完成对应上线关卡。未满足时，客户侧只能保留本修复后的安全降级与人工补充路径。
