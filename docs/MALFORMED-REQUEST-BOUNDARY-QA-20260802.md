# 无效请求正文边界 QA（2026-08-02）

## 问题与根因

登录接口接收无效 JSON 时，Spring 会在进入控制器前抛出 `HttpMessageNotReadableException`。此前全局异常处理器没有专门处理该异常，使本应由客户端修正的请求格式问题落入通用异常分支，并返回 `500 INTERNAL_ERROR`。

这会错误抬高服务端异常告警，也会让开放 API 调用方无法区分“请求正文无效”和“服务不可用”。问题不涉及账号、密码、会话、数据库数据或限流规则。

## 修复

- 在 `GlobalExceptionHandler` 中增加 `HttpMessageNotReadableException` 专用映射；
- 无效 JSON 统一返回 `400` 与 `INVALID_REQUEST_BODY`；
- 保留通用异常分支给真正的未处理服务端错误；
- 增加单元测试，固定状态码、失败标识与错误码契约。

## 验证

- 修复前：经本机前端 Nginx 向 `/api/v1/auth/login` 提交合成的无效 JSON，复现 `500`；
- 修复后：相同路径返回 `400`；近五分钟后端日志中 `Unhandled request failure` 为 `0`；
- `GlobalExceptionHandlerTest` 定向通过；
- 后端 Surefire `177/177`、JAR 打包通过；
- 前端类型、格式和生产构建通过；
- 本机 `verify-local-p0p1.ps1` 通过；Playwright 在 1440×900、768×1024、390×844 三种视口 `117/117` 通过；
- 受控浏览器首页实测：三种视口页面级 `scrollWidth` 与 `clientWidth` 一致、破损图片为 `0`、页面控制台无 warning/error。

## 数据与回退边界

没有数据库迁移、数据写入或订单状态变更。若需回退，只移除该异常映射和对应单元测试；不应借此把无效请求重新归类为服务端故障。
