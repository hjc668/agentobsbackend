# 当前交付里程碑

核对日期：2026-09-22。`[x]` 为代码或本机测试已核对，`[~]` 为部分完成，`[ ]` 为未完成，`[-]` 为产品明确延期。旧版清单在 `docs/archive/MILESTONES_2026-09-07.md`，其 React/Vite 描述、测试数与页面完成标记已失效。

## 基础工程

- [x] Spring Boot 2.7.18、Java 8 目标、MyBatis 注解；ClickHouse JDBC 0.4.6、PolarDB-X Connector 2.2.12。
- [x] Vue 2.6.12 + Element UI 2.13.2 + Webpack 4 前端；前后端各自启动，统一文根 `/icbc/hmp/agentobs`。
- [x] Tracing、Observability、Widget SQL Provider 查询 AgentObs `_all` 表；同步 CRUD 使用 PolarDB-X。
- [x] 2026-09-22：本机后端 158/158 单测通过，前端 Webpack 构建通过；**这不替代内网/真数据验收**。

## Tracing 优先交付

- [x] 前端 `/traces` 接线；列表、筛选、Facet、趋势、Trace 抽屉 Tree/Timeline/Graph/Data、Formatted/JSON 和本页 CSV 有实现。
- [x] 类型/等级入参大小写兼容；新表缺失字段白名单处理并记日志；后端查询切换 AgentObs。
- [~] 原始 Langfuse 页面 1:1 视觉和交互验收：需用同一固定 Trace 在目标数据环境逐项复验。
- [~] 大数据性能：历史本机压测不代表目标集群；按真实数据量、时间窗和目标 ClickHouse 版本复测。
- [ ] 删除、批量删除、全部筛选结果导出。Score/Comment UI 按当前产品范围暂不做。
- [ ] SDK→Collector/Sinker→ClickHouse 字段端到端校验，尤其 TTFT 秒→毫秒和根 Trace 摘要。
- [ ] 智能体关注/跳过筛选：目前仅有字段与查询方案讨论，尚未开发；需先确认稳定 agent_id 上报。

## 其他页面与认证

- [x] Home、Users 路由接线；Dashboard/Widget/Prompt CRUD 后端代码保留。
- [ ] Sessions、Dashboards、Prompts 前端路由目前为占位；不能按后端 API 存在情况标记前端完成。Prompt Management 按产品决策延期。
- [~] Mock Session/CSRF、VIEW/ADMIN 后端；Hermes AAM 适配器有源码，但真实 jar、票据、用户信息和权限需内网验证。
- [ ] 修正行内启动脚本强制 Mock、完成 `-Paam` 打包；前端补 `/aamlogin` 回调与真实 SSO 登录/退出。
- [ ] AAM 模式下复验 `/icbc/hmp/agentobs/healthz` 不被 Hermes Filter 拦截，随后做 F5/SLB 验收。
- [-] Evaluation、Scores 配置、Datasets/Experiments；不随 Tracing 自动扩范围。

完成定义：功能须有**前端路由、后端接口、真实数据、权限、浏览器操作和目标环境**的相应验收记录；单纯存在源码或 Mock 测试不足以打 `[x]`。
