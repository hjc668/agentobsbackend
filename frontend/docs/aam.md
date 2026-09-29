# 当前前端 AAM 改造接续

核对日期：2026-09-22。当前正式前端是 `frontend/`，Vue 2.6.12 + Element UI 2.13.2 + Webpack 4，**不是**旧文档的 `frontend0914`、Vue 2.7 或 Vite。旧方案保存在项目根目录 `docs/archive/frontend_aam_plan_before_2026-09-22.md`，不能直接按其中的路径或响应假设施工。

## 现状

- `src/router/index.js` 的 base 为 `/icbc/hmp/agentobs/`，但没有 `/aamlogin` 路由。
- `src/App.vue`、`src/store/index.js`、`src/features/auth/LoginPage.vue` 当前只支持手工 Mock 登录；未根据认证模式自动跳转 Portal。
- `src/api/client.js` 已在 Mock 登录前获取 CSRF，但尚未封装后端已有的 `GET /api/v1/auth/config`、`POST /aam/login/auth` 和 Portal 退出流程。
- `webpack.config.js` 当前只代理 `/icbc/hmp/agentobs/api`；如使用本地代理联调，还要明确处理 `/icbc/hmp/agentobs/aam/login/auth` 与 Portal 登录/退出的跨域或网关路径。

## 实施边界

1. 启动时取后端匿名 `/icbc/hmp/agentobs/api/v1/auth/config`。`mode=mock` 保持现有手工登录；`mode=aam` 且 `/me` 未认证时，保存文根内的原路径，再整页跳转接口返回的 `loginUrl`。不要在前端写死 AAM 域名。
2. 新增文根内 `/aamlogin` 回调页：读取 `SSIAuth`/`SSISign`，立即移除地址栏参数；向 `/icbc/hmp/agentobs/aam/login/auth` 提交票据，成功后恢复原页面并重新获取 `/me` 与 CSRF。票据不能进日志、localStorage、错误提示或遥测。
3. 退出先调用本地 `/api/v1/auth/logout` 清 Session，再整页跳至 `config.logoutUrl` 清 Portal Cookie；即使本地退出请求失败，也要按安全策略处理浏览器状态，避免残留已登录 UI。
4. 回调、过期、取消、伪造票据、重定向循环、CSRF、VIEW/ADMIN 菜单行为和刷新恢复登录均需联调。AAM 验票失败应按后端当前实现处理为 **HTTP 401 / `AAM_AUTH_FAILED`**，不要按旧方案的 HTTP 200 处理。

后端行内依赖、启动脚本阻断项和全链路验收见项目根目录 [`AAM_INTRANET_HANDOVER.md`](../../AAM_INTRANET_HANDOVER.md)。当前**不能**把前端 AAM 标记为已完成。
