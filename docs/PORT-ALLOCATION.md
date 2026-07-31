# 本机网站端口分配

本表只记录实际源码与正式启动脚本，不把 checkpoint、release 副本、交付压缩包或临时预览目录视为独立网站。

| 端口 | 项目或服务 | 用途 | 处理结论 |
| ---: | --- | --- | --- |
| 3000 | 牛媒信源 GEO | 统一入口 | 保留现有配置 |
| 3200 | 一手 GEO 信任站 | Next.js 本地站点 | 保留现有配置 |
| 4188 | 东驰品牌站 | 本地静态服务 | 保留现有配置 |
| 5173 | 牛媒信源 GEO | 主站前端 | 保留给 GEO |
| 5174 | 云发布旧本地预览 | Vue 前端 | 保留旧进程，不作为当前部署入口 |
| 5191 | AIBIS | 独立前端预览 | 保留现有配置 |
| 5217 | 云发布商用版 | Docker 前端与源码调试 | 当前首页端口 |
| 6380 | 云发布旧环境 | Redis 映射端口 | 保留现有配置 |
| 6381 | 云发布 V4.1 隔离质检 | Redis 映射端口 | 仅本机验收；不得用于生产 |
| 6382 | 云发布商用版 | Redis 映射端口 | 当前 Docker 栈 |
| 8090 | 云发布旧完整站 | 旧版 API | 仅维护旧项目时使用 |
| 8091 | 云发布旧完整站 | 旧版 Web 服务 | 仅维护旧项目时使用 |
| 8092 | 云发布旧本地后端 | Spring Boot API | 保留旧进程，不作为当前部署入口 |
| 8116 | WinPress.cn 早期原型 | 建议的独立预览端口 | 启动时显式指定 8116，避免与 8091 冲突 |
| 8192 | 云发布商用版 | Spring Boot API | 当前 Docker 栈 |
| 8787 | 牛媒信源 GEO / AIBIS | 共享平台 API | 两者按现有架构共享 |
| 55432 | 云发布旧环境 | PostgreSQL 映射端口 | 保留现有配置 |
| 55433 | 云发布 V4.1 隔离质检 | PostgreSQL 映射端口 | 仅本机验收；不得用于生产 |
| 55434 | 云发布商用版 | PostgreSQL 映射端口 | 当前 Docker 栈 |

## 冲突处理规则

1. 启动云发布前运行 `scripts/check-ports.ps1`。
2. `5217` 或 `8192` 已被占用时中止启动，先核对 PID、进程名和命令行。
3. 不自动结束进程，不把其他网站切走，也不让 Vite 自动选择下一端口。
4. PostgreSQL 与 Redis 已监听通常表示基础设施正在运行，只报告状态，不按冲突处理；须同时核对实例归属，不能把当前 `55434/6382` 与旧环境或隔离质检端口混用。
5. WinPress.cn 早期原型与旧完整站原先都默认使用 `8091`；两者需要并行时，早期原型使用 `8116`。

早期原型的 PowerShell 启动方式：

```powershell
$env:PORT='8116'
node server.mjs
```

## 常用检查

检查云发布全部端口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-ports.ps1
```

只检查前端：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-ports.ps1 -Scope frontend
```

输出机器可读结果：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-ports.ps1 -Json
```

盘点所有已登记网站端口（只报告，不结束任何进程）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-ports.ps1 -Scope registry
```
