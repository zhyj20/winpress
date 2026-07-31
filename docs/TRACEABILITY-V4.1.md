# 云发布 V4.1 需求追踪矩阵

> 历史追踪矩阵（非当前验收基准）。V4.2 的有效需求、实现状态与运行证据分别以 [`PRODUCT-PRD-V4.2.md`](PRODUCT-PRD-V4.2.md)、[`WINPRESS-GLOBAL-PRODUCT-CODE-AUDIT-20260728.md`](WINPRESS-GLOBAL-PRODUCT-CODE-AUDIT-20260728.md) 和 [`P0-P1-EXECUTION-QA-20260728.md`](P0-P1-EXECUTION-QA-20260728.md) 为准。

| 编号 | 产品要求 | 页面/组件 | API | 数据对象 | 验收证据 |
|---|---|---|---|---|---|
| WP-P0-001 | 发布计划保存后不立即生成任务 | `ChannelsView` 计划确认弹窗 | `POST /projects/{id}/publish-plans` | `publish_plan`、`publish_plan_item` | 草稿创建测试、任务数不变 |
| WP-P0-002 | 客户确认后才拆分任务，且重复确认幂等 | `ChannelsView` 二次确认 | `POST /publish-plans/{id}/confirm` | `publish_task.publish_plan_item_id` | 接口测试、唯一约束 |
| WP-P0-003 | 云采写保存980元价格快照 | 创建需求、项目详情 | `POST /requirements` | `service_price_book`、`customer_requirement` | 服务价格测试 |
| WP-P0-004 | 写手派单、接单和拒单可追踪 | 写手任务台 | 写手派单与响应接口 | `writer_profile`、`writing_assignment` | 状态迁移测试 |
| WP-P0-005 | 客户接口不暴露供应商和成本 | 媒体库、任务列表 | `/channels`、`/publish-tasks` | 字段投影 | 角色接口断言 |
| WP-P0-006 | 成本价和客户价可分别人工调整 | 报价与比价 | `POST /admin/pricing/quotes` | `channel_quote`、`quote_adjustment` | 前端字段与接口测试 |
| WP-P0-007 | 官网按客户状态提供四项入口 | `HomeView` | 无 | 首页内容模型 | 桌面/移动截图 |
| WP-P0-008 | 新闻发布会按专业清单推进 | 项目详情 | 发布会事项接口 | `conference_work_item` | 清单结构与状态测试 |
| WP-P0-009 | 未授权品牌不冒充客户案例 | `HomeView` 案例区 | 无 | 内容治理 | 文案扫描 |
| WP-P0-010 | 关于我们拥有可到达页面 | Header、AboutView | 无 | 无 | 路由与导航测试 |
| WP-P0-011 | 普通媒体邀请可从活动简报开始，不强制要求定稿 | `ChannelsView` 媒体邀请计划 | 发布计划创建与确认接口 | 可空稿件引用、`media_pr_invitation` | 无稿件媒体邀请测试、迁移 12 |

## 验收纪律

每项标记完成前必须同时具备源码、接口或页面、自动检查以及运行证据。仅有设计稿、SQL 文件、构建成功或演示数据，不等于业务闭环已经完成。
