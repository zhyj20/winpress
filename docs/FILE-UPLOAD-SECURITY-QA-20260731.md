# 文件上传安全修补与回归记录（2026-07-31）

## 问题与根因

`StorageService` 已限制 MIME 类型、扩展名、文件体积与常见文件头，但 `.docx` / `.xlsx` 此前只检查 ZIP 文件头。普通 ZIP 文件可以伪装为 OOXML 文件通过该一层校验，增加下游人工打开或转交附件时的风险。

## 已实施修补

对 Word 与 Excel OOXML 上传增加服务端包结构校验：

1. 必须具有 `[Content_Types].xml`；
2. `.docx` 必须至少包含 `word/` 文档部件，`.xlsx` 必须至少包含 `xl/` 工作簿部件；
3. 拒绝 ZIP 内的绝对路径、反斜杠、空路径段、`.` 和 `..` 路径段；
4. 最多允许 1,024 个 ZIP 条目；
5. 限制单个与累计展开体积为服务端上传大小限制的 10 倍，并拒绝超过该比例的高度压缩条目；
6. 检查在隔离的 ZIP 中央目录层完成，不解压到应用目录；校验失败时删除刚写入的临时文件。

客户下载仍强制使用附件响应、`no-store, private` 与 `X-Content-Type-Options: nosniff`；项目权限校验发生在读取文件前。

## 验证证据

| 检查 | 结果 |
| --- | --- |
| 普通 ZIP 冒充 DOCX | 拒绝，`FILE_CONTENT_MISMATCH` |
| 有效 DOCX 包结构 | 接受 |
| 高压缩、超展开 XLSX | 拒绝，`FILE_CONTENT_MISMATCH` |
| `StorageServiceTest` | 9 项通过 |
| `backend\\mvnw.cmd verify` | 172 项通过，0 failure / 0 error / 0 skipped |
| `frontend\\npm.cmd run check` | 通过 |
| `frontend\\npm.cmd run build` | 通过 |
| 本机 P0/P1 权限与数据边界 | `scripts\\verify-local-p0p1.ps1` 通过 |
| 运行镜像一致性 | 受控本机重建后，容器 JAR 与 `backend/target-local` SHA-256 一致 |

## 运行与恢复演练

同轮已完成以下隔离验证：

- 全新 PostgreSQL 冷启动：53 张表、4 项独立服务、媒体成果、发布会候选和迁移台账约束通过；
- PostgreSQL 自定义格式备份恢复：关键汇总、53 张表、4 项服务类型和治理约束一致；
- 上传卷备份恢复：文件哈希、目录、所有权、权限、大小与总字节数一致；
- 生产 Compose 边界：未自动装载种子账号、案例、媒体目录、报价或开放 API 密钥。

## 未覆盖的生产条件

此修补不等于恶意软件检测或生产文件治理验收。上线前仍须由主体方确定病毒扫描、隔离、人工审核、保留期限、删除策略、访问日志与事件响应方案，并在隔离预发布环境完成真实恢复演练。不得把本机演示上传文件或测试数据带入生产。
