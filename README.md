# Langfuse Web 迁移工程

**新模型或重置上下文后先读 [`PROJECT_PROGRESS.md`](PROJECT_PROGRESS.md)。** 它是截至 2026-09-22 的现状、验收边界和待办；[`MIGRATION_MATRIX.md`](MIGRATION_MATRIX.md) 只列功能覆盖；历史过程在 `docs/archive/`，不要当现行操作手册。

本工程把可观测 Web 从原 Langfuse Web 中分离：`frontend/` 独立运行，`backend/` 提供 Spring Boot API。当前重点是 Tracing；Prompt Management 与 Evaluation 不属于本轮交付。原 Langfuse 服务可用于对比，但本工程不保证其 Worker、Redis、MinIO 正在运行，也不负责管理那些进程。

## 当前技术与数据流

```text
浏览器：Vue 2.6.12 + Element UI 2.13.2 + Webpack 4 (:5173)
      │ Session Cookie + CSRF，路径 /icbc/hmp/agentobs
      ▼
Spring Boot 2.7.18 + Java 8 目标 + MyBatis 2.3.2 (:8080)
      ├─ AgentObs ClickHouse *_all：Tracing / Sessions / Users / 指标读取
      └─ PolarDB-X：Dashboard、Widget、Prompt、Comment、AAM 角色等同步 CRUD
```

后端依赖 ClickHouse JDBC 0.4.6、`polardbx-connector-java` 2.2.12。`backend/src/main/java/.../postgres/` 是历史包名，当前事务数据库是 PolarDB-X。ClickHouse 查询不依赖浏览器提供的 project ID；`/api/v1/projects/{id}` 仅是兼容路由。表结构以 `backend/sql/clickhouse/agentobs-clickhouse-schema-final.sql` 和实际部署库为准；本地 SQL 的保留期不同。

## 本地启动

在项目根目录执行。真实数据库连接值放在 `backend/src/main/resources/application-mybatis.yml`；默认 Spring profile 为 `mybatis`，认证模式在 `application.yml` 中默认为 `mock`。请先确认目标数据库和表已准备好。

```bash
# 终端 1：真实本地数据源；脚本选择 JDK 8，停掉本工程旧后端后重启
bash scripts/start-backend.sh

# 终端 2：前端；脚本选择 pnpm/npm，停掉本工程旧前端后重启
bash scripts/start-frontend-local-langfuse.sh
```

不连接数据库、只验证前后端交互时，把第一条换为 `bash scripts/start-backend-mock.sh`。在 IDEA 中以 JDK 8 运行 `com.icbc.aiops.langfuse.LangfuseQueryApplication`，配置使用相同的 resources 文件。

- 前端：`http://127.0.0.1:5173/icbc/hmp/agentobs/traces`
- 后端进程探针：`http://127.0.0.1:8080/icbc/hmp/agentobs/healthz`
- 后端依赖探针：`http://127.0.0.1:8080/icbc/hmp/agentobs/actuator/health/readiness`

当前前端是 **Webpack 4，不是 Vite**。`frontend/package.json` 声明 Node 16.16.0、pnpm 8.15.9；若在 Node 17+ 手工构建，需要 `NODE_OPTIONS=--openssl-legacy-provider npm run build`。前端启动脚本已按 Node 版本设置该选项。构建后代码的 API 基址由 `frontend/src/api/client.js` 与 `webpack.config.js` 的代理规则决定。

注意：脚本里的 `VITE_API_BASE_URL` 等名称是迁移遗留；当前 Webpack 配置没有将这些值注入浏览器代码。开发代理会按 `webpack.config.js` 工作，部署时必须单独核对同源 API 转发。`application-mybatis.yml` 的 PolarDB-X URL/Driver 是**本地容器配置**，不能直接当成行内连接参数。

## 验证与实际可用范围

```bash
cd backend && mvn -o test
cd ../frontend && NODE_OPTIONS=--openssl-legacy-provider npm run build
```

2026-09-22 本机结果：后端 **158/158** 测试通过（运行 JVM 为 Java 17.0.1，尚非本轮 JDK 8 运行时复验）；前端 Webpack 构建通过。**没有在本轮执行目标 ClickHouse/PolarDB-X 真数据回归，也没有内网 AAM 验票结果。**

前端导航实际接线：Home、Tracing、Users。Sessions、Dashboards、Prompts 等虽有部分组件或后端接口，当前路由仍是占位页；不能把后端接口存在等同于前端完成。Tracing 的 Tree/Timeline/Graph、Facet、筛选、趋势、详情和本页 CSV 已有实现，仍需原版逐屏与真数据验收；Score/Comment 页签、删除和全量导出暂未完成。以 [`MIGRATION_MATRIX.md`](MIGRATION_MATRIX.md) 的“前端接线/后端能力”两列区分状态。

## AAM 上线警示

默认构建不含 Hermes 适配器；内网须用 Maven `-Paam` 构建并实测。**`scripts/start-backend-intranet.sh` 当前强制 `--app.auth.mode=mock`**，所以仅把 `application.yml` 改成 `aam` 不能启用真实 AAM。前端也没有 `/aamlogin` SSO 回调路由。需要在上线前分别修复并按 [`AAM_INTRANET_HANDOVER.md`](AAM_INTRANET_HANDOVER.md) 验收；不要使用归档手册的旧依赖、路径和响应码。

## 文档分工

- [`PROJECT_PROGRESS.md`](PROJECT_PROGRESS.md)：模型接续首读，当前事实、风险、验证与下一步。
- [`MIGRATION_MATRIX.md`](MIGRATION_MATRIX.md)：前端路由与后端 API 分开记录覆盖范围。
- [`MILESTONES.md`](MILESTONES.md)：按当前产品范围归纳的完成门槛。
- [`AAM_INTRANET_HANDOVER.md`](AAM_INTRANET_HANDOVER.md)：真实 AAM 的内网待办和验收边界。
- [`docs/INGESTION_MAPPING_CHECKLIST.md`](docs/INGESTION_MAPPING_CHECKLIST.md)：SDK→Collector/Sinker→ClickHouse 的字段口径与待验收清单；不是已实现声明。
- [`TRACING_CLICKHOUSE_REFACTOR_PLAN.md`](TRACING_CLICKHOUSE_REFACTOR_PLAN.md)：**历史实施方案，已部分执行**；不可再把“待实施”章节当现状。
- `backend/sql/clickhouse/schema3-design.md`、`sdk-guide.md`：采集和存储设计；Collector/Sinker 生产实现仍需取得实际代码核验。
