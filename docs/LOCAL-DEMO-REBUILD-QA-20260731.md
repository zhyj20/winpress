# 本机演示重建一致性 QA（2026-07-31）

## 问题

本机演示后端使用 `backend/Dockerfile.local`，该镜像只复制 `backend/target-local/` 中已经准备好的 JAR。若仅执行 Maven 测试后直接运行 `docker compose ... build`，Docker 可以继续使用旧 JAR 层；测试的源码与运行容器便可能不是同一版本。

这不是生产部署方案，也不涉及数据库内容，但会削弱本机验收的可追溯性。

## 修复

新增统一入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/rebuild-local-demo.ps1
```

它固定按以下顺序执行：

1. 预检 5217、8192、55434、6382 端口；已由当前 WinPress 演示栈占用时允许原地更新，其他进程占用时终止；
2. 在临时源码副本中重新测试并打包 Java 后端，刷新 `target-local` JAR；
3. 重建前后端镜像；
4. 启动依赖服务并等待 Docker 健康检查通过；
5. 强制比较准备好的后端 JAR 与容器 `/app/app.jar` 的 SHA-256；
6. 输出容器状态。

2026-08-01 起，第 4 步后会强制比较 `backend/target-local/` 中准备好的 JAR 与容器
`/app/app.jar` 的 SHA-256；不一致即使健康检查为绿也会使重建失败。这样可以阻止“源码
已通过测试、Docker 仍运行旧 JAR”的假阳性。

该入口不删除 PostgreSQL、Redis 或上传文件卷，不执行数据库迁移，也不创建或删除业务项目、任务、订单和外部履约记录。

## 本次实测

- 首次执行新增校验脚本时发现参数默认值阶段无法可靠读取 `$PSScriptRoot`，已改为脚本启动后解析默认路径并重跑；
- 端口预检确认 5217 与 8192 仅由当前项目容器占用；
- 后端 JAR 在临时副本完成打包，Docker 构建日志确认 `COPY target-local/...jar` 重新执行而非复用旧层；
- `docker compose up -d --wait` 后，前端、后端、PostgreSQL、Redis 均为 `healthy`；
- 容器 `/app/app.jar` 与当前 `target-local` JAR 的 SHA-256 完全一致：`A1B154B59F60CC16621E9D17E4B4E9F14E891A6B29A9968FD8E08ABAD85B3344`；
- 前端类型、格式与生产构建通过；后端 Maven Surefire `174/174` 通过。

## 边界

本脚本只覆盖本机演示环境。生产仍必须使用 `docker-compose.production.yml`、受保护的环境变量、预发布迁移和独立的 TLS、域名、监控、备份恢复与灾备验收；本机健康状态不能替代这些生产门槛。
