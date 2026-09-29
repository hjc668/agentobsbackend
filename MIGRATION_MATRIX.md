# Web 迁移范围矩阵

核对日期：2026-09-22。**“后端已有接口”不等于“前端已接线”，也不等于“真数据和原版 1:1 验收通过”。** 当前路由依据 `frontend/src/router/index.js`，查询表依据 `TracingSqlProvider`、`ObservabilitySqlProvider`、`WidgetMetricSqlProvider`。过时的旧矩阵保存在 `docs/archive/MIGRATION_MATRIX_before_2026-09-22.md`。

| 产品面 | 前端路由现状 | 后端现状 | 本轮结论 |
| --- | --- | --- | --- |
| 应用外壳/导航 | Vue 2.6 + Element UI；侧栏显示各入口 | Session/CSRF、Mock 登录；AAM 接口仅有后端适配 | 外壳可用；导航项不能代表页面已完成 |
| Home | `/home` → `HomePage.vue` | AgentObs summary/timeseries | 已接线；真数据需目标环境复验 |
| Tracing 列表/详情 | `/traces` → `TracingPage.vue`、`TracePeekPanel.vue` | AgentObs traces/observations/locator；MyBatis 注解；Facet/Pulse/详情 | 本轮重点，部分功能可用；1:1 逐屏、真数据、容量仍待验收 |
| Tracing 同步写入 | 当前仅本页/选中行 CSV 导出；无删除 UI | Score/Comment 读取接口存在；无完整原版删除/批量导出链路 | 未完成；Scores/Comments 按产品决策暂缓 |
| Sessions | `/sessions` → `UnderConstruction.vue`；`SessionsPage.vue` 文件尚未接线 | AgentObs Session 列表/详情查询 | 前端未完成，不能标记“已迁移” |
| Users | `/users` → `UsersPage.vue` | AgentObs User 查询 | 已接线；目标数据复验待做 |
| Dashboards/Widget | `/dashboards` → 占位组件；有未接线路由的组件 | PolarDB-X Dashboard/Widget CRUD，AgentObs Widget 指标查询 | 后端已有能力，前端未完成；Widget 非当前优先级 |
| Prompts | `/prompts` → 占位组件；有历史页面代码 | PolarDB-X Prompt 版本 CRUD | 产品决策延期，保留代码但不纳入本轮验收 |
| AAM | 当前手工 Mock 登录；没有 `/aamlogin` 回调路由 | `-Paam` 才编译 Hermes 适配器；真实行内验签未验证 | 真实 AAM 未完成；见 `AAM_INTRANET_HANDOVER.md` |
| Alerts / Playground / Settings | 路由均为占位组件 | 无可宣称的端到端迁移 | 未完成，不随 Tracing 自动扩范围 |
| Evaluation / Scores 配置 / Datasets / Experiments | 路由占位 | 非当前交付范围 | 延期 |
| SDK/Collector/Sinker 摄取 | 不属于前端 | 本仓库有 DDL 与设计文档，缺实际生产转换验收证据 | 不可宣称已按 69 字段契约采集完成 |

## 必须保留的契约

- 前后端路径前缀均为 `/icbc/hmp/agentobs`；认证使用 Session Cookie + CSRF。Trace 查询只读 AgentObs `_all` 表，旧 `events_core/events_full` 已不是现行 SQL Provider 的来源。
- ClickHouse 读取不依赖浏览器 project ID；`/api/v1/projects/{ignoredProjectId}` 只是兼容入口。PolarDB-X 写入由后端 workspace 与 VIEW/ADMIN 权限约束。
- 新表不存在的字段走白名单：不强行支持旧表字段，后端记录不支持字段的日志；前端不要展示无法落地的预设。`Missed tool calls` 因缺 `tool_calls` 列而禁用。
- 前端当前只提供**当前页/选中数据** CSV，不能说已实现“所有筛选结果导出”。
- Trace/Observation/Score 分域：Trace 摘要是显式业务根快照，Score 经独立 REST；Score/Comment UI 按要求暂不做。
- 测试通过不等于上线：内网 AAM、实际 Collector/Sinker 映射、目标 ClickHouse/PolarDB-X、原版页面对比和目标容量压测要分别验收。
