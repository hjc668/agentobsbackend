# Langfuse Web Java 迁移进度

最后更新：2026-09-14

本文档是上下文重置或更换模型后的首要接续入口。继续开发前请先阅读本文档、`MIGRATION_MATRIX.md` 和 `design-qa.md`。

## 1. 项目目标与范围

- 将 Langfuse v4.15.0 Web 与查询/同步 CRUD 后端解耦，前后端可独立启动。
- 迁移代码位于 `/Users/mac/Desktop/icbc/Aiops/langfuse-web`。
- 前端：Vue 2.7.16 + Element UI 2.15.14 + Vite，端口 5173；React 入口已退出正式构建链路。
- 后端：Spring Boot 2.7.18 + Java 8 + MyBatis 2.3.2 注解，端口 8080。
- ClickHouse 继续存储链路数据；同步 CRUD 已由 PostgreSQL 替换为 PolarDB-X。原 Redis、Worker 和 MinIO 保持不变。
- 当前优先级：Tracing。Prompt Management 和 Evaluation 已按产品决策延期，不作为当前验收范围。

## 2. 当前架构

```text
Vue 2 + Element UI/Vite :5173
      |
      | Session cookie + CSRF token
      v
Spring Boot :8080
      |-- ClickHouse + MyBatis 注解：Tracing、Sessions、Users、指标查询
      |-- PolarDB-X + MyBatis 注解：Dashboard、Comment、已保留的 Prompt CRUD
      `-- Spring Security：AAM 身份、VIEW/ADMIN 权限与 CSRF

原 Langfuse Worker/Redis/MinIO：继续处理摄取、队列、媒体和未迁移异步任务
原 Langfuse Web :3000：保留用于 1:1 对比
```

## 3. Java 8 / Spring Boot 2.7 兼容迁移（2026-09-07）

- Spring Boot 已由 3.5.14 降至 2.7.18，编译目标由 Java 17 改为 Java 8（class major version 52）。
- MyBatis Spring Boot Starter 已切换为与 Spring Boot 2.7 兼容的 2.3.2，现有注解 Mapper 和双数据源结构保持不变。
- ClickHouse JDBC 已按行内环境要求固定为 0.4.6，并使用 `com.clickhouse.jdbc.ClickHouseDriver`；连接 URL 设置 `compress=0`，避免旧驱动与当前服务端的 LZ4 帧不兼容。
- `jakarta.*` 已切换为 `javax.*`；record、文本块、switch 表达式及 Java 9+ 集合/Stream API 均已替换为 Java 8 等价实现。
- DTO 的 JSON 字段、REST 路径和 record 风格访问方法保持兼容，单字段请求 DTO 增加显式 Jackson 构造映射。
- 后端在真实 JDK 8.0_292 上全量测试：34/34 通过；Tracing SQL、筛选、详情、Prompt/Dashboard CRUD 控制器均无回归。
- 真实 `mybatis` 模式已连接原 ClickHouse/PostgreSQL 验证：健康检查为 UP，2026-09-07 重启验收时 Trace 列表共 15,734 条，Trace 详情、Observation 列表及 TYPE facet 均返回正常。
- 正式 8080 服务已于 2026-09-07 使用 JDK 8.0_292 重启；运行时依赖核验为 Spring Boot 2.7.18 与 ClickHouse JDBC 0.4.6。临时验证端口 18080 已关闭，只保留正式实例。
- 新增 JDK 9+ Maven 激活配置，使用 `--release 8` 防止以后在高版本 JDK 构建时误引入 Java 9+ API。
- 前端无需因本次后端版本迁移修改，仍通过原 REST/JSON 契约连接后端。

## 4. 本轮已完成（需求 1、5、7）

### 4.1 真实 Trace 图谱（需求 1）

- 新增 `frontend/src/features/observability/TraceGraphCanvas.tsx`。
- Graph 不再是树列表的替代样式，而是根据 Observation 的 `parentObservationId` 绘制有向父子连线。
- 支持 Start/End 节点、节点选择、缩放、适配画布。
- `Aggregated`：按步骤名聚合，重复步骤显示调用次数，支持重复/循环步骤表达。
- `Expanded`：每次 Observation 调用独立显示，保持实际父子拓扑。
- 与右侧 Observation 详情联动。
- 真实数据验收：Trace `668904f3534ff5678327bcf81a3f5814` 有 4 个 Observation；聚合视图显示 3 个步骤，展开视图显示 4 次调用。

已知边界：图谱基于 Langfuse Observation 父子关系推导；当前 DTO 未迁移 LangGraph 专用 node/step 属性，因此不会额外生成数据库中不存在的业务节点。

### 4.2 复杂筛选（需求 5）

后端 `ObservabilitySqlProvider` 已支持：

- 文本字段的模糊、精确和负向匹配，例如 `name:auth`、`traceId:=...`、`-environment:default`。
- 数值比较：`>`、`>=`、`<`、`<=`，覆盖 latency、TTFT、TPS、token 和 cost 字段。
- 时间比较：`startTime:>=2026-09-01`。
- 标签任一/全部组合：`tags:(a OR b)`、`tags:(a AND b)`。
- 任意 Metadata 键：`metadata.resourceAttributes.env:=local-langfuse`，也支持带引号的嵌套键。
- Score：`scores.accuracy:>=0.8`，同时覆盖 Trace 级和 Observation 级 Score。
- 空值/存在判断：`has:endTime`、`-has:endTime`，并扩展到 input、output、status、version、release。
- 所有动态值通过 MyBatis 参数绑定，字段和运算符走白名单，不拼接用户值。
- 已按 Langfuse 源码的 `CategoryPresetChips`/preset catalog 补齐 Quality、Slow、Cost 三组快速筛选下拉菜单。预设包含错误/警告、Generation 审阅、遗漏工具调用、高延迟、高成本和高 Token 场景；选中项可再次点击取消。
- 顶部输入、左侧 Facet、Trace ID 和快速预设统一生成实时筛选标签。标签支持逐个删除及 `Clear all` 批量清除，删除后 URL、趋势图、Facet 和列表会使用同一查询状态重新请求。
- 为源码中的 `Missed tool calls` 预设补齐 `toolDefinitions`、`toolCalls` 数值条件，Spring Boot/MyBatis 会映射到 ClickHouse `tool_definitions` 和 `tool_calls`。

前端已增加字段提示、语法帮助、无效/未完成表达式提示、URL 持久化，并保持顶部搜索与左侧 Facet 的同一状态源。

真实数据验收：精确 Trace ID 返回 4 行；Metadata 环境筛选成功返回真实数据；未完成的 `scores.accuracy:` 显示错误提示且不会把该 token 发送给后端。

### 4.3 性能、错误、权限和并发（需求 7）

- CORS `OPTIONS` 预检明确放行；Session Cookie 请求使用显式 CSRF token。
- 参数类型错误和 Bean Validation 错误统一返回 400 `INVALID_QUERY`。
- Tomcat 线程、连接、排队参数已设置边界；ClickHouse 连接池已有超时、keepalive 和最大生命周期配置。
- 新增 SQL Provider 64 并发构造测试，验证参数隔离和无共享状态。
- 新增真实 API 验收脚本 `scripts/test-tracing-reliability.sh`。

2026-09-04 验收结果：

- 后端完整测试：36 个测试全部通过。
- 前端生产构建：1686 个模块构建通过。
- 真实 ClickHouse 基线：8 请求、并发 2，冷态 p95 3.727 秒；最终热态复测 p95 0.705 秒，全部 HTTP 200。
- 非法 `size=201`：HTTP 400。

容量边界：本机单节点 ClickHouse 的 `events_core FINAL` 全量计数仍是主要耗时点。此前 24 请求/并发 6 的压力会超过 20 秒目标，不能据此宣称已达到生产高并发容量。生产上线前需要用目标数据量重新压测，并优先考虑预聚合表/物化视图、缓存总数或移除非必要 `FINAL`。

## 5. 已修复的重要问题

- Trace ID 粘贴值会 trim，不再因前导空格查不到。
- 输入字段关键字后选择建议会替换草稿，不残留 `tra`。
- 顶部搜索与侧边 Facet 双向同步，取消选择与实际选中项一致。
- 默认使用浅色主题。
- 趋势图、表格字段、分页、排序、列显隐、详情 Tree/Timeline/Graph/Data、Preview/Log View 已可用；Scores/Comments 按当前范围暂不迁移。
- 新增项目请求头后发现 CORS 预检被误拦截，已修复并补充回归测试。
- Quality、Slow、Cost 已由单按钮改为与源码一致的预设下拉；手动筛选和侧栏筛选均实时显示为可删除标签，不再只能通过原始表达式判断当前状态。
- 筛选标签现在直接位于 Tracing 搜索框内部，并按源码显示完整表达式（如 `type:SPAN`），标签内提供 `×` 单项删除；搜索框右侧提供 `Clear all`。输入草稿与已提交标签分离，窄屏下仍可继续添加条件。
- 单项删除会清理相邻的悬空 `AND`/`OR`，避免组合查询在删除标签后变成无效表达式；`Clear all` 同时清空输入、Facet、Trace ID、预设和 URL 筛选状态。

## 6. 启动与验收

原 Langfuse Docker Compose 运行时：

```bash
cd /Users/mac/Desktop/icbc/Aiops/langfuse-web

# 终端 1：Spring Boot 连接原 ClickHouse + 新 PolarDB-X
bash scripts/start-backend-local-langfuse.sh

# 终端 2：迁移后的前端
bash scripts/start-frontend-local-langfuse.sh
```

- 迁移页面：`http://127.0.0.1:5173/traces?dateRange=30d`（旧 `/project/{id}/traces` 链接兼容解析，但 id 不再影响查询）
- 原始页面：`http://localhost:3000/project/cmt2am51r0006pa07ghcbt7vi/traces?dateRange=30d`
- 后端健康检查：`http://127.0.0.1:8080/actuator/health`

测试命令：

```bash
cd /Users/mac/Desktop/icbc/Aiops/langfuse-web/backend && mvn test
cd /Users/mac/Desktop/icbc/Aiops/langfuse-web/frontend && npm run build
cd /Users/mac/Desktop/icbc/Aiops/langfuse-web && bash scripts/test-tracing-reliability.sh
```

Observability API 不接受项目请求头，统一读取专用 ClickHouse 链路库。

## 7. 关键文件索引

- Tracing 页面：`frontend/src/features/observability/TracingPage.vue`
- Vue 应用入口：`frontend/src/App.vue`、`frontend/src/main.js`
- 前端 API：`frontend/src/api/client.ts`
- 页面样式：`frontend/src/styles.css`
- 查询 Controller：`backend/src/main/java/com/icbc/aiops/langfuse/api/ObservabilityQueryController.java`
- MyBatis Mapper：`backend/src/main/java/com/icbc/aiops/langfuse/mapper/ObservabilityMapper.java`
- 动态查询：`backend/src/main/java/com/icbc/aiops/langfuse/mapper/ObservabilitySqlProvider.java`
- AAM 权限：`backend/src/main/java/com/icbc/aiops/langfuse/config/SecurityConfig.java`
- **AAM 内网接续手册**：`AAM_INTRANET_HANDOVER.md`（到行内后照它做；§31 的执行版）
- PolarDB-X 事务库建表脚本（表名带 `langfuse_` 前缀）：`backend/sql/polardbx-schema.sql`
- PolarDB-X 旧库改名迁移：`backend/sql/polardbx-rename-tables.sql`（§29，非幂等，只跑一次）
- PolarDB-X 事务 Mapper：`backend/src/main/java/com/icbc/aiops/langfuse/postgres/mapper/`
- 完整范围矩阵：`MIGRATION_MATRIX.md`
- UI/功能验收：`design-qa.md`
- ClickHouse 表比对与查询改造方案：`CLICKHOUSE_SCHEMA_DIFF.md`

## 8. 下一步（本轮不继续实施）

Tracing 尚不能标记为全量 1:1 完成，推荐按顺序继续：

1. 对齐原版删除语义、批量删除和完整导出；Comment/Score 继续延期。
2. 将 Mock AAM verifier 替换为网关签名或远程 AAM 验票实现。
3. 对同一固定 Trace 做原页面/迁移页面逐项截图回归，补齐细节交互和响应式差异。
4. 针对 `events_core FINAL` 做生产级查询优化和目标容量压测。
5. Tracing 验收后再继续 Sessions、Users 和 Alerts；Prompt Management、Evaluation 继续延期。

## 9. 接续开发检查清单

新模型或新上下文接手时：

1. 不要重新初始化项目，也不要覆盖现有源码。
2. 先检查 Docker 节点、5173/8080/3000 端口和本文件的最后更新时间。
3. 运行后端测试（**当前基线 158/158**）、前端 build 和真实可靠性脚本。默认构建**不得**依赖任何 `com.icbc` 行内构件（§31.1）。
4. 使用固定 Trace `668904f3534ff5678327bcf81a3f5814` 验证 Graph 的 3 个聚合步骤/4 次展开调用。
5. 使用 `traceId:=...`、`metadata.resourceAttributes.env:=local-langfuse`、`type:SPAN latency:>1`、`Missed tool calls` 和无效 `scores.accuracy:` 做筛选回归；同时检查完整表达式是否在搜索框内形成标签、标签内 `×` 单删与 `Clear all`。
6. 每次完成一个垂直切片，都同步更新本文、`MIGRATION_MATRIX.md` 和 `design-qa.md`。
7. **PolarDB-X 表名一律带 `langfuse_` 前缀（§29）**：新增或修改 mapper 时不得写裸表名；只改了 mapper 却没改 `polardbx-schema.sql`（或反之）会让运行时直接报表不存在。
8. **改动前后端前先读 §29.3**：PolarDB-X **不支持 `UPDATE ... SET col = (SELECT ...)`**（`PXC-4518`），Dashboard/Widget 的更新接口当前因此在真实库上不可用；`INSERT` 同形状不受影响，这正是历史验收只测过 POST/GET/DELETE 而漏掉 PUT 的原因。
9. **AAM 相关改动先读 §31**：认证开关是 `app.auth.mode`（`mock`/`aam`），**不是 Spring profile**——用 profile 会让 mock 认证器静默生效（§31.4）。Hermes 适配器在 `src/main/aam/java`，只在 `-Paam` 下编译，本地无法验证；改动它之后必须在内网跑一次 `mvn -Paam compile`。**切换到 `aam` 模式后必须复验健康探针**（§40.5）：`@EnableAam` 可能注册全局 servlet Filter，它跑在 Spring Security 之前，`permitAll` 拦不住，`/healthz` 会被跳转导致负载均衡摘除全部节点。
10. **「本机没有浏览器自动化工具」是过时结论（§23.5 已被 §34.1 推翻）**：本机 Chrome + CDP 可用，Node 22+ 内置 `WebSocket` 即可驱动，**无需任何 npm 依赖**。做法：`chrome --headless=new --remote-debugging-port=9222`，再连 `http://127.0.0.1:9222/json/list` 拿 `webSocketDebuggerUrl`。**Trae D（Tree/Timeline/Graph/Preview/Log View 交互回归）现在可以自动化完成，不必再等人手工点击。**
11. **UI 有问题先确认服务还活着**：`curl http://127.0.0.1:8080/icbc/hmp/agentobs/healthz` + `lsof -i:8080`。存活时应返回 `@the@health@is@good@`。**健康检查必须带文根**（§38），旧根路径 `/actuator/health` 返回 404。负载均衡探针用 `/healthz`（纯文本，§40），**不要**用 `/actuator/health` 或 `readiness`——它们含数据源，数据库抖动会摘除全部节点（§40.1）。只在日志里 grep `ERROR` 会漏掉"Maven `BUILD FAILURE` / `exit code 143` 但日志前半段干净"的情况——本机发生过一次，误导排查半小时（§34.4）。另：数 DOM 用 `querySelectorAll`，**不要 grep HTML 文本**（会把内联 CSS 类名一起数进去，§34.6）。

## 10. 2026-09-05 Trace/Span 详情弹框

- 已按原始 Langfuse 源码与同一真实数据源重构详情抽屉：窄屏为全屏抽屉，提供 Tree、Timeline、Graph（多节点时）和 Data；桌面为导航/详情双栏。
- Data 详情提供毫秒时间、Latency、TTFT、Environment、Version、Release、Model、Tokens、Cost、Status 等可用属性，并提供 Preview/Log View 与 Formatted/JSON。
- Metadata 从整块 JSON 改为原版样式的 Path/Value 表；Input、Output、模型参数、Usage、Cost 按是否有值展示。
- Spring Boot 新增 `GET /api/v1/observability/traces/{traceId}/view` 聚合读模型，一次返回 Trace 和 Observation 树；前端由 4 次请求降为 1 次，不再加载 Scores/Comments。
- 真实样本 `243175040f0c1f2b37dbcb426e8d067c` 已从本地 ClickHouse 返回 Trace `auth`、Span `d8b2e9025d55a8be`、版本 `0.153.1` 和完整 Metadata。
- 后端 36 个测试全部通过，前端 TypeScript 检查与 Vite 生产构建通过。当前后端以 `mybatis` Profile 连接原 Langfuse ClickHouse，迁移前端仍运行在 5173。

## 11. 2026-09-07 Observation Type 与 Log View 对齐

- 已对照 Langfuse 源码 `packages/shared/src/domain/observations.ts` 和 `ItemBadge.tsx`：前端、Spring Boot 枚举及查询层均覆盖 10 种 Observation Type：`SPAN`、`GENERATION`、`EVENT`、`AGENT`、`TOOL`、`CHAIN`、`RETRIEVER`、`EVALUATOR`、`EMBEDDING`、`GUARDRAIL`。
- 左侧 Type Facet 与原版一样只展示当前专用链路库、时间范围和其他筛选条件下真实存在的类型，不用 0 计数补齐全部枚举。2026-09-07 的 30 天聚合结果是 `SPAN=313570`、`GENERATION=2679`，因此页面显示两个类型是数据分布结果，不是识别遗漏。
- 所有 10 种类型现已使用与原版一致的独立图标和颜色语义；新增后端枚举完整性回归测试，防止 Langfuse 类型升级时静默漏迁。
- Log View Formatted 已改为源码式表格：Observation、Depth、Start、Duration 四列，短 ID 名称、相对时间、层级、毫秒开关、缩进开关、搜索、单行展开、全部展开/收起和复制 JSON。
- 展开行仅展示非空的 Input、Output、Metadata，与源码的按需详情内容一致；JSON 模式从错误的 `{ traceId, observations }` 包装改为源码式 Observation 数组。
- JSON 与展开内容已改为可逐层折叠的高亮树视图，显示对象/数组成员数量、类型化值颜色和 Date 空对象结构，视觉及交互更接近原版 PrettyJsonView。
- 浏览器已在同一 393×757 视口对比原版和迁移版的 Formatted、JSON、行展开状态；当时前端生产构建通过，Spring Boot 完整测试为 34/34 通过。
# AAM authentication and global observability scope (2026-09-07)

- Observability API now uses `/api/v1/observability/**`; ClickHouse reads are global to this dedicated AIOps trace store and do not use browser-supplied project scope.
- `project_id` remains untouched in Langfuse ClickHouse tables. 2026-09-07 已对本机 `langfuse-clickhouse-1` 执行只读检查：`SELECT count() AS duplicate_trace_ids FROM (SELECT trace_id FROM events_core FINAL WHERE is_deleted = 0 GROUP BY trace_id HAVING uniqExact(project_id) > 1)`，结果为 `0`。当前快照中 `trace_id` 可安全作为全库详情定位键；接入历史多项目数据后须重新执行该检查，若出现重复则需改为 `(project_id, trace_id)` 复合详情定位，物理列继续保留。
- AAM boundary is session based: `POST /api/v1/auth/login`, `GET /api/v1/auth/me`, `POST /api/v1/auth/logout`, and `GET /api/v1/auth/csrf`. Mock mode currently accepts non-empty `aamId` and `ticket`; roles are server-side only. `38971135` and `admin` are ADMIN; others are VIEW.
- Browser mutations require the Cookie CSRF token (`XSRF-TOKEN` / `X-XSRF-TOKEN`); frontend requests use `credentials: include`. Replace `MockAamCredentialVerifier` with an AAM gateway-signature or remote-ticket verifier before production deployment.
- PostgreSQL CRUD uses `APP_WORKSPACE_PROJECT_ID` internally for Langfuse-compatible Dashboard, Widget and Prompt rows. It does not take a project id from browser headers or URLs.
- Review hardening completed: VIEW may log out with CSRF protection; successful login rotates the pre-login session id; gateway/remote verifier failures use stable `401 UNAUTHENTICATED` JSON; Session cookie settings are explicit (`HttpOnly`, 30 minutes, `SameSite=Lax`, production `SERVER_SESSION_COOKIE_SECURE=true`).
- AAM verifier calls now carry an independent `AamVerificationContext`: only configurable signature/timestamp/nonce/app-id headers, request path and source address are exposed to a future gateway implementation. Mock ignores this context; gateway/remote modes remain fail-closed. The ADMIN allowlist is `APP_AUTH_ADMIN_USERS` (legacy `APP_AUTH_MOCK_ADMIN_USERS` is fallback-only).
- Local backend startup now resolves `APP_WORKSPACE_PROJECT_ID` only when PostgreSQL contains exactly one project. The current local database has two project rows, so an operator must set the variable explicitly; this value is used only for PostgreSQL CRUD, never ClickHouse observability queries.
- Final compatibility validation: AdoptOpenJDK `1.8.0_292` backend test suite passed 36 active tests with zero failures/errors; frontend production build passed. Existing `target/surefire-reports` can contain a stale deleted `ProjectScopeFilterTest` report and must not be counted.

## 12. 2026-09-08 AAM 全库读取与 ClickHouse 查询稳定性

- 已由主开发直接完成并复核 AAM 会话认证、VIEW/ADMIN 服务端授权、CSRF、Session Fixation 防护，以及 Gateway/Remote 模式 fail-closed 接口；浏览器传入的角色、项目 ID 和用户 ID 均不参与授权。
- Observability 统一走 `/api/v1/observability/**`，登录用户读取专用 ClickHouse 中的全部链路。物理 `project_id` 列保留，不再作为 Web 查询条件；PostgreSQL CRUD 继续仅使用服务端 `APP_WORKSPACE_PROJECT_ID`。
- 本地真实快照核验：`events_core` 455,249 行、15,734 个 Trace；当前没有重复 `(project_id, trace_id, span_id)` 版本、没有删除版本，跨项目重复 Trace ID 为 0。
- Trace 列表改为“小字段候选分页 + 候选 Trace 聚合”，Observation 列表改为摘要查询；Input、Output、Metadata、模型参数等大 JSON 仅在 Trace 详情 `/view` 按需加载，弹框功能不受影响。
- 高频列表不再执行全表 `FINAL`；详情查询仍保留 `FINAL`。ClickHouse 默认连接增加 `max_final_threads=1`，用于限制其他保留 `FINAL` 查询的峰值并行内存。
- Observation 分页总数使用当前 append-only 快照的原始行计数。若未来摄取流程开始写入同一 Observation 的多版本或删除标记，应改用物化 latest-state 视图后再计数，不能直接恢复全表 `FINAL`。
- 真实接口连续回归 25 次：Trace、SPAN、GENERATION、`traceId:=...`、Cost 排序全部 HTTP 200，0 次失败；单次响应约 0.09–0.84 秒。
- 最终质量门禁：Java 8 + Spring Boot 2.7.18 后端 37/37 测试通过；前端生产构建通过（1687 modules）；启动脚本语法检查通过。
- 两个后端启动脚本会校验完整 Java 8 JDK，并避开 macOS 仅含 `java`、不含 `javac` 的旧浏览器 JRE。
- 2026-09-08 启动脚本再次增强：优先自动发现 JDK 8；离线机器仅有 JDK 17 等更高版本时也可直接运行（`pom.xml` 编译目标仍为 Java 8）。启动前会自动停止同项目、同 `SERVER_PORT` 的旧后端；若端口属于无关进程则拒绝误杀。
- 本地 PostgreSQL 存在多个项目时不再中断启动：脚本优先选择 ClickHouse 链路行数最多且在 PostgreSQL 中存在的项目作为 CRUD 工作区；无交集时确定性选择 PostgreSQL 最近更新项目。显式 `APP_WORKSPACE_PROJECT_ID` 始终具有最高优先级。
- 前端启动脚本支持 `pnpm`/`npm` 自动降级，已有 `node_modules` 时跳过安装以适配离线环境；启动前自动停止同项目、同 `FRONTEND_PORT`（默认 5173）的旧 Vite 进程，并拒绝误停无关进程。

## 13. 2026-09-08 PolarDB-X 替换 PostgreSQL

- 事务数据库依赖已替换为 `com.alibaba.polardbx:polardbx-connector-java:2.2.12`，并显式引入其 MySQL 协议依赖 `com.mysql:mysql-connector-j:8.0.33`；驱动类为 `com.alibaba.polardbx.Driver`。
- `mybatis` Profile 使用 `app.datasource.polardbx`，环境变量统一为 `POLARDBX_JDBC_URL`、`POLARDBX_USER`、`POLARDBX_PASSWORD` 以及连接池对应的 `POLARDBX_*` 参数。
- Dashboard、Widget、Comment 和保留的 Prompt 兼容 Mapper 已从 PostgreSQL 方言迁移到 PolarDB-X/MySQL 方言：JSONB/数组/枚举 Cast、`ILIKE`、`DISTINCT ON` 和 advisory lock 均已替换。
- Prompt 并发版本号保护改为 `prompt_locks` 行锁；创建版本时先 `INSERT IGNORE` 锁记录，再在同一事务内执行 `SELECT ... FOR UPDATE`。
- 新增 `backend/sql/polardbx-schema.sql`，包含 Java 后端实际访问的 projects、users、comments、dashboards、dashboard_widgets 及保留 Prompt 兼容表，并初始化 `aiops-default` 工作区和 `migration-service` 审计用户。
- ClickHouse Tracing 查询、AAM Session/VIEW/ADMIN 权限和前端 REST 契约未改动。旧 PostgreSQL 数据不会自动搬迁；如需保留历史 Dashboard/Comment，须另行执行一次性数据迁移。
- Java 8.0_292 完整后端回归为 40/40 通过；新增测试会直接加载 PolarDB-X Driver、MySQL Driver 和 `JoranException`。依赖树确认运行时仍为 Spring Boot 管理的 Logback 1.2.12，不受驱动 POM 中 `provided` 日志依赖影响。前端生产构建通过（1687 modules）。
- 当前环境没有可用的目标 PolarDB-X 实例和账号，因此尚未执行真实建表/CRUD 联调；接入环境后首个验收动作应是执行 SQL 脚本并验证 `/actuator/health`、Dashboard CRUD 和 Trace 详情读取。

## 14. 2026-09-09 Vue 2 + Element UI 正式切换与 Tracing 校准

- 正式 `frontend` 已切换为 Vue 2.7.16、Element UI 2.15.14 和 Vite；`index.html` 入口为 `src/main.js`，生产依赖不再包含 React。
- Tracing 保持浅色主题，并对齐原版工作台结构：顶部筛选编辑器、Quality/Slow/Cost 快捷入口、Table/Chart、左侧 Facet、真实趋势图、观测表格和详情抽屉。
- 筛选输入草稿与已提交条件分离。输入 `tra` 后选择 Trace ID 会替换为 `traceId:`；粘贴值前的空格会被清理；提交后生成框内 Tag，支持单项 `×` 和 `Clear all`，URL、左侧 Environment/Type Facet、趋势与列表共享同一筛选状态。
- 趋势图已由静态占位柱切换为 `/api/v1/observability/observations/pulse` 的真实 ClickHouse 聚合，并随 24h/7d/30d/90d、筛选和 Count/Cost/Latency 指标变化。
- Trace/Span 详情使用单次 `/api/v1/observability/traces/{traceId}/view` 聚合请求；保留 Tree、Timeline、Data、Preview、Log View、Formatted、JSON、Input、Output 与 Metadata，按要求不展示 Scores 和 Comments。
- 已在真实 ClickHouse 数据上浏览器验证：Trace ID `a874a391240d48d9cfcfe66ea874b9cb` 精确筛选返回 2 个 Observation；Tag 删除后恢复 30 天结果；详情抽屉、Log View 和 JSON 切换正常。
- JSON 模式使用递归可折叠树展示，包含对象/数组成员数量和字符串、数字、布尔值、空值的语义配色；Log View 的工具栏在窄屏下不再重叠。
- 正式目录离线安装完成并通过 Vite 生产构建：244 个模块，退出码 0。旧 `.tsx` 文件仅作为迁移参考保留，不再由 `index.html`、依赖清单或生产构建引用。

## 15. 2026-09-09 Filters 交互与 Mock 联调补全

- 左下角账号入口及窄屏顶部头像共用统一的字体、字号、字重、颜色、行高和菜单项状态；窄屏菜单脱离隐藏侧栏渲染，点击头像后可正常显示语言切换和退出操作。
- Filters 的 Environment、Type、Is Root Observation 中 Select/Text 已绑定真实模式切换；Text 模式支持 Enter 或“应用”，折叠筛选项也可提交为顶部筛选 Tag。
- Filters 清除按钮、单项勾选、单个 Tag 删除和 Clear all 均联动 URL、列表、Facet 和趋势数据；Type 展示覆盖 SPAN、GENERATION、EVENT、AGENT、TOOL、CHAIN、RETRIEVER、EVALUATOR、EMBEDDING、GUARDRAIL 共 10 种。
- Mock 服务新增 `trace-types-004` 及 5 个补充 Observation，使 10 种 Type 均有可渲染数据；每条详情包含 Input、Output、Metadata、Usage、Cost、Model Parameters 等详情抽屉所需结构。
- Mock 查询实现支持 `type:`、`environment:`、`root:`、Trace/User/Session/Tag、状态、模型、输入输出、元数据、延迟/Token/Cost 数值比较等常用筛选 DSL；列表、Facet 和 Pulse 使用同一套过滤逻辑。
- 新增 `scripts/start-backend-mock.sh`：自动选择 Java 8、停止本项目旧的 8080 后端、固定启用 mock Profile，不依赖未启动的 PolarDB-X 或 ClickHouse。
- 验证结果：Vue 2 + Element UI 前端生产构建通过（244 modules）；Java 8 完整后端回归 41/41 通过，其中 Mock Observability 控制器专项测试 10/10 通过，包括 10 类 Facet、Type/Environment/Root 筛选和 Pulse 联动。

## 16. 2026-09-10 移除 com.mysql 依赖以适配内网

**背景**：内网 Maven 私服没有 `com.mysql` 坐标，需确认 `backend/pom.xml` 中的 MySQL 驱动能否换成旧坐标 `mysql:mysql-connector-java`。

**核查结论（本地实证，不要凭记忆推翻）**：

- `mysql:mysql-connector-java:8.0.33` **不能**解决问题：该版本目录下只有 `.pom` 没有 `.jar`，是一份 `<distributionManagement><relocation>` 空壳，Maven 会继续跳转下载 `com.mysql:mysql-connector-j:8.0.33`，内网依然失败。旧坐标下**最后一个含真实 jar 的版本是 8.0.30**（8.0.31 起全部是重定向 POM），本地仓库 `8.0.23` 为真实 jar 可直接对比验证。
- PolarDB-X Connector **自包含**，不需要独立 MySQL 驱动：`polardbx-connector-java-2.2.12.jar` 自带 `com/alibaba/polardbx/Driver.class`、`core/jdbc/Driver.class` 和 `core/cj/jdbc/*`（整个 Connector/J 被重命名内嵌），全 jar 对 `com.mysql` 的类/字符串引用数为 **0**；其 POM 也只声明 slf4j/log4j/logback/javassist，且全部为 `provided`，没有任何 MySQL 驱动依赖。
- 生产代码与配置均未引用 `com.mysql`：`application-mybatis.yml` 的 `driver-class-name` 是 `com.alibaba.polardbx.Driver`，全仓库对 `com.mysql` 的唯一引用是测试断言。

**关键约束：`POLARDBX_JDBC_URL` 必须保持 `jdbc:polardbx://` 前缀**。实测 `com.alibaba.polardbx.Driver` 的 `acceptsURL("jdbc:polardbx://…")` 返回 `true`，而 `acceptsURL("jdbc:mysql://…")` 返回 **`false`**；因此一旦去掉独立 MySQL 驱动，任何 `jdbc:mysql://` 连接串都会直接报 "No suitable driver"。当前 yml 默认值、README 与启动脚本示例均为 `jdbc:polardbx://`，无需修改，但后续不得改回。

**修改内容**：

- `backend/pom.xml`：删除 `com.mysql:mysql-connector-j:8.0.33` 依赖块与 `mysql-connector.version` 属性；PolarDB-X 依赖旁改为说明「自包含、内网无 com.mysql 坐标、URL 必须用 jdbc:polardbx://」的注释。
- `backend/src/test/java/com/icbc/aiops/langfuse/mapper/PolarDbxDialectContractTest.java`：`java8RuntimeCanLoadDatabaseAndLoggingClasses` 移除 `Class.forName("com.mysql.cj.jdbc.Driver")` 断言；新增 `noStandaloneMysqlConnectorOnClasspath`，断言 `com.mysql.cj.jdbc.Driver` **不在** classpath 上，防止该依赖被重新引入（内网构建失败且与 PolarDB-X 冗余）；`mybatisProfileUsesPolarDbxDriverAndEnvironmentVariables` 增加 `jdbc:polardbx://` 存在、`jdbc:mysql://` 不存在的断言。

**验证结果（Java 8.0_292 + Maven 3.9.1）**：

- `mvn test`：**42/42 通过**，0 失败 0 错误 0 跳过。注意 `target/surefire-reports` 中仍有 2026-09-07 的陈旧的 `ProjectScopeFilterTest.txt`（4 个用例，源码已删除），按 §13 的既有警告**不得计入**；剔除该文件后总数才等于 42（上次基线 41 + 本次新增 1）。
- `mvn dependency:tree`：与数据库相关的依赖只剩 `spring-boot-starter-jdbc` + `HikariCP 4.0.3`、`clickhouse-jdbc 0.4.6`、`polardbx-connector-java 2.2.12`；全树（含传递依赖）匹配 `com.mysql` / `mysql-connector` 的行数为 **0**，`BUILD SUCCESS`。
- 独立探针（classpath 仅含 PolarDB-X 一个 jar，无任何 MySQL 驱动）：`driver.connect("jdbc:polardbx://…")` 与 `DriverManager.getConnection("jdbc:polardbx://…")` 均抛出 `com.alibaba.polardbx.core.cj.jdbc.exceptions.CommunicationsException: Communications link failure`，即**已到达 TCP socket 层**，而非 `NoClassDefFoundError`/`ClassNotFoundException`。
- 真实 `mybatis` Profile 启动（临时端口 18080，未影响 8080 正式实例）：`Started LangfuseQueryApplication in 4.954 seconds`，Tomcat 正常监听，日志中无任何类加载错误、无 `com.mysql` 字样。运行期由 `/actuator/health` 的 `DataSourceHealthIndicator` 触发 PolarDB-X 连接池，失败栈为 `DriverDataSource.getConnection` → `com.alibaba.polardbx.Driver.connect(Driver.java:356)` → `HaManager.getManager` → `HaManager.probeClusterId` 抛 `NullPointerException`。该 NPE 是 PolarDB-X 在目标不可达时的自身行为，**与本改动无关**——HikariCP 会直接对 `driver-class-name` 指定的驱动实例调用 `connect()`，`jdbc:polardbx://` 从来不经由 MySQL 驱动，故改动前后该路径行为完全一致。
- 前端本轮未改动，仍执行生产构建回归：Vite 7.3.5 构建通过，**244 modules transformed**，退出码 0（与 §14/§15 记录的模块数一致）。

## 17. 2026-09-10 PolarDB-X Docker 实例与真实数据联调

### 17.1 实例搭建

- 镜像选用**一体化镜像 `polardbx/polardb-x:latest`**（构建于 2024-05-14，内核 `5.4.19`，即 PolarDB-X 2.4.x 线），它同时包含 CN（GalaxySQL）、DN（GalaxyEngine）和 CDC。
  注意：`polardbx/polardbx-sql` 与 `polardbx/polardb-engine` 是**组件镜像**，单独启动无法提供 8527 服务，不要误用。
- 启动命令（本机 Docker VM 仅 7.67 GB 内存，故将 `mem_size` 由默认 4096 压到 1024）：

```bash
docker run -d --name polardbx-playground \
  -p 8527:8527 \
  -e mem_size=1024 \
  -e disk_size=4096 \
  --restart unless-stopped \
  polardbx/polardb-x:latest
```

- 就绪判据：容器内 `SELECT VERSION()` 返回 `5.6.29-PXC-5.4.19-SNAPSHOT`。CN 的 JVM 启动较慢，日志打印 `try polardb-x by: mysql -h127.1 -P8527 -upolardbx_root` 后仍需等待约 30–60 秒，期间连接会被拒绝，属正常现象。
- 默认账号 `polardbx_root` / `123456`，与 `application-mybatis.yml` 的默认用户名一致。全部容器内存合计约 5.1 GB。

### 17.2 关键发现：连接器 2.2.12 的 HA 驱动与 2.4.x 服务端不兼容

**这是本轮最重要的结论，接入内网前必须先确认服务端版本。**

`com.alibaba.polardbx.Driver`（签入配置使用的驱动）的连接建立在 `HaManager` 集群探测之上，它依赖以下服务端对象：

| 连接器 2.2.12 的探测依赖 | PolarDB-X 5.4.19 实测结果 |
|---|---|
| `information_schema.alisql_cluster_global` | ❌ `ERROR 4642 (HY000) ERR_UNKNOWN_TABLE` |
| `information_schema.alisql_cluster_local` | ❌ `ERROR 4642 (HY000) ERR_UNKNOWN_TABLE` |
| `SET session ping_mode='...'` | ❌ `ERROR 1193 Unknown system variable 'ping_mode'` |
| `@@cluster_id` | ✅ 存在（=1234） |

失败表现为连接阶段直接抛出：

```
java.sql.SQLException: Communications link failure
No available nodes meet the conditions in 18000 ms.
    at com.alibaba.polardbx.HaManager.getAvailableAndAddRefWithWait(HaManager.java:988)
    at com.alibaba.polardbx.Driver.connect(Driver.java:361)
```

说明该驱动面向 **AliSQL X-Cluster 架构**的旧版 PolarDB-X，而 5.4.19 已改用 GMS/GalaxyEngine 架构，`alisql_cluster_*` 表与 `ping_mode` 均已移除。连接器内**没有**任何关闭 HA 的开关（全部属性仅有 `ha.enableJMX`、`ha.loadBalanceStrategy`、`polardbx.logType`）。

### 17.3 解决方案：改用连接器内自带的普通驱动

**同一个 `polardbx-connector-java:2.2.12.jar` 内另有一套不含 HA 的驱动**，实测可用：

| 驱动类 | `acceptsURL(jdbc:polardbx://)` | `acceptsURL(jdbc:mysql://)` | 是否走 HaManager | 对 5.4.19 |
|---|---|---|---|---|
| `com.alibaba.polardbx.Driver` | true | false | 是 | ❌ 不可用 |
| `com.alibaba.polardbx.core.jdbc.Driver` | false | true | **否** | ✅ 可用 |
| `com.alibaba.polardbx.core.cj.jdbc.Driver` | false | true | 否 | ✅ 可用 |

因此本地联调改用 `com.alibaba.polardbx.core.jdbc.Driver` + `jdbc:mysql://` URL。**无需新增任何 Maven 依赖**（驱动就在现有连接器 jar 内），故 §16 的内网约束继续成立。

数据库 URL 和驱动仍通过环境变量覆盖（前缀 `app.datasource.polardbx`，Spring Boot 松散绑定把 `driver-class-name` 映射为 `DRIVER_CLASS_NAME`）：

```bash
export POLARDBX_JDBC_URL='jdbc:mysql://127.0.0.1:8527/langfuse_web?useSSL=false&characterEncoding=UTF-8&serverTimezone=UTC&connectTimeout=5000&socketTimeout=30000'
export POLARDBX_USER='polardbx_root'
export POLARDBX_PASSWORD='<local-container-password>'
export APP_WORKSPACE_PROJECT_ID='aiops-default'
export APP_DATASOURCE_POLARDBX_DRIVER_CLASS_NAME='com.alibaba.polardbx.core.jdbc.Driver'
export APP_AUTH_MODE='mock'
bash scripts/start-backend-local-langfuse.sh
```

这样做是刻意的：客户内网真实 PolarDB-X 若是 X-Cluster 旧版本，签入的 `com.alibaba.polardbx.Driver` 才是正确配置，**不应为了迁就本地 Docker 环境而改动生产配置**。

同理，§16 新增的断言（yml 中必须是 `jdbc:polardbx://`、不得出现 `jdbc:mysql://`）描述的仍是签入配置的默认值，**该断言依然成立、无需回退**；本地覆盖只发生在运行期的环境变量层。

### 17.4 建表与真实数据验证

- `backend/sql/polardbx-schema.sql` 执行成功：创建 `langfuse_web` 库与 9 张表（`projects`、`users`、`comments`、`dashboards`、`dashboard_widgets`、`prompts`、`prompt_locks`、`prompt_protected_labels`、`prompt_dependencies`），并写入 `aiops-default` 工作区与 `migration-service` 审计用户。
- schema 依赖的 `JSON` 列、`DATETIME(3)`、`ON UPDATE CURRENT_TIMESTAMP(3)` 语法在 5.4.19 上**全部可用**（已用独立探针建表并做读写往返验证）。
- ClickHouse 真实数据核对：`events_core FINAL WHERE is_deleted = 0` 为 **455,249 行 / 15,734 个 Trace**，时间范围 2026-08-21 ~ 2026-09-07（与 §12 一致）。注意数据止于 09-07，**`dateRange=24h` 会返回空**，本地验证请用 `30d`。

浏览器联调启动后的实测结果：

| 验证项 | 结果 |
|---|---|
| `/actuator/health` | `{"status":"UP"}`（PolarDB-X 不可达时为 503/DOWN，故该值即证明两个数据源均连通） |
| 链路列表 | HTTP 200，`total = 455249`，返回真实 span（`auth`、`configRequirements/read` 等） |
| TYPE Facet | `SPAN=451536`、`GENERATION=3713` |
| Pulse 趋势 | HTTP 200，按 DAY 分桶返回 |
| PolarDB-X 读 | `GET /api/v1/workspace/dashboards` → HTTP 200 正常分页 |
| PolarDB-X 写 | `POST` → HTTP 201，直查 `langfuse_web.dashboards` 确认落库（`project_id=aiops-default`） |
| PolarDB-X 删 | `DELETE` → HTTP 204，表行数归 0，测试数据已清理 |
| CORS 预检 | 5173 → 8080 返回 200，`Allow-Credentials: true`，`Allow-Headers` 含 `x-xsrf-token` |

### 17.5 风险与后续

- **核对待接入环境的 PolarDB-X 版本是第一优先级。** 若客户内网为 2.4.x 或更新（`alisql_cluster_*` 已移除），签入的 `com.alibaba.polardbx.Driver` 会在**连接阶段**直接失败，与本地现象一致；此时需要把签入配置改为 `com.alibaba.polardbx.core.jdbc.Driver` + `jdbc:mysql://`，并同步调整 §16 新增的两条 URL 断言。若客户内网是 X-Cluster 旧版本，则现有配置正确，无需改动。
- 本地 Docker 实例以 `mem_size=1024` 运行（Docker VM 仅 7.67 GB）。若后续出现 OOM 或性能异常，可在 Docker Desktop 中调大内存后以更大 `mem_size` 重建容器。
- 本轮**未改动任何签入的源码、配置或测试**，`mvn test` 基线仍为 §16 的 42/42。
- 前后端当前均以真实数据运行：后端 `mybatis` Profile（8080），前端 Vite（5173，`VITE_DATA_SOURCE_LABEL="Live ClickHouse · MyBatis"`）。

### 17.6 排障记录：HikariCP 连接池被"毒化"导致 PolarDB-X 持续不可用

**现象**：后端进程存活、HTTP 可响应、登录正常、ClickHouse 查询正常（返回 455,249），但 `/actuator/health` 持续 `DOWN`/503，且 `/api/v1/workspace/**` 返回：

```json
{"code":"DATA_SOURCE_UNAVAILABLE","message":"Langfuse data source is temporarily unavailable. Check ClickHouse/PolarDB-X connectivity and retry."}
```

**排查过程（结论：不是配置问题，也不是基础设施问题）**：

- 宿主机 `nc` 到 8527 通；用项目连接器从宿主机直连 PolarDB-X 执行 `SELECT COUNT(*) FROM dashboards` **成功**。
- PolarDB-X 容器 `Up`，容器内 CN 响应正常；`SHOW PROCESSLIST` 显示**只有 CDC 内部连接，没有任何来自应用的连接**——即应用侧连接池是空的、已卡死。
- 用**完全相同的 HikariCP 参数**（同一驱动、同一 URL、同一账号、`maximumPoolSize=8`、`connectionTimeout=5000`、`validationTimeout=3000`、`idleTimeout=60000`、`maxLifetime=300000`、`keepaliveTime=30000`、`connectionTestQuery=SELECT 1`）独立起池，**一次成功**，池状态 `0 active / 1 idle / 1 total`。

**根因**：HikariCP 在池初始化失败后会把数据源置为**永久失败状态**，后续每次 `getConnection()` 都重抛同一异常、不会自愈。该实例在冷启动时连接池初始化失败后即被毒化。

**触发因素**：本机曾发生系统时钟跳变（旧实例日志记录 `Thread starvation or clock leap detected (housekeeper delta=8m29s)` 与 `Retrograde clock change detected (housekeeper delta=29s)`，为休眠/唤醒所致）。时钟异常会干扰 HikariCP 的 `maxLifetime` 淘汰与连接校验逻辑。

**恢复方式：重启后端进程即可**，无需改动任何配置或重建容器。重启后日志出现：

```
Using PolarDB-X driver: com.alibaba.polardbx.core.jdbc.Driver
Using Hikari pool: HikariPool(Mysql)
HikariPool(Mysql) - Start completed.
```

重启后复验：`/actuator/health` 连续三次 `UP`；ClickHouse 列表 `total=455249`；PolarDB-X `GET` 200 / `POST` 201（直查确认落库）/ `DELETE` 204（已清理）。

**运维提示**：若再次出现"ClickHouse 正常但 PolarDB-X 报 DATA_SOURCE_UNAVAILABLE 且健康检查 DOWN"，应**优先重启后端进程**，而不是去排查 PolarDB-X 容器——本节的排查路径已证明容器与网络通常是好的。同时应留意 macOS 休眠/唤醒引起的时钟跳变，它是已知诱因。

## 18. 2026-09-10 本地 PolarDB-X 启动脚本与真实回归

- `scripts/start-backend-local-langfuse.sh` 现在可自动识别 `polardbx-playground`：检查容器运行状态与账号、幂等执行 `backend/sql/polardbx-schema.sql`、默认使用 `aiops-default`，并在启动前安全停止本项目占用 8080 的旧后端。
- 本地 5.4.19 容器自动使用连接器 2.2.12 内置的 `com.alibaba.polardbx.core.jdbc.Driver` + `jdbc:mysql://127.0.0.1:8527`；显式配置内网 `jdbc:polardbx://` 时仍使用 `com.alibaba.polardbx.Driver`，两种环境互不污染。
- 本地容器从宿主机访问必须提供密码；脚本要求通过 `POLARDBX_PASSWORD` 注入，拒绝把密码写入仓库。容器内免密 `mysql` 成功不代表宿主机 JDBC 可以免密。
- `application-mybatis.yml` 的 Hikari 属性保持正确键名 `pool-name`，默认池名改为内网要求的 `HikariPool(Mysql)`，可由 `POLARDBX_HIKARI_POOL_NAME` 覆盖。
- 用户提供的 `com.alibbaba.polardbx.Driver` 拼写不存在；连接器 2.2.12 中可加载的生产驱动是 `com.alibaba.polardbx.Driver`。错误拼写会直接触发 `ClassNotFoundException`。
- Java 8 + Spring Boot 2.7.18 回归：Maven **42/42** 通过，启动脚本 `bash -n` 通过；真实后端以 `mybatis` Profile 在 8080 启动。
- `/actuator/health` 返回 HTTP 200 `UP`，日志确认 `langfuse-clickhouse` 与 `HikariPool(Mysql)` 均 `Start completed`。
- Mock AAM 管理员登录成功；真实 Tracing 查询返回 **15,734 个 Trace**，首条为 `a874a391240d48d9cfcfe66ea874b9cb`。
- PolarDB-X Dashboard 完成 POST 创建、GET 读取、DELETE 删除，删除返回 204，数据库直查临时记录为 0，测试数据已清理。
- 当前后端 PID 为 33409，端口 8080；原 Langfuse 3000、迁移前端 5173 与本后端可以并行运行。

## 19. 2026-09-10 单一 Local 配置与启动脚本精简

- 按最新要求取消生产 Profile，只保留一套可运行的本地真实数据库配置；§18 的“脚本探测容器并注入变量”方案已废弃，以本节为准。
- `application.yml` 的默认 Profile 改为 `mybatis`，IDEA 使用 JDK 8 直接运行 `LangfuseQueryApplication` 即可，不需要填写 Active Profiles、VM options 或数据库环境变量。
- `application-mybatis.yml` 已直接保存本地 ClickHouse 与 PolarDB-X 的 URL、驱动、用户名、明文密码、连接池参数和 `HikariPool(Mysql)` 池名；工作区固定为 `aiops-default`。
- 本地 PolarDB-X 5.4.19 继续使用已实测兼容的 `com.alibaba.polardbx.core.jdbc.Driver` + `jdbc:mysql://127.0.0.1:8527`。这套驱动仍来自 `polardbx-connector-java:2.2.12`，没有重新引入 `com.mysql` 依赖。
- 删除临时创建的 `application-local.yml` 和 `application-prod.yml`，避免 Profile 合并顺序反向覆盖本地驱动。
- `scripts/start-backend.sh` 与 `scripts/start-backend-local-langfuse.sh` 均精简为：选择 Java 8、停止本项目旧 8080 进程、执行 `mvn spring-boot:run`；脚本不再包含数据库地址、账号、密码、容器名、工作区或连接池变量。
- `scripts/start-backend-mock.sh` 只额外通过 Maven 参数选择 `mock` Profile，不再导出业务配置变量。
- 用无环境变量的 `bash scripts/start-backend.sh` 实测启动成功，日志显示默认 Profile `mybatis`，Java 1.8.0_292，后端 PID 48701；`/actuator/health` 返回 HTTP 200 `UP`。
- Mock Profile 在 `application-mock.yml` 中单独固定 `demo-project`，避免真实工作区 `aiops-default` 影响 Mock 数据；Java 8 全量回归最终为 **42/42** 通过。

## 20. 2026-09-10 ClickHouse 表比对与查询改造方案

- 新增文档 **`CLICKHOUSE_SCHEMA_DIFF.md`**（553 行），比对 Langfuse 原始 ClickHouse 表与 `backend/sql/agentobs-clickhouse-schema-final.sql`（AgentObs）的字段与配置差异，并给出查询改造方案。
- 该 SQL 文件与微信收到的 `agentobs-clickhouse-schema-final.sql` 经 `diff` 验证**完全一致**（15402 字节）。
- A 侧结构全部来自 `SHOW CREATE TABLE` 实测，非文档推测：`events_core` / `events_full` 各 75 列、`observations` 36 列、`traces` 19 列、`scores` 27 列；实测数据基线 455,249 行 / 15,734 Trace，跨度 17 天。
- 核实后端实际只查询三张表：`FROM events_core`（14 处）、`FROM scores`（3 处）、`FROM events_full`（1 处）；`FROM observations` 与 `FROM traces` 均为 **0**（代码中的同名标识符只是 Java 变量名与 REST 路径，不是表引用）。
- B 侧为 8 张事实表 + 7 张 Distributed 表：`traces`(24 列)、`observations`(66)、`scores`(14)、`span_events`(11)、`span_links`(9)、`logs`(23)、`metric_points`(40)、`trace_locator`(5)。
- **三个硬性阻断点**：① B 侧全部事实表带 **7 天 TTL**，而 A 侧无 TTL，前端却有 30d/90d 时间范围，属产品语义冲突；② B 侧排序键首列是 `service_name`，而后端从不按该列过滤（`project_id` 也已是 0 引用），切换后主键索引无法命中；③ B 侧无 `is_deleted` 与 `event_ts`（后端分别依赖 12 处 / 9 处），删除语义丢失、版本排序语义由业务时间变为写入序号。
- 字段级缺口另含：当前 B 侧 observations 已有 JSON String 形式的 `tool_definitions`，但仍无 `tool_calls` / `tool_call_names`；此外无 `model_id`、无 `release`。`completion_start_time`（时刻）在 B 侧只有 `time_to_first_chunk_ms`（时长），语义不同；traces 表无 usage/cost，Trace 级 tokens、cost、span 数需另行聚合。
- 改造方案给出三条路径：**兼容视图**（过渡，但 UNION ALL + JOIN 无法下推，且仍需改 `event_ts`）、**查询重写**（推荐，直查拆分表，聚合语句可大幅简化）、**物化兼容层**（长期稳态），并配套给出 `hmp_agentobs_trace_metrics` 预聚合表 DDL 以补足 Trace 级指标并解决主键不匹配问题。
- 文档列出 10 项风险与 5 个待业务确认问题（7 天 TTL 是否硬性、上游是否产生删除记录、`release`/`model_id`/tool 筛选能否放弃、Trace 级 tokens/cost/span 数是否列表必需、B 侧是否上集群）。
- **本轮未改动任何后端查询代码与 schema**，仅新增比对文档；`mvn test` 基线仍为 42/42。

## 21. 2026-09-10 Tracing 切换 AgentObs 新表执行方案

- 新增根目录文档 `TRACING_CLICKHOUSE_REFACTOR_PLAN.md`，作为后续模型实施 Tracing 查询重构的执行契约。
- 方案以 `backend/sql/clickhouse/agentobs-clickhouse-schema-final.sql` 当前内容为权威输入，明确采用 Trace/Observation 拆表直查，不将新表长期伪装成旧 `events_core`。
- 方案列出 Mapper/SQL Provider/Rows 的解耦边界、现有 HTTP/DTO 保持策略、列表分页后二次补齐 Trace Name/Tags、locator 详情查询、Facet/Pulse 共用谓词、筛选 DSL 新字段映射及性能约束。
- 增加小批量调试数据平移章节：按完整 Trace 迁移，提供批次记录、Trace 摘要合成、Observation 66 列映射、行数/父子关系/接口验收和仅限本地的回滚步骤。
- 只读实测确认目标 `hmp_agentobs_*` 表尚未创建；旧表仍为 `events_core/events_full` 各 455,249 行、`scores` 0 行。已选取简单 SPAN、含 GENERATION 和较深链路三类候选样本。
- 本轮仅产出方案并更新进度文档，**未创建新表、未平移数据、未修改后端查询代码**。

## 22. 2026-09-10 执行 Tracing 重构方案阶段 A 与阶段 B

按 `TRACING_CLICKHOUSE_REFACTOR_PLAN.md` 实施，阶段 A、B 均已完成并验收。

### 22.1 阶段 A：本地新表与调试数据

- 新增 `backend/sql/clickhouse/agentobs-clickhouse-schema-local.sql`：从 final DDL 派生，只做单机化变换（去 `ON CLUSTER`、`ReplicatedReplacingMergeTree(path,replica,ingestion_version)` → `ReplacingMergeTree(ingestion_version)`、`ReplicatedAggregatingMergeTree` → `AggregatingMergeTree()`、8 张 Distributed `_all` → 本地同名 View），**列/分区/排序键/索引/TTL/MV 与 final DDL 逐列一致**（脚本核对 8 张表列名与顺序全部相同）。
- 新表已建：8 张本地表 + 8 个 `_all` View + `trace_locator_mv`。
- 新增 `backend/sql/clickhouse/migrate-tracing-debug-sample.sql`：按完整 Trace 平移 3 条样本（`a874a391…` 2 个 Observation、`ba8298c8…` 11 个、`c4b4ad22…` 8 个，均含 1 个唯一根节点且全部已结束）。
- 验收全通过：行数 2/2、11/11、8/8 OK；每条 Trace 恰好一条摘要；**无悬空父节点**；locator MV 由 observations 自动回填；类型覆盖 SPAN+GENERATION。旧表 `events_core`/`events_full` 保持 455,249 行未被修改。
- **对方案 SQL 的四处修正**（已在方案与脚本注释中回写）：
  1. §10.3 的 `min(start_time) AS start_time` 会因 ClickHouse 别名文本替换报 `Code 184 ILLEGAL_AGGREGATION`（同时污染 `coalesce(max(end_time), min(start_time))` 与多处 `argMinIf(…, start_time, …)`）；派生的 `level` 别名同样污染 `status_code` 的 `countIf(level='ERROR')`。已改为聚合列别名化（`min_start_time`/`max_end_time`/`trace_level`）再于外层投影。
  2. §10.4 的 `coalesce(end_time, start_time) AS end_time` 有循环别名隐患，已改名为 `resolved_end_time`。
  3. §10.5 的 `FROM … FINAL AS o` 是语法错误，正确写法是 `FROM … AS o FINAL`。
  4. **整份脚本不能一次 `--multiquery` 执行**：同会话内语句间内存累积，§10.4 会触发 `Code 241 MEMORY_LIMIT_EXCEEDED`（约 6.9 GiB，cgroup 推算上限）；分节独立执行则全部通过。已写入脚本头部。

### 22.2 阶段 B：Tracing 查询解耦

- 新增 `TracingSqlProvider`(711 行)、`TracingMapper`(81 行)、`TracingRows`(214 行)、`TracingSqlProviderTest`(16 个用例)。
- `ObservabilitySqlProvider` 由 668 行精简至 183 行，仅保留 Dashboard summary、Trace scores、Sessions、Users；`ObservabilityMapper` 移除 8 个已迁移的 Tracing 方法。
- `ObservationFacet` 新增 `SERVICE_NAME`；`ObservationQuery` 新增可选 `serviceName`；`/observations`、`/observations/facets`、`/observations/pulse` 新增可选 `serviceName` 查询参数，旧前端不传时行为不变。
- 查询模型：Trace 列表用**时间窗受限**的 LEFT JOIN 聚合补齐 tokens/cost/observationCount；Observation 列表用**当前页 trace_id 批量查询**补齐 traceName。二者都非无界 JOIN。
- 期间修复一个真实缺陷：`OBSERVATION_DETAIL_COLUMNS` 引用了派生别名 `usageDetailsJson` 但派生子查询未定义它，导致 `/traces/{id}/view` 返回 503（`UNKNOWN_IDENTIFIER`）。现已按 `usage_present` 过滤键后生成，并补了针对性单测。

### 22.3 验证结果

- `mvn test`（Java 8.0_292）：**51/51 通过**，0 失败 0 错误（已排除 2026-09-07 的陈旧 `ProjectScopeFilterTest` 报告）。
- 前端 `npm run build`：Vite 构建通过，退出码 0。
- 真实 ClickHouse 端到端（`mybatis` Profile，8080）：
  - `/observations?traceId=c4b4ad22…` → HTTP 200，`total=8`，SPAN×7 + GENERATION×1，traceName 全部补齐；
  - `/traces/c4b4ad22…/view` → HTTP 200，`observationCount=8` 与实际一致，**无悬空父节点**，GENERATION `get_model_info` / model `codex-auto-review`；
  - `/traces` → `total=3`，观测数 2/11/8 与详情吻合；
  - Facet：`TYPE` SPAN=20/GENERATION=1、`ENVIRONMENT` default=21、`SERVICE_NAME` codex-app-server=21（20+1=21 自洽）；
  - 筛选：`type:GENERATION`=1、`type:SPAN`=20、`-type:SPAN`=1、`root:true`=3 全部自洽；
  - `serviceName=codex-app-server` → 21，`serviceName=nonexistent-service` → 0，`serviceName:` DSL → 21；
  - 7 个核心端点全部 HTTP 200。

### 22.4 未完成与风险

- 阶段 C（筛选语义闭合到前端）、D（详情与前端浏览器回归）、E（压测与删除旧 Tracing 残留）**尚未执行**。
- `scores.<name>` DSL 本期不生效（源表为空），已显式登记为不支持字段而非静默出错；待 scores 有数据后恢复。
- `toolCalls`/`modelId`/`release`/`tps`/`inputCost`/`outputCost`/`sdkName`/`sdkVersion` 无来源，已丢弃；前端建议项与 Facet 尚未按 §11 阶段 C 清理。
- 目标 DDL 的 **7 天 TTL 与前端 30d/90d 范围仍是未决的产品冲突**（§13 表）；本轮调试数据同样受 7 天 TTL 约束，2026-09-14 后需重新平移。
- 运行中 Trace（RUNNING）无法表达：目标表 `end_time` 非空，本轮只迁移已结束 Trace。
- 未在目标 ClickHouse 21.8.14.5 上验证；本轮全部 SQL 实测环境为 ClickHouse 25.12.11。

## 23. 2026-09-11 执行方案阶段 C（筛选语义闭合）

### 23.1 前端改动

- `TracingPage.vue` 的 `SEARCH_FIELDS` 移除 **`inputCost` / `outputCost` / `release`**（新 schema 无来源），`collapsedFilters` 与 `labelFor` 移除 **`modelId`**，搜索提示里的 `scores.accuracy` 示例改为 `serviceName`。每条被移除的字段都在注释中写明原因，避免后续被误加回。
- **新增 Service Facet**：左侧筛选面板新增「服务 / Service」分组（选择/文本双模式），`loadFacets` 增加 `SERVICE_NAME` 请求，并接入 `watch`、`clearFilters` 与 URL 筛选状态同步。
- 前端 `npm run build` 通过（244 modules）；Vite 实际服务的模块中已确认包含 `serviceNameOptions` / `SERVICE_NAME`，且 `Input Cost` / `Output Cost` / `Release` / `Model ID` 均为 0 处。

### 23.2 发现并修复运行时缺陷：`model` 别名与基表列冲突

**这是本轮最有价值的发现，静态测试无法捕捉。**

- **现象**：`model:<任意值>` 筛选一律返回 **HTTP 503 `DATA_SOURCE_UNAVAILABLE`**，而 `type:`、`name:`、`level:`、`root:` 等筛选全部正常；`count` 端点单独调用也正常。
- **根因**：观测列清单把模型表达式别名成 `model`——`nullIf(coalesce(nullIf(model, ''), request_model), '') AS model`。基表本身就有 `model` 列，**ClickHouse 会把 SELECT 别名替换进 WHERE 子句**，于是同一列名出现两种定义，抛
  `Code: 352. DB::Exception: Block structure mismatch in (columns with identical name must have identical structure)`。
  该缺陷需要「列表形状（含该别名）+ WHERE 引用 `model`」同时成立才触发，因此无筛选的冒烟测试、类型/名称筛选、以及 count 端点都不受影响。
- **定位方法**：先用 `clickhouse-client` 直接执行同一 SQL（**全部通过**），排除 SQL 本身问题；再用 clickhouse-jdbc 0.4.6 写独立探针按变量法逐项复现，最终确认 `AS model` 是唯一触发条件，换成 `modelName` 立即恢复。
- **修复**：输出别名改为 `modelName`（`TracingRows.ObservationRow.modelName`），DSL 字段与对外 DTO 仍是 `model`，前端契约不变。
- **回归防护**：新增 `doesNotAliasAnOutputColumnAfterAReferencedBaseColumn`，断言观测 SQL 中不出现 `AS model,` 且存在 `AS modelName,`。
- **同类风险核查**：`environment` / `name` 是无表达式直通选择（别名与列同型，安全）；`traceDetail` 的 `nullIf(version, '') AS version` 同样与基表列同名，但其 WHERE 只含 `trace_id`，当前未触发——**后续若给 Trace 详情增加 version 条件，必须一并改名**。

### 23.3 验证结果

- 后端 `mvn test`（Java 8.0_292）：**52/52 通过**（含新增的别名回归测试）。
- 筛选逐项：`traceId:=<32位>`=8、`traceId:<前缀>`=8、`type:SPAN`=20、`type:GENERATION`=1、`level:DEFAULT`=21、`environment:default`=21、`serviceName:codex-app-server`=21、`model:codex-auto-review`=1、`name:build`=1、`root:true`=3、`metadata.resourceAttributes.env:=local-langfuse`=21。
- 被禁用字段 `toolCalls:` / `modelId:` / `release:` / `scores.accuracy:` 均返回 **HTTP 200 且忽略该条件**（不再产生错误结果，也不再 503）。
- **完成条件（列表 / Pulse / Facet 数据集合一致）**：

| 筛选 | 列表 total | Pulse 合计 | |
|---|---:|---:|---|
| 无筛选 | 21 | 21 | ✓ |
| `type:SPAN` | 20 | 20 | ✓ |
| `type:GENERATION` | 1 | 1 | ✓ |
| `type:SPAN serviceName:codex-app-server` | 20 | 20 | ✓ |
| `-type:SPAN` | 1 | 1 | ✓ |
| `model:codex-auto-review` | 1 | 1 | ✓ |

  Facet 交叉核对：TYPE 合计 21、SERVICE_NAME 合计 21、列表 total 21，三者一致。
- 端到端（模拟浏览器打开 Tracing 页的完整请求序列）：登录 200 → 列表 200(total=21) → Pulse 200(10 个点) → ENVIRONMENT/SERVICE_NAME/TYPE/ROOT 四个 Facet 全部 200 → Trace 详情 200（字段无缺失、2 个节点、0 悬空父节点、1 个根节点）。

### 23.4 环境事件与内存约束（重要）

- 本轮期间宿主机**内存耗尽**，Docker 里的 ClickHouse / Langfuse web / Langfuse worker 被 SIGKILL（exit 137），后端与前端进程也被系统停止。已恢复 ClickHouse（数据完好：`events_core` 455,249、新表 3+21）并重启后端与前端。
- 宿主机仅 16GB，Docker VM 7.67GB；当前 PolarDB-X ~3.9GB + ClickHouse ~1.6GB 已占大部分。**内存是本项目当前最现实的运行风险**，压测或再起 Langfuse 原栈前需先评估。
- 另注：会话期间 macOS 曾再次撤销 Terminal 对「桌面文件夹」的 TCC 授权，导致项目目录一度完全不可读写；恢复授权后继续，未造成文件损坏。

### 23.5 未完成

- 阶段 D（Tree/Timeline/Graph/Preview/Log View 的**人工浏览器交互**回归）、阶段 E（压测与清理旧 Tracing 残留）尚未执行。
- 本机无浏览器自动化工具，阶段 D 的「点击、切换页签、对比节点数」等交互**未做真实人工回归**；已用等价的请求序列验证数据层，但 UI 交互仍需人工确认。
- `scores.<name>` 仍不生效（源表为空）；`environment` / `name` / `version` 三个同名别名的潜在风险见 §23.2。

## 24. 2026-09-11 执行方案阶段 E（性能与清理）

至此 `TRACING_CLICKHOUSE_REFACTOR_PLAN.md` 的阶段 **A / B / C / E 全部完成，仅剩阶段 D**（需人工浏览器交互）。

### 24.1 旧 Tracing 残留清理核查（全部通过）

- `ObservabilitySqlProvider` 仅剩 `summary` / `metricTimeSeries` / `traceScores` / `sessions*` / `users*`，即 Dashboard 指标、Trace Score、Sessions、Users —— 均为方案 §1 明确不在本次迁移范围的功能；Tracing 方法已全部移除。
- `ObservabilityMapper` 无任何 Tracing 方法残留。
- **从未引入过 `app.observability.agentobs-enabled` 开关**，故无临时开关需删除；也不存在同一次请求内混查新旧表的代码路径。
- 唯一仍在读 `events_core` 的 Tracing 无关模块是 `WidgetMetricSqlProvider`（Dashboard 组件指标），同样不在本次范围。Sessions / Users 保留旧查询是方案明确允许的。

### 24.2 压测结论

本地新表仅 21 行调试数据，直接压测无意义。因此另建**独立压测表** `hmp_agentobs_observations_loadtest`（与生产表**完全相同的排序键与分区键**，去掉 7 天 TTL），从 `events_core` 灌入 **455,249 行**并合成 **20 个服务**，压测后已 `DROP`。调试数据全程未受影响（压测前后均为 3 Trace / 21 Observation）。

**实测结果（服务端耗时，各 6 次）**

| 查询 | p50 | p95 | 扫描行数 | 峰值内存 |
|---|---:|---:|---:|---:|
| 列表（无 service） | 48 ms | **104 ms** | 488,017 | 8.9 MiB |
| 列表（`service_name='svc-3'`） | 32 ms | 43 ms | — | — |
| count（无 service） | 12 ms | 32 ms | 455,249 | 5.1 MiB |
| count（有 service） | 14 ms | 22 ms | — | — |
| Pulse DAY | 25 ms | 44 ms | 455,249 | 6.2 MiB |
| Facet TYPE | 20 ms | 24 ms | 455,249 | 1.4 MiB |
| `trace_id` 点查 | 8 ms | 10 ms | 455,249 | — |

端到端 HTTP（21 行调试数据，含 JDBC + 服务层 + 序列化）：观测列表 p50 99 ms / p95 286 ms；Pulse 23/31 ms；Facet 26/30 ms；Trace 列表 52/231 ms；TraceView 101/113 ms；全部 HTTP 200。

**结论：p95 远低于目标、峰值内存个位数 MiB，无需新增查询投影或服务表。**

**§8.6 排序键风险的实测结论**（`EXPLAIN indexes = 1`）：

| 查询条件 | 主键实际使用的列 | 扫描粒度 |
|---|---|---|
| **带** service 条件 | `service_name` + `toDate(start_time)` | **5/58**（二分查找） |
| **不带** service 条件（页面默认） | 仅 `toDate(start_time)` | **58/58** |

不加 service 条件时主键无法按首列裁剪，只能靠分区键与 MinMax 索引。455k 行下 p95 为 104 ms vs 43 ms，**可接受**；但两者都随数据量线性增长，**生产上线前需按目标数据量复测**，不达标时首选增加按 `(start_time, trace_id, span_id)` 排序的 Web 查询服务表（阶段 C 已提供 Service Facet 作为运维侧裁剪手段）。

### 24.3 两个测量陷阱（后续压测请勿重犯）

1. **不要用 `docker exec` 整体计时**：实测固定开销 **221 ms**，会把真实的服务端耗时（6–11 ms）完全淹没。第一版压测因此得出「count 读 2196 行比读 455,249 行还慢」的荒谬结论。应使用 `clickhouse-client --time` 取服务端耗时。
2. **不要把灌数据的 `INSERT` 计入查询统计**：`system.query_log` 中一条 12,970 ms / 1022 MiB 的记录一度被怀疑是 `trace_id` 点查退化，实际是 `INSERT INTO … loadtest (… trace_id …) SELECT … FROM events_core` 这条**数据装载语句本身**（其列清单含 `trace_id`，被粗略分组误归类）。真实 `trace_id` 点查为 p50 8 ms / p95 10 ms。

### 24.4 文档更新

- `TRACING_CLICKHOUSE_REFACTOR_PLAN.md`：阶段 A/B/C/E 清单已勾选并写入实施记录（21 项完成，剩阶段 D 的 6 项）。
- `MIGRATION_MATRIX.md`：更新了已过时的陈述——Tracing 的 ClickHouse 数据源、`has:`/score/tool 筛选现状、性能基线与约束（原文仍称 Tracing 读 `events_core`/`events_full`、约束来自 `events_core FINAL`、metadata 取自 `events_full`）。

### 24.5 剩余事项

- **阶段 D（6 项）未执行**：Tree/Timeline/Graph/Preview/Log View 的人工浏览器交互回归。本机无浏览器自动化工具，无法代替人工点击验证。
- **7 天 TTL 风险仍在**：调试数据取自 2026-09-07，`2026-09-14` 后会被 TTL 清除，届时需按 `migrate-tracing-debug-sample.sql` 第 3 节的动态选样重新平移。
- 未在目标 ClickHouse **21.8.14.5** 上验证；本轮压测环境为 ClickHouse 25.12.11。
- 内存仍是本机最主要的运行风险：宿主机 16 GB、Docker VM 7.67 GB，PolarDB-X 约 3.9 GB + ClickHouse 约 1.6 GB 已占过半。

## 25. 2026-09-11 后端数据正确性改造（P0/P1）

按《Tracing Spring Boot 后端修改方案》实施，**只改后端，未改前端**。方案提出的每一条问题都先经代码核实**全部属实**，其中三条是 §22/§23 遗留的自身缺陷。

### 25.1 P0.1 Trace Name 筛选

`traceName` 此前无 SQL 映射，条件被**静默丢弃**。现已实现为 trace-id 子查询（Trace Name 只存在于 traces 表）：

```sql
trace_id IN (SELECT toString(trace_id) FROM default.hmp_agentobs_traces_all FINAL
             WHERE trace_name = ?)          -- 精确 :=
             WHERE positionCaseInsensitiveUTF8(trace_name, ?) > 0  -- 模糊
```

支持 `:=精确`、`前缀*`、`*后缀`、子串、`-` 取反与 `OR` 组合。因为列表/count/Pulse/Facet 全部经由同一个 `observationPredicate`，该筛选**必然同时作用于四者**（有测试断言）。

### 25.2 P0.2 版本去重 —— 以及一个被静默忽略的 `FINAL`

**这是本轮最重要的发现。**

方案指出目标表是 `ReplacingMergeTree(ingestion_version)`、后台合并非即时，必须去重。核实后确认：我在 §22 曾假设"快照 append-only 故无需 FINAL"，**该假设与 `schema3-design.md` 第 32 行的权威要求直接矛盾**（"需要精确结果时，在有界筛选下使用 FINAL"）。已为全部 observations 查询加 FINAL。

但加了 FINAL 后实测**去重仍然失效**：API 返回 22 而基础表 `FINAL` 为 21。根因是——

> **ClickHouse 对普通 VIEW 会静默忽略 `FROM view FINAL`。**

阶段 A 为单机环境把 `_all` 建成了普通 View（`AS SELECT * FROM 表`），于是 `_all FINAL` 变成空操作，去重从未真正生效。而集群环境 `_all` 是 Distributed 表（**支持** FINAL），**本地与集群行为因此不一致**——单测只检查 SQL 文本，抓不到这一点。

修复：本地 `_all` 视图定义内自带 FINAL（`AS SELECT * FROM 表 FINAL`）。已验证「视图内含 FINAL」时直接查得 21，且再显式加 `FINAL` 也不报错，因此 provider 统一输出 `_all FINAL` 在**单机与集群两种形态下都正确**。`trace_locator_all` 保持不加（AggregatingMergeTree，读取方用 `minMerge`/`maxMerge`）。

实测（物理插入 1 条同 `trace_id+span_id`、更高 `ingestion_version` 的重放行）：
基础表 22 行 / 视图 21 行 / **API 列表、Pulse、Facet 三者均为 21** —— 重放不再放大任何数字。测试行已用 mutation 清除（`is_done=1`）。

### 25.3 P0.3 接入 Trace Locator

`selectTraceLocator()` 此前**已定义但从未被调用**（死代码）。现已接入详情与指标聚合两条路径：先查 locator 得到 `service_name` 与时间范围，再用于收窄 observations 读取。Locator 未命中时回退为仅按 `trace_id` 查询并记 WARN（locator 是派生索引，滞后属预期，**收窄永远只是性能边界、不是正确性过滤**，绝不隐藏数据）。

只在该 Trace 归属**单一 service** 时才加 service 条件——跨服务 Trace 不能被收窄成其中一个。

已确认收窄 SQL 实际进入 ClickHouse（`system.query_log` 中检出带 `service_name` + `start_time` 的查询）。

### 25.4 P0.4 时间范围保护

未传时间时后端会生成**无界查询**。现于服务层统一强制：缺省窗口 = 最近 24 小时；单次范围上限 **90 天**（与前端最大档一致），超限或起止倒置返回 400 `INVALID_REQUEST`。校验保留其余全部筛选字段不变。

### 25.5 P1 字段映射

- **usageDetails 修正**：原实现把 input/output 之外的**所有** key 都兜底映射到 `usage_total_tokens`，导致 cache read / cache write / reasoning / audio 被当成"总量"重复计入。现按真实来源列逐一映射（input/output/total/cache_read/cache_write/reasoning/audio，含常见别名），**无来源的 key 先被过滤掉再映射**，兜底分支改为字面量 0，绝不落到总量计数器。
- **completionStartTime**：AgentObs 只有 `time_to_first_chunk_ms`（时长），无绝对时刻。按确认的决策**保持 null**，只返回 `timeToFirstTokenMs`，不伪造数据中不存在的"实测时刻"。
- **未知字段处理**：新增 `TracingFilterFields` 作为字段白名单**唯一来源**（provider 从它导入 token 正则与丢弃列表，杜绝两处漂移）。未知字段（拼写错误等）返回 **400**，错误信息列出可用字段；已知但无来源的字段（toolCalls/modelId/release/inputCost/outputCost/tps/sdk*/scores）记 **WARN 后忽略**，避免一个过期预设让整页报错。

  特例：裸 `metadata:x`（缺 key）归入"忽略+WARN"而非 400——它是**已知字段的不完整用法**，且前端建议项插入的正是这个裸形式，返回 400 会让用户点建议项就失败。已在测试中固化该判定。

### 25.6 四.1 Trace 列表改为两阶段

原实现对**整个时间窗口**的 observations 聚合后再分页，每翻一页都重算一遍。现按排序类型分流：仅 TOKENS/COST 排序需要聚合先于分页（排序键本身是聚合值），其余排序先分页 Trace 表、再**只对该页的 trace_id** 取指标。无 JOIN 的页形态返回占位 0，由服务层 `mergeMetrics` 填充。

### 25.7 验证结果

- 后端 `mvn test`（Java 8.0_292）：**84/84 通过**，0 失败 0 错误（较上轮 52 增加 32 个用例）。
  新增：`TracingSqlProviderTest` 28 项（traceName 精确/模糊/通配/取反、四查询一致性、全部查询走 FINAL、locator 收窄与回退、usage 键映射、两阶段分流）、`TracingFilterFieldsTest` 12 项、`TracingQueryBoundsTest` 9 项。
- 真实 ClickHouse 端到端验收（全部通过）：

| 验收项 | 结果 |
|---|---|
| `traceName:=configRequirements/read` | **2**（原 21） |
| 精确 Trace ID | **8** |
| `type:GENERATION` 列表 / Pulse | **1 / 1** |
| 重放同一 Span 后列表总数 | **21 不变**（物理 22 行） |
| 重放后 Pulse / Facet | **21 / 21**，与列表一致 |
| 未传时间 | 自动 24h（返回 0 而非全表，证明有界） |
| 未知字段 | **400 INVALID_REQUEST** |
| 范围超 90 天 | **400 INVALID_REQUEST** |
| 不支持字段 | **200 且忽略** |
| 前端 21 个 SEARCH_FIELDS + 快捷预设 | 全部 **200** |
| Trace 详情 /view | 200，locator 收窄已进入 SQL |

### 25.8 未完成与风险

- **TTL 冲突仍未决**（业务决策，非代码问题）：目标表 7 天 TTL 与前端 30d/90d 冲突，需在"扩 TTL / 砍前端档位 / 冷热分表"中选一种。在决定前 **30d 与 90d 不能判定为可用**。
- **未在目标 ClickHouse 21.8.14.5 验证**：`FINAL`、JSON 函数、`DateTime64(6)`、Array/Map 函数、JDBC 0.4.6 均只在 25.12.11 实测。
- **前端 Metadata 建议项**：`selectSearchField` 对 metadata 插入 `metadata:` 而非 `metadata.`，用户需自行补 key。后端已改为忽略+WARN 以免报错，但前端仍应修正为插入 `metadata.`——**本轮按方案要求未改前端**。
- `scores.<name>` 仍不生效（源表为空），已白名单化为忽略+WARN。
- **集群形态未实测**：`_all FINAL` 在 Distributed 表上的行为（含 `FINAL` 下推）未在真实集群验证，仅依据 ClickHouse 语义推断。

### 25.9 本地 TTL 改为 30 天（2026-09-11）

按要求把**本地 ClickHouse** 的 TTL 由 7 天改为 30 天，并同步到本地建表脚本。

- 对 8 张表执行 `ALTER TABLE … MODIFY TTL <时间列> + INTERVAL 30 DAY`：`traces`、`observations`、`scores`、`span_events`、`span_links`、`logs`、`metric_points`、`trace_locator`。逐一 `SHOW CREATE TABLE` 复验均为 `toIntervalDay(30)`。
- `agentobs-clickhouse-schema-local.sql` 同步替换 8 处（`INTERVAL 7 DAY` → `INTERVAL 30 DAY`），并在文件头新增**转换规则 6** 明确这是**与 final DDL 唯一的一处刻意偏离**，防止后续有人"照 final 重新派生"时把它改回去。
- **`agentobs-clickhouse-schema-final.sql` 未改动**（仍是 7 天）——它是集群契约，TTL 是业务决策，不能由本地联调代劳。
- 效果：调试数据（9/7）到期日由 **2026-09-14 延后到 2026-10-07**；API 实测 `1d=0`、`7d=21`、`30d=21`、`90d=21`，**30d 档现已能返回完整数据**（此前受 7 天 TTL 限制）。
- 顺带清理：重放测试行的派生残留使 `trace_locator` 多出 1 行（AggregatingMergeTree 不随源表删除回收），已按 MV 定义从 `observations` 全量重建为 3 行。**这是派生索引的固有行为，删除观测后需手动重建 locator。**
- ⚠️ **仅本地生效**：集群仍是 7 天，「7 天 vs 30d/90d」的产品决策**依然未决**。

## 26. 2026-09-11 验收意见整改（2×P1 + 3×P2）

针对验收结论逐条整改。**五条意见全部核实属实**，其中 P1-1 与 P2-3 是真实缺陷，P1-2 是交付遗漏。

### 26.1 P1 `/traces` 筛选语义不一致 —— 实现 Trace 专用 DSL

核实：同一 search 串在两个接口行为不同——

| search | `/traces`（整改前） | `/observations`（整改前） |
|---|---:|---:|
| `name:turn_context.build` | **0** | 1 |
| `release:2026.1` | **0**（当字面文本） | 21（正确忽略） |

即 `findTraces` 调用了统一校验（宣称支持 DSL），但 `tracePredicate` 仍把整串当普通文本。

**采用「实现 Trace 专用 DSL」而非「移除校验」**：`/observations` 已支持 DSL，同一 API 组内行为分裂对调用方是陷阱；移除校验只是让「静默返回 0」合法化，并未解决问题。

- `TracingFilterFields` 新增 **`Scope`（OBSERVATION / TRACE）**，两个端点各自声明词表与「无来源」集合。`tokens`/`cost`/`ttft` 对 traces 无来源（在 observations 上存在），`traceName`/`serviceName` 仅 traces 有。
- `TracingSqlProvider` 新增 `appendTraceSearch` / `appendTraceToken` 及 Trace 字段表达式、tags、status、presence、数值、时间谓词。
- `status:RUNNING` 显式译为 `0 = 1` 并注释说明：AgentObs 的 trace 行 `end_time` 非空，无法表达运行中——**匹配不到 ≠ 不生效**。
- 整改后实测：

| search | `/traces` | 结论 |
|---|---:|---|
| 空 | 3 | — |
| `turn_context.build` | 1 | 自由文本，不变 |
| `name:turn_context.build` | **1** | 由 0 修复 |
| `release:2026.1` | **3** | 由 0 修复（与 observations 一致地忽略） |
| `traceName:` / `latency:>0.001` | 1 / 3 | 新 DSL 生效 |

  三个抽查（`name:` / `release:` / `bogus:`）在两个端点的状态码**完全一致**（200 / 200 / 400）。

### 26.2 P1 旧环境重跑 DDL 不会升级 View

`CREATE VIEW IF NOT EXISTS` 不会替换既有定义，因此**旧环境重跑 `agentobs-clickhouse-schema-local.sql` 无法获得 FINAL 修复**。

- 新增 **`backend/sql/clickhouse/upgrade-views-add-final.sql`**：用 `CREATE OR REPLACE VIEW` 安全替换 8 个 `_all` 视图（7 个带 FINAL，locator 按设计排除），脚本末尾自带校验查询输出 `has_final`。
- 本地 DDL 头部已指明：已存在旧视图的环境须运行该升级脚本，而非重跑建表脚本。
- 实测：执行后 7 个视图 `has_final=1`、locator 为 `0`；**重跑一次 0 异常（幂等）**；数据完好（21/3/3）。

### 26.3 P2 日志缺上下文与限流

日志由 `Ignoring filter field(s): [release]` 增强为：

```
Ignoring filter field(s) with no source column for TRACE (suppressing repeats for 300s):
fields=[release] search="release:2026.1" caller=user=admin endpoint=GET /api/v1/observability/traces
```

含 Scope、字段、原始 search（截断 200 字符）、**用户**（来自 SecurityContext）与**请求方法+路径**（来自 RequestContextHolder），二者均 best-effort 且不因缺失而失败。
**限流**：按 `(Scope, 字段集合)` 去重，同一情形每 300 秒最多一条；缓存有上限（512）防止无限增长。实测连打 5 次 → **新增 0 条**。

### 26.4 P2 `metadata` 支持状态表述不准确

原先「支持列表含 `metadata`」与「裸 `metadata:value` 被忽略」自相矛盾。

- 400 错误信息中改为展示 **`metadata.<key>`**，不再宣称裸形式可用。
- 同时修正归类：裸 `metadata` **不属于**「无来源字段」（列存在），而是「已知字段的不完整用法」，警告文案已相应区分。
- 有测试断言错误信息含 `metadata.<key>` 且**不含**裸 `metadata, `。

### 26.5 P2 Trace 指标未用 service 收窄

`getTrace` 原以 `traceParameters(traceId, false)` 调用，导致单 Service Trace 的指标聚合只用时间范围、不用 `service_name`——而 `service_name` 正是 observations 表的排序键首列。

改为 `true`。`traceParameters` 本身已保证**仅当 locator 报告单一归属服务时**才加入该条件，跨服务 Trace 不会被错误收窄。实测：`/view` 请求触发的指标查询已带 `service_name`（query_log 检出）。

### 26.6 验证结果

- `mvn test`（Java 8.0_292）：**96/96 通过**（较上轮 84 增加 12 项）。
  新增覆盖：Trace DSL 解析（字段映射、tags、取反、自由文本、元数据、`status:RUNNING`）、Trace 无来源字段丢弃、两端点词表差异、Trace 作用域校验、metadata 展示形式。
- 端到端：`/traces` 四组用例、两端点一致性、Trace DSL 各字段、升级脚本幂等性、日志上下文与限流、指标收窄——全部通过。

### 26.7 仍未完成（与上轮一致，非本轮引入）

- **ClickHouse 21.8.14.5 未实测**：`FINAL`、JSON 函数、`DateTime64(6)`、JDBC 0.4.6 仅在 25.12.11 验证。
- **集群 Distributed 表上的 `FINAL` 未实测**。
- **集群 DDL 仍是 7 天 TTL**（本地 30 天），产品决策未决。
- 前端 `metadata` 建议项仍插入裸 `metadata:`（后端已改为忽略+WARN 以避免报错，但建议改为插入 `metadata.`）。

## 27. 2026-09-11 ClickHouse 21.8 目标版本兼容性验证

补上了上轮列出的第一项外部验收。目标版本 **21.8.14.5**，实测用 `clickhouse/clickhouse-server:21.8`（实际 **21.8.15.7**，同属 21.8 系列）。

### 27.1 ⚠️ 发现并修复一个真实的 21.8 不兼容

**`JSONExtractKeys` 在 21.8 上不存在**，而 `toolDefinitions` 数值谓词用的正是它：

```
JSONExtractKeys  →  Received exception from server (version 21.8.15)  ✗
JSONLength       →  对象键数 2 / 数组长度 3 / 空对象 0                 ✓
```

**这是单测完全无法发现的缺陷**——单测只断言 SQL 文本（`length(JSONExtractKeys(tool_definitions))`），文本对了但目标版本不认。若不上 21.8 实测就会带着它交付。

修复：改用 `JSONLength(tool_definitions)`。已验证该函数在 **21.8 与 25.12 上返回完全一致**（`2 / 3 / 0`）。测试中同时锁定：`assertFalse(sql.contains("JSONExtractKeys"))`，并注明原因。

### 27.2 视图 FINAL 行为在 21.8 上一致

| 检查 | 21.8 结果 |
|---|---|
| 物理 2 行（1 条重放） | 基础表无 FINAL = **2** |
| 基础表 `FINAL` | **1**，且取到最新版本 `REPLAYED` |
| 视图（定义内含 FINAL） | **1** ✓ |
| 视图 `_all FINAL`（provider 形态） | **1** ✓ |
| **对照组：定义内不含 FINAL 的普通视图** | 无 FINAL = 2，**显式 FINAL 仍 = 2** |

即：**21.8 与 25.12 行为一致，普通视图同样静默忽略 `FINAL`**。这说明 §25.2 的修复对目标版本**既必要又有效**，不是新版特有现象。

### 27.3 全量 Provider SQL 在 21.8 上执行

新建探针 `scripts/clickhouse-version-probe/SqlCompat.java`：反射渲染 `TracingSqlProvider` 能产生的**全部 26 种语句**，把 MyBatis 占位符替换为实际绑定值后，逐条投给目标服务器执行。

```
通过 26/26
```

覆盖：观测列表 / count / traceName 精确与模糊 / metadata / toolDefinitions / 数值比较 / tags / TAG·TRACE_NAME·SERVICE_NAME Facet / HOUR·DAY·WEEK Pulse / 详情（含与不含 locator 收窄）/ locator / traceDetail / traceLookups / traceMetrics / Trace 列表四种排序 / Trace DSL / traceCount。

> 探针首版自身有 bug（直接调用的语句未做占位符替换，导致 21/26 假失败），已修正——记录在此以免后续误判为服务端问题。

### 27.4 其余 21.8 验证项

| 项 | 结果 |
|---|---|
| 本地 DDL 全量执行 | 18 个对象全部建成，无报错 |
| `CREATE OR REPLACE VIEW`（升级脚本依赖） | **支持** |
| 升级脚本执行 | 7 个视图 `has_final=1`、locator `=0`；**重跑 0 异常（幂等）** |
| `clickhouse-jdbc 0.4.6` 驱动 | **连接 21.8.15.7 成功**，参数绑定正常，`JSONLength` 经驱动可用 |
| `DateTime64(6)` / `JSONExtractString` / `toInt32OrNull` / `arrayMap`+`arrayFilter`+`has` / `multiIf` / `toStartOfWeek` / `minMerge`+`maxMerge` / `startsWith` / `endsWith` / `lowerUTF8` / `positionCaseInsensitiveUTF8` / `toJSONString` / `empty` / `notEmpty` / `ifNull` / `nullIf` / `coalesce` / `round` | 全部可用 |

### 27.5 新增交付物

- **`scripts/clickhouse-version-probe/SqlCompat.java`**：渲染并执行全部 Provider 语句的探针。
- **`scripts/clickhouse-version-probe/DriverProbe.java`**：验证 JDBC 驱动对目标版本的连接与参数绑定。
- **`scripts/clickhouse-version-probe/README.md`**：运行方法、已验证结果表，以及换版本时应一并检查的项（视图 FINAL 行为、`CREATE OR REPLACE VIEW`、表设置、`system.query_log` 列名差异）。

### 27.6 验证结果

- `mvn test`（Java 8.0_292）：**96/96 通过**（`JSONLength` 变更后同步更新断言）。
- 主环境（25.12）回归：观测列表 21、`toolDefinitions:>0` 200、`/traces` 200、`search=name:turn_context.build` = 1、Trace 详情 200——全部正常。
- 21.8 验证容器已停止并删除，内存恢复。

### 27.7 仍未完成

- **集群 Distributed 表上的 `FINAL` 未实测**：本地是 View、集群是 Distributed，两者 FINAL 语义不同（这正是 §25.2 的教训）。上线前须在真实集群验证一次。
- **集群 DDL 仍是 7 天 TTL**（本地 30 天），产品决策未决。
- 21.8 实测版本为 **21.8.15.7**，与目标 **21.8.14.5** 同系列但非同一补丁号；如需严格对齐可改用 `clickhouse/clickhouse-server:21.8.14.5` 重跑探针。

## 28. 2026-09-11 集群形态（Distributed）验证

补上了 §27.7 列出的第二项外部验收。本地 `_all` 是 View、集群 `_all` 是 **Distributed**，二者 FINAL 语义不同——这正是 §25.2 踩过的坑，必须实测。

### 28.1 构造方法

用**单节点 + 自定义 cluster** 模拟集群：给服务器一份 `remote_servers` 配置（1 shard / 1 replica 指回自身），再把 8 个 `_all` 从 View 换成 `Distributed(verify_cluster, default, <本地表>, xxHash32(trace_id))`——与 `agentobs-clickhouse-schema-final.sql` 的集群定义同形。

### 28.2 🎯 关键结果：FINAL 行为按形态不同，且两种形态都正确

插入 1 行 + 1 行同 `(trace_id, span_id)`、更高 `ingestion_version` 的重放：

| | 物理行数 | `_all` 无 FINAL | `_all FINAL` |
|---|---:|---:|---:|
| **本地**（`_all` 是 View） | 2 | **2** | **2** ← FINAL 被静默忽略 |
| **集群**（`_all` 是 Distributed） | 2 | 2 | **1** ← FINAL 正确下推 |

即：
- **普通 View 在 21.8 与 25.12 上都静默忽略 `FROM view FINAL`**（两次独立实测一致）；
- **Distributed 表正确执行 FINAL**，下推到分片，并保留最新版本（`REPLAYED`）。

结论：provider 统一输出 `_all FINAL` 在**两种形态下都正确**——但本地能正确，**完全依赖 `agentobs-clickhouse-schema-local.sql` 把视图定义成含 FINAL**；集群则不需要这个变通。**这也意味着 §25.2 的视图修复绝不能"简化"掉，否则本地会与集群长期静默不一致。**

### 28.3 全量 Provider SQL 在集群形态下执行

```
通过 26/26
```

与本地形态、21.8 形态结果一致。

### 28.4 AggregatingMergeTree locator 经 Distributed

| 检查 | 结果 |
|---|---|
| locator MV 随插入回填 | ✓ |
| `minMerge`/`maxMerge` 跨分片聚合 | ✓ 返回正确的服务与时间范围 |
| provider 的 `traceLocator` 查询 | ✓ 返回 `svc-a` + `2026-09-10 00:00:00 ~ 00:00:01` |

状态函数经 Distributed 边界折叠正确，locator 收窄在集群形态下同样可用。

### 28.5 交付物更新

`scripts/clickhouse-version-probe/README.md` 增加「验证集群形态」一节：完整的 cluster 配置、Distributed 表构造命令（含 25.12 镜像需要凭据的注意事项），以及上述 FINAL 对照表。探针本身增加 `ch.user` / `ch.password` 参数。

### 28.6 验证结果

- `mvn test`（Java 8.0_292）：**96/96 通过**（本轮未改 Java 代码）。
- 集群形态：26/26 通过，locator 语义正确。
- 验证容器已停止并删除；主环境（ClickHouse 25.12 healthy / PolarDB-X / 后端 8080 UP / 前端 5173）全部正常。

### 28.7 剩余事项

- **集群 DDL 仍是 7 天 TTL**（本地 30 天）——这是**唯一剩下的外部验收项**，且是产品决策而非技术问题。
- 21.8 实测为 21.8.15.7，非目标的 21.8.14.5 精确补丁号。
- 前端 `metadata` 建议项仍插入裸 `metadata:`。
- 本次为**单节点模拟集群**：验证了 Distributed 引擎路径与 FINAL 下推语义，但未覆盖多分片、副本、Keeper 与网络分区等真实集群特性。

## 29. 2026-09-14 PolarDB-X 表名统一加 `langfuse_` 前缀

**动机**：`langfuse_web` 库与其他项目共库，`projects` / `users` / `comments` / `prompts` 这类通用名无法区分归属。

### 29.1 改动内容

- **`backend/sql/polardbx-schema.sql`**：9 张表全部加前缀——`langfuse_projects`、`langfuse_users`、`langfuse_comments`、`langfuse_dashboards`、`langfuse_dashboard_widgets`、`langfuse_prompts`、`langfuse_prompt_locks`、`langfuse_prompt_protected_labels`、`langfuse_prompt_dependencies`。文件头写明前缀是与 mapper 的硬契约。
- **索引名刻意保持不变**（`idx_projects_updated_at` 等）：InnoDB 索引名是表内作用域，改名无必要，纯属美观。
- **4 个 Mapper 同步替换**（共 40 处，与改动前逐表计数完全一致）：
  `CommentMapper`(1)、`DashboardMapper`(9+5)、`DashboardWidgetMapper`(6+3)、`PromptMapper`(14+3+2+1)。
  `ObservabilityMapper` / `ObservabilitySqlProvider` 里的 `users` 是 **ClickHouse `events_core` 的 user 聚合**，与 PolarDB-X 的 `langfuse_users` 无关，**未改动**。
- **新增 `backend/sql/polardbx-rename-tables.sql`**：给"前缀之前就已建好"的库做一次 `RENAME TABLE` 原地迁移，保留 `aiops-default` 工作区、`migration-service` 用户及既有 Dashboard/Comment/Prompt 数据（避免 DROP 重建导致数据丢失）。脚本含 pre-flight / post-flight 校验，并注明**非幂等**、须只跑一次；末尾附 `CREATE TABLE ... LIKE` + `INSERT SELECT` 兜底方案。

### 29.2 验证结果（全部为真实执行，非推断）

| 验证项 | 结果 |
|---|---|
| `mvn test`（Java 8.0_292） | **96/96 通过**，与 §28 基线持平 |
| 新库 DDL 路径 | 在 scratch 库执行新 `polardbx-schema.sql`：9 张带前缀表全部建成，seed 2 行写入，`mysql exit=0`，重跑幂等（`IF NOT EXISTS`） |
| **旧库 RENAME 迁移路径** | 对**真实旧库**（当时仍是 9 张旧名表 + seed 数据）执行迁移脚本：9 张全部改名成功，`projects_kept=1` / `users_kept=1`，**旧名残留 = 0** |
| `RENAME TABLE` 兼容性 | PolarDB-X 5.4.19 **支持**，且保留行数据（先用小表探针确认再跑正式脚本） |
| 全部 Mapper 语句对真实库执行 | 反射渲染 4 个 mapper 的**全部 35 条语句**（照 §27.3 `SqlCompat` 的做法，测的是编译后的注解而非手抄文本），逐条投给真实 PolarDB-X：**31 条通过**，4 条失败（见 §29.3） |
| 事务回滚 | 探针在 `START TRANSACTION` 内执行并以 `ROLLBACK` 结束，复验 `langfuse_dashboards` / `_widgets` / `_prompts` / `_prompt_locks` 均为 **0 行**，未污染库 |

### 29.3 ⚠️ 探针发现一个**预先存在**的缺陷：PolarDB-X 不支持 `UPDATE ... SET col = (SELECT ...)`

**这不是本次改名引入的**，与表名无关。

- 现象（4 条 mapper 语句失败，报错完全一致）：
  `ERROR 4518 (HY000) ERR-CODE: [PXC-4518][ERR_VALIDATE] : update not support subquery node`
- 受影响语句：`DashboardMapper.updateMetadata`、`updateDefinition`、`updateFilters`、`DashboardWidgetMapper.update`——四者都把 `updated_by` 写成
  `updated_by = (SELECT id FROM langfuse_users WHERE id = #{actor} LIMIT 1)`。
- **最小复现**：两张无关的探针表（`_subq_parent` / `_subq_child`）执行同样形状的 UPDATE，**同样报 PXC-4518**，证明是 PolarDB-X 校验层限制，与表名、与本次改名前缀均无关。
- **注意 INSERT 不受影响**：同样形状的子查询在 `INSERT ... VALUES ((SELECT ...))` 里**可以正常工作**，这正是 §17.4 的 POST 能返回 201 而 PUT 从未被验证过的原因——**§17.4 / §18 只验证过 POST/GET/DELETE，没有验证过任何 PUT**。
- **可行修法（已实测）**：改写为多表 JOIN 形式即可，`UPDATE 父表 p LEFT JOIN 子表 c ON c.id = ? SET p.col = c.id WHERE ...` 在 5.4.19 上**执行成功**。纯字面量赋值（无子查询）同样正常。
- **结论**：`Dashboard` / `Widget` 的**更新**接口在 PolarDB-X 上是坏的，需要在接入环境前修掉（本轮只做改名，**未改 SQL 语义**）。

### 29.4 环境事件（重要，非本项目代码问题）

- **ClickHouse 后台 mutation 失控**：`langfuse-clickhouse-1` 一个后台 mutate/merge 进程内存涨到 4 GiB+、CPU 95%，日志中出现内存分配失败的栈；它把 Docker VM（7.666 GB）挤爆，**直接导致 Docker Desktop 的 VM 重启**——我手动停掉的 5 个 Langfuse 容器因此**自行复活**，同时 `polardbx-playground` 被 OOM 杀掉（`Exited 137`，与 §23.4 同因）。这是 §23.4 / §24.5 记录的"内存是本机最主要运行风险"的再次应验。
- **Docker Desktop 端口转发曾损坏**：VM 重启后，`8527` 的 `-p` 映射在 `docker inspect` 里仍显示正常（`0.0.0.0:8527->8527/tcp`），但宿主机上**没有任何进程监听**、连接一律 `Connection refused`；容器内 CN 完全正常。用一个**全新容器**发布 `18527` 端口做对照，同样 REFUSED，**证明是 Docker Desktop 守护进程的端口转发整体失效，不是 PolarDB-X 特有问题**。`stop`/`start`、`restart` 多次均无效。
  - 影响：后端（跑在宿主机）连不上 PolarDB-X，`HikariPool(Mysql)` 初始化报 `CJCommunicationsException: Connection refused`，`/api/v1/workspace/**` 返回 503 `DATA_SOURCE_UNAVAILABLE`（与 §17.6 现象相同，但本次根因是端口转发，**不是连接池毒化**）。
  - **已解决**：见 §29.6——`osascript quit` 只退出 GUI，**`com.docker.backend` 与 VM 并未重启**（进程 uptime 仍是 8 天 22 小时，VM 进程根本不存在），必须 `pkill` 掉全部 docker 进程再 `open -a Docker` 才是真重启。真重启后 8527 立即恢复。
- 另注：会话末期 macOS **再次撤销了终端对项目目录的 TCC 授权**（与 §23.4 同一问题），导致 `PROJECT_PROGRESS.md` 及顶层文件一度读写全部 `EPERM`；恢复授权后本节才得以写入。
- **OOM 在会话内第三次发生**：Docker 真重启后全栈均正常，但随后跑一次 `mvn test` 即再次把 VM 压爆——`langfuse-web` / `langfuse-worker` / `langfuse-clickhouse` 被 SIGKILL（`Exited 137`），其余优雅退出。已全部重启恢复。**结论：本机在 8 GB Docker VM 下，Maven 全量测试与 Langfuse 全栈无法同时运行**，跑测试前应先停 Langfuse 栈（或调大 VM 内存）。

### 29.5 端到端 HTTP CRUD 验证（Docker 恢复后补做）

Docker Desktop 真重启后，`mybatis` Profile 后端在 8080 启动，`/actuator/health` = **HTTP 200 UP**（该值即证明 PolarDB-X 与 ClickHouse 两个数据源均连通）。

**结论：表名改前缀本身完全成功**——读、写、删三条路径经真实 HTTP 与 JDBC 全部打通。**唯一失败项是 §29.3 那个与改名无关的预先存在缺陷**（PUT），它在本轮之前就存在，只是历史上从未被端到端验证过。

| 操作 | 结果 |
|---|---|
| `POST /api/v1/auth/login`（admin） | **200**，role=ADMIN |
| `GET /api/v1/workspace/dashboards` | **200**，`total=0`（读 `langfuse_dashboards` 成功） |
| `POST /api/v1/workspace/dashboards` | **201**，返回 id；**直查 `langfuse_dashboards` 确认落库**（`project_id=aiops-default`） |
| `PUT .../{id}/metadata` | **503 `DATA_SOURCE_UNAVAILABLE`** —— §29.3 缺陷经**真实 HTTP 路径**复现，后端日志为完全相同的 `PXC-4518 ERR_VALIDATE: update not support subquery node` |
| `DELETE .../{id}` | **204**，复核 `langfuse_dashboards` **归 0**，测试数据已清理 |
| ClickHouse 观测链路 | **未被表名改动影响**：`/observations` = **21**、`/traces` = **3**，与 §23.3 基线完全一致 |

- `created_by` / `updated_by` 落库为 **NULL**：审计列的子查询取 `#{actor}`，而 `langfuse_users` 只有 `migration-service`，当前登录用户 `admin` 不匹配——**属预期行为，与改名前缀无关**。
- 一个测试陷阱：`/observations` 等接口**不接受 `dateRange=`**（那是前端参数），时间只能通过 `fromTimestamp`/`toTimestamp` 传；不传时按 §25.4 默认最近 24 小时，故用 `dateRange=30d` 会得到 `total=0`，**别误判为数据丢失**。调试数据（2026-09-07）用
  `fromTimestamp=2026-09-06T00:00:00Z&toTimestamp=2026-09-08T00:00:00Z` 可正常查出 21/3。

### 29.6 Docker Desktop 真重启方法（本次踩坑记录）

`osascript -e 'quit app "Docker"'` **不可靠**：它退出 GUI 后 `com.docker.backend` 仍在运行（实测 uptime 8 天 22 小时，跨了整个"重启"），且 Virtualization VM 进程**根本不存在**，守护进程处于半死状态，`open -a Docker` 也拉不起来。表现就是 daemon 一直 `not ready`。

正确做法是**把 docker 相关进程全部杀掉再启动**：

```bash
pkill -f "Docker Desktop"; pkill -f "com.docker.backend"
pkill -f "com.docker.dev-envs"; pkill -f "com.docker.build"; pkill -f "com.docker.extensions"
sleep 15 && open -a Docker     # VM 进程出现后 daemon 5 秒内就绪
```

- 另注：`pgrep -x Docker` 判活**不可用**，该进程名是 `Docker Desktop` 而非 `Docker`，会得到假阴性。
- 7 个容器均为 `always` / `unless-stopped`，daemon 恢复后**自动全部拉起**，无需手工 `start`。
- 重启后宿主机内存需重新评估：VM 仍为 8.2 GB，`langfuse-clickhouse-1` 的后台 mutation 风险未消除（§29.4）。

### 29.7 剩余事项

1. **`UPDATE` 子查询缺陷待修**（§29.3），影响 Dashboard/Widget 更新接口，经真实 HTTP 路径确认（PUT → 503），需在接入 PolarDB-X 前处理。
2. 本机 ClickHouse 的后台 mutation 需要关注，它是 VM OOM/重启的直接诱因。
3. 「7 天 TTL vs 前端 30d/90d」产品决策**仍未决**（与 §25.8 / §28.7 一致）。

## 30. 2026-09-14 Observability Mapper 全量迁移至 AgentObs

### 30.1 改造结果

- `ObservabilitySqlProvider` 已只读 `hmp_agentobs_traces_all`、`hmp_agentobs_observations_all`、`hmp_agentobs_scores_all`，所有语句均带 `FINAL`。
- Summary 与最近 7 天趋势拆成 Trace / Observation 两组独立 SQL，Service 按 UTC 日期合并并补零，不在两个 Distributed 表之间做无界 JOIN。
- Session/User 列表及汇总统一基于 Observation 聚合；Trace 数使用 `uniqExact(trace_id)`，token total 为 0 时回退 input + output，cost 使用 Nullable 安全汇总。
- Session/User 明细的 Trace/Observation 行查询迁入 `TracingMapper`；Trace 行先按 session/user 读取，再仅对有限 Trace ID 批量补齐 Observation count、token、cost。Session Observation 继续复用已验证的大小字段拆分、TTFT、usage/cost JSON 和 Trace Lookup 映射。
- Score 已切换到 `hmp_agentobs_scores_all`，映射 `score_id` / `value_string`，删除旧删除标记语义。
- Widget：Observation 指标使用 AgentObs Observation；Trace count/latency 直接使用 Trace；Trace token/cost 使用 Observation，限无维度/environment/userId。`inputCost`、`outputCost` 及 `Trace name + token/cost` 明确返回 400。
- ClickHouse 查询不再使用 HTTP 路径项目 ID；项目 ID 仅保留在 PolarDB-X Widget 定义读取链路。
- `ObservabilityRows` 已删除重复的 Trace、TraceDetail、Observation 行模型，统一复用 `TracingRows`。
- 无 AgentObs 来源字段的 WARN 已脱敏：不再记录完整搜索表达式或筛选值，只保留字段名、用户、端点和请求 ID，并维持 5 分钟重复抑制。

### 30.2 ClickHouse 21.8 实测发现并修复

本轮把兼容性探针扩展到 Tracing、Observability、Widget 三组 Provider，共 47 条语句。首次真实执行发现两个纯文本单测无法识别的问题：

1. 21.8 的 `dateDiff` 不支持 `millisecond` 单位。Session 时长改为 `toUnixTimestamp64Milli(max(end_time)) - toUnixTimestamp64Milli(min(start_time))`。
2. User 列表中输出别名 `environment` 会被 21.8 代入同层 WHERE，形成 aggregate-in-WHERE。环境过滤改到内层 Observation 查询，外层再执行 `argMaxIf`。
3. Widget 空维度统一使用 21.8 支持的 `CAST(NULL AS Nullable(...))` 语法；探针的 Instant 替换也改为显式 `toDateTime64(..., 6)`，避免测试工具自身产生字符串转换假失败。

修复后结果：

- 空表：**47/47** Provider SQL 通过。
- 有数据：**47/47** Provider SQL 通过。
- 每条语句均先执行 `EXPLAIN SYNTAX`，再执行实际查询；ClickHouse 21.8 与当前 25.12 环境均为 **47/47**。
- 样本覆盖：单行、重复 `ingestion_version`、Nullable cost、total token 为 0 回退、空 session/user、FixedString Trace/Span ID、Score。
- 数据口径：去重后 Trace=1、Observation=2；Observation tokens=30、cost=1.25；Session/User 均为 Trace=1、Observation=1、tokens=25、cost=1.25；空 session/user 行不会进入对应聚合。

临时 ClickHouse 21.8 容器及测试数据已删除。

### 30.3 测试与真实接口回归

- JDK **1.8.0_292** 全量 Maven 测试：**131/131 通过**，0 failure / 0 error / 0 skipped；class major version = 52。
- 新增 Service 回归，锁定 Summary/趋势合并、Session Trace 两阶段指标补齐，以及 Widget 不支持组合在进入 Mapper 前失败。
- 重启真实 `mybatis` 后端后，以 mock AAM 登录执行：login / summary / summary timeseries / sessions / users / traces / widgets 均 HTTP 200。
- 真实 AgentObs 数据：Summary 返回 Trace=3、Observation=21；指定历史窗口 Trace 列表返回 3 条；Trace Score 查询 HTTP 200（当前样本无 Score，返回空数组）。
- 临时创建一个 Trace count 时间序列 Widget：创建 201、指标执行 200（返回 2026-09-07 count=3）、删除 204；测试定义已清理。

### 30.4 剩余风险

1. 当前真实 AgentObs 样本的 `session_id` / `user_id` 均为空，因此真实 HTTP 只能验证 Session/User 列表 200 + 空结果；详情路径已由 Service 单测和 ClickHouse 21.8 有数据 SQL 验证，仍建议在接入包含真实 session/user 的环境后补一次 HTTP 详情验收。
2. §28 的 Distributed 验证覆盖的是旧 26 条 Tracing SQL；本轮新增 21 条在 21.8 单节点 View 形态完成验证，尚未在真实多分片集群重新跑 47 条全量探针。
3. 7 天 TTL 与前端 30d/90d 的产品决策仍未解决。

## 31. 2026-09-14 接入行内 AAM（统一认证）

按《Langfuse Web 后端接入行内 AAM 方案》实施。**采用「端口 / 适配器」拆分**：所有策略逻辑本地实现并真实测试，只有不可本地编译的 Hermes 握手集中到一个薄适配器。

### 31.1 ⚠️ 关键约束：`hermes-aam` 本地无法解析

**这是本轮的唯一硬约束，后续接手必须先知道。**

- `com.icbc.hermes:hermes-aam:6.69.5500050.0` **不在** `~/.m2`（`com/icbc/` 下只有空的 `sirius`、`dmqs` 目录），**不在** Maven Central，本机**没有** `settings.xml` / mirror / 仓库配置；全盘搜索 `hermes*.jar`、`ssic*.jar` 均为空。
- 结论：**任何 import `com.icbc.hermes.*` / `com.icbc.ssic.base.*` / `com.icbc.sirius.aam.ldap.*` 的代码在本机无法编译**；一旦把这些依赖写进默认构建，121 个测试会全部跑不起来。

**因此的工程决定（不是妥协，是设计）：**

| | |
|---|---|
| 默认构建（无 profile） | **不引入任何行内依赖**，保持 `mvn -o test` 全绿 |
| `-Paam` | 才声明 hermes/sirius 依赖 + 用 `build-helper` 把 `src/main/aam/java` 加为源码根 |

实测验证：`mvn -o -Paam compile` 会**明确因缺少那两个行内构件而失败**（`Could not resolve dependencies ... hermes-aam ... sirius-aam-ldap`），证明 profile 与源码根接线正确；在内网仓库可达处加 `-Paam` 即可编译适配器。

### 31.2 端口 / 适配器边界

- **端口**：`AamTicketAuthenticator`（`security/AamTicketAuthenticator.java`）——契约写明返回的用户号**只能来自已验签的凭据**，且返回**原始**用户号（标准化交给调用方，从而可脱离 hermes 测试）。
- **适配器**：`src/main/aam/java/.../HermesAamTicketAuthenticator.java` + `IcbcAamConfiguration.java`，**只在 `aam` profile 编译**。已对照参考工程 `LoginController` 取出真实 API：`com.icbc.hermes.aam.EnableAam`、`AamConfig#getServerSideAuth()`、`AamWrapper#auth(request,response,ssiAuth,ssiSign,authenticator)`、`request.getAttribute("ssiCredentials")` → `com.icbc.ssic.base.Credentials#getSSICUser()`、`new AamWrapper("SM2")`、`AamWrapper#logout(req,resp,"","",authenticator)`；LDAP 用 `com.icbc.sirius.aam.ldap.UserAuthenticate#searchAamUser`。
- 适配器刻意保持“薄”：标准化、权限解析、Session/CSRF 全在无依赖的类里，**内网只需验证这一个握手**。

### 31.3 本地已实现并测试的部分

| 方案条目 | 实现 |
|---|---|
| §6 请求 DTO | `AamSsoLoginRequest`：只有 `SSIAuth`/`SSISign`（+ 小写别名）。`aamId`/`role`/`admin`/`department`/`userName` **根本没有字段可绑定** |
| §7 登录 Controller | `AamSsoController`：判空→`URLDecoder.decode` **只解码一次**→握手→身份取自凭据；失败返回 `{"code":"1"}`，HTTP 仍 200 |
| §7.6 用户号标准化 | `AamUserIdNormalizer`：**照搬参考实现**（`LoginController.initSession`）——`length()==10` 时 `substring(1,10)`，即 10 位去首位得 **9 位** |
| §8 Session 衔接 | `AamSessionService`：`getSession(true)`→`changeSessionId()`→新建 `SecurityContext`→写入 session（`SPRING_SECURITY_CONTEXT`）→设置超时→重发 CSRF。**未沿用参考工程的 Shiro 空密码 `subject.login()`** |
| §9 放行与 CSRF | `/aam/login/auth`、`/api/aam/login`、`/api/v1/auth/config` 匿名；**仅** `/aam/login/auth` 豁免 CSRF（`ignoringAntMatchers`），其余写接口照旧校验 `X-XSRF-TOKEN` |
| §10 权限 | `AamRoleResolver` + `ConfiguredAamRoleResolver`（本地白名单）/ `TableAamRoleResolver`（`langfuse_user_roles`）；未知 code、无行、**数据库不可达**一律回落 **VIEW（拒绝提升）** |
| §11 响应 | `AamCommonResponse` + `AamLoginResponse`：`{code,msg,result:{user:{...,csrfToken},menu:[]}}` |
| §12 退出 | `/aamlogout` 与 `/api/v1/auth/logout` 都先调 AAM logout 再清本地 session；**AAM logout 失败也照常清本地**，不留活会话 |
| §13 隔离 | `app.auth.mode` 是**唯一开关**（见 §31.4）；mock 登录端点 `MockLoginController` 只在 mock 注册，aam 模式下该路由**不存在**（404，有测试断言） |
| §2 占位替换 | 删除 `GatewayAamCredentialVerifier`、`RemoteAamCredentialVerifier`、`AamLoginCredential`、`AamVerificationContext`、`AamGatewayHeaderProperties` 及其两个测试类 |
| §3 依赖 | pom 新增 `aam` profile（hermes-aam 排除 `hsm-hardware-encryption` + sirius-aam-ldap + build-helper 源码根） |
| §4 配置 | **全部并入 `application.yml`**，不新增 Spring profile 文件；`aam.*` 与 `app.auth.mode` 同一个文件、同一个开关。值全部走环境变量，**仓库内不含任何私钥、口令或系统标识** |
| §5 启动校验 | `AamConfigurationValidator`（`BeanFactoryPostProcessor`）：`mode=aam` 时密钥缺失**在实例化任何 bean 之前**失败，并列出缺失的键名（**不打印值**） |

### 31.4 开关设计：一个配置项，两层含义（务必分清）

「认证用哪个」和「能不能编译」是**两件不同的事**，不要混为一谈：

| | 作用层面 | 位置 | 取值 |
|---|---|---|---|
| `app.auth.mode` | **运行期**选认证方式 | `application.yml` | `mock`（默认）/ `aam` |
| Maven `aam` profile | **编译期**能否引用 hermes 的类 | `pom.xml` | 需要时加 `-Paam` |

**运行期只有 `application.yml` 一个开关，没有 Spring profile。** `application-aam.yml` 已删除，`aam.*` 配置块直接放在 `application.yml` 里，用空默认值保证 mock 模式无需任何 AAM 环境变量即可启动。

Maven 的 `aam` profile **必须保留**：hermes 适配器里的 `import com.icbc.hermes.*` 是**编译期**依赖，如果它进入默认源码树而本机又没有那些 jar，**整个项目连编译都过不去**——mock 模式、132 个测试、本地前端联调会全部停摆。这个 profile 不是运行期开关，去掉它不等于简化配置，而是放弃本地可编译性。

> **如果确认以后只在内网构建**，可以彻底去掉 `-Paam`（移依赖到默认 `<dependencies>` + 移源码到 `src/main/java`）。
> 完整改法、代价和验证步骤见 **`AAM_INTRANET_HANDOVER.md` §2.1**。**本轮未执行**——做了这台 Mac 就编不了了。

启动方式（内网）：设 `APP_AUTH_MODE=aam` + 六个 `AAM_*` 环境变量，构建时加 `-Paam`。**不需要**再激活任何 Spring profile。

### 31.4.1 一处对方案的刻意偏离：用 `app.auth.mode` 而非 `@Profile("aam")`

方案 §5 写的是 `@Profile("aam")`。**未采用**，原因是一个真实的安全陷阱：

`MockAamCredentialVerifier` 的 `matchIfMissing=true`，如果按 Spring profile 做开关，**只要有人激活了 `aam` profile 却没设 `app.auth.mode=aam`，Mock 认证器就会静默生效**——这正是方案 §13 明令禁止的“AAM 验证失败后自动退回 Mock 登录”。

改为以 `app.auth.mode` 为唯一开关后：未识别的取值会导致 `AamRoleResolver` 根本没有 bean，**应用启动直接失败**（fail-closed），而非静默降级。这与方案 §5 “密钥缺失应直接启动失败”的意图一致且更可靠。

### 31.5 数据库

- 新增表 `langfuse_user_roles`（带 §29 的 `langfuse_` 前缀）：`aam_user_no` 主键 + `role_code`，只允许 `VIEW`/`ADMIN`。
- **真机验证**：该 DDL 已在真实 PolarDB-X 5.4.19 建表成功；`UserRoleMapper` 的确切 SQL（`SELECT role_code FROM langfuse_user_roles WHERE aam_user_no = ?`）实测 ADMIN/VIEW/未知用户三种情形均正确，`updated_at` 自动填充。测试数据已清理。
- 顺带记录一个 PolarDB-X 行为：**无 `WHERE` 的 `DELETE FROM t` 会被拒绝**（`PXC-4620 ERR_FORBID_EXECUTE_DML_ALL`），需带条件或加 `/*TDDL:FORBID_EXECUTE_DML_ALL=FALSE*/` 提示。

### 31.6 验证结果

- `mvn test`（Java 8.0_292）：**125/125 通过**（较 §29 的 96 增加 29）。
- **真实进程启动验证**（非仅单测）：
  - `APP_AUTH_MODE=aam` 且六个密钥全空 → 启动失败，输出 `AamConfigurationValidator` 的明确信息（逐条列出缺失键名），且**先于**「找不到 `AamTicketAuthenticator` bean」报出——证明 `BeanFactoryPostProcessor` 的时序生效。
  - 默认 `mock`、**未设任何 `AAM_*` 环境变量** → 10 秒内正常启动，`/actuator/health` = `UP`，`/api/v1/auth/config` 返回 `{"mode":"mock","loginUrl":"/api/v1/auth/login"}`。
- 新增覆盖（对应方案 §15）：`AamUserIdNormalizerTest` 5 项（10 位去首位、短/长值不动、空白拒绝）、`AamRoleResolverTest` 7 项（白名单、表读、大小写、未知 code→VIEW、**库不可达→VIEW 不提升**、按标准化后的号查询）、`AamConfigurationValidatorTest` 4 项（缺失键名逐条列出、空白视为缺失、**失败信息不泄漏口令值**、完整配置放行）、`AamSsoIntegrationTest` 10 项（aam 模式全链路）。
- 关键断言：验票失败→`code:"1"`；缺参→400；小写别名可用；**请求体伪造 role/admin 无效**；**回调免 CSRF 而其他写接口仍 403**；登录前后 **session id 已更换**；`/api/v1/auth/me` 可恢复身份；退出后 401；**aam 模式下 mock 登录路由 404**；mock 模式下 `/aam/login/auth` 404 且 `config` 报 `mock`。
- 默认构建隔离：`mvn -o test` 无需任何行内依赖即全绿；`mvn -o -Paam compile` 如期因缺少内网构件失败。

### 31.7 内网启动方式

```bash
# 1. 改配置：把 application.yml 的 app.auth.mode 改成 aam，并填好文件底部的 aam: 块
#    （aam.ssic.server.ip / pub_key_path / version，aam.ssic.client.site_url /
#      key_name / pri_key_path / pri_key_passwd）
#    不需要设任何环境变量，不需要激活任何 Spring profile。

# 2. 构建（需要行内 Maven 仓库可达，hermes 依赖才会解析成功）
mvn -Paam -DskipTests package

# 3. 直接运行
java -jar target/langfuse-web-service-0.1.0-SNAPSHOT.jar
```

关掉 AAM 只需把 `app.auth.mode` 改回 `mock`，`aam:` 块留空即可——已实测无任何环境变量也能正常启动。

### 31.8 未完成与风险

1. **Hermes 适配器未在真实环境编译/运行过**——这是本轮唯一未经实测的代码（`HermesAamTicketAuthenticator`、`IcbcAamConfiguration`）。内网首次接入时**必须**先跑 `mvn -Paam compile` 验证 API 签名，尤其是 `Credentials`/`SSICUser`/`ServerSideAuthenticator` 的实际包路径与 `AamConfig.getServerSideAuth()` 的返回类型。
2. **`/api/aam/login` 跳转入口由 Hermes 的 `@EnableAam` 提供**，本轮未自建；需在内网确认它注册的路径与放行规则一致。
3. **§14 单点登录控制（JNOS Redis `sso-portal-{userId}`）未实现**——方案定为第二阶段，上线前需向行内 AAM/安全团队确认是否强制。
4. **前端未改动**：`/aamlogin` 回调页、`LOGIN`/`loginType` 清理、`config` 驱动的跳转均属前端工作，本轮只交付后端。
5. **会话超时默认 30m**，需与行内标准核对（直接改 `application.yml` 的 `app.auth.session-timeout`）。
7. **`application.yml` 里填了真实值后不要提交回仓库**：`aam.ssic.client.pri_key_passwd` 是明文口令。若部署平台支持注入，可把该单个值改回 `${AAM_CLIENT_PRIVATE_KEY_PASSWORD}`。
6. `departmentId` 目前回显部门名（目录只提供名称，无独立 id 列），若行内要求真实部门编号需补充目录字段映射。

## 32. 2026-09-15 AAM 验收整改（P1×2，跳过第 1、4 点）

针对验收结论逐条核实。**按用户要求，第 1 点（真实 Hermes 编译）与第 4 点（JNOS Redis 单点会话）本轮不处理**；第 2、3 点已整改。

### 32.1 核实结果：两条 P1 全部属实，且都能对上参考工程源码

先核实再改，两条都有据：

**第 2 点 —— LDAP 未完整接入：属实，且比验收描述的更严重。**

参考工程 `conf/AAMConfig.java` 确实显式注册了 Bean：

```java
@Configuration
@ConditionalOnProperty(prefix = "aam", name = "enableSSIC", havingValue = "true", matchIfMissing = true)
@EnableAam
public class AAMConfig {
    @Bean
    public UserAuthenticate userAuthenticate() { return new UserAuthenticate(); }
}
```

而 `AAMLdapConfig`（`@EnableAamLdap`）的条件是 `enableSSIC=**false**`——**两条路径互斥**。本项目用的是 `enableSSIC: true`，正对应 `AAMConfig` 这条，所以**必须显式注册**，我之前的"靠自动配置提供"是错的。

同时核实了用户号时机：参考 `LoginController.initSession` 是

```java
if (userId.length() == 10) { userId = userId.substring(1, 10); }   // 先标准化
getUserBasicInfo(userInfoBean, userId);                            // 再查目录
```

我原来是拿 `ssicUser.getUserName()` 的**原始 10 位**去查目录 —— 真实环境**必然查不到**，姓名部门会永远为空。这是本轮最有价值的修正。

**第 3 点 —— 门户退出闭环缺失：属实。**

前端 `Navbar/index.vue:162-172` 的时序是**两条分支都跳转**：

```js
un.post("/aamlogout", {})
  .then(...).catch(...)                                  // 无论成败
  // 两条分支内都是：
  window.location.href = serverPath + "/api/aam/logout"; // 整页跳转门户退出
```

即：本地 `/aamlogout` 只是**一半**，之后浏览器必须整页访问 Hermes 的 `/api/aam/logout` 以清除门户自己的 SSO 会话。**XHR 做不到**——只有整页导航才会带上门户 Cookie。缺这一步，用户退出后下次打开页面会被 AAM 直接重新登入。

### 32.2 改动内容

- **`IcbcAamConfiguration`**：新增显式 `@Bean UserAuthenticate userAuthenticate()`，并用 `@ConditionalOnMissingBean` 防止与其它来源冲突；注释写明与参考工程 `AAMConfig` 的对应关系与 `enableSSIC` 互斥条件。
- **`HermesAamTicketAuthenticator`**：
  - 查目录**前**先 `AamUserIdNormalizer.normalize(...)`（与参考实现同序）；
  - **只查一次**目录（原来姓名、部门各查一次，两次远程往返）；
  - 从同一条 `AamUser` 记录取出 `getUserName()` / `getDepartmentId()` / `getDepartmentName()`，**保留真实 departmentId**（原来把部门名回显成 id，§31.8 第 6 条登记过）。
- **`AamVerifiedIdentity` / `AamUserPrincipal` / `CurrentUserResponse` / `AamLoginResponse`**：把 `department` 拆成 `departmentId` + `departmentName` 贯穿全链路。
- **门户退出闭环**：`AuthConfigResponse` 新增 `logoutUrl`；新增配置 `app.auth.portal.login-url` / `logout-url`（默认 `/api/aam/login`、`/api/aam/logout`）；`AuthenticationController#config` 在 aam 模式下下发 `logoutUrl`，**mock 模式下为 null**（无门户可退）。
- **`AuthenticationController#logout`** 注释写明这只是"一半"，另一半是前端整页跳转。

### 32.3 ⚠️ 前端改动未经验证 —— 且 `frontend/` 目录存在环境不一致

为闭合退出流程，改了前端三处（`store/index.js` 先取 `logoutUrl`、`api/client.js` 加 `authApi.config()`、`App.vue` 用 `departmentName`）。**但这三处都没能构建验证**，原因是一个**预先存在的环境问题**：

| 目录 | 时间 | 技术栈 | node_modules |
|---|---|---|---|
| `frontend/` | **09-14** | **webpack 4 + Vue 2.6.12** | **缺失，无法构建** |
| `frontend_back/` | 09-09 | Vite 7.3.5 + Vue 2.7.16（§14 描述的版本） | 存在 |

- `scripts/frontend-runtime.sh:19` 检查的是 `frontend/node_modules/.bin/vite`，**说明启动脚本仍预期 `frontend/` 是 Vite 工程**，但它现在已经是 webpack 工程。
- 另存有 `frontend0914(1).zip`（09-14 17:08）。
- **本轮未处理该不一致**（属前端/构建范围，非 AAM 任务），但需要有人确认 `frontend/` 到底是不是当前交付目标；**在前端能构建之前，这三处改动属于"已写未验"**。

### 32.4 验证结果

- `mvn -o clean test`（Java 8.0_292）：**132/132 通过**（较 §31 的 125 增加 7）。
- 新增断言：
  - `AamSsoIntegrationTest`：登录响应含真实 `departmentId=D-1001` 与 `departmentName=研发部`（**证明不再是名字回显**）；aam 模式 `/api/v1/auth/config` 下发 `logoutUrl=/api/aam/logout`；新增 `logoutStillSucceedsWhenThePortalIsUnreachable` 证明门户不可达**不影响本地退出**。
  - `AamSecurityIntegrationTest`：mock 模式 `logoutUrl` **不存在**（前端不应跳转）。
- 前端：**未验证**（见 §32.3）。

### 32.5 过程陷阱：Maven 增量编译掩盖了编译错误

改完 `AamUserPrincipal` 构造器后 `mvn -o test` 报的是**运行期** `NoSuchMethodError`（3 个 Controller 测试），而不是编译错误——因为测试**源码**没变、只有依赖变了，增量编译没有重编它们（`target/test-classes` 09-14 10:14 vs `target/classes` 10:22）。

**必须先 `mvn -o clean test`**，否则会看到误导性的 `NoSuchMethodError`。clean 之后才暴露出真正的编译错误（`AamVerifiedIdentity` 旧构造器调用）。

### 32.6 剩余事项

- 第 1 点（`-Paam` 真实编译）与第 4 点（JNOS Redis）按用户要求未处理。
- **前端三处改动未验证**，且需先解决 `frontend/` 目录不一致（§32.3）。
- `/aamlogin` 回调页仍未创建（§31.8 第 4 条未变）。

## 33. 2026-09-15 Observability 验收整改（Dashboard/Widget/Session）

按最新验收意见处理两个 P1/P2 阻断和风险；用户明确要求暂不修改的
`scripts/frontend-runtime.sh` 保持原样。

### 33.1 PolarDB-X UPDATE 子查询

- `DashboardMapper` 三条 UPDATE 与 `DashboardWidgetMapper` 一条 UPDATE 已移除
  `SET updated_by = (SELECT ...)`。
- Service 在同一事务内先通过 `selectActorId` 解析存在的用户 ID，再把 Nullable
  `updatedBy` 作为普通参数传给 UPDATE，保留旧行为且避开 `PXC-4518`。
- 增加方言契约测试：Dashboard/Widget 的所有 `@Update` SQL 中不得出现 `SELECT`。
- 真实 mybatis + PolarDB-X HTTP：Dashboard create=201，metadata/definition/filters
  update=200，delete=204；Widget create=201，update=200，delete=204。临时数据已清理。

### 33.2 Widget Filters 与 Trace 指标粒度

- 删除“只要有 Filters 就拒绝”的总开关。已接入 AgentObs 可映射维度
  `name/environment/userId`（Observation 另含 `providedModelName/level`）的 string、
  stringOptions、categoryOptions、null 筛选；字段、类型、操作符均使用白名单，值全部绑定，
  单个 Widget 最多 20 个筛选条件。未迁移字段继续明确返回 400，不生成任意 SQL。
- Trace token/cost 改成 Trace 驱动：先按 `trace_id` 汇总 Observation token/cost，再与
  有界时间窗内 Trace LEFT JOIN，形成一 Trace 一行的 fact；外层 avg/min/max/pXX/count
  均按 Trace 粒度，sum 保持总量口径，并恢复 Trace name 分组能力。
- 带 environment Filters 的真实 Widget metrics 返回 HTTP 200（1 行）。

### 33.3 Session 详情边界

- Session Trace 上限 200、Observation 上限 2000；SQL 通过绑定的 `LIMIT` 查询“上限+1”。
- 超限时在构造 Trace ID 指标查询前返回明确 400，引导调用分页的 Trace/Observation
  列表接口，避免 SQL ID 列表、响应体和 JVM 内存无界增长，也不静默返回残缺详情。

### 33.4 验证

- Java 8.0_292 `clean test`：**132/132 通过**，0 failure / 0 error / 0 skipped。
  此数字来自清理旧 Surefire 报告后的 19 个实际测试类；增量执行时曾因残留报告误计为 137，
  已纠正，不再沿用叠加口径。
- Provider 探针新增 Filters 实际执行项，基线由 47 升为 48：ClickHouse 21.8.15.7
  与 25.12 均完成 `EXPLAIN SYNTAX` + 实际查询，**48/48 通过**。
- 临时 ClickHouse 21.8 容器、Dashboard、Widget 和临时会话文件均已删除。

### 33.5 未处理项

- `frontend-runtime.sh` 的 Vite/Webpack 依赖判断按用户要求本轮不改。
- Session API 现阶段采用“有界完整详情 + 超限明确失败”，尚未引入详情响应本身的分页协议；
  大型 Session 应改走现有分页列表接口。

## 34. 2026-09-15 前后端联调启动与「链路追踪打不开」排查

用户要求启动前后端验证功能。过程中暴露了一个前端构建缺陷、一次后端进程死亡，以及**一个长期被误认为不可用的能力**。

### 34.1 ⭐ 本机其实可以做浏览器自动化 —— §23.5 的结论已过时

§23.5 写着「本机无浏览器自动化工具，阶段 D 的交互回归未做」。**这个结论是错的，已可推翻。**

本机装有 **Google Chrome**，用 `--headless=new --remote-debugging-port=9222` 启动后，通过 **CDP**
（Node 22+ 内置 `WebSocket` + `fetch`，无需任何 npm 依赖）即可完整驱动页面：
导航、执行 JS、读取 DOM、抓 `Runtime.exceptionThrown` 与 `Runtime.consoleAPICalled`。

**这意味着此前一直挂着的「阶段 D：Tree/Timeline/Graph/Preview/Log View 的人工浏览器交互回归」现在可以自动化完成**，
不必再等人手工点击。建议后续接手者优先补上。

> 探针脚本本轮写在 `/tmp`（`probe-click.mjs` 等），**未入库**。若要长期使用应移入
> `scripts/` 并补 README，与 `scripts/clickhouse-version-probe/` 的组织方式保持一致。

### 34.2 前端构建缺陷：`frontend/` 缺少 `.babelrc`（已修复）

**现象**：`npm install` 成功后 `npm run dev` 直接 `Failed to compile`，5 个 `.vue` 文件报
`Module parse failed: Unexpected token`，位置都在可选链 `?.`。

**根因**：`webpack.config.js` 里 `babel-loader` **只配了 `cacheDirectory: true`，没有任何
presets/plugins**，且工程里**不存在 `.babelrc` / `babel.config.js`**。而 `package.json` 中
`@babel/core`、`@babel/preset-env`、`@babel/plugin-proposal-optional-chaining`、
`@babel/plugin-proposal-nullish-coalescing-operator` **四个包都已声明且已安装**——显然是配置文件丢了、包还在。

**修复**：新增 `frontend/.babelrc`，把已声明的包装上（`preset-env` + 两个 proposal 插件，`modules: false`）。
补完立即 `Compiled successfully`。**纯构建配置，不改任何行为。**

**从未构建成功过**：报错的 5 个文件都不是本轮改动涉及的，说明这是 `frontend/` 换进来时就带的问题。

### 34.3 ⚠️ `frontend/` 与 `frontend_back/` 技术栈冲突仍未解决

| 目录 | 时间 | 技术栈 | node_modules |
|---|---|---|---|
| `frontend/` | 09-14 | **webpack 4 + Vue 2.6.12** | 本轮才装上 |
| `frontend_back/` | 09-09 | Vite 7.3.5 + Vue 2.7.16（§14 描述的版本） | 存在 |

而 `scripts/frontend-runtime.sh:19` 检查的是 `${PROJECT_ROOT}/frontend/node_modules/.bin/vite`
—— **启动脚本仍按 Vite 工程写**，与 `frontend/` 现状不符。用户已确认跑 `frontend/`（webpack），
但脚本与 §14 描述**尚未同步**。另有 `frontend0914(1).zip` 备份。

**待办**：明确 `frontend_back/` 与 zip 的去留，并同步 `frontend-runtime.sh` 与 §14 的表述。

### 34.4 「链路追踪打不开」的真实原因：后端进程已死

用户报告点击 Tracing 菜单无反应、不跳转。

**根因不是前端**：后端进程在 **11:11:08 收到 SIGTERM 正常关闭**（Spring shutdown hook 完整执行、
Hikari 两池干净收尾、Maven 报 `exit code 143`），此后 8080 无监听，前端所有 `/api` 请求失败。
期间有人重新执行了启动脚本——该脚本会先停掉占用 8080 的旧后端，那条 SIGTERM 即由此而来。
新进程起来后一切正常。

**其次要原因是浏览器缓存**：用户首次打开页面时前端正处在编译失败状态，之后即使 dev server 修好并重启，
浏览器仍持有坏的 chunk / 失效的 HMR 状态。清站点数据 + 关标签页重开即恢复。

### 34.5 端到端验证结果（无头 Chrome + CDP，非推断）

在干净的无头 Chrome 中，先经接口登录再驱动页面：

| 检查 | 结果 |
|---|---|
| 登录前 | 菜单项 **0 个**（未登录，侧边栏不渲染） |
| 登录后 | 菜单项 **14 个** |
| 点击「Tracing」 | `/project/…/home` → `/project/…/traces`，**跳转成功** |
| Tracing 页面内容 | Filters、Quality/Slow、Table/Chart、**观测表格 21 行**（与接口 `total=21` 一致） |
| 控制台 | **零 error/warning** |
| 用户信息渲染 | `38971135 · AIOps` —— 即 §32 的 `departmentName` 改动**已生效** |

### 34.6 本轮的三个判断失误（记下来避免重犯）

1. **只看日志内容、没做服务存活检查**：第一遍扫日志只 grep `ERROR|Exception`，漏掉了 Maven 的
   `BUILD FAILURE` / `exit code 143`，导致"日志干净但服务已死"的盲区持续了半小时。
   **已纠正**：监听器加入 `curl /actuator/health` 存活探测。
2. **`grep -c "el-menu-item"` 数 DOM**：把内联 CSS 里的类名一起数进去了，误判"菜单已渲染"。
   实际 `querySelectorAll` 为 0。**数 DOM 必须用 `querySelectorAll`，不能 grep HTML 文本。**
3. **按 `getAttribute('index')` 找菜单项**：Element UI 的 `index` 是内部 prop，**本来就渲染成 `null`**，
   不是 bug。改用文本匹配后正常。

三次都是**先下结论再验证**。改用受控实验（先登录 → 按文本点击 → 对比前后 URL）后才拿到可信结论。

### 34.7 当前运行状态

- 后端 `mybatis` profile 运行在 8080（Java 8.0_292），`/actuator/health` = `UP`
- 前端 webpack dev server 运行在 5173，`Compiled successfully`
- 容器：PolarDB-X 5.4.19、ClickHouse、Langfuse 全栈均 Up
- 链路追踪功能经无头浏览器端到端验证通过

## 35. 2026-09-15 `backend-intranet` 拆分方案评审（**未实施**）

有人提出把后端拆成 `backend`（行外开发）+ `backend-intranet`（行内唯一交付）两份，行内版固定单一 AAM 链路。
**方案方向认可，但本轮未改动任何代码**（用户明确「先不用改造」）。以下是核实到代码行的**两个阻断点**与一个陷阱，实施前必须先解决。

### 35.1 🔴 阻断点一：前端**必须**改，方案「避免已交付前端再次修改」与事实不符

当前前端的登录是**凭据表单 POST 到 mock 端点**：

```js
// frontend/src/api/client.js:224
login: async (aamId, ticket) => { ...; return mutate("/api/v1/auth/login", "POST", { aamId, ticket }); }
// frontend/src/App.vue:6 → LoginPage 表单 → store.login → 上面这个
```

方案第 2 条要求行内版**不存在** `/api/v1/auth/login` —— 那前端就登不进去。
AAM 登录是**整页跳转**形态（浏览器 → `/api/aam/login` → 门户 → 前端 `/aamlogin` 页 → POST `/aam/login/auth`），
这三步**一个都不存在**（§31.8 第 4 条、交接手册 §6 均记「完全未做」）。

- `/me`、`/csrf`、`/logout`、`/config` 的路径与结构**可以保持稳定**
- 但**登录入口必然要改**，方案需把前端改造列进范围，不能假设不用改

### 35.2 🔴 阻断点二：PolarDB-X 驱动改动是**对赌**，必须先确认行内服务端版本

方案第 3 条要求把 `application-mybatis.yml` 改成 `jdbc:polardbx://` + `com.alibaba.polardbx.Driver`。
**§17.2/§17.3 是实测结论**：

| 驱动 | URL 前缀 | 走 HaManager | 老 X-Cluster | 2.4.x+（如 5.4.19） |
|---|---|---|---|---|
| `com.alibaba.polardbx.Driver` | `jdbc:polardbx://` | 是 | ✅ | **❌ 连接阶段直接失败** |
| `com.alibaba.polardbx.core.jdbc.Driver` | `jdbc:mysql://` | 否 | — | ✅ 实测可用 |

若行内是 2.4.x+，改完会得到 `Communications link failure / No available nodes meet the conditions in 18000 ms`，
**在任何 SQL 执行之前就失败**，现象像网络问题，极费排查时间。

**§17.5 早已写明「核对待接入环境的 PolarDB-X 版本是第一优先级」，至今未确认。**
判别依据：`SELECT VERSION()`，或看 `information_schema.alisql_cluster_global` 是否存在。**改之前必须先查。**

**该改动会直接打破现有测试，必须同 commit 修改**：

```
PolarDbxDialectContractTest.java:57  assertTrue (contains "com.alibaba.polardbx.core.jdbc.Driver")
                                :61  assertTrue (contains "jdbc:mysql://127.0.0.1:8527/langfuse_web")
                                :62  assertFalse(contains "jdbc:polardbx://")
```

### 35.3 🟠 陷阱：删 `app.auth.mode` 必须**同步删 4 处** `@ConditionalOnProperty`

`matchIfMissing` 默认为 `false`，配置里没有该键时这些 bean **静默不注册**：

| 文件 | 行 | 后果 |
|---|---:|---|
| `api/AamSsoController.java` | 31 | `/aam/login/auth` **404**，登录彻底不可用 |
| `config/IcbcAamConfiguration.java` | 28 | 无 `AamWrapper` / 认证器 bean → 启动失败 |
| `config/AamAuthConfiguration.java` | 41 | 无 `AamRoleResolver` → 启动失败 |
| `config/AamConfigurationValidator.java` | 34 | 密钥缺失不再拦截启动（**静默降级**） |

另需一并删除 `AamAuthConfiguration:29`（mock 的 resolver bean）及所有 `matchIfMissing = true` 的类
（`MockLoginController`、`MockAamCredentialVerifier`）。

### 35.4 次要

- **重复维护漂移**：两份 backend 必然分化。建议在 §9 写明「行内版为后续交付主线，行外版仅作可编译基线，共性修复自内向外同步」。
- **复制时排除** `backend/target/`（含旧 class）；`.gitignore` 需加 `backend-intranet/target`。
- 行内版可**沿用** `application.yml` 里已就位的 `aam:` 块与 `AamConfigurationValidator` 的 fail-fast 行为，无需重写。

## 36. 2026-09-15 AgentObs Observation 类型/等级大小写兼容修复

行内 `GET /api/v1/observability/observations` 在结果中出现小写 `span` 时整页 500。已修复并真实验证。

### 36.1 复现（本地数据原本全是大写，需先造数据）

本地 `hmp_agentobs_observations_all` 原本是 `SPAN=20 / GENERATION=1 / DEFAULT=21`，**复现不了小写场景**。造一条小写行后三个失败模式全部复现：

| 场景 | 修复前 |
|---|---|
| 结果中含小写行 | **HTTP 500**，`No enum constant ... ObservationType.span` |
| `?type=span` | **HTTP 400**（Spring 默认枚举转换区分大小写） |
| TYPE facet | 分裂为 `SPAN`(20) + `span`(1) 两项 |

**造数据的正确方法**（`SELECT * REPLACE` 在 ClickHouse 25.12 上不可用，`EXCEPT` 会把表达式追加到列尾导致列序错位，必须显式写 66 列列名）：

```bash
# 1. 取列名（按 position 顺序）
docker exec langfuse-clickhouse-1 clickhouse-client --query \
  "SELECT name FROM system.columns WHERE database='default' AND table='hmp_agentobs_observations' ORDER BY position FORMAT TSV" > cols.txt
# 2. 用脚本生成 INSERT ... (全部列名) SELECT ...（把 type/level/span_id/service_name 换成字面量）
#    span_id / service_name 必须换，否则与源行同键会被 ReplacingMergeTree 去重
# 3. 执行：docker exec -i ... clickhouse-client --multiquery < insert.sql
```

> 踩坑：`sed 's/'"'"'span'"'"' AS type/'` 匹配不上 —— 生成的 SQL 里字面量是裸的 `'span'`，没有 `AS type`。
> 第一次 sed 静默失败，往库里插了 3 条同键行（视图 FINAL 去重后显示 1 条，**基表是 3 条**，容易看漏）。

### 36.2 根因

两条独立路径，都要修：

1. **结果转换**：`MybatisObservabilityQueryService` 直接调 `ObservationType.valueOf(row.type())`，遇到 `span` 抛 `IllegalArgumentException` → 整个请求 500。`ObservationLevel.valueOf` 同病。
2. **请求参数**：控制器声明 `@RequestParam ObservationType type`，Spring 内置枚举转换**区分大小写**，`?type=span` 在进方法前就被拒。
3. **SQL 比较**：`AND type = #{query.type}`、DSL 精确 `toString(type) = ?`、facet `GROUP BY type` 三处都直接比对原始大小写。

**注意 DSL 的模糊匹配本来就是大小写不敏感的**（`lowerUTF8` / `positionCaseInsensitiveUTF8`），只有精确匹配是敏感的 —— 所以 `type:span` 一直能搜到，`type:=SPAN` 搜不到，这个不对称很容易误判。

### 36.3 修改文件

| 文件 | 改动 |
|---|---|
| `domain/ObservationType.java` | 新增 `UNKNOWN`（**仅展示**）+ `tryParse` / `fromStorage` / `isKnown` / `filterable`；`Locale.ROOT` 规范化 |
| `domain/ObservationLevel.java` | 同上 |
| `service/UnknownStoredValueWarner.java` | **新增**。未知存储值的限频告警（300s / 512 键上限，形状照抄 `TracingFilterFields` 的既有实现） |
| `service/MybatisObservabilityQueryService.java` | `valueOf` → `storedType()` / `storedLevel()`，未知值降级为 `UNKNOWN` 并告警 |
| `api/ObservabilityQueryController.java` | `type` / `level` 参数改为 `String` + 显式解析；未知**请求**值抛 `InvalidRequestException` → 400，错误信息列出允许值 |
| `mapper/TracingSqlProvider.java` | 新增 `ENUM_UPPER_TYPE`/`ENUM_UPPER_LEVEL` 常量；谓词、DSL 表达式、facet 表达式统一用 `upper(...)`；DSL 精确匹配的**绑定值**同步转大写 |

**两种错误策略是刻意区分的**：未知**请求参数**是调用方的错 → 400；未知**存储值**不是 → 降级为 `UNKNOWN` 展示 + 告警，既不 500 也不伪装成 `SPAN`（否则计数会被悄悄并入 SPAN）。

### 36.4 真实接口验证（对着本机 ClickHouse）

| 验收项 | 结果 |
|---|---|
| 含小写行的列表 | **HTTP 200**（修复前 500） |
| `?type=span` / `?type=SPAN` / `?type=SpAn` | 三者**均 21**（20 SPAN + 1 span，大小写都命中） |
| `?type=bogus` | **HTTP 400**，信息含全部允许值 |
| DSL `type:=SPAN` 与 `type:=span` | **均 21**（精确匹配到小写存储） |
| TYPE facet | **`SPAN: 21`** 合并为一项（修复前分裂两项） |
| 列表 / facet 合计 / pulse 合计 | **23 = 23 = 23** 三者一致 |
| 未知类型 `BRANDNEWTYPE` | 返回 **HTTP 200 + `type: "UNKNOWN"`**，日志有 `Stored observation type value 'BRANDNEWTYPE' is not recognised` |
| 未知值是否被并入 SPAN | **否**，facet 中单独成项 |

测试数据验证后已清理，库还原为 `SPAN=20 / GENERATION=1`。

### 36.5 测试

`mvn -o clean test`（Java 8.0_292）：**145/145 通过**（较上轮 132 增加 13）。

新增：`ObservationTypeParsingTest`（6 项：大小写/空白、未知请求值返回 null、**UNKNOWN 不可作为请求值**、存储值降级、**未知值不被强制映射到已知类型**、isKnown 一致性）、`UnknownStoredValueWarnerTest`（5 项：首次记录、区间内抑制、超时后再记、不同值独立、超上限仍可用）、`TracingSqlProviderTest` 新增 3 项（枚举绑定走 `upper(...)`、大小写等价、facet 合并）。

同时更新了 2 处原有断言：`values()` → `filterable()`（因为 `UNKNOWN` 不该参与筛选循环）、`type = #{query.type}` → `upper(type) = #{query.type}`。

### 36.6 前端兼容性与遗留项

- **前端对 `UNKNOWN` 安全**：`TracingPage.vue:861` / `TracePeekPanel.vue:333` 的图标映射是 `return icons[type] || "◇"`，有兜底，未知类型显示默认图标而非崩溃。**无需前端改动。**
- **遗留（需产品决策）**：facet 会把未知存储值按原始名（如 `BRANDNEWTYPE`）列出，但点击它去筛选会得到 **400**（因为它不是合法请求类型）。同理 `UNKNOWN` 本身也不可筛选。也就是**facet 可能提供一个筛不了的选项**。三种处理方式各有取舍，未擅自选择：把未知值在 facet 中也折叠为 `UNKNOWN`（仍不可筛）、让 `UNKNOWN` 可筛选（需定义 SQL 语义）、或让 facet 不列出非白名单值（会隐藏真实数据）。**建议按行内对"未知类型应可查还是应隐藏"的要求决定。**

### 36.7 行内复核建议

本地数据全大写，**行内才是真实场景**。到行内后建议至少跑一轮：

```bash
# 1. 看库里真实分布与大小写变体
SELECT type, level, count() FROM default.hmp_agentobs_observations_all GROUP BY type, level ORDER BY count() DESC;
# 2. 记录枚举白名单外的值（若有，本次修复会以 UNKNOWN 展示并告警，但不会自动支持）
# 3. 逐条走 §36.4 的验收表
```

## 37. 2026-09-15/16 Trace 详情 locator 时间范围修复

仅修改后端 trace 详情范围参数和 SQL；保留 locator 的上下界，没有增加范围查询为空时的无时间条件兜底，未改前端或 ClickHouse 数据。

### 37.1 本机实际表与已知 trace 核查

本机 ClickHouse 25.12 的 `system.columns` 显示 `default.hmp_agentobs_observations_all.start_time` 为 `DateTime64(6, 'UTC')`，locator 的 `minStartTime` / `maxEndTime` 聚合状态也为 UTC；此结论是**本机实际表**，不能代替行内集群的元数据。已知 trace `ba8298c87681f90ab4ae2f580068e3bb` 的 locator 为 `2026-09-07 08:20:53.332000` 至 `2026-09-07 08:20:53.579000`。对 observations 分别只加下界、只加上界、同时加两界及 service 条件，三种情况均返回 11 行；上下界在本机都**未排除**数据。下界恰好相等的 observation 有 4 行。完整范围内 token 与 cost 汇总均为 0；本机没有找到非零 token/cost 的 trace 样本。

### 37.2 后端处理

- `traceParameters` 不再把 locator `LocalDateTime` 转成 `Instant`；依据配置的 locator 源时区与 observation 列时区做真正的时区转换，输出固定六位微秒 `yyyy-MM-dd HH:mm:ss.SSSSSS`。两者默认 UTC，行内须按实际列元数据配置；不依据 CK 服务时区猜测列时区。
- `TracingSqlProvider.locatorNarrowing` 为共享的 `traceObservations` / `traceMetrics` 生成 `toDateTime64(#{locatorMinStart}, 6, 'UTC')` 等显式类型比较；只允许 UTC 或 Asia/Shanghai 作为 SQL 时区常量，范围值继续走 `#{}` 绑定。若 locator 的时间范围为空或倒置，详情查询提前报错，不用无边界查询掩盖索引异常。
- 已覆盖六位微秒、上下界相等、UTC 与东八区列时区、两条 SQL，以及异常 locator 范围。

### 37.3 验证与剩余验收

Java 8.0_292 `mvn -q clean test`：**151/151 通过**。本机 ClickHouse 25.12 SQL 兼容探针更新后 **49/49**（语法及实际执行）；21.8 与 Distributed 形态的新版 49 项尚未复跑，旧结果见探针 README。Java 8 启动后 `/actuator/health` 为 `UP`；已知 trace 的详情和 observations 接口均 HTTP 200，`observationCount=11` 与列表长度 11 一致，`totalTokens=0`、`totalCost=0` 与 CK 原始行汇总一致。

**行内验收仍待完成**：当前工作环境没有行内 ClickHouse 连接，尚不能声明已核实行内实际 `start_time` 列时区、locator 值或非零 token/cost 样本。部署前先查行内 `system.columns` 和 locator/observation 对照；如参数正确但限定范围仍空，应检查 locator 范围值及物化视图索引一致性并修复索引，不得取消时间条件。行内表若非 UTC，按实际元数据设置源与列时区后复测上下界及 traceMetrics。

## 38. 2026-09-16 后端应用文根 `/icbc/hmp/agentobs`

Spring Boot 后端已统一使用 `/icbc/hmp/agentobs` context path。Controller、Security 和 CORS 的内部映射仍保持 `/api/**`、`/aam/**`、`/actuator/**`，没有手工拼入文根；MyBatis、ClickHouse、PolarDB-X 查询代码未修改。

### 38.1 改造内容

- `application.yml` 配置 `server.servlet.context-path`、Session Cookie Path，并启用 Spring Framework forwarded-header 处理；默认网关保留文根原样转发。
- CSRF Cookie 创建路径从 `/` 改为注入的 context path；退出时 `JSESSIONID` 和 `XSRF-TOKEN` 删除 Cookie 使用 `request.getContextPath()`，创建与删除路径一致。
- auth config 对相对登录/退出地址添加当前 context path；完整 HTTP(S) 地址及已带文根的地址保持不变，防止重复前缀。
- Dashboard、Widget、Prompt 的 `Location` 改由 `ServletUriComponentsBuilder.fromCurrentContextPath()` 生成，支持 context path 和 `X-Forwarded-Proto/Host`。
- 新增文根集成测试并更新 AAM、Dashboard、Widget、Prompt 测试，覆盖公开端点、鉴权、AAM 回调、Cookie Path、退出清理和 Location。

### 38.2 验证结果

Java 8.0_292 执行 `mvn -q clean test`：**154/154 通过**；`mvn -q -DskipTests package` 成功。真实启动日志显示 Tomcat context path 为 `/icbc/hmp/agentobs`：新健康地址返回 `UP`，旧根地址 `/actuator/health` 返回 404。Mock 登录、config、observability 列表均 HTTP 200；临时 Dashboard 创建 201、删除 204、退出 204。

真实响应中的 Cookie 均为 `Path=/icbc/hmp/agentobs`：登录创建的 `JSESSIONID` / `XSRF-TOKEN` 和退出返回的两个过期 Cookie 路径完全一致。带 `X-Forwarded-Proto: https`、`X-Forwarded-Host: agentobs.example.internal` 创建资源时，Location 为 `https://agentobs.example.internal/icbc/hmp/agentobs/api/v1/workspace/dashboards/{id}`。

### 38.3 待行内联调

Hermes 验票由测试替身覆盖了 Controller、安全豁免、用户规范化、角色与 Session 链路，但本机没有行内 Hermes 服务和真实密钥，仍需用 `https://部署域名/icbc/hmp/agentobs/aamlogin` 作为 `aam.ssic.client.site_url` 完成真实 SSO 往返。网关须保留 `/icbc/hmp/agentobs`，且仅在可信代理边界设置/覆盖 `X-Forwarded-*`；不能再删除一次文根。

## 39. 2026-09-17 行内 AAM 验签与 UniformTeller 用户信息接入

按 `dsf-self-analysis/LoginController` 的实际实现迁移了 AAM 链路，默认构建仍不依赖行内 SDK；真实适配器只由 Maven `aam` profile 加入编译。

### 39.1 登录链路

1. `/aam/login/auth` 对 `SSIAuth` / `SSISign` 做非空校验和一次 URL 解码。
2. `HermesAamTicketAuthenticator` 调用 `AamWrapper.auth(..., aamConfig.getServerSideAuth())`；false、SDK 异常、`ssiCredentials` 类型错误、`SSICUser`/用户号缺失均拒绝登录。
3. 最终身份只取 Hermes 写入的 `ssiCredentials -> Credentials -> SSICUser.getUserName()`，不接受前端用户号或角色。
4. 用户号规范化后，通过 `UniformTeller/2.0/qryTellerInfo` 查询基本信息：使用 `EncryptUtils.genEncryptParamJson`，POST `[`密文`]` 到 Cocoa Router，校验 `appStat.return_code=0`，再用本次请求返回的解密参数解密 `data`。
5. 映射 `TELLERNO/TELLERNAME/NOTESID/BRANCHID/BRANCHNAME`；缺少有效姓名、返回身份不一致、服务/解密/解析失败均拒绝登录，不再用 LDAP 或伪造用户资料兜底。
6. 继续由 `AamSessionService` 完成用户号二次规范化后的角色查询、Session fixation protection、SecurityContext 持久化及 CSRF 重签发。权限仍来自内部表：管理员 `ADMIN`，其他用户 `VIEW`。

认证失败统一返回 HTTP 401、`code=AAM_AUTH_FAILED`、`message=AAM authentication failed`，不返回票据、SDK 错误、密钥或内部地址；失败票据测试确认不会创建 Session。

### 39.2 配置与依赖边界

- 新增类型化 `AamIntegrationProperties` / `DsfCocoaRouterProperties`；`clientId` 等全部为 String，测试确认 `001642` 不丢前导零。
- `AamConfigurationValidator` 在 `aam` 模式校验规定的 13 项配置，只列缺失键名；`mock` 模式不触发。
- 删除 `sirius-aam-ldap`、`UserAuthenticate`、`AamUser`、`NamingException`、LDAP Bean 和失败后继续登录的 LDAP 兜底。
- 删除 `aam.ssic.server.pub_key_path`、`aam.ssic.client.pri_key_path`；AAM 配置只保留约定键。启动脚本没有 AAM 模式、地址、密钥或用户服务参数。
- `aam` profile 增加参考工程同版本 `com.icbc.aam:encrypt-client:7.1.2`；Hermes 保持 `6.69.5500050.0`。

### 39.3 验证

- Java 8.0_292 默认构建：`mvn -q clean test` **156/156 通过**；`mvn -q -DskipTests package` 成功。
- 实际 Mock 启动：成功；匿名业务请求 401、Mock 登录 200、`/auth/me` 200、无 CSRF 写请求 403，Session 中管理员角色为 ADMIN。
- AAM 测试替身：验签失败 401 且无 Session；成功身份来自适配器；普通用户 VIEW、内部表管理员 ADMIN；CSRF、用户号规范化和用户信息映射通过。
- 以 `app.auth.mode=aam` 和空行内配置启动默认包：进程按预期失败，异常逐项列出缺失配置且未输出值。
- `mvn -Paam -DskipTests clean package` 已真实执行但**未成功**：本机 Maven 仅能访问 Central，无法解析 `com.icbc.hermes:hermes-aam:6.69.5500050.0` 与 `com.icbc.aam:encrypt-client:7.1.2`。没有伪造行内构建结果。

### 39.4 行内剩余验证

接入行内 Maven 仓库后首先复跑 `mvn clean package -Paam`，确认 `hermes-aam` 是否传递提供 `com.icbc.ssic.base` 类型；随后用真实 SSIC 票据、Cocoa Router 与密钥验证验签、`ssiCredentials`、加密请求和解密响应。需确认 Cocoa 路由要求的 `X-Request-App` 仍为参考工程使用的 `F-HMP`，并核对实际 `TELLERNO` 与规范化用户号的格式一致性。

## 40. 2026-09-21 健康检查分组（供 F5 / SLB 探针使用）

### 40.1 背景与风险判断

申请 F5 与 SLB 需要提供健康检查接口。改造前的 `/actuator/health` **把两个数据源计入聚合状态**，任一数据源不可用即返回 HTTP 503。

直接用它做负载均衡探针会放大故障：ClickHouse 与 PolarDB-X 为**全部应用实例共享**，数据库故障属于**相关性故障**——一旦抖动，所有节点会在同一时刻被判为不健康并**同时被摘除**，把局部故障放大成全站不可用。本项目已出现过同类现象：PolarDB-X 连接瞬时不可用导致健康检查转 DOWN，而实际处置手段仅是重启后端进程（§17.6、§18 运维提示）。

结论：负载均衡检查应只反映**节点自身**是否可用，不反映共享依赖的状态；数据源健康交由监控告警。

### 40.2 改造内容

`application.yml` 增加健康检查分组，两个端点用途严格区分：

| 端点 | 组成 | 用途 |
|---|---|---|
| **`/healthz`** | 常量 `@the@health@is@good@` | **F5 / SLB 探针**。纯文本，不访问任何依赖 |
| `/actuator/health/liveness` | `ping` | 同上，JSON 形式，保留备用 |
| `/actuator/health/readiness` | `db,ping` | 监控告警，数据源不可用时返回 503 |
| `/actuator/health` | 全部 | 保持原状，聚合两组 |

`SecurityConfig` 放行规则由 `/actuator/health` 改为 **`/healthz` + `/actuator/health` + `/actuator/health/**`**。`antMatchers` 只做**精确匹配、不覆盖子路径**，不加 `/**` 会让 `/actuator/health/liveness` 落到认证之后返回 401，负载均衡探针直接不可用——这条容易漏。

#### `/healthz` 的实现约束（踩过的坑）

`HealthController` **不能**写成 `@GetMapping(value="/healthz", produces=TEXT_PLAIN_VALUE)` 返回 `String`。声明 `produces` 后，只要 `Accept` 不匹配（例如 `application/json`），Spring 就返回 **406**，负载均衡会把正常节点判为故障。单元测试 `plainTextProbeIgnoresTheAcceptHeader` 已捕获该缺陷。

正确做法是在响应上显式设置 Content-Type：`ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(PROBE_RESPONSE)`。响应头一旦带具体 Content-Type，Spring 跳过 Accept 协商，任意 `Accept` 都稳定返回 200 + `text/plain`（实测 `*/*`、`application/json`、`text/html`、`text/plain`、无 Accept 头五种情况一致）。

`HealthController.PROBE_RESPONSE` 是**与 F5 / SLB 配置的契约值**，当前为 `@the@health@is@good@`（20 字节，无换行）。测试中以**字面量**锁定而非引用该常量——改动必须让测试失败，以强制确认负载均衡侧同步修改，避免两边不一致导致正常节点被判故障。

#### 已知且无害的行为：探针响应下发 `XSRF-TOKEN`

`CsrfFilter` 会给**所有**未携带 token 的请求生成并下发 `XSRF-TOKEN` Cookie，`/healthz`、`/actuator/health`、`/api/v1/auth/config` 均如此，属既有平台行为。**不创建 `JSESSIONID`**，因此负载均衡秒级轮询不会造成会话增长。

曾尝试用 `ignoringAntMatchers("/healthz")` 消除该 Cookie，**无效**：Spring Security 5.7 的 `CsrfFilter` 在匹配器检查**之前**就已生成并保存 token，`ignoringAntMatchers` 只跳过校验、不跳过 Cookie 下发。该改动已回滚，勿重复尝试。

### 40.3 验证结果

`mvn -o package`：**158/158 通过**（新增 `ContextPathIntegrationTest` 中 2 个 `/healthz` 测试）。

实测（文根 `/icbc/hmp/agentobs`，探针无需 Cookie）：

| 场景 | `/healthz` | liveness | readiness |
|---|---|---|---|
| 两个数据源正常 | 200 `@the@health@is@good@` | 200 `UP` | 200 `UP` |
| **停止 ClickHouse 容器** | **200 `@the@health@is@good@`** | **200 `UP`** | **503 `DOWN`** |

即数据库故障时负载均衡**不会**摘除节点，而监控系统能正确感知。这是本次改造要达成的核心行为。

`mock` profile 同样验证通过：没有 `db` 健康组件时应用正常启动，各端点均正常，Spring Boot 对分组中不存在的组件不报错。旧根路径 `/actuator/health` 在文根下返回 404（§38 已记录），探针必须使用完整路径。

### 40.4 申请 F5 / SLB 时提交的参数

```
协议       HTTP
端口       8080（可由 SERVER_PORT 覆盖）
路径       /icbc/hmp/agentobs/healthz
方法       GET
期望状态码  200
响应内容    @the@health@is@good@（可选，用于等值/包含匹配）
```

建议：间隔 5–10s、超时 3s、连续 2 次成功置 UP、连续 3 次失败摘除。

**探针路径包含应用文根**，网关必须原样转发 `/icbc/hmp/agentobs`（§38.3）。若探针经网关而非直连后端节点，网关需对 `/icbc/hmp/agentobs/healthz` 免认证，否则探针会收到统一认证的跳转或 401。

### 40.5 切到 `app.auth.mode=aam` 后必须复验探针（本地无法验证）

**这是本方案唯一的未验证环节，且只在切换认证模式后才暴露。** 当前内网启动脚本用的是 `--app.auth.mode=mock`，此模式下已验证通过；一旦切到 `aam`，请按本节复验。

已核查的事实（本地源码）：

| 项 | 结论 |
|---|---|
| 主源码无任何 `Filter` / `HandlerInterceptor` | ✅ 已核查，无 |
| `WebConfig` 只注册 CORS 映射 | ✅ 已核查 |
| AAM 适配器仅 3 个类，无 Filter | ✅ 已核查 |
| `IcbcAamConfiguration` 由 `@ConditionalOnProperty(app.auth.mode=aam)` 控制 | ✅ 已核查 |
| **Hermes `@EnableAam` 是否注册全局 servlet Filter** | ❌ **无法本地验证** |

风险机理：SSIC/SSO 类库**通常**会注册一个全局 `Filter`。它运行在 **Spring Security 之前的 servlet 容器过滤器链**上，因此 **`SecurityConfig` 里的 `permitAll` 拦不住它**。若 Hermes 如此，`/healthz` 会被重定向到统一认证页，负载均衡会把**全部节点**判为故障。

本地无法验证的两个原因（与 §39.4 同源）：

1. `com.icbc.hermes:hermes-aam` 只有内网私服有，本机 Maven 仅能访问 Central，`-Paam` 构建失败；
2. AAM 适配器位于 `src/main/aam/java`，只在 `-Paam` 下编译，默认构建的产物里**根本不含这些类**，也就无法在本地触发该路径。

**内网切换后的必做验证（一条命令）：**

```bash
curl -s -i http://<后端节点>:8080/icbc/hmp/agentobs/healthz
```

- `HTTP/1.1 200` + `@the@health@is@good@` → 通过，可提交/激活 F5、SLB 探针
- `302` / `401` / 跳转到统一认证 → Hermes 的 Filter 拦住了，需把 `/healthz` 加入其白名单后再提交

`AamIntegrationProperties` 中有 `enableSpecialUrl` 开关（当前 `"false"`），从命名看可能与"放行特殊 URL"有关，但**这只是推测**——届时请对照 Hermes 官方文档确认其确切语义，**不要凭猜测改动该值**，避免影响统一认证链路。
