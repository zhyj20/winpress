# GitHub 公开源码可复现性复核（2026-08-02）

适用范围：当前工作分支 `agent/marketing-ui-and-media-boundary` 的公开源码候选。本文只记录本机隔离校验，不代表外部媒体数据、供应商履约或真实生产环境已经验收。

## 结论

公开源码可以在不提供媒体目录、渠道报价、测试账号文档、供应商信息、附件原件、令牌或生产凭据的前提下完成数据库结构初始化与隔离冷启动。公开样例仅验证导入结构；不构成实时媒体或记者数据接入，也不构成真实报价能力。

## 本次校验

| 检查 | 结果 | 核验结果 |
| --- | --- | --- |
| 干净数据库初始化 | 通过 | `PUBLIC_HEADERS_ONLY`；53 张表、4 条内置渠道结构样例、3 条内置报价结构样例；未导入外部媒体数据。 |
| 四项服务结构 | 通过 | 服务受理任务、任务验收、发布计划、云采写档期、发布会工作项、候选名单及结果保护结构均可初始化。 |
| 隔离冷启动 | 通过 | 输出 `external_media_data=not_asserted`；未把表头样例误表述为实时检索或外部数据能力。 |
| 临时资源回收 | 通过 | 复核后无 `winpress-clean-db-*` 或 `winpress-public-source-coldstart-*` 容器残留。 |
| 生产前端构建边界 | 通过 | 生产构建未发现本机测试身份、测试账号或密码选择标记。 |

## 执行命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-clean-local-db.ps1 `
  -MediaChannelsCsv database/media_channels.example.csv `
  -MediaQuotesCsv database/media_quotes.example.csv `
  -SkipAccountHashVerification

powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-cold-start.ps1 `
  -Container winpress-public-source-coldstart-20260802 `
  -MediaChannelsCsv database/media_channels.example.csv `
  -MediaQuotesCsv database/media_quotes.example.csv
```

## 公开仓库边界

- 可以提交源码、数据库结构/迁移、公开表头样例、脱敏示例、构建与校验脚本、`.env.example` 和已核准的文档。
- 不提交测试账号清单、真实媒体目录或报价、供应商成本/订单、外部接口令牌、合同、客户附件原件、生产 `.env`、证书、密钥、运行日志、数据库备份或会话数据。
- `docs/TEST-ACCOUNTS.md` 为本机受控文档，已由 `.gitignore` 排除；本记录不包含任何账户密码或哈希校验结果。

## 仍待有权主体验收

正式外部数据授权、数据范围、令牌、限流、真实检索；供应商真实下单、回执、对账和异常处理；主体信息、法律文本、案例授权；以及生产预发布的 TLS、域名、监控、网络权限、备份与迁移演练，均不在本次结论内。
