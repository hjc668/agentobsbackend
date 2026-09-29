# 行内 AAM 接续与验收

状态核对：2026-09-22。**这是待办，不是已验收证明。** 旧手册保存在 `docs/archive/AAM_INTRANET_HANDOVER_2026-09-15.md`；其中 `sirius-aam-ldap`、`frontend0914`、Vue 2.7/Vite、伪造票据返回 HTTP 200、固定 132 个测试等描述均已过时，不能照做。

## 当前代码边界

| 部分 | 当前事实 |
| --- | --- |
| 运行时模式 | `backend/src/main/resources/application.yml` 的 `app.auth.mode`，当前为 `mock`；`aam` 模式不会回退到 Mock 登录 |
| Maven 构建 | 默认构建不引入行内 jar；`mvn -Paam ...` 才编译 `src/main/aam/java` 并加入 `hermes-aam:6.69.5500050.0`、`encrypt-client:7.1.2`；**没有 `sirius-aam-ldap`** |
| 验票 | `AamSsoController` → `HermesAamTicketAuthenticator`：`AamWrapper.auth(...)` 返回 boolean，成功后仅从 request 的 `ssiCredentials` 提取已验证身份 |
| 用户资料 | `UniformTellerInfoClient` 调用加密的 `UniformTeller/2.0/qryTellerInfo`；标准化用户号、校验响应身份，然后创建 Spring Session/CSRF |
| 权限 | `langfuse_user_roles` 中 `ADMIN` 或 `VIEW`；无记录、未知值、角色表暂不可达均按 VIEW |
| 失败响应 | `AamAuthenticationException` → **HTTP 401**、`AAM_AUTH_FAILED`；不是旧手册的 HTTP 200/`code=1` |
| 前端 | `frontend/` 为 Vue 2.6 + Element UI + Webpack 4；仍是手工 Mock 登录，没有 `/aamlogin` SSO 回调，也没有完整的门户退出跳转 |

后端关键文件：`backend/src/main/java/com/icbc/aiops/langfuse/api/{AamSsoController,AuthenticationController}.java`、`backend/src/main/aam/java/com/icbc/aiops/langfuse/{security,config}/`、`backend/src/main/java/com/icbc/aiops/langfuse/security/{AamSessionService,TableAamRoleResolver}.java`。参照行内 `dsf-self-analysis` 的真实方法，但**实际 jar 签名以行内依赖为准**。

## 上线前按顺序完成

1. **构建验证**：在行内 Maven 仓库可用时执行 `mvn -Paam clean test` 和 `mvn -Paam clean package`。若报方法签名错误，查看当前 jar 的公开方法并调整仅行内适配层；不要为通过编译删除验签或回退到 Mock。默认 `mvn -o test` 也必须继续通过。
2. **部署模式**：`scripts/start-backend-intranet.sh` 目前硬编码 `--app.auth.mode=mock`，会覆盖 `application.yml`；Dockerfile 只复制预构建 JAR。必须让部署使用经 `-Paam` 构建的产物，并取消强制 Mock，才可由配置切换真实 AAM。此项当前是**阻断项**。
3. **配置核对**：在行内填写本应用独有的 `aam.enableSSIC`、`aam.enableSpecialUrl`、`aam.ssic.*`、`aam.service.*`、`dsf.cocoa.router.addr`。密钥和票据不得进入提交文件、日志或前端。`AamConfigurationValidator` 会在必填项缺失时拒绝以 AAM 模式启动。
4. **前端接入**：启动时读取 `GET /api/v1/auth/config`；AAM 模式未登录时整页跳转返回的 loginUrl；增加文根下 `/aamlogin` 回调，读取 `SSIAuth/SSISign`，立即从地址栏清除并 POST `/aam/login/auth`，恢复原页面。Mock 模式保留现有手工登录。退出须在本地 `/api/v1/auth/logout` 后整页跳至配置中的 Portal logoutUrl；当前 `frontend/src/store/index.js` 仅清本地状态，**尚未完成这一步**。
5. **端到端验收**：真实票据成功、伪造票据 401、用户资料与号码一致、角色 ADMIN/VIEW、CSRF、登录前后 Session 轮换、刷新恢复身份、退出清双侧会话、敏感信息不入日志。`/aam/login/auth` 可免登录前 CSRF；其他写接口不能免。
6. **F5/SLB 探针复验**：文根下 `/icbc/hmp/agentobs/healthz` 返回纯文本 `@the@health@is@good@`。切 AAM 后要现场确认 Hermes 引入的 Filter 不重定向此路径；Spring Security 的 `permitAll` 本身不能保证拦住更早的 Servlet Filter。`/actuator/health/readiness` 检查数据库，应交监控而非负载均衡摘节点。

## 配置和路径注意

- 文根是 `/icbc/hmp/agentobs`。后端匿名发现接口完整路径为 `/icbc/hmp/agentobs/api/v1/auth/config`，回调 POST 为 `/icbc/hmp/agentobs/aam/login/auth`；Portal URL 以接口实际返回为准，不在前端硬编码。
- `aam.ssic.client.site_url` 应指向前端**回调页面**，不是后端验票 API；须核对真实域名、文根与网关转发规则。
- `app.auth.mode` 是运行时开关；Maven `-Paam` 是编译行内依赖开关，两者不能互相代替。
- 本机 2026-09-22 默认构建测试 158/158 通过；**未执行 `-Paam`，未用行内票据或 Cocoa Router 验收**。只有本节第 1–6 项全部在行内通过，才能将 AAM 标记为完成。
