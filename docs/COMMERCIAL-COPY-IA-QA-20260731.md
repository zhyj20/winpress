# 商业文案与标题层级 QA（2026-07-31）

## 范围

本轮只审查客户可见的营销页面标题、说明文字与旧站文章入口，依据 `PRODUCT-PRD-V4.2.md` 的客户信息架构和当前公开服务边界执行。未改动需求、项目、任务、订单、价格、外部适配器、权限、数据库或接口合同。

## 发现与修复

| 类型 | 原问题 | 修复 |
| --- | --- | --- |
| 标题层级 | API 页将说明句“把传播项目接入现有业务系统”作为 H1。 | H1 收敛为“API 接入”，接入对象和验收条件保留为正文说明。 |
| 导航一致性 | 顶部频道使用“行业资讯”“方法论”，页面 H1 却使用“发布会文章与实务”“发布会项目管理”。 | 统一为“行业资讯”“发布会方法论”，减少客户在频道与页面之间的语义切换。 |
| 内部腔调 | “项目经验选读”“项目复盘选读”“会后复盘”等词更像内部总结，不能直接帮助客户判断页面内容。 | 改为“项目资料选读”“会后交付 / 项目记录”；原有材料来源和证据边界说明保留。 |
| 说明文字 | “逐项确认”类过程性表述占据正文。 | 改为“明确负责人和执行顺序”“核对事实、表达与版本”等可执行描述。 |

## 修改文件

- `frontend/src/views/ApiIntegrationView.vue`
- `frontend/src/views/InsightsView.vue`
- `frontend/src/views/MethodologyView.vue`
- `frontend/src/views/CasesView.vue`
- `frontend/src/marketing/content/caseStudy.ts`
- `frontend/src/marketing/content/home.ts`
- `frontend/tests/e2e/commercial-smoke.spec.ts`

## 验证证据

- `npm.cmd run check` 通过（Vue TypeScript 与 Prettier）。
- `npm.cmd run build` 通过，Vite 转换 1,766 个模块。
- 执行 `scripts/rebuild-local-demo.ps1`，前后端镜像已重建；前端、后端、PostgreSQL、Redis 容器均为 `healthy`。
- 文案专项 Playwright：桌面端、移动端 2/2 通过；新增防漂移断言后，完整 Playwright：62/62 通过，使用 1440×900 与 390×844 配置。
- `mvnw.cmd test`：148/148 通过。
- `scripts/verify-local-p0p1.ps1`：通过；涵盖四项独立服务、三角色权限、数据库就绪、字段边界、外部媒体降级和历史组合归档。
- 浏览器实际复核：行业资讯页桌面视口无横向溢出、无破图；API 接入页 390px 视口无横向溢出、可见图片 0 张失效、菜单触控高度 44px。
- 修改后对目标公共文案扫描 `KPI`、`客户卡点`、`项目动作`、`从哪里起价`、`真正跑起来`、`赋能`、`沉淀`、`闭环`、`抓手`、`AI 味`、`复盘` 等候选词，结果为 0。

## 数据与回滚边界

- 无数据库迁移、无数据写入、无配置变更。
- 回滚只需恢复上述前端文件和测试断言；不影响存量项目、订单、媒体资料、外部接口或容器数据卷。
- 外部媒体数据、供应商履约、法律主体、生产凭据、域名、TLS、监控与灾备门禁仍待外部验收；本轮不将其表述为已上线能力。
