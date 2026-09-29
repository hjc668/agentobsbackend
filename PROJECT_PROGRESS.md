# Langfuse Web 迁移：模型接续入口

最后核对：2026-09-29。本文只记录**当前可从代码和本次构建核实的状态**；它不是按日期累加的实施日志。旧日志保存在 [`docs/archive/PROJECT_PROGRESS_through_2026-09-21.md`](docs/archive/PROJECT_PROGRESS_through_2026-09-21.md)，其中的路径、测试数和“下一步”均可能已过时。接手时先读本文，再按任务查具体方案；不要直接执行归档文档中的旧步骤。

## 一句话现状

`frontend/` 是 Vue **2.6.12**、Element UI **2.13.2**、**Webpack 4**；`backend/` 是 Spring Boot **2.7.18**、Java **8 目标字节码**、MyBatis 注解。后端已查询 AgentObs ClickHouse `_all` 表，并用 PolarDB-X 处理同步 CRUD。**前端当前实际接通的主要页面是 Home、Tracing、Users；Sessions、Dashboards、Prompts 等侧栏入口仍指向占位组件。** AAM 后端适配器存在，但内网 Hermes 构建/验签和前端 SSO 回调未在本地完成，不能写成“已接入”。

## 代码与部署边界

| 层 | 当前实现 | 权威位置 |
| --- | --- | --- |
| 前端 | `frontend/`，Vue 2.6 + Element UI 2.13 + Webpack 4；当前路由基址 `/icbc/hmp/agentobs/`，开发端口 5173 | `frontend/package.json`、`frontend/webpack.config.js`、`frontend/src/router/index.js` |
| Web API | Spring Boot 2.7.18、Java 8、MyBatis 2.3.2 注解；应用文根 `/icbc/hmp/agentobs`，默认端口 8080 | `backend/pom.xml`、`backend/src/main/resources/application.yml` |
| 链路查询 | `TracingSqlProvider` 查询 `hmp_agentobs_traces_all`、`observations_all`、`trace_locator_all`；`ObservabilitySqlProvider` 与 `WidgetMetricSqlProvider` 也查询 AgentObs `_all` 表，**不再读旧 `events_core/events_full`** | `backend/src/main/java/com/icbc/aiops/langfuse/mapper/` |
| 同步业务数据 | PolarDB-X：Dashboard、Widget、Prompt、Comment、AAM 用户角色等；源码包名 `postgres` 是历史命名，不代表还依赖 PostgreSQL | `backend/src/main/java/com/icbc/aiops/langfuse/postgres/`、`backend/sql/polardbx-schema.sql` |
| 采集链路 | 当前工程提供 AgentObs 表 DDL、SDK/Collector 设计说明和少量调试数据脚本；**不能仅凭这些文件认定 Collector/Sinker 生产映射已经实现或验证** | `backend/sql/clickhouse/` |

`project_id` 不参与当前 ClickHouse 全库读取。`/api/v1/projects/{ignoredProjectId}` 是兼容别名，路径 ID 不改变查询范围；PolarDB-X CRUD 使用服务端配置的内部 workspace。原 Langfuse 页面及 Worker/Redis/MinIO 可用于对照或继续承载未迁移链路，但其**运行状态需现场检查**，本文不声称它们当前在线。

## 页面与功能：区分“有代码”和“能从导航访问”

| 页面/能力 | 当前证据 | 状态 |
| --- | --- | --- |
| Home | 路由指向 `HomePage.vue`，调用后端 summary API | 已接线；真实数据内容须按目标环境复验 |
| Tracing | 路由指向 `TracingPage.vue`；有筛选、Facet、趋势图、表格、CSV 本页导出及 Trace 抽屉 Tree/Timeline/Graph/Data、Formatted/JSON | 核心可用，但**未完成原版 1:1 验收**；Score/Comment UI 暂不做，删除与全量导出未补 |
| Users | 路由指向 `UsersPage.vue`，可查列表及导出本页 CSV | 已接线；目标数据回归待做 |
| Sessions | 有 `SessionsPage.vue` 和后端接口，但**路由仍指向 `UnderConstruction.vue`** | 前端未接线，不能标记完成 |
| Dashboards / Prompts | 后端存在 CRUD、前端也有组件，但现有路由均指向占位页 | 前端未接线；Prompt Management 按产品决策延期 |
| Alerts / Playground / Evaluation / Scores 等 | 导航或路由占位 | 未迁移；Evaluation 暂不在本轮范围 |

后端 `GET /traces/{id}/scores`、`/comments` 接口仍可存在，但这不代表前端 Score/Comment tab 或写操作已完成。Widget 后端代码存在，当前产品范围不应因此扩大。

## 认证与行内上线的真实边界

- `application.yml` 目前 `app.auth.mode: mock`。Mock 登录使用 `/api/v1/auth/login`；后端 AAM 模式使用 `/aam/login/auth`，共享 Session、CSRF、VIEW/ADMIN 角色规则。浏览器写请求需 CSRF token。
- 行内 Hermes 适配器位于 `backend/src/main/aam/java`，只在 Maven `-Paam` 下参与编译。默认本地构建**不包含**该适配器；本地测试通过不等于真实 AAM 验签通过。
- **当前 `scripts/start-backend-intranet.sh` 强制追加 `--app.auth.mode=mock`，覆盖配置文件的 `aam` 值；Dockerfile 也只是复制预先构建的 JAR。** 真实 AAM 上线前必须修正脚本及打包流程，并在行内验证 `-Paam` 产物、配置、回调和退出。
- `frontend/src/router/index.js` 没有 `/aamlogin` 路由；`App.vue`/`LoginPage.vue` 仍走手工 Mock 登录。后端已有匿名 `/api/v1/auth/config`，但前端尚未按它切换登录流程。
- 行内接续请读 [`AAM_INTRANET_HANDOVER.md`](AAM_INTRANET_HANDOVER.md)。切勿照归档旧手册使用 `sirius-aam-ldap`、旧响应码或 `frontend0914` 路径。

## 数据契约与当前缺口

- **ClickHouse 事实表**：`backend/sql/clickhouse/agentobs-clickhouse-schema-final.sql` 面向集群，默认 TTL 7 天；`agentobs-clickhouse-schema-local.sql` 是本地 30 天版本。实际部署应以目标库 `SHOW CREATE TABLE` 为准，不能从文件推断线上 TTL。
- **PolarDB-X 本地/行内差异**：当前 `application-mybatis.yml` 使用 `com.alibaba.polardbx.core.jdbc.Driver` 和 `jdbc:mysql://127.0.0.1:8527/...`，适配本地容器；行内目标若要求 `jdbc:polardbx://` 及另一驱动入口，必须在目标 jar/服务端实测并配置，不能假定两种 URL/Driver 可直接互换。Hikari 池名当前为 `HikariPool(Mysql)`。
- **Tracing 时间**：查询必须按 `DateTime64(6)` 的列时区绑定范围，不能把 UTC 字符串当数据库本地文本直接拼 SQL。当前配置的 locator/observation 列时区均为 UTC；变更物理列类型后需同步核对。
- **Observation type/level**：入参大小写兼容；库里未知枚举显示为 `UNKNOWN` 并告警，不扩充不存在的字段。筛选字段有白名单，不支持字段记录日志并忽略；不要把旧模型字段硬映射到新表。
- **TTFT 待验证映射**：SDK 约定 `gen_ai.response.time_to_first_chunk` 为**秒**，ClickHouse `time_to_first_chunk_ms` 为**毫秒**。设计决策为在 OTLP→CK 转换层乘 1000、入库保留 `Float64` 小数，查询层再按显示需要取整；尚未在本工程中验证转换代码。
- SDK/Collector 的其他 7 项映射口径和验收状态集中记录在 [`docs/INGESTION_MAPPING_CHECKLIST.md`](docs/INGESTION_MAPPING_CHECKLIST.md)；其中“建议”均不是已上线事实。
- **Trace 摘要**：当前架构定义为显式业务根 Span 的快照。Trace input/output/name/tags 应按根节点映射；Score 走独立 REST，不是 OTLP Span。`langfuse.release` 不应混充 `version`。
- **智能体筛选设想尚未实施**：现有 observations 有 `agent_id/agent_name/agent_version`。若暂不新增表，可将 `agent_id` 约定为来源体系＋命名空间＋来源 ID 的稳定全局标识，前提是 SDK 实际上报并完成历史值兼容；Trace 多智能体时按限定时间窗匹配 observations 的 trace_id，不能简单给 traces 加单一 agent_id。跨设备关注列表与长期智能体目录仍需持久化设计。
- 69 字段的最小查询清单在当前工作区的 `../langfuse/outputs/01a01e68-f25c-72c2-9f53-82e59adfe6f4/tracing_clickhouse_minimum_fields.xlsx`；它是字段**需求清单**，不是“SDK 已覆盖”的证明。SDK 侧所报 50 项职责、33 项覆盖尚未在此仓库独立核验。
- **前端环境变量遗留**：启动脚本仍设置 `VITE_*`，但当前前端已是 Webpack 4，`webpack.config.js` 的 `DefinePlugin` 只注入 `NODE_ENV/BASE_URL`；不能假定这些变量会进入浏览器 bundle。开发代理另由 `VITE_API_PROXY_TARGET` 在 Webpack 配置中读取。部署时需单独核对 API 同源路由/代理，不能沿用旧 Vite 的配置说明。

## 2026-09-29 本机验证

- 三个 Mock CRUD Service（Dashboard、Dashboard Widget、Prompt）已完成线程安全扫描兼容修复：状态集合改为 `CopyOnWriteArrayList`，公开 CRUD 方法继续使用 `synchronized` 保证复合操作原子性，更新按稳定 ID 原位替换；写入的 JSON Map/List 使用递归不可变快照，避免请求对象和返回对象造成深层可变状态逃逸。
- 已按 2026-09-29 行内 Sonar 截图修复当前工作区可定位的 **15 条主要级别问题**：分页局部变量遮蔽、3 处嵌套三元表达式、AAM 退出 Cookie 的 `HttpOnly`/`Secure` 标志、重复 getter 实现，以及 7 处布尔测试断言写法；Cookie 属性已加入回归断言。截图中的另 2 条 `CodeStatisticUploadUtil.java` 在当前工作区不存在，需在行内确认扫描源或分支。仓库没有可直接运行的行内 Sonar/PMD 配置，因此本机未宣称复扫通过。
- 已为 2026-09-29 覆盖率截图中可识别、且未标记“白”白名单的类补充或核对配套测试：新增 API/领域值对象、Mapper 行模型、Java 8 集合与不可变 JSON 工具、MyBatis 工厂、AAM 本地安全对象、Mock 查询/Widget 指标、三个 PolarDB-X CRUD Service 的直接单元测试；已有 Controller、SQL Provider、MyBatis 查询和三个 Mock CRUD 的专项测试继续复用。测试中的 Mapper 均为内存代理，**不代表真实 PolarDB-X 联调**。
- `backend/pom.xml` 已显式声明测试作用域的 `junit-platform-launcher`；`aam` profile 也已登记 `src/test/aam/java`，为 `HermesAamTicketAuthenticator` 和 `UniformTellerInfoClient` 提供调用内网 SDK 前的输入校验测试。由于本机缺少 `com.icbc.hermes:hermes-aam` 与 `com.icbc.aam:encrypt-client` 行内制品，`mvn -o -Paam -DskipTests test` 在依赖解析阶段失败，这两项测试尚未在本机编译或执行，必须在行内仓库环境验证。
- `backend/` 执行指定定向命令 `mvn -o -q -Dtest=MockCrudServiceThreadSafetyTest,DashboardControllerTest,DashboardWidgetControllerTest,PromptControllerTest test`：**14 个测试，0 failure / 0 error / 0 skipped**；其中新增直接 Service 测试 7 个，覆盖深层隔离、不可变返回、Widget 位置稳定、Prompt 并发版本与 `latest` 唯一性、Dashboard 并发 Clone 名称唯一性。
- Sonar 修复涉及的定向测试（AAM SSO/配置、Context Path、Tracing/Widget SQL、Mock CRUD、MyBatis 迁移）共 **75 个测试，0 failure / 0 error / 0 skipped**。
- 完成上述测试补充后，`backend/` 执行 `mvn -o test`：**199 个测试，0 failure / 0 error / 0 skipped**。本次 Maven 运行使用 Java 17.0.1，并通过 `release 8` 配置限制 Java 8 API；这是默认本地构建及 Mock profile 测试，**不是 JDK 8 运行时验收，不代表真实 ClickHouse/PolarDB-X、AAM 或行内环境验收，也不等同行内 Sonar 复扫**。
- 上述 CRUD 结论仅来自 **Mock profile 本机测试**，不代表真实 PolarDB-X 联调、AAM 集成验收或行内环境验收。
- `frontend/` 最近一次构建验证仍为 2026-09-22：在 Node 24.16.0 下执行 `NODE_OPTIONS=--openssl-legacy-provider npm run build`，Webpack 4 构建通过。直接 `npm run build` 会因 OpenSSL/MD4 报 `ERR_OSSL_EVP_UNSUPPORTED`；Node 16 按 package.json 是原声明版本。本次未重新构建前端，也未作浏览器逐屏或目标 ClickHouse/PolarDB-X 联调。
- 已尝试离线执行 `mvn -o -Paam -DskipTests test`，但本机没有行内 Hermes/Encrypt Client 制品，构建在依赖解析阶段停止；因此未完成 `aam` profile 编译、测试或真实认证验收。采集网关的 TTFT、metadata、tags、status 映射也未验收。

## 接手顺序与未完成工作

1. 先检查 `frontend/package.json`、路由，`backend/pom.xml`、`application*.yml`、两个 SQL Provider 和目标库 DDL；**以代码/数据库为准，不以旧日志的结论为准**。
2. 优先闭合 Tracing：用同一固定 Trace 对照原版与迁移页面；复验列表、筛选、趋势、详情、时间范围及真实数据，再讨论删除/全量导出。Scores/Comments、Prompt、Evaluation 不在当前验收范围。
3. 若任务是智能体筛选，先确认 SDK 上报的稳定 `agent_id`、`agent_name`、根/子 Span 关系及查询代价；不要直接改项目鉴权或凭空生成每次调用 ID。
4. 若任务是采集映射，先取得 Collector/Sinker 实际代码并验收 TTFT 秒→毫秒、根 Trace 字段、环境/版本、metadata 前缀、状态三态、tags、Score REST 边界；当前仅有设计文件。
5. 若任务是行内 AAM，先解除启动脚本的强制 Mock、用 `-Paam` 构建并核对真实 jar 签名，再补前端 `/aamlogin` 回调与退出，最后做行内端到端验证；不要把后端 Mock 测试当作 AAM 验收。
6. 每次改动更新本文的**当前状态和验证日期**；长期过程日志放 `docs/archive/`，不要继续把多轮相互矛盾的“已完成/下一步”附加到本文末尾。

## 定位文件

- 页面：`frontend/src/features/observability/TracingPage.vue`、`TracePeekPanel.vue`、`TraceGraphCanvas.vue`；请求：`frontend/src/api/client.js`；路由：`frontend/src/router/index.js`。
- API：`backend/src/main/java/com/icbc/aiops/langfuse/api/ObservabilityQueryController.java`；查询：`backend/src/main/java/com/icbc/aiops/langfuse/mapper/TracingSqlProvider.java`、`ObservabilitySqlProvider.java`、`WidgetMetricSqlProvider.java`。
- DDL/数据：`backend/sql/clickhouse/agentobs-clickhouse-schema-{final,local}.sql`、`backend/sql/polardbx-schema.sql`；历史实施方案：`TRACING_CLICKHOUSE_REFACTOR_PLAN.md`（**已执行，非待执行任务书**）。
- 健康探针：`/icbc/hmp/agentobs/healthz` 仅用于进程/F5 检查；`/actuator/health/readiness` 含数据库，供监控；切换真实 AAM 后仍需验证 Hermes Filter 是否影响探针。
