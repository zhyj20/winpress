# 前端安全响应头修补与回归记录（2026-07-31）

## 问题与根因

本机前端 Nginx 在首页和案例页此前没有统一返回内容安全、反嵌入、引用来源和浏览器权限策略响应头。即使 Vue 默认会转义模板内容，缺少浏览器侧纵深防护仍会增加嵌入式钓鱼、意外资源加载和潜在脚本注入的影响面。

## 已实施修补

- 新增 `frontend/security-headers.conf`，统一设置：
  - `Content-Security-Policy`
  - `X-Frame-Options: DENY`
  - `X-Content-Type-Options: nosniff`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Permissions-Policy`（禁用摄像头、定位、麦克风、支付与 USB）
- Nginx 在带有 `Cache-Control` 或 `X-Robots-Tag` 的子路由中显式再次包含该文件，避免 Nginx 的 `add_header` 继承规则使安全头失效。
- CSP 限制脚本、连接、图片、媒体、字体与表单到同源受控范围；原始项目文件仍仅能经鉴权下载接口取得。

## 已验证范围

| 检查 | 结果 |
| --- | --- |
| 修补前首页响应头 | 5 项安全头均缺失，已作为失败基线复现 |
| 修补后首页响应头 | 5 项安全头均存在且策略符合预期 |
| 受控案例路由 | 同时保留 CSP 与 `X-Robots-Tag` |
| Nginx 配置 | `nginx -t` 通过 |
| 前端类型/格式检查与生产构建 | 通过 |
| 后端 `mvn verify` | 通过 |
| 本机容器 | 前端、后端、PostgreSQL、Redis 健康 |
| 1440px、768px、390px 首页 | 正常加载、无横向溢出、无控制台 warning/error |
| 受控产业论坛案例页 | 正常加载、无控制台 warning/error |

## 不在本轮宣称的能力

本轮没有配置生产 TLS 终止、HSTS、WAF、CDN、集中式 CSP 报告端点或跨域单点登录。它们需要生产域名、证书、网关拓扑、日志保留和主体方授权后，在隔离预发布环境验收；本机 HTTP 容器验证不能替代该流程。
