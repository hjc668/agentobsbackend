# Tracing 查询切换至 AgentObs ClickHouse 表实施方案

> 适用项目：`/Users/mac/Desktop/icbc/Aiops/langfuse-web`  
> 目标后端：Spring Boot 2.7.18 + Java 8 + MyBatis 注解/`@SelectProvider` + ClickHouse JDBC 0.4.6  
> 方案日期：2026-09-10  
> 状态：**历史实施方案，不再是待执行任务书**。截至 2026-09-22，`TracingSqlProvider`、`ObservabilitySqlProvider`、`WidgetMetricSqlProvider` 已查询 AgentObs `_all` 表；本方案中“目标表尚未创建”“Sessions/Users 保留旧表”等阶段性描述已过时。当前状态和未完成项以 [`PROJECT_PROGRESS.md`](PROJECT_PROGRESS.md)、现行 SQL Provider、目标库实际 DDL 为准。本文保留用于理解设计取舍和小批量调试数据操作，执行其中任何 DDL/平移 SQL 前须重新核对目标环境。

## 1. 目标与边界

本次将 Tracing 查询从 Langfuse 旧表：

- `events_core`
- `events_full`
- `scores`

切换到 `backend/sql/clickhouse/agentobs-clickhouse-schema-final.sql` 定义的 AgentObs 表：

- `hmp_agentobs_traces[_all]`
- `hmp_agentobs_observations[_all]`
- `hmp_agentobs_scores[_all]`
- `hmp_agentobs_trace_locator[_all]`

必须达到以下结果：

1. 现有 Vue Tracing 页不改变响应 DTO 即可正常显示列表、趋势图、Facet 筛选和详情抽屉。
2. 筛选输入、左侧 Facet、排序、分页和趋势图使用同一套过滤语义。
3. Trace 详情能返回完整父子 Observation，Tree、Timeline、Graph、Preview、Log View 均可使用。
4. AAM 登录用户继续读取专用 ClickHouse 中的全量 Trace，不恢复 `project_id` 条件。
5. 本地可将少量完整 Trace 从旧表平移到新表，形成可重复、可核验、可清理的调试数据。

本期不新增以下功能：

- Scores 页签和 Comments 页签的 UI；已有接口可保持，但不作为本次验收阻断项。
- Prompt Management、Evaluation。
- `span_events`、`span_links`、`logs`、`metric_points` 的新 UI。
- 改造上游 SDK、Collector、Kafka 或 Sinker。
- Sessions、Users 的全面新表迁移。它们暂时保留旧查询，后续单独迁移。

## 2. 权威输入与冲突处理

实施前必须完整阅读：

1. `backend/sql/clickhouse/agentobs-clickhouse-schema-final.sql`：**当前字段契约，以此为准**。
2. `backend/sql/clickhouse/schema3-design.md`：采集、路由、存储和查询约束。
3. `backend/sql/clickhouse/sdk-guide.md`：字段产生方式和缺失值语义。
4. `backend/sql/clickhouse/CLICKHOUSE_SCHEMA_DIFF.md`：已有差异分析，只作辅助。

发现文档冲突时，以当前 DDL 为准。例如现有差异文档称目标 Observation 没有 `tool_definitions`，但当前 DDL 已包含 `tool_definitions String`；真正仍缺失的是旧表中的 `tool_calls` 和 `tool_call_names`。

## 3. 已确认的本地基线

本地旧 ClickHouse 容器为 `langfuse-clickhouse-1`，旧表实测：

| 表 | 行数 | 说明 |
|---|---:|---|
| `events_core` | 455,249 | 当前列表、Facet、Pulse 主数据源 |
| `events_full` | 455,249 | 当前详情数据源 |
| `scores` | 0 | 当前没有可迁移 Score |

数据包含：

- `SPAN` 451,536 行；
- `GENERATION` 3,713 行；
- 主要 `service_name` 为 `codex-app-server`；
- 旧数据中的 `is_app_root` 基本没有被标记，调试迁移必须把 `parent_span_id = ''` 的最早根节点视为产品根，否则新 Trace 表会为空。

目标 `hmp_agentobs_*` 表当前尚未在本地创建。

## 4. 为什么不能只替换表名

旧表是“一张扁平事件表重建 Trace”，新表是“Trace 摘要 + Observation 事实”拆表模型。

| 语义 | 旧表 | 新表 | 改造要求 |
|---|---|---|---|
| Trace 摘要 | 从 `events_core` 按 `trace_id` 聚合 | `hmp_agentobs_traces` 一行一条 Trace | 列表/详情直接读 Trace 表 |
| Observation | `events_core/events_full` | `hmp_agentobs_observations` | 直接读离散列 |
| 最新版本 | `event_ts` | `ingestion_version` | 去重语义改写 |
| 软删除 | `is_deleted` | 无 | 删除旧条件；确认上游不写删除墓碑 |
| 根节点 | `is_app_root` | `is_product_root` | 同时兼容空父 ID |
| Token | `usage_details Map` | `usage_*_tokens` 离散列 | SQL 与详情映射改写 |
| 成本 | `cost_details`/派生列 | `cost Nullable(Float64)` | `NULL` 表示未知，不得伪装为 0 |
| 元数据 | 两个 Array 组成 Map | `metadata` JSON String | 使用 JSON 函数及 Java 解析 |
| Trace Name/Tags | 每个旧事件携带 | 仅 Trace 表携带 | Observation 列表分页后二次批量补齐 |
| TTFT | `completion_start_time` 绝对时间 | `time_to_first_chunk_ms` 时长 | 需要反算兼容 DTO，或调整 DTO |
| Prompt Version | `Nullable(UInt16)` | `String` | 兼容现有数字 DTO时使用 `toInt32OrNull` |
| 运行中 Trace | `end_time Nullable` | `end_time` 非空 | 当前目标表无法精确表达 RUNNING |
| 项目隔离 | `project_id` | 无 | 保持 AAM 后全库读取 |

## 5. 总体技术方案

### 5.1 选择直接查询拆分表，不建长期兼容扁平视图

不要把两个新事实表长期 `UNION ALL` 成假的 `events_core`。该方式会引入大范围 JOIN、伪造 `event_ts/is_deleted`，并掩盖成本、TTFT、Trace 标签的真实语义。

目标查询路径：

```text
Tracing 列表 / Facet / Pulse
  -> hmp_agentobs_observations_all（有界时间查询）
  -> 当前页 trace_id 批量查询 hmp_agentobs_traces_all
  -> Java 服务层合并 traceName / tags / trace 级字段

点击 Observation
  -> hmp_agentobs_trace_locator_all 定位 service_name + 精确时间窗
  -> hmp_agentobs_traces_all 查询 Trace 摘要
  -> hmp_agentobs_observations_all 查询完整 Span 集合
  -> Java 按 parent_span_id 返回现有 TraceView DTO
```

### 5.2 后端始终查询 `_all`

集群环境的 `_all` 是 DDL 中的 Distributed 表。本地单节点另建同名普通 View：

```sql
CREATE VIEW IF NOT EXISTS default.hmp_agentobs_traces_all AS
SELECT * FROM default.hmp_agentobs_traces;

CREATE VIEW IF NOT EXISTS default.hmp_agentobs_observations_all AS
SELECT * FROM default.hmp_agentobs_observations;

CREATE VIEW IF NOT EXISTS default.hmp_agentobs_scores_all AS
SELECT * FROM default.hmp_agentobs_scores;

CREATE VIEW IF NOT EXISTS default.hmp_agentobs_trace_locator_all AS
SELECT * FROM default.hmp_agentobs_trace_locator;
```

这样 Java 不需要接受可注入的动态表名，也不需要在启动脚本中保存环境变量。禁止把浏览器参数或未校验配置通过 MyBatis `${table}` 拼到 SQL。

### 5.3 保持 HTTP 与前端 DTO 契约

以下接口路径和 JSON 字段保持不变：

- `GET /api/v1/observability/observations`
- `GET /api/v1/observability/observations/facets`
- `GET /api/v1/observability/observations/pulse`
- `GET /api/v1/observability/traces`
- `GET /api/v1/observability/traces/{traceId}`
- `GET /api/v1/observability/traces/{traceId}/observations`
- `GET /api/v1/observability/traces/{traceId}/view`

允许增加可选 `serviceName` 参数和 `serviceName:` DSL 字段，但不能要求旧前端必须传入。未指定服务时必须返回当前时间窗内所有服务的数据。

## 6. 代码拆分与文件级改造

为避免 Tracing 切表时连带破坏 Sessions/Users，先把 Tracing 查询从大而全的 Mapper 中拆出来。

### 6.1 新增文件

| 文件 | 责任 |
|---|---|
| `backend/src/main/java/com/icbc/aiops/langfuse/mapper/TracingMapper.java` | 仅 Tracing 相关 MyBatis 方法 |
| `backend/src/main/java/com/icbc/aiops/langfuse/mapper/TracingSqlProvider.java` | 只生成 AgentObs 新表 SQL |
| `backend/src/main/java/com/icbc/aiops/langfuse/mapper/TracingRows.java` | Observation、Trace、Facet、Pulse、TraceLookup 行模型 |
| `backend/src/test/java/com/icbc/aiops/langfuse/mapper/TracingSqlProviderTest.java` | SQL 字段、绑定和旧表名隔离测试 |
| `backend/sql/clickhouse/agentobs-clickhouse-schema-local.sql` | 从集群 DDL手工维护的本地单节点 DDL |
| `backend/sql/clickhouse/migrate-tracing-debug-sample.sql` | 本文第 10 节的数据平移脚本 |

### 6.2 修改文件

| 文件 | 修改 |
|---|---|
| `MybatisObservabilityQueryService.java` | 注入 `TracingMapper`；Tracing 方法走新 Mapper；Sessions/Users 暂走旧 Mapper |
| `ObservabilityMapper.java` | 移除已迁走的 Tracing 方法，只保留 Sessions/Users 等旧查询 |
| `ObservabilitySqlProvider.java` | 移除 Tracing SQL，防止以后误查旧表 |
| `ObservabilityRows.java` | 仅保留未迁移功能行模型；或暂时兼容后再清理 |
| `ObservationQuery.java` | 可选增加 `serviceName`，所有时间条件继续使用 `Instant` |
| `ObservationFacet.java` | 增加 `SERVICE_NAME`（建议） |
| `ObservabilityQueryController.java` | 只添加可选 `serviceName`，不改已有参数和返回类型 |
| `application-mybatis.yml` | 不需要放表名；连接配置保持在 resources |

### 6.3 分支切换原则

迁移过程中禁止同一个方法在异常时静默回退旧表。可在 resources 中增加布尔开关 `app.observability.agentobs-enabled` 进行整组切换，但：

- 默认值在本地联调阶段设为 `true`；
- `true` 时任何新表查询失败都应明确报错；
- 不能在一次请求内混查新旧表，否则数量、趋势和详情会互相矛盾；
- 验收后删除开关和旧 Tracing SQL。

## 7. 查询设计

### 7.1 Observation 列表

列表先直接查询 Observation 小字段，不读取大文本：

```sql
SELECT
    toString(span_id) AS id,
    toString(trace_id) AS traceId,
    '' AS traceName,
    nullIf(parent_span_id, '') AS parentObservationId,
    name,
    type,
    start_time AS startTime,
    end_time AS endTime,
    level,
    nullIf(coalesce(nullIf(model, ''), request_model), '') AS model,
    toInt64(round(duration_ms)) AS latencyMs,
    toInt64(usage_input_tokens) AS inputTokens,
    toInt64(usage_output_tokens) AS outputTokens,
    ifNull(cost, 0.0) AS totalCost,
    CAST(NULL AS Nullable(String)) AS inputJson,
    CAST(NULL AS Nullable(String)) AS outputJson,
    CAST(NULL AS Nullable(DateTime64(6, 'UTC'))) AS completionStartTime,
    if(time_to_first_chunk_ms IS NULL, CAST(NULL AS Nullable(Int64)),
       CAST(round(time_to_first_chunk_ms) AS Nullable(Int64))) AS timeToFirstTokenMs,
    nullIf(status_message, '') AS statusMessage,
    CAST(NULL AS Nullable(String)) AS modelId,
    CAST(NULL AS Nullable(String)) AS modelParametersJson,
    '{}' AS usageDetailsJson,
    '{}' AS costDetailsJson,
    nullIf(prompt_name, '') AS promptName,
    toInt32OrNull(prompt_version) AS promptVersion,
    '{}' AS metadataJson
FROM default.hmp_agentobs_observations_all
WHERE start_time >= #{query.fromTimestamp}
  AND start_time < #{query.toTimestamp}
ORDER BY start_time DESC, span_id ASC
LIMIT #{query.size} OFFSET #{offset}
```

注意：上面是列契约示例，不应原样复制后忽略以下要求：

- `timeToFirstTokenMs` 应对 Float64 正确取整，建议直接 `toInt64(round(time_to_first_chunk_ms))` 并保留 NULL。
- 列表可不返回 Input/Output/Metadata 大字段；详情必须返回。
- 列表暂不构造 Usage/Cost 明细 JSON；详情 Row 读取离散列后由 Java 组装，避免依赖 ClickHouse 21.8 的 Map 构造兼容性。
- `cost IS NULL` 在 API 中仍会暂时映射为 0 以兼容前端，但 `costDetails` 必须保留“未知”和“已知 0”的区别。
- 所有用户值继续使用 `#{}` 绑定；排序列只能由 enum 白名单选择。

拿到当前页后，用 `trace_id IN (...)` 批量查询 Trace Lookup：

```sql
SELECT toString(trace_id) AS traceId, trace_name AS traceName, toJSONString(tags) AS tagsJson
FROM default.hmp_agentobs_traces_all FINAL
WHERE trace_id IN (...MyBatis foreach bound values...)
```

在 Java 服务层按 `traceId` 补齐 `Observation.traceName`。禁止列表主查询无界 JOIN Trace 表。

### 7.2 Observation 详情和 TraceView

详情按以下顺序执行：

1. 从 locator 查询该 Trace 涉及的服务与时间范围。
2. 用 locator 结果限制 Observation 查询。
3. 单独读取 Trace 摘要。
4. 服务层保持 `startTime ASC, spanId ASC`，前端继续按 `parentObservationId` 组树。

Locator SQL：

```sql
SELECT
    service_name,
    minMerge(min_start_state) AS minStartTime,
    maxMerge(max_end_state) AS maxEndTime
FROM default.hmp_agentobs_trace_locator_all
WHERE trace_id = #{traceId}
GROUP BY service_name
```

Observation 详情列映射：

- `inputJson <- nullIf(input, '')`
- `outputJson <- nullIf(output, '')`
- `modelParametersJson <- nullIf(model_parameters, '')`
- `metadataJson <- if(empty(metadata), '{}', metadata)`
- `inputTokens/outputTokens <- usage_input_tokens/usage_output_tokens`
- `usageDetailsJson`：由 Java 组装所有 `usage_*_tokens`，并参考 `usage_present` 判断缺失值。
- `costDetailsJson <- cost IS NULL ? '{}' : {"total": cost}`
- `completionStartTime <- Java 使用 start_time + time_to_first_chunk_ms 反算`
- `timeToFirstTokenMs <- time_to_first_chunk_ms`
- `modelId <- null`（目标表无此列）
- `promptVersion <- toInt32OrNull(prompt_version)`，非数字版本暂返回 null，避免破坏现有 DTO。

如果 locator 没有结果，允许执行一次仅带 `trace_id` 的受控兜底点查，便于处理 MV 尚未补齐的数据；该兜底只能用于单 Trace 详情，不能用于列表。

### 7.3 Trace 详情

Trace 基础字段直接来自 `hmp_agentobs_traces_all`：

- `name <- trace_name`
- `timestamp <- start_time`
- `latencyMs <- duration_ms`
- `inputJson/outputJson <- trace_input/trace_output`
- `metadataJson <- metadata`
- `version <- version`
- `release <- null`（目标表无 release）
- `status <- status_code = 2 ? ERROR : SUCCESS`

`totalTokens`、`totalCost`、`observationCount` 从该 Trace 的 Observation 详情结果在 Java 中计算，避免详情再发一次聚合查询。Trace 表 `end_time` 非 Nullable，当前无法返回 RUNNING；如果产品必须展示 RUNNING，需先修改 DDL 增加 `is_ended` 或把 `end_time` 改为 Nullable，不能靠 `duration_ms = 0` 猜测。

### 7.4 Trace 列表

Trace 基础列表读 Trace 表；Token、成本、Observation 数量需在同一时间窗口先对 Observation 做有界聚合，再按 `trace_id` 连接当前页 Trace。实施顺序：

1. 先按 Trace 表筛选、排序并拿到一页 Trace ID。
2. 用这页 ID 在 Observation 表做 `GROUP BY trace_id`。
3. Java 合并指标。

只有按 TOKENS/COST 排序时，才需要先对整个有界窗口聚合再分页。若生产压测不达标，再增加 `hmp_agentobs_trace_metrics` 服务表；首期不要使用会在重放时重复累加的普通 Summing MV。

### 7.5 Pulse 趋势图

```sql
SELECT
    toStartOfDay(start_time) AS timestamp,
    toInt64(count()) AS count,
    sumIf(cost, cost IS NOT NULL) AS totalCost,
    toInt64(round(avg(duration_ms))) AS averageLatencyMs
FROM default.hmp_agentobs_observations_all FINAL
WHERE start_time >= #{query.fromTimestamp}
  AND start_time < #{query.toTimestamp}
  -- 追加与列表完全相同的 DSL 条件
GROUP BY timestamp
ORDER BY timestamp ASC
```

HOUR/DAY/WEEK 只允许 enum 映射到 `toStartOfHour/toStartOfDay/toStartOfWeek`。Pulse 和列表必须调用同一个 `buildObservationPredicate`，禁止复制后逐渐产生不同筛选语义。

### 7.6 Facet

直接来自 Observation 的 Facet：

- `ENVIRONMENT -> environment`
- `SERVICE_NAME -> service_name`
- `TYPE -> type`
- `ROOT -> if(parent_span_id = '' OR is_product_root, 'true', 'false')`
- `LEVEL -> level`
- `NAME -> name`
- `MODEL -> coalesce(nullIf(model, ''), request_model)`
- `PROMPT_NAME -> prompt_name`
- `USER_ID -> user_id`
- `SESSION_ID -> session_id`

需要 Trace 表的 Facet：

- `TRACE_NAME -> hmp_agentobs_traces.trace_name`
- `TAG -> arrayJoin(hmp_agentobs_traces.tags)`

这两类 Facet 必须在同一时间窗先限制 Observation/Trace ID，再做有界关联。Facet 自己对应的当前筛选是否排除，应延续当前前端行为，不要在迁移时改变交互语义。

### 7.7 筛选 DSL 映射

| DSL | 新表达式/处理 |
|---|---|
| `environment` | `environment` |
| `serviceName` / `service` | `service_name` |
| `type` | `type` |
| `level` | `level` |
| `name` | `name` |
| `traceName` | Trace ID 子查询或有界 Trace 关联 |
| `model` | `coalesce(nullIf(model,''), request_model)` |
| `promptName` | `prompt_name` |
| `traceId` | `trace_id` 精确或文本白名单谓词 |
| `sessionId` / `userId` | 同名列 |
| `statusMessage` | `status_message` |
| `version` | `version` |
| `input` / `output` | 同名大字段，仅该筛选启用时扫描 |
| `metadata.<key>` | `JSONExtractString(metadata, #{key})`；值仍绑定 |
| `tags` | `trace_id IN (SELECT trace_id FROM traces WHERE has(tags,...))` |
| `latency` | `duration_ms`，界面单位秒需转换为毫秒 |
| `ttft` | `time_to_first_chunk_ms` |
| `tokens` | `usage_total_tokens`，缺失 total 时回退 input + output |
| `inputTokens/outputTokens` | 对应离散列 |
| `cost` | `cost`，NULL 不匹配数值条件 |
| `root` | `parent_span_id = '' OR is_product_root` |
| `toolDefinitions` | 对 JSON String 取对象键数；先验证 ClickHouse 21.8 可用函数 |
| `toolCalls` | 目标表无来源，相关 Quick Filter 必须禁用并说明原因 |
| `modelId` / `release` | 目标表无来源，从建议项和 Facet 中移除 |
| `scores.<name>` | 可改查 `hmp_agentobs_scores_all`；本地源表为空，本期非阻断项 |

自由文本仍搜索 `name/span_id/trace_id/model/input/output/metadata`。所有文本值必须绑定，不能直接拼入 SQL。

精确 Trace ID 在进入 Mapper 前执行 `trim()` 并校验 `^[0-9a-fA-F]{32}$`，SQL 使用绑定值转 `FixedString(32)`；不得保留前导空格，也不得把非法长度交给 ClickHouse 隐式补零。

## 8. 时间、去重与性能约束

1. 列表、Facet、Pulse 必须有 `fromTimestamp/toTimestamp`。后端缺省窗口设为最近 24 小时，不能无界扫全表。
2. 页面当前支持 24h/7d/30d/90d，但目标 DDL TTL 为 7 天。这是产品冲突：
   - 若保留 30d/90d，投产前必须将事实表和 locator TTL 同步扩到至少 90 天；
   - 若坚持 7 天 TTL，前端必须移除 30d/90d，不能展示一个永远不可能完整的范围。

   **2026-09-11 更新**：本地 ClickHouse 的 8 张表 TTL 已由 7 天改为 **30 天**（`ALTER TABLE … MODIFY TTL`），
   并同步到 `agentobs-clickhouse-schema-local.sql`（该文件唯一一处刻意偏离 final DDL 的地方，已在文件头规则 6 说明）。
   现在本地 30d 档可返回完整数据，90d 档返回现有数据。
   **但这只解决本地联调**——`agentobs-clickhouse-schema-final.sql`（集群契约）仍是 7 天，**
   投产前仍须业务在「扩到 90 天 / 砍前端档位 / 冷热分表」中定夺，30d 与 90d 在集群上仍不能判定为可用**。
3. `ReplacingMergeTree` 合并前可能重复。精确计数使用有界 `FINAL` 或按 `(trace_id, span_id)` 对 `ingestion_version` 取最新版本。
4. Trace 详情可使用 `FINAL`，因为已由 locator 限定服务、日期和 Trace ID。
5. 列表禁止 `SELECT *`，详情才读取 input/output/metadata 等大字段。
6. 新表排序键首列为 `service_name`，而当前全库页面不强制服务筛选。必须增加 Service Facet，并对“无 service 条件”的真实数据做压测；若 p95 不达标，增加按 `(start_time, trace_id, span_id)` 排序的 Web 查询投影/服务表。
7. 不要直接把至少一次重放流接到 additive Summing MV，否则重复数据会永久放大 Token、成本和计数。

## 9. 本地新表部署

集群 DDL不能直接在本地单节点执行。后续模型先从 final DDL 手工生成 `agentobs-clickhouse-schema-local.sql`：

1. 移除全部 `ON CLUSTER {cluster}`。
2. `ReplicatedReplacingMergeTree(path, replica, ingestion_version)` 改为 `ReplacingMergeTree(ingestion_version)`。
3. `ReplicatedAggregatingMergeTree(path, replica)` 改为 `AggregatingMergeTree()`。
4. 保留字段、分区键、排序键、索引、TTL 和 locator MV，不得随意删列。
5. Distributed DDL改为第 5.2 节的本地同名 View。
6. 在执行前后分别输出 `SHOW CREATE TABLE`，与 final DDL逐列核对。

本地执行示例：

```bash
docker exec -i langfuse-clickhouse-1 clickhouse-client \
  --user clickhouse --password clickhouse \
  < backend/sql/clickhouse/agentobs-clickhouse-schema-local.sql
```

建表验收：

```sql
SELECT name, engine, total_rows
FROM system.tables
WHERE database = 'default' AND name LIKE 'hmp_agentobs%'
ORDER BY name;
```

## 10. 小批量调试数据平移

### 10.1 安全原则

- 只在本地/测试环境执行，禁止未经确认在生产执行 `ALTER ... DELETE`。
- 按完整 `trace_id` 平移，不能随机复制若干 Observation。
- 只选择 ID 长度合法、父子结构完整、已经结束的 Trace。
- 新表有 7 天 TTL，候选 Trace 必须位于 TTL 窗口内；否则先调整测试表 TTL，不能篡改业务时间来“保活”。
- 先创建 locator MV，再插入 Observation，使索引自动回填。
- 每批使用唯一 `batch_id` 记录 Trace ID，以便验收和清理。

### 10.2 创建迁移批次表

```sql
CREATE TABLE IF NOT EXISTS default.hmp_agentobs_debug_migration_batch
(
    batch_id String,
    trace_id String,
    selected_at DateTime DEFAULT now()
)
ENGINE = MergeTree
ORDER BY (batch_id, trace_id);
```

本次已核验的固定样本：

| Trace ID | Observation 数 | 覆盖 |
|---|---:|---|
| `a874a391240d48d9cfcfe66ea874b9cb` | 2 | 简单父子 SPAN |
| `c4b4ad2262f4a4893f26d33da248e905` | 8 | SPAN + GENERATION + 模型名 |
| `ba8298c87681f90ab4ae2f580068e3bb` | 11 | 较深链路 |

固定 ID 只适用于当前本地快照。若已超过 TTL，用下面的动态选择 SQL重新选 3 条，不要修改时间戳：

```sql
INSERT INTO default.hmp_agentobs_debug_migration_batch (batch_id, trace_id)
SELECT 'tracing-refactor-20260910', trace_id
FROM default.events_core FINAL
WHERE is_deleted = 0
  AND length(trace_id) = 32
  AND length(span_id) = 16
  AND start_time >= now() - INTERVAL 6 DAY
GROUP BY trace_id
HAVING countIf(parent_span_id = '') = 1
   AND countIf(end_time IS NULL) = 0
   AND count() BETWEEN 2 AND 100
ORDER BY countIf(type = 'GENERATION') DESC, count() DESC, min(start_time) DESC
LIMIT 3;
```

使用固定样本时：

```sql
INSERT INTO default.hmp_agentobs_debug_migration_batch (batch_id, trace_id) VALUES
('tracing-refactor-20260910', 'a874a391240d48d9cfcfe66ea874b9cb'),
('tracing-refactor-20260910', 'c4b4ad2262f4a4893f26d33da248e905'),
('tracing-refactor-20260910', 'ba8298c87681f90ab4ae2f580068e3bb');
```

执行前必须确认目标不存在同 ID 数据：

```sql
SELECT trace_id, count()
FROM default.hmp_agentobs_observations_all
WHERE trace_id IN
(
    SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch
    WHERE batch_id = 'tracing-refactor-20260910'
)
GROUP BY trace_id;
```

查询非空时先停止，不能覆盖来源不明的数据。

### 10.3 平移 Trace 摘要

旧快照没有可靠 `is_app_root`，因此按唯一空父节点合成 Trace 摘要：

```sql
INSERT INTO default.hmp_agentobs_traces
(
    schema_version, root_span_id, service_name, trace_id, trace_name,
    start_time, end_time, duration_ms, user_id, user_hash, session_id,
    conversation_id, request_id, tags, environment, version, status_code,
    status_message, level, trace_input, trace_output, metadata,
    extra_attributes, ingestion_version
)
SELECT
    'legacy-langfuse-migration-v1' AS schema_version,
    CAST(argMinIf(span_id, start_time, parent_span_id = '') AS FixedString(16)) AS root_span_id,
    coalesce(nullIf(argMinIf(service_name, start_time, parent_span_id = ''), ''), 'legacy-langfuse') AS service_name,
    CAST(trace_id AS FixedString(32)) AS trace_id,
    coalesce(
        nullIf(argMaxIf(trace_name, event_ts, trace_name != ''), ''),
        argMinIf(name, start_time, parent_span_id = ''),
        argMin(name, start_time)) AS trace_name,
    min(start_time) AS start_time,
    coalesce(max(end_time), max(start_time)) AS end_time,
    toFloat64(greatest(toInt64(0), dateDiff('millisecond', min(start_time), coalesce(max(end_time), max(start_time))))) AS duration_ms,
    argMax(user_id, event_ts) AS user_id,
    '' AS user_hash,
    argMax(session_id, event_ts) AS session_id,
    '' AS conversation_id,
    '' AS request_id,
    argMax(tags, event_ts) AS tags,
    argMax(environment, event_ts) AS environment,
    argMax(version, event_ts) AS version,
    toInt8(if(countIf(level = 'ERROR') > 0, 2, 1)) AS status_code,
    argMaxIf(status_message, event_ts, status_message != '') AS status_message,
    if(countIf(level = 'ERROR') > 0, 'ERROR', 'DEFAULT') AS level,
    coalesce(nullIf(argMaxIf(input, event_ts, is_app_root), ''), argMinIf(input, start_time, parent_span_id = '')) AS trace_input,
    coalesce(nullIf(argMaxIf(output, event_ts, is_app_root), ''), argMinIf(output, start_time, parent_span_id = '')) AS trace_output,
    toJSONString(mapFromArrays(
        argMinIf(metadata_names, start_time, parent_span_id = ''),
        argMinIf(metadata_values, start_time, parent_span_id = ''))) AS metadata,
    '{}' AS extra_attributes,
    toUInt64(toUnixTimestamp64Milli(max(event_ts))) AS ingestion_version
FROM default.events_core FINAL
WHERE is_deleted = 0
  AND trace_id IN
  (
      SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch
      WHERE batch_id = 'tracing-refactor-20260910'
  )
GROUP BY trace_id;
```

### 10.4 平移 Observations

```sql
INSERT INTO default.hmp_agentobs_observations
(
    schema_version, is_product_root, usage_present, tool_definitions,
    scope_name, scope_version, resource_schema_url, scope_schema_url,
    trace_state, trace_flags, dropped_attributes_count, dropped_events_count,
    dropped_links_count, error_type, service_name, trace_id, span_id,
    parent_span_id, span_kind, type, name, operation, start_time, end_time,
    duration_ms, level, status_code, status_message, session_id, user_id,
    user_hash, conversation_id, request_id, environment, version,
    request_model, model, provider, model_parameters,
    usage_input_tokens, usage_output_tokens, usage_total_tokens,
    usage_cache_read_tokens, usage_cache_write_tokens, usage_reasoning_tokens,
    usage_audio_output_tokens, cost, finish_reasons, response_id,
    time_to_first_chunk_ms, tool_name, tool_call_id, tool_type,
    agent_name, agent_id, agent_version, prompt_name, prompt_version,
    system_instructions, input, output, metadata, resource_attributes,
    scope_attributes, extra_attributes, ingestion_version
)
SELECT
    'legacy-langfuse-migration-v1' AS schema_version,
    (is_app_root OR parent_span_id = '') AS is_product_root,
    arrayFilter(k -> mapContains(usage_details, k), ['input', 'output', 'total']) AS usage_present,
    toJSONString(tool_definitions) AS tool_definitions,
    scope_name,
    scope_version,
    '' AS resource_schema_url,
    '' AS scope_schema_url,
    '' AS trace_state,
    toUInt32(0) AS trace_flags,
    toUInt32(0) AS dropped_attributes_count,
    toUInt32(0) AS dropped_events_count,
    toUInt32(0) AS dropped_links_count,
    if(level = 'ERROR', status_message, '') AS error_type,
    coalesce(nullIf(service_name, ''), 'legacy-langfuse') AS service_name,
    CAST(trace_id AS FixedString(32)) AS trace_id,
    CAST(span_id AS FixedString(16)) AS span_id,
    parent_span_id,
    'INTERNAL' AS span_kind,
    if(type IN ('SPAN','GENERATION','EVENT','AGENT','TOOL','CHAIN','RETRIEVER','EVALUATOR','EMBEDDING','GUARDRAIL'), type, 'SPAN') AS type,
    name,
    name AS operation,
    start_time,
    coalesce(end_time, start_time) AS end_time,
    toFloat64(greatest(toInt64(0), dateDiff('millisecond', start_time, coalesce(end_time, start_time)))) AS duration_ms,
    if(level IN ('DEBUG','DEFAULT','WARNING','ERROR'), level, 'DEFAULT') AS level,
    toInt8(if(level = 'ERROR', 2, 1)) AS status_code,
    status_message,
    session_id,
    user_id,
    '' AS user_hash,
    '' AS conversation_id,
    '' AS request_id,
    environment,
    version,
    provided_model_name AS request_model,
    provided_model_name AS model,
    '' AS provider,
    model_parameters,
    toUInt64(usage_details['input']) AS usage_input_tokens,
    toUInt64(usage_details['output']) AS usage_output_tokens,
    toUInt64(if(mapContains(usage_details, 'total'), usage_details['total'], usage_details['input'] + usage_details['output'])) AS usage_total_tokens,
    toUInt64(0) AS usage_cache_read_tokens,
    toUInt64(0) AS usage_cache_write_tokens,
    toUInt64(0) AS usage_reasoning_tokens,
    toUInt64(0) AS usage_audio_output_tokens,
    if(
        mapContains(cost_details, 'total'),
        CAST(cost_details['total'] AS Nullable(Float64)),
        if(calculated_total_cost > 0, CAST(calculated_total_cost AS Nullable(Float64)), CAST(NULL AS Nullable(Float64)))) AS cost,
    CAST([] AS Array(String)) AS finish_reasons,
    '' AS response_id,
    if(completion_start_time IS NULL, CAST(NULL AS Nullable(Float64)),
       CAST(dateDiff('millisecond', start_time, completion_start_time) AS Nullable(Float64))) AS time_to_first_chunk_ms,
    '' AS tool_name,
    '' AS tool_call_id,
    '' AS tool_type,
    '' AS agent_name,
    '' AS agent_id,
    '' AS agent_version,
    prompt_name,
    if(prompt_version IS NULL, '', toString(prompt_version)) AS prompt_version,
    '' AS system_instructions,
    input,
    output,
    toJSONString(mapFromArrays(metadata_names, metadata_values)) AS metadata,
    '{}' AS resource_attributes,
    '{}' AS scope_attributes,
    '{}' AS extra_attributes,
    toUInt64(toUnixTimestamp64Milli(event_ts)) AS ingestion_version
FROM default.events_full FINAL
WHERE is_deleted = 0
  AND length(trace_id) = 32
  AND length(span_id) = 16
  AND trace_id IN
  (
      SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch
      WHERE batch_id = 'tracing-refactor-20260910'
  );
```

说明：本次候选已排除未结束数据，所以 `coalesce(end_time,start_time)` 只是类型保护。生产摄取不能用这个办法表达运行中 Trace。

### 10.5 平移验收

```sql
-- 逐 Trace 对比 Observation 行数
SELECT
    b.trace_id,
    old_rows,
    new_rows
FROM
(
    SELECT trace_id, count() AS old_rows
    FROM default.events_full FINAL
    WHERE is_deleted = 0
      AND trace_id IN
      (
          SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch
          WHERE batch_id = 'tracing-refactor-20260910'
      )
    GROUP BY trace_id
) AS b
LEFT JOIN
(
    SELECT toString(trace_id) AS trace_id, count() AS new_rows
    FROM default.hmp_agentobs_observations FINAL
    WHERE trace_id IN
    (
        SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch
        WHERE batch_id = 'tracing-refactor-20260910'
    )
    GROUP BY trace_id
) AS n USING trace_id
ORDER BY b.trace_id;

-- 每条 Trace 必须恰好有一条摘要
SELECT toString(trace_id), count()
FROM default.hmp_agentobs_traces FINAL
WHERE trace_id IN
(
    SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch
    WHERE batch_id = 'tracing-refactor-20260910'
)
GROUP BY trace_id;

-- 父节点不可悬空；根节点除外
SELECT toString(o.trace_id), toString(o.span_id), o.parent_span_id
FROM default.hmp_agentobs_observations FINAL AS o
LEFT JOIN default.hmp_agentobs_observations FINAL AS p
  ON o.trace_id = p.trace_id AND o.parent_span_id = toString(p.span_id)
WHERE o.parent_span_id != ''
  AND p.span_id = CAST('', 'FixedString(16)')
  AND o.trace_id IN
  (
      SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch
      WHERE batch_id = 'tracing-refactor-20260910'
  );
```

最后用 API 验收固定 Trace：

```bash
curl -s 'http://127.0.0.1:8080/api/v1/observability/observations?traceId=c4b4ad2262f4a4893f26d33da248e905&fromTimestamp=2026-09-01T00:00:00Z&toTimestamp=2026-09-11T00:00:00Z&page=0&size=50'

curl -s 'http://127.0.0.1:8080/api/v1/observability/traces/c4b4ad2262f4a4893f26d33da248e905/view'
```

预期：第一个接口返回 8 个 Observation；第二个接口的 Observation 数也是 8，包含 1 个 GENERATION，父子引用无悬空。

### 10.6 本地回滚

仅当第 10.2 节已确认这些 ID 是本批独占数据时执行：

```sql
ALTER TABLE default.hmp_agentobs_observations DELETE WHERE toString(trace_id) IN
(
    'a874a391240d48d9cfcfe66ea874b9cb',
    'c4b4ad2262f4a4893f26d33da248e905',
    'ba8298c87681f90ab4ae2f580068e3bb'
);

ALTER TABLE default.hmp_agentobs_traces DELETE WHERE toString(trace_id) IN
(
    'a874a391240d48d9cfcfe66ea874b9cb',
    'c4b4ad2262f4a4893f26d33da248e905',
    'ba8298c87681f90ab4ae2f580068e3bb'
);

ALTER TABLE default.hmp_agentobs_debug_migration_batch
DELETE WHERE batch_id = 'tracing-refactor-20260910';
```

等待 `system.mutations.is_done = 1` 后再重新插入。Locator 是派生索引；测试环境可按该批 Trace 删除对应状态，或清表后从全部 Observation 重建。

如果第 10.2 节使用的是动态候选，先把批次表查出的 ID 固化为字面量并人工复核，再替换上面的三个 ID。不要在 mutation 中依赖会变化的子查询结果。

## 11. 分阶段执行清单

### 阶段 A：DDL 与样本数据

- [x] 生成并审查本地单节点 DDL。
- [x] 创建本地事实表、locator、MV 和 `_all` View。
- [x] 执行 3 条完整 Trace 的小批量迁移。
- [x] 行数、根节点、父子关系、类型和 locator 全部验收。

完成条件：新表可独立返回调试数据，旧表未被修改。**已于 2026-09-10 达成。**

实施记录与对本文 SQL 的修正（详见 `PROJECT_PHASE_A` 记录与 `migrate-tracing-debug-sample.sql` 头部注释）：

1. 第 10.3 节 SQL 在 ClickHouse 上会因**别名文本替换**报 `Code 184 ILLEGAL_AGGREGATION`：
   `min(start_time) AS start_time` 会污染后续所有 `start_time` 读取（`coalesce(max(end_time), min(start_time))`、`argMinIf(metadata_names, start_time, …)` 等），
   派生的 `level` 别名同样会污染 `status_code` 里的 `countIf(level = 'ERROR')`。
   已改为聚合列别名化（`min_start_time` / `max_end_time` / `trace_level`）再于外层投影映射。
   第 10.4 节 `coalesce(end_time, start_time) AS end_time` 存在循环别名隐患，同样已改名。
2. 第 10.5 节 `FROM … FINAL AS o` 是**语法错误**，正确写法是 `FROM … AS o FINAL`（已验证）。
3. **整个迁移文件不能一次 `--multiquery` 执行**：同一会话内语句间内存累积，第 10.4 节会触发 `Code 241 MEMORY_LIMIT_EXCEEDED`（约 6.9 GiB，cgroup 推算上限）；
   分节独立执行则全部通过。已在脚本头部写明。
4. 实测结果：3 条 Trace（2 / 11 / 8 个 Observation）行数全部匹配，每条恰好一条摘要，无悬空父节点，locator MV 由 observations 自动回填，
   旧表 `events_core` / `events_full` 保持 455,249 行未被修改。

### 阶段 B：Tracing 查询解耦

- [x] 新增 `TracingMapper/TracingSqlProvider/TracingRows`。
- [x] 迁移 Observation 列表、count、Facet、Pulse。
- [x] 迁移 Trace/TraceView 详情。
- [x] 迁移 Trace 列表和排序。
- [x] `ObservabilityMapper` 暂留 Sessions/Users 查询。

完成条件：Tracing Provider 中不出现 `events_core/events_full/scores`。**已于 2026-09-10 达成。**

实施记录：

- 新增 `TracingSqlProvider`(711 行)、`TracingMapper`(81 行)、`TracingRows`(214 行)、`TracingSqlProviderTest`(16 个用例)。
- `ObservabilitySqlProvider` 由 668 行精简到 183 行，仅保留 Dashboard summary、Trace scores、Sessions、Users；`ObservabilityMapper` 相应移除 8 个 Tracing 方法。
- `ObservationFacet` 增加 `SERVICE_NAME`；`ObservationQuery` 增加可选 `serviceName`；三个 observation 端点增加可选 `serviceName` 查询参数（均不破坏旧前端契约）。
- Trace 列表用**时间窗受限**的 LEFT JOIN 聚合补齐 tokens/cost/observationCount（§7.4 的"有界聚合再连接"），Observation 列表用**当前页 trace_id 批量二次查询**补齐 traceName（§7.1 的两阶段）。二者都不是无界 JOIN。
- 实现中发现的偏离，需在后续阶段确认：
  1. §7.2 要求 `usageDetailsJson` 由 Java 组装；实现改为在 SQL 中用 `usage_present` 过滤键后生成 JSON，保证"缺失"与"已知 0"仍可区分，且避免在 Java 侧重复解析 6 个离散列。
  2. `scores.<name>` DSL 本期**不生效**（`hmp_agentobs_scores_all` 为空）。已把 `scores` 登记进 `UNSUPPORTED_FIELDS` 显式丢弃，而非静默产生错误结果；待 scores 有数据后恢复。
  3. 无来源字段（`toolCalls`/`modelId`/`release`/`tps`/`inputCost`/`outputCost`/`sdkName`/`sdkVersion`）同样显式丢弃，多 token 查询仍可正常执行。

### 阶段 C：筛选语义闭合

- [x] 所有列表、Facet、Pulse 复用同一个谓词构造器。
- [x] `traceId` 精确过滤通过。
- [x] Type/Level/Environment/Model/Name/Root/Session/User 通过。
- [x] Trace Name/Tags 通过有界 Trace 关联。
- [x] Metadata JSON 筛选通过。
- [x] 禁用无来源的 Tool Calls、Model ID、Release 条件。
- [x] 建议项和左侧 Facet 不再展示后端无法查询的字段。

完成条件：Tag/Chip、左侧 Facet、表格和 Pulse 返回完全一致的数据集合。**已于 2026-09-11 达成。**

实施记录与发现：

1. 前端 `SEARCH_FIELDS` 移除 `inputCost` / `outputCost` / `release`，移除 `collapsedFilters` 与 `labelFor` 中的 `modelId`，搜索提示中的 `scores.accuracy` 示例改为 `serviceName`。
2. 新增 **Service Facet**（§7.6）：左侧筛选面板增加「服务」分组，`loadFacets` 增加 `SERVICE_NAME` 请求，`ObservationQuery.serviceName` 与 `withFacet` 联动。
3. **发现并修复一个真实运行时缺陷**：Observable 列清单把模型表达式别名成 `model`，而基表也有同名列。
   ClickHouse 会把 SELECT 别名替换进 WHERE，导致同一列名出现两种定义，抛 `Code 352 Block structure mismatch`。
   该问题**只在 `model:...` 筛选时触发**，无筛选的冒烟测试无法发现（`count` 端点和类型/名称筛选都正常）。
   已把输出别名改为 `modelName`（`TracingRows.ObservationRow.modelName`），DSL 与 DTO 仍用 `model`。
   已补回归测试 `doesNotAliasAnOutputColumnAfterAReferencedBaseColumn` 锁死该模式。
4. 三处同类潜在风险已核查：`environment` / `name` 是无表达式直通选择（类型相同，安全）；`traceDetail` 的 `nullIf(version,'') AS version` 与基表同名，但其 WHERE 只含 `trace_id`，当前未触发，**后续若给 Trace 详情加 version 条件需一并改名**。

实测一致性（ClickHouse 25.12.11，3 条调试 Trace / 21 个 Observation）：

| 筛选 | 列表 total | Pulse 合计 | 结论 |
|---|---:|---:|---|
| 无筛选 | 21 | 21 | ✓ |
| `type:SPAN` | 20 | 20 | ✓ |
| `type:GENERATION` | 1 | 1 | ✓ |
| `type:SPAN serviceName:codex-app-server` | 20 | 20 | ✓ |
| `-type:SPAN` | 1 | 1 | ✓ |
| `model:codex-auto-review` | 1 | 1 | ✓ |

Facet 交叉核对：TYPE 合计 21、SERVICE_NAME 合计 21、列表 total 21，三者一致。
被禁用字段（`toolCalls`/`modelId`/`release`/`scores.*`）均返回 HTTP 200 且忽略该条件，不再产生错误结果。

### 阶段 D：详情与前端回归

- [ ] 点击 SPAN、GENERATION 均能打开详情。
- [ ] Tree、Timeline、Graph 的节点数量一致。
- [ ] Preview 的 Input/Output/Metadata 正确。
- [ ] Log View Formatted/JSON 正确。
- [ ] 无 Scores/Comments 页签不影响详情。
- [ ] 浅色主题、筛选 Tag 和删除交互无回归。

### 阶段 E：性能与清理

- [x] 真实目标数据量执行列表、count、Facet、Pulse、详情压测。
- [x] 记录冷/热 p50、p95、扫描行数和峰值内存。
- [x] p95 不达标时增加 Web 查询投影/服务表，不先堆大连接池。
- [x] 删除旧 Tracing Provider 和临时切换开关。
- [x] 更新 `PROJECT_PROGRESS.md` 和 `MIGRATION_MATRIX.md`。

**已于 2026-09-11 达成。** 实施记录：

**清理核查（全部通过）**

- `ObservabilitySqlProvider` 仅剩 `summary` / `metricTimeSeries` / `traceScores` / `sessions*` / `users*`——即 Dashboard 指标、Trace Score、Sessions、Users，均为方案 §1 明确不在本次迁移范围的功能；Tracing 方法已全部移除。
- `ObservabilityMapper` 中无任何 Tracing 方法残留。
- **从未引入过 `app.observability.agentobs-enabled` 开关**，因此没有需要删除的临时开关；也不存在同一次请求内混查新旧表的代码。
- 残留说明：`WidgetMetricSqlProvider` 仍查 `events_core`，属 Dashboard 组件指标，同样不在本次范围。

**压测方法**

本地新表仅有 21 行调试数据，直接压测无意义。因此另建独立压测表 `hmp_agentobs_observations_loadtest`（与生产表**完全相同的排序键与分区键**，但去掉 7 天 TTL），从 `events_core` 灌入 **455,249 行**并合成 **20 个服务**，压测后已 `DROP`。调试数据全程未受影响。

**关键测量修正**：第一版用 `docker exec` 整体计时，而 `docker exec` 的固定开销实测为 **221 ms**，把真实数字完全淹没（真实服务端耗时才 6–11 ms）。最终改用 `clickhouse-client --time` 取服务端耗时。**其余人复用本机压测时请勿重复此错误。**

**实测结果（455,249 行 / 20 服务 / 15,734 Trace；服务端耗时，各 6 次）**

| 查询 | p50 | p95 | 扫描行数 | 峰值内存 |
|---|---:|---:|---:|---:|
| 列表（无 service） | 48 ms | 104 ms | 488,017 | 8.9 MiB |
| 列表（`service_name='svc-3'`） | 32 ms | 43 ms | — | — |
| count（无 service） | 12 ms | 32 ms | 455,249 | 5.1 MiB |
| count（有 service） | 14 ms | 22 ms | — | — |
| Pulse DAY | 25 ms | 44 ms | 455,249 | 6.2 MiB |
| Facet TYPE | 20 ms | 24 ms | 455,249 | 1.4 MiB |
| `trace_id` 点查 | 8 ms | 10 ms | 455,249 | — |
| `root:true` | 25 ms | 44 ms | 455,249 | — |

**结论：p95 全部远低于目标，峰值内存个位数 MiB，无需新增查询投影或服务表。**

**§8.6 排序键风险的实测结论**

`EXPLAIN indexes = 1` 证实了方案此前的担忧，但影响可控：

| 查询条件 | 主键实际使用的列 | 扫描粒度 |
|---|---|---|
| **带** service 条件 | `service_name` + `toDate(start_time)` | **5/58**（二分查找，5 个区间） |
| **不带** service 条件（页面默认） | 仅 `toDate(start_time)` | **58/58**（无额外裁剪） |

即：不加 service 条件时主键无法按首列裁剪，只能依赖分区键与 MinMax 索引。这在 455k 行下表现为 p95 104 ms vs 43 ms——**可接受**。但两者都随数据量线性增长，因此**生产上线前仍应压测目标数据量**；若届时 p95 不达标，首选按 §8.6 增加按 `(start_time, trace_id, span_id)` 排序的 Web 查询服务表，而不是加大连接池。前端已提供 Service Facet（阶段 C 完成），可作为运维侧的主要裁剪手段。

**一个已澄清的误报**：压测期间 `system.query_log` 出现过一条 12,970 ms / 1022 MiB 的记录，一度疑似 `trace_id` 点查退化。经核查该记录是**灌数据的 `INSERT` 语句本身**（其列清单含 `trace_id`，被分析用的粗略分组误归类）。真正的 `trace_id` 点查为 p50 8 ms / p95 10 ms。**不要把 INSERT 计入查询压测统计。**

## 12. 测试要求

### 12.1 SQL Provider 单测

至少覆盖：

1. 新 Provider 生成的 SQL 不包含 `events_core`、`events_full`、旧 `scores`、`is_deleted`、`event_ts`、`project_id`。
2. 所有用户输入都通过 `#{}` 绑定，攻击字符串不会出现在 SQL 文本。
3. 排序、方向、Facet、Pulse bucket 都是 enum 白名单。
4. `traceId:=...`、否定条件、OR、AND Tags、Metadata、Token、成本条件正确。
5. 64 路并发构建 SQL 时动态参数互不污染。
6. Observation 10 种枚举类型全部可映射；未知类型在摄取/迁移处归一化，不能让 `valueOf` 抛错。
7. 在目标 ClickHouse 21.8.14.5 上执行全部 SQL 的 `EXPLAIN SYNTAX`/小数据实跑；本文关键迁移表达式已在本地 ClickHouse 25.12 做过只读语法检查，但这不能代替目标版本兼容性验证。

### 12.2 ClickHouse 集成测试

对小批量样本执行：

- 列表总数与源表一致；
- Facet TYPE 至少包含 SPAN、GENERATION；
- Pulse count 之和等于同条件列表 count；
- 精确 Trace ID 返回完整 Observation；
- TraceView 节点数一致且没有悬空父节点；
- Trace 汇总 latency/token/cost/count 与其 Observation 聚合一致；
- Metadata JSON 能解析；
- `cost NULL` 不会在 `cost:>0` 中被匹配。

### 12.3 前端验收路径

打开：

`http://127.0.0.1:5173/project/cmt2am51r0006pa07ghcbt7vi/traces?dateRange=7d`

逐项验证：

1. 输入 `traceId:=c4b4ad2262f4a4893f26d33da248e905`，应出现 8 行。
2. 移除 Trace ID Tag，数据恢复。
3. 选择 Type=GENERATION，列表、TYPE Facet 和趋势图同步变化。
4. 点击 GENERATION，详情树包含它及同 Trace 的全部节点。
5. 切换 Tree/Timeline/Graph，节点数一致。
6. 切换 Preview/Log View 与 Formatted/JSON，无解析异常。
7. 清除全部筛选，URL、Tag、Facet 勾选和请求参数同时复位。

## 13. 阻断项与默认决策

| 问题 | 默认决策 | 是否阻断本地联调 |
|---|---|---|
| 7 天 TTL 与 30d/90d UI 冲突 | **本地已改 30 天**；集群 final DDL 仍 7 天，投产前业务确认 | 否（本地）/ 是（投产） |
| 新表不能表达 RUNNING | 本期只迁移完成 Trace；投产前确认是否改 DDL | 否 |
| Tool Calls 无来源 | 禁用 `toolCalls` Quick Filter；保留 `tool_definitions` | 否 |
| Trace Name/Tags 不在 Observation | 当前页二次查 + Facet 有界关联 | 否 |
| Trace 总 Token/成本不在 Trace 表 | 详情 Java 聚合，列表两阶段聚合 | 否 |
| Score 源表为空 | 接口可迁移，Scores UI 不验收 | 否 |
| `service_name` 为排序键首列 | 增加 Service Facet并压测，必要时建查询服务表 | 投产前阻断 |
| 删除墓碑语义缺失 | 要求上游确认；不能自行恢复 `is_deleted` 条件 | 投产前阻断 |

## 14. 交付物和完成定义

后续模型只有同时交付以下内容，才能宣称 Tracing 切表完成：

1. 本地单节点 DDL 和小批量迁移 SQL。
2. 新的 Tracing Mapper、SQL Provider 和 Row 模型。
3. 服务层两阶段合并与 locator 详情查询。
4. 更新后的筛选映射、Facet 和 Pulse。
5. 单测、集成验证、前端构建和浏览器验收结果。
6. 性能数据及 TTL、RUNNING、删除语义的最终决策记录。
7. `PROJECT_PROGRESS.md` 和 `MIGRATION_MATRIX.md` 更新。

不能仅凭“后端能启动”或“某个列表 HTTP 200”判定完成；必须用同一批 Trace 证明列表、筛选、趋势图和详情的数据闭环一致。

## 15. 给后续模型的执行提示

开始前先阅读本文和第 2 节的四个文件，然后从阶段 A 顺序执行。每完成一个阶段：

1. 勾选本文对应清单；
2. 运行该阶段测试；
3. 将实际命令、结果、已知差异写入 `PROJECT_PROGRESS.md`；
4. 测试失败时停在当前阶段修复，不要继续扩大改动面；
5. 不修改原 Langfuse 数据，不在未核验的环境执行删除 SQL。
