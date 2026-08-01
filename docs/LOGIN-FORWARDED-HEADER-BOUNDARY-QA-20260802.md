# 登录来源转发头边界 QA（2026-08-02）

## 问题与影响

后端启用了 Spring 的转发头处理，用于在前端 Nginx 代理后识别请求来源。此前 Nginx 对 `/api/` 使用 `$proxy_add_x_forwarded_for`：该写法会把浏览器自行携带的 `X-Forwarded-For` 一并转发。攻击者可借此伪造登录失败限流的来源，削弱同一来源的失败次数控制。

## 修复

- `frontend/nginx.conf` 对 API 代理改为 `proxy_set_header X-Forwarded-For $remote_addr`；
- 应用边界不再拼接不可信的客户转发链；
- 后端、数据库与 Redis 仍只绑定回环地址，浏览器只能经前端代理访问 API；
- `docs/DEPLOYMENT.md` 明确：如生产另有 TLS/CDN/WAF 代理，必须在受信任的上游代理按固定网段解析和净化客户端地址，禁止透传浏览器自带的转发头，也不得公开后端端口。

## 验证

在项目根目录执行：

```powershell
cd frontend
npm.cmd run check
npm.cmd run build

cd ..
docker compose -f docker-compose.local-demo.yml up -d --build frontend
```

本轮结果：

- Vue 类型检查、Prettier 检查和生产构建通过；
- 后端 Surefire `176` 项通过，`0` failure、`0` error、`0` skipped，并完成 Spring Boot JAR 打包；
- 前端与后端容器均恢复为 `healthy`；
- `http://127.0.0.1:5217/` 与 `http://127.0.0.1:8192/api/v1/health` 均返回 `200`；
- 运行中的前端容器配置已核对为 `proxy_set_header X-Forwarded-For $remote_addr`，未保留 `$proxy_add_x_forwarded_for`。
- 从后端容器经前端 Nginx 对虚构账号连续发起 8 次失败登录后均返回 `401`；第 9 次仅改写客户端自带的 `X-Forwarded-For` 仍返回 `429`。这证明限流来源取自 Nginx 覆盖后的连接地址，而非浏览器可伪造的声明值。
- Playwright 经前端 Nginx 入口完成桌面、平板和移动端 `117/117`；
- `scripts/verify-local-p0p1.ps1` 通过：登录、会话、客户组织隔离、管理员边界、文件边界、四项独立服务订单和外部能力降级均保持正确。

## 边界

本轮未向任何真实账号发起失败登录，也未输出账号、密码、令牌、用户标识或 Redis 限流键。动态探针只使用后端容器来源和虚构账号；该容器来源的临时限流在冷却期后自动过期，不影响浏览器用户。它证明源码与本机 Compose 边界已阻止客户端伪造转发链；不替代正式 TLS/CDN/WAF 的受信任代理网段配置、生产 WAF 规则、监控告警或真实生产环境的安全验收。
