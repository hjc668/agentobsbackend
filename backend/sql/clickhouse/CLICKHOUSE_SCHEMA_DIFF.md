# ClickHouse 表结构比对与查询改造方案

> **2026-09-10 改造前审计快照，不是当前查询现状。** A 侧“后端当前查询 `events_core/events_full`”等措辞仅适用于当时；截至 2026-09-22，三个现行 SQL Provider 已切换 AgentObs `_all`。继续开发请先读项目根目录 `PROJECT_PROGRESS.md` 并核对当前 SQL Provider、目标库 `SHOW CREATE TABLE`。下方旧行数、容器版本、字段数均是历史样本。

比对对象：

- **A 侧（现状）**：Langfuse 原始 ClickHouse 表，即迁移后端当前实际查询的表。
- **B 侧（目标）**：`backend/sql/clickhouse/agentobs-clickhouse-schema-final.sql`（AgentObs final cluster schema，目标 ClickHouse 21.8.14.5）。

生成日期：2026-09-10
实测环境：`langfuse-clickhouse-1`（ClickHouse 25.12.11），数据库 `default`
A 侧数据基线：`events_core` 455,249 行 / 15,734 个 Trace，时间跨度 2026-08-21 ~ 2026-09-07（17 天）

> 本文所有 A 侧结构均来自 `SHOW CREATE TABLE` 实测，非文档推测。B 侧结构来自 schema 文件原文。

---

## 1. 表清单与实测基线

### 1.1 A 侧：Langfuse 原始表

| 表 | 引擎 | 列数 | 行数 | 后端是否使用 |
|---|---|---:|---:|---|
| `events_core` | ReplacingMergeTree(event_ts, is_deleted) | **75** | **455,249** | ✅ **唯一主力表**，SQL 中 14 处 `FROM` |
| `events_full` | ReplacingMergeTree(event_ts, is_deleted) | 75 | 455,249 | ✅ 1 处（Trace 详情）；`events_core` 的上游 |
| `scores` | ReplacingMergeTree(event_ts, is_deleted) | 27 | 0 | ✅ 3 处（查询存在但无数据） |
| `observations` | ReplacingMergeTree(event_ts, is_deleted) | 36 | 0 | ❌ 未使用 |
| `traces` | ReplacingMergeTree(event_ts, is_deleted) | 19 | 0 | ❌ 未使用 |
| `events_core_mv` | MaterializedView | — | — | ❌ 摄取链路用 |
| `analytics_traces` / `analytics_observations` / `analytics_scores` | View | — | — | ❌ 未使用 |

关键事实：**迁移后端只依赖 `events_core`、`events_full`、`scores` 三张表**，其中 `events_core` 承担了绝大部分负载。`observations` 和 `traces` 这两张 Langfuse 原生表**是空的、且完全没被查询**——它们既是 A 侧的历史遗留，也正好是 B 侧拆表模型的天然对照物。

### 1.2 B 侧：AgentObs

| 表 | 列数 | 性质 | A 侧对应物 |
|---|---:|---|---|
| `hmp_agentobs_traces` | 24 | 事实表，1 行/Trace | `events_core` 的 trace 行 / `traces` |
| `hmp_agentobs_observations` | 66 | 事实表，1 行/Span | `events_core` 的 span 行 / `observations` |
| `hmp_agentobs_scores` | 14 | 事实表（REST 提交） | `scores` |
| `hmp_agentobs_span_events` | 11 | 事实表（异常事件） | **无** |
| `hmp_agentobs_span_links` | 9 | 事实表（跨 Trace 链接） | **无** |
| `hmp_agentobs_logs` | 23 | 事实表（日志） | **无** |
| `hmp_agentobs_metric_points` | 40 | 事实表（指标，非 Trace 记录） | **无** |
| `hmp_agentobs_trace_locator` | 5 | 派生读索引 + AggregatingMergeTree | **无** |
| `*_all` × 7 | — | Distributed 表 | **无** |

---

## 2. 配置级比对

| 维度 | A 侧 `events_core` | B 侧 `hmp_agentobs_observations` | 影响 |
|---|---|---|---|
| 引擎 | `ReplacingMergeTree(event_ts, is_deleted)` | `ReplicatedReplacingMergeTree('/clickhouse/tables/{shard}/…','{replica}', ingestion_version)` | ⚠️ 版本列不同；B 侧需 Keeper/ZooKeeper |
| 集群 | 无 | `ON CLUSTER {cluster}` + 7 张 Distributed | ⚠️ 单机 Docker 无法直接执行 |
| 分区键 | `toYYYYMM(start_time)` | `toDate(start_time)` | 分区粒度变细（月→日） |
| 排序键 | `(project_id, toStartOfMinute(start_time), xxHash32(trace_id), span_id, start_time)` | `(service_name, toDate(start_time), start_time, trace_id, span_id)` | ⚠️ **首列不匹配，见 §5.1** |
| 主键 | `(project_id, toStartOfMinute(start_time), xxHash32(trace_id))` | `(service_name, toDate(start_time))` | 同上 |
| **TTL** | **无（永久保留）** | **`+ INTERVAL 7 DAY`** | 🔴 **产品语义冲突，见 §5.2** |
| 软删除 | `is_deleted UInt8`（版本列第二参数） | **无** | 🔴 **见 §5.3** |
| 时间版本列 | `event_ts DateTime64(3)` | 无（改用 `ingestion_version UInt64`） | 🔴 见 §5.3 |
| 索引 | bloom_filter × 多列 + `text` 全文 + `ngrambf_v1` | `bloom_filter` × 3（trace_id / span_id / session_id） | B 侧索引更精简，metadata 全文检索能力弱化 |
| 采样 | `SAMPLE BY xxHash32(trace_id)` | 无 | 影响大表采样查询 |
| Codec | 大字段 `CODEC(ZSTD(3))` | 大字段 `CODEC(ZSTD(3))` | ✅ 一致 |

---

## 3. 字段级比对

### 3.1 Trace 维度：`events_core`(is_app_root 行) → `hmp_agentobs_traces`

| 后端需要的语义 | A 侧列 / 类型 | B 侧列 / 类型 | 处理 |
|---|---|---|---|
| Trace 标识 | `trace_id` `String` | `trace_id` `FixedString(32)` | ⚠️ 类型收紧，空值语义不同（`''` vs 32 个零字节） |
| Trace 名称 | `trace_name` `String` | `trace_name` `String` | ✅ 同名 |
| 开始/结束 | `start_time` `DateTime64(6)` / `end_time` `Nullable(DateTime64(6))` | `start_time` `DateTime64(6,'UTC')` / `end_time` `DateTime64(6,'UTC')` | ⚠️ B 侧带时区且非 Nullable |
| 用户 / 会话 | `user_id` `String` / `session_id` `String` | `user_id` `String` / `session_id` `String` | ✅ 同名 |
| 环境 / 版本 | `environment` `LowCardinality(String)` / `version` `String` | 同 | ✅ 同名 |
| 标签 | `tags` `Array(String)` | `tags` `Array(String)` | ✅ 同名 |
| 级别 | `level` `LowCardinality(String)` | `level` `LowCardinality(String)` | ✅ 同名 |
| 输入 / 输出 | `input` / `output` `String` | `trace_input` / `trace_output` `String` | 🟡 改名 |
| 元数据 | `metadata_names` + `metadata_values` `Array(String)` | `metadata` `String`(JSON) | 🟡 B 侧反而更简单，应用可省掉 `mapFromArrays` |
| 根节点标记 | `is_app_root` `Bool` | `is_product_root` `Bool` | 🟡 改名 |
| Token 用量 | `usage_details` `Map(LowCardinality(String), UInt64)` | **不存在** | 🔴 需从 observations 聚合 |
| 成本 | `cost_details` `Map` + `calculated_total_cost` `Decimal(18,12)` MATERIALIZED | **不存在** | 🔴 需从 observations 聚合 |
| 删除标记 | `is_deleted` `UInt8` | **不存在** | 🔴 |
| 时间版本 | `event_ts` `DateTime64(3)` | **不存在** | 🔴 |
| 项目隔离 | `project_id` `String` | **不存在** | ✅ 后端已不使用（见 §4） |

B 侧 traces 表**独有**：`root_span_id FixedString(16)`、`duration_ms Float64`、`status_code Int8`、`conversation_id`、`request_id`、`ingestion_version`、`schema_version`、`extra_attributes`。

### 3.2 Observation 维度：`events_core` → `hmp_agentobs_observations`

| 后端需要的语义 | A 侧列 / 类型 | B 侧列 / 类型 | 处理 |
|---|---|---|---|
| Span 标识 | `span_id` `String` | `span_id` `FixedString(16)` | ⚠️ 类型收紧 |
| 父 Span | `parent_span_id` `String` | `parent_span_id` `String` | ✅ 同名（根判断 `= ''` 仍可用） |
| 类型 | `type` `LowCardinality(String)` | `type` `LowCardinality(String)` | ✅ 同名 |
| 名称 | `name` `String` | `name` `String` | ✅ 同名 |
| 模型 | `provided_model_name` `String` | `request_model` / `model` `LowCardinality(String)` | 🟡 改名 + 语义拆分 |
| 模型 ID | `model_id` `String` | **不存在** | 🔴 |
| 输入 Token | `usage_details['input']` `Map` | `usage_input_tokens` `UInt64` | 🟡 **Map → 离散列** |
| 输出 Token | `usage_details['output']` `Map` | `usage_output_tokens` `UInt64` | 🟡 同上 |
| 总 Token | `usage_details['total']` | `usage_total_tokens` `UInt64` | 🟡 同上 |
| 成本 | `cost_details` `Map` + `calculated_total_cost` | `cost` `Nullable(Float64)` | 🟡 粒度变粗（无分项） |
| 首 Token 时刻 | `completion_start_time` `Nullable(DateTime64(6))` | **不存在**（只有 `time_to_first_chunk_ms Nullable(Float64)`） | 🔴 **语义不同：B 侧是"时长"，A 侧是"时刻"** |
| 状态 | `level` / `status_message` | `level` / `status_message` / 另加 `status_code Int8` | ✅ B 侧更丰富 |
| 模型参数 | `model_parameters` `String` | `model_parameters` `String` | ✅ 同名 |
| Prompt | `prompt_name` `String` / `prompt_version` `Nullable(UInt16)` | `prompt_name` `String` / `prompt_version` `String` | ⚠️ 版本类型 UInt16→String |
| 元数据 | `metadata_names` + `metadata_values` | `metadata` `String`(JSON) | 🟡 简化 |
| 删除标记 / 时间版本 | `is_deleted` / `event_ts` | **不存在** | 🔴 |

B 侧 observations 表**独有**：`tool_name` / `tool_call_id` / `tool_type`、`agent_name` / `agent_id` / `agent_version`、`provider`、`operation`、`span_kind`、`finish_reasons`、`response_id`、`conversation_id`、`request_id`、6 类 `usage_*_tokens`、`system_instructions`、`scope_*` / `resource_*` 属性等。

> 注意：B 侧**没有** `tool_definitions` / `tool_calls` / `tool_call_names` 三个 `Map`/`Array` 列，而 A 侧有，且后端的 `Missed tool calls` 快速预设正依赖 `tool_definitions` 与 `tool_calls`。这是一处**功能性缺口**，需确认 B 侧是否有替代来源。

### 3.3 Score：`scores` → `hmp_agentobs_scores`

三组中最接近的一组。

| 后端需要的语义 | A 侧 | B 侧 | 处理 |
|---|---|---|---|
| 主键 | `id` `String` | `score_id` `String` | 🟡 改名 |
| Trace / Observation | `trace_id` `Nullable(String)` / `observation_id` `Nullable(String)` | `trace_id` `String` / `observation_id` `String` | ⚠️ 去掉 Nullable |
| 名称 / 值 | `name` `String` / `value` `Float64` | `name` `LowCardinality(String)` / `value` `Float64` | ✅ |
| 数据类型 | `data_type` `String` | `data_type` `LowCardinality(String)` | ✅ |
| 字符串值 | `string_value` `Nullable(String)` | `value_string` `String` | 🟡 改名 |
| 来源 / 评论 | `source` `String` / `comment` `Nullable(String)` | `source` / `comment` `String` | ✅ |
| 创建时间 | `created_at` `DateTime64(3)` | `created_at` `DateTime64(3,'UTC')` | ✅ |
| 删除标记 / 时间版本 | `is_deleted` / `event_ts` | **不存在** | 🔴 |

### 3.4 B 侧独有能力（A 侧完全无对应物）

| 表 | 提供的能力 | 说明 |
|---|---|---|
| `hmp_agentobs_span_events` | Span 异常事件（`exception_type` / `exception_message` / `exception_stacktrace`） | 前端目前无展示入口 |
| `hmp_agentobs_span_links` | 跨 Trace 链接（`linked_trace_id` / `linked_span_id`） | 前端目前无展示入口 |
| `hmp_agentobs_logs` | OTel 日志（`severity_number` / `body` / `raw_log`） | 前端目前无展示入口 |
| `hmp_agentobs_metric_points` | OTel 指标点（含直方图 / 指数直方图） | 非 Trace 记录 |
| `hmp_agentobs_trace_locator` | `trace_id → service_name + 时间范围` 读索引 | 由 observations 派生，非事实来源 |

**这五张表不应被视为"平替对象"，它们是能力扩展。** 现有 Tracing 页面没有对应 UI。

---

## 4. 应用层依赖盘点

后端 `ObservabilitySqlProvider` 对 A 侧列的依赖统计（实测）：

| 列 / 语义 | 出现次数 | B 侧是否存在 |
|---|---:|---|
| `is_deleted` | **12** | 🔴 否 |
| `is_app_root` | **10** | 🟡 改名为 `is_product_root` |
| `event_ts` | **9** | 🔴 否 |
| `project_id` | **0** | ✅ 不需要（后端已改为全库读取） |
| `calculated_total_cost` | 多处 | 🔴 否 |
| `usage_details` / `cost_details` | 多处 | 🟡 改为离散列 |
| `metadata_names` / `metadata_values` | 多处 | 🟡 改为 `metadata` JSON |

### 4.1 筛选 DSL 字段 → 列映射（改造时需逐条替换）

| DSL 字段 | A 侧列 | B 侧对应 |
|---|---|---|
| `environment` / `env` | `environment` | `environment` ✅ |
| `type` | `type` | `type` ✅ |
| `level` | `level` | `level` ✅ |
| `name` | `name` | `name` ✅ |
| `traceName` | `trace_name` | `trace_name` ✅ |
| `model` | `provided_model_name` | `request_model` / `model` 🟡 |
| `modelId` | `model_id` | **无** 🔴 |
| `prompt` / `promptName` | `prompt_name` | `prompt_name` ✅ |
| `traceId` / `trace` | `trace_id` | `trace_id` ✅（FixedString） |
| `session` / `sessionId` | `session_id` | `session_id` ✅ |
| `user` / `userId` | `user_id` | `user_id` ✅ |
| `status` / `statusMessage` | `status_message` | `status_message` ✅ |
| `version` / `release` | `version` / `release` | `version` ✅ / `release` **无** 🔴 |
| `input` / `output` | `input` / `output` | `input` / `output` ✅ |
| `tags` | `tags` | `tags` ✅ |
| `metadata.<key>` | `metadata_names`/`metadata_values` | `metadata` JSON 🟡 |
| `scores.<name>` | 子查询 `scores` | `hmp_agentobs_scores` 🟡 |
| `latency` / `ttft` / `tokens` / `cost` / `tps` 等数值 | 表达式 | 需重写 🟡 |
| `toolDefinitions` / `toolCalls` | `tool_definitions` / `tool_calls` | **无** 🔴 |
| 根节点判定 | `parent_span_id = '' OR is_app_root` | `parent_span_id = '' OR is_product_root` 🟡 |

---

## 5. 三个硬性阻断点

### 5.1 排序键首列不匹配 → 性能悬崖

- A 侧主键首列：`project_id`
- B 侧主键首列：`service_name`

后端 SQL 中 `project_id` 出现 **0 次**（见 `PROJECT_PROGRESS.md` §12，已改为全库读取），所以 A 侧的 project_id 首列**早已失效**，这反而不是问题。

但 B 侧把 **`service_name` 放在主键第一列**，而**后端从不按 service_name 过滤**。ClickHouse 的稀疏主键索引依赖前缀匹配，首列缺失意味着：**改造后每一次查询都无法有效利用主键索引，退化为大范围扫描**。

缓解手段（择一）：
1. 建**查询专用投影/物化视图**，排序键按后端真实访问模式重排（推荐，见 §6.2）。
2. 为后端查询补上 `service_name` 条件（需业务能确定服务范围，当前不现实）。
3. 依赖 `trace_id` / `span_id` 的 `bloom_filter` 跳数索引，但只对精确 ID 查询有效，对 Facet、Pulse、列表分页无效。

### 5.2 TTL：7 天 ↔ 永久保留

| | |
|---|---|
| A 侧 5 张表 | **无 TTL**，实测数据跨 17 天 |
| B 侧全部事实表 | **`TTL … + INTERVAL 7 DAY`**（`ttl_only_drop_parts=1`） |
| 前端可选时间范围 | `24h` / `7d` / **`30d`** / **`90d`** |

**B 侧的 7 天 TTL 会让前端默认的 30d 和 90d 直接失效**，且 Trend 图的分桶在长期范围下会出现空洞。

这是**产品语义冲突，不是技术细节**，必须由业务侧拍板：
- 若必须保留 30d/90d → B 侧 TTL 需放宽或移除（并评估存储成本）。
- 若接受 7 天 → 前端需下线 30d/90d 选项，并同步调整 URL 默认值与文档。

### 5.3 删除语义与版本列

| | A 侧 | B 侧 |
|---|---|---|
| 引擎版本列 | `event_ts`（时间戳） | `ingestion_version`（UInt64 序号） |
| 删除标记 | `is_deleted UInt8` | **无** |
| 后端用法 | `WHERE is_deleted = 0`（12 处）、`argMax(x, event_ts)`（9 处） | 需全部改写 |

改造后：
- `is_deleted = 0` **无处可加**。若上游不产生删除记录，可整体去掉；否则需要 B 侧补一列删除标记，或改用分区级删除。
- `argMax(x, event_ts)` → `argMax(x, ingestion_version)`。两者都能选出"最新版本"，但 `event_ts` 是业务时间、`ingestion_version` 是写入序号，**排序语义不同**：跨批次重放时结果可能不同。

---

## 6. 改造方案

### 6.0 方案选型

| 方案 | 改动面 | 性能 | 适用阶段 |
|---|---|---|---|
| **A. 兼容视图** | 后端零改动 | 差（UNION ALL + 无法下推） | 快速验证 / 过渡 |
| **B. 查询重写（推荐）** | 仅改 `ObservabilitySqlProvider` | 好（直查拆分表） | 目标形态 |
| **C. 物化兼容层** | 需新增 MV + 双写 | 好 | 长期稳态 |

**推荐路径：先用方案 A 打通，再逐查询迁移到方案 B，C 视容量需要再评估。**

---

### 6.1 方案 A：兼容视图（过渡）

在 B 侧之上重建一个 `events_core` 形状的视图，让后端 SQL 一行不改即可跑通。

```sql
CREATE VIEW default.events_core_compat AS
SELECT
    ''                                   AS project_id,      -- 后端已不使用
    trace_id                             AS trace_id,
    ''                                   AS span_id,         -- trace 行无 span_id
    ''                                   AS parent_span_id,
    start_time,
    end_time,
    trace_name                           AS name,
    'TRACE'                              AS type,            -- ⚠️ 需确认 B 侧 Trace 行的 type 语义
    environment,
    version,
    ''                                   AS release,
    trace_name,
    user_id,
    session_id,
    tags,
    level,
    status_message,
    CAST(NULL AS Nullable(DateTime64(6))) AS completion_start_time,
    true                                 AS is_app_root,
    false AS bookmarked, false AS public,
    '' AS prompt_id, '' AS prompt_name, CAST(NULL AS Nullable(UInt16)) AS prompt_version,
    '' AS model_id, '' AS provided_model_name, '' AS model_parameters,
    CAST(map() AS Map(LowCardinality(String), UInt64)) AS provided_usage_details,
    CAST(map() AS Map(LowCardinality(String), UInt64)) AS usage_details,
    CAST(map() AS Map(LowCardinality(String), Decimal(18,12))) AS provided_cost_details,
    CAST(map() AS Map(LowCardinality(String), Decimal(18,12))) AS cost_details,
    toDecimal64(0, 12) AS calculated_total_cost,
    CAST(map() AS Map(String,String)) AS tool_definitions,
    CAST([] AS Array(String)) AS tool_calls,
    CAST([] AS Array(String)) AS tool_call_names,
    trace_input                          AS input,
    trace_output                         AS output,
    CAST([] AS Array(String)) AS metadata_names,
    CAST([] AS Array(String)) AS metadata_values,
    toDateTime64(0, 3)                   AS event_ts,        -- 🔴 见下
    0                                    AS is_deleted,      -- 🔴 见下
    now() AS created_at, now() AS updated_at
FROM default.hmp_agentobs_traces

UNION ALL

SELECT
    '',
    trace_id,
    span_id,
    parent_span_id,
    start_time,
    end_time,
    name,
    type,
    environment,
    version,
    '',
    ''                                   AS trace_name,      -- 需 LEFT JOIN traces 补齐
    user_id,
    session_id,
    CAST([] AS Array(String))            AS tags,            -- Trace 级字段需 JOIN
    level,
    status_message,
    if(time_to_first_chunk_ms IS NULL, NULL,
       toDateTime64(toUnixTimestamp64Milli(start_time) + toInt64(time_to_first_chunk_ms), 6)) AS completion_start_time,
    is_product_root                      AS is_app_root,
    false, false, '', '', CAST(NULL AS Nullable(UInt16)),
    '' AS model_id,
    coalesce(nullIf(model,''), request_model) AS provided_model_name,
    model_parameters,
    CAST(map('input', usage_input_tokens, 'output', usage_output_tokens) AS Map(LowCardinality(String), UInt64)) AS provided_usage_details,
    CAST(map('input', usage_input_tokens, 'output', usage_output_tokens, 'total', usage_total_tokens) AS Map(LowCardinality(String), UInt64)) AS usage_details,
    CAST(map() AS Map(LowCardinality(String), Decimal(18,12))) AS provided_cost_details,
    CAST(map('total', toDecimal64(ifNull(cost,0),12)) AS Map(LowCardinality(String), Decimal(18,12))) AS cost_details,
    toDecimal64(ifNull(cost,0), 12)      AS calculated_total_cost,
    CAST(map() AS Map(String,String)) AS tool_definitions,
    CAST([] AS Array(String)) AS tool_calls,
    CAST([] AS Array(String)) AS tool_call_names,
    input, output,
    CAST([] AS Array(String)), CAST([] AS Array(String)),
    toDateTime64(0, 3), 0, now(), now()
FROM default.hmp_agentobs_observations;
```

**视图方案必须正视的四个问题：**

1. `event_ts` 无对应列 → 填空值会让 `argMax(x, event_ts)` **全部退化**，必须改用 `ingestion_version` 重写。**这意味着方案 A 也需要改 SQL**，收益打折。
2. `is_deleted` 无对应列 → 硬编码 0 只在上游不产生删除记录时成立。
3. `trace_name` / `tags` 等 Trace 级字段在 observations 表**不存在**，视图里需 `LEFT JOIN hmp_agentobs_traces`，而 ClickHouse 视图的 JOIN 无法有效下推，**性能会显著恶化**。
4. `time_to_first_chunk_ms` 是**时长**，A 侧 `completion_start_time` 是**时刻**，上面的换算只是近似，且 A 侧语义是模型返回首 token 的绝对时间。

→ **结论：方案 A 只适合做冒烟验证，不适合长期运行。**

---

### 6.2 方案 B：查询重写（推荐目标形态）

把 `ObservabilitySqlProvider` 的查询从"扫一张扁平表 + 聚合"改为"直接读拆分表"。

#### B-1. Trace 列表与指标：不再需要聚合

A 侧 `TRACE_COLUMNS` 用 15 个聚合表达式（`argMaxIf(..., is_app_root)`、`countIf`、`min(start_time)`）从扁平表里"重建"Trace 指标。B 侧 `hmp_agentobs_traces` 本来就是 1 行/Trace：

```sql
-- 改造前（A 侧）
SELECT trace_id AS id,
       coalesce(nullIf(argMaxIf(trace_name, event_ts, is_app_root),''), argMin(name, start_time)) AS name,
       min(start_time) AS timestamp, ... toInt32(count()) AS observationCount
FROM events_core FINAL WHERE ... GROUP BY trace_id

-- 改造后（B 侧）——聚合全部消失
SELECT trace_id AS id,
       trace_name AS name,
       start_time AS timestamp,
       nullIf(user_id,'') AS userId,
       nullIf(session_id,'') AS sessionId,
       environment,
       multiIf(status_code <> 0, 'ERROR', end_time IS NULL, 'RUNNING', 'SUCCESS') AS status,
       toInt64(duration_ms) AS latencyMs,
       toInt64(0) AS totalTokens,          -- 🔴 见下
       toDecimal64(0,12) AS totalCost,     -- 🔴 见下
       toInt32(0) AS observationCount      -- 🔴 见下
FROM default.hmp_agentobs_traces
WHERE start_time >= {from} AND start_time <= {to}
```

**三个字段在 B 侧 traces 表无来源**：`totalTokens`、`totalCost`、`observationCount`。B 侧 traces 表没有 usage/cost 列。三种处理：

- **(a)** 不改表，用子查询从 observations 聚合（每次列表查询都多一次聚合，成本高）。
- **(b)** 在 B 侧建**物化视图**，按 trace_id 预聚合 tokens / cost / span 数写回一张 `hmp_agentobs_trace_metrics` 表（推荐，见 B-4）。
- **(c)** 前端列表去掉这三列（业务是否接受待确认）。

#### B-2. Observation 列表：Map → 离散列

```sql
-- 改造前
toInt64(usage_details['input'])  AS inputTokens,
toInt64(usage_details['output']) AS outputTokens,
if(mapContains(cost_details,'total'), cost_details['total'], calculated_total_cost) AS totalCost,
toJSONString(mapFromArrays(metadata_names, metadata_values)) AS metadataJson

-- 改造后
toInt64(usage_input_tokens)  AS inputTokens,
toInt64(usage_output_tokens) AS outputTokens,
toDecimal64(ifNull(cost,0),12) AS totalCost,
metadata                     AS metadataJson          -- B 侧本身就是 JSON 字符串，更简单
```

#### B-3. 筛选 DSL 的条件重写

| 改造前 | 改造后 |
|---|---|
| `WHERE is_deleted = 0` | **整条删除**（B 侧无此列；需业务确认上游是否产生删除） |
| `argMax(x, event_ts)` | `argMax(x, ingestion_version)` |
| `is_app_root` | `is_product_root` |
| `provided_model_name` | `coalesce(nullIf(model,''), request_model)` |
| `tool_definitions` / `tool_calls` | 🔴 **无对应，`Missed tool calls` 预设需下线或另找来源** |
| `release` | 🔴 **无对应**，需下线该筛选字段 |
| `model_id` | 🔴 **无对应**，需下线 |
| `sdk_name` / `sdk_version` | 改用 `scope_name` / `scope_version` |
| 根节点判定 | `(parent_span_id = '' OR is_product_root)` |

#### B-4. 补足能力的物化视图

为 B-1 的 (b) 方案，建议新增一张 Trace 指标预聚合表：

```sql
CREATE TABLE default.hmp_agentobs_trace_metrics (
    trace_id            FixedString(32),
    observation_count   UInt64,
    total_input_tokens  UInt64,
    total_output_tokens UInt64,
    total_tokens        UInt64,
    total_cost          Float64,
    ingestion_version   UInt64
) ENGINE = ReplacingMergeTree(ingestion_version)
ORDER BY trace_id;

CREATE MATERIALIZED VIEW default.hmp_agentobs_trace_metrics_mv
TO default.hmp_agentobs_trace_metrics AS
SELECT trace_id,
       count()                        AS observation_count,
       sum(usage_input_tokens)        AS total_input_tokens,
       sum(usage_output_tokens)       AS total_output_tokens,
       sum(usage_total_tokens)        AS total_tokens,
       sum(ifNull(cost, 0))           AS total_cost,
       max(ingestion_version)         AS ingestion_version
FROM default.hmp_agentobs_observations
GROUP BY trace_id;
```

这同时解决 §5.1 的主键首列问题：该表按 `trace_id` 排序，与后端访问模式一致。

---

### 6.3 方案 C：物化兼容层（长期稳态）

在 B 侧之上维护一张"后端专用"的扁平表，排序键按后端真实查询重排：

```sql
CREATE TABLE default.events_core_srv
ENGINE = ReplacingMergeTree(ingestion_version)
PARTITION BY toYYYYMM(start_time)
ORDER BY (toStartOfMinute(start_time), xxHash32(trace_id), span_id, start_time)
-- 不设 TTL，或按业务要求显式设置
AS SELECT ... FROM default.events_core_compat;   -- 复用 6.1 的视图逻辑
```

由物化视图从 `hmp_agentobs_traces` + `hmp_agentobs_observations` 增量写入。代价是**多一份存储 + 需保证 MV 逻辑与源表演进同步**。

---

## 7. 分阶段实施步骤

| 阶段 | 动作 | 验收 |
|---|---|---|
| **0. 前置确认** | 业务确认 ①7 天 TTL 是否可接受 ②上游是否产生删除记录 ③`release` / `model_id` / tool 相关筛选项能否下线 | 书面结论 |
| **1. 单机化改造** | 把 `ReplicatedReplacingMergeTree` 降为 `ReplacingMergeTree`、去掉 `ON CLUSTER` 与 7 张 Distributed 表，在本地 Docker 建表 | 8 张表建成，`SELECT count()` 通过 |
| **2. 兼容视图冒烟** | 按 §6.1 建 `events_core_compat`，后端指向它，跑通一次列表/详情/筛选 | HTTP 200，无异常 |
| **3. 逐查询重写** | 按 §6.2 改写 `ObservabilitySqlProvider`，优先 Trace 列表 → Observation 列表 → Facet → Pulse → 详情 | 每个查询有对应单测 |
| **4. 补足指标** | 建 §6.2 B-4 的 `hmp_agentobs_trace_metrics` + MV | Trace 列表 tokens/cost/span 数正确 |
| **5. 回归验证** | 用同一 Trace 在 A/B 两侧对比字段值，逐项核对 | 字段差异清单闭合 |
| **6. 文档同步** | 更新 `PROJECT_PROGRESS.md`、`MIGRATION_MATRIX.md` | — |

**阶段 5 的对比锚点**：用固定 Trace `668904f3534ff5678327bcf81a3f5814`（4 个 Observation）和 `a874a391240d48d9cfcfe66ea874b9cb`（2 个 Observation）在两侧跑同一查询，逐字段比对。

---

## 8. 风险清单

| # | 风险 | 等级 | 缓解 |
|---|---|---|---|
| 1 | 7 天 TTL 与 30d/90d 冲突 | 🔴 高 | 业务拍板；或放宽 TTL |
| 2 | `service_name` 首列导致索引失效 | 🔴 高 | §6.2 B-4 建按 trace_id / start_time 排序的专用表 |
| 3 | `is_deleted` 缺失，删除语义丢失 | 🔴 高 | 确认上游行为；必要时 B 侧补列 |
| 4 | `event_ts` → `ingestion_version` 排序语义变化 | 🟠 中 | 跨批次重放场景需专门验证 |
| 5 | Trace 级 tokens/cost/span 数无来源 | 🟠 中 | §6.2 B-4 预聚合表 |
| 6 | `tool_definitions` / `tool_calls` 缺失 | 🟠 中 | `Missed tool calls` 预设需下线或改用 span_events |
| 7 | `release` / `model_id` 筛选字段无对应 | 🟡 低 | 前端下线这两个字段 |
| 8 | B 侧引擎需 Keeper，单机跑不起来 | 🟡 低 | 阶段 1 单机化改造 |
| 9 | `FixedString` 空值语义（32 个零字节 ≠ `''`） | 🟡 低 | 统一用 `notEmpty` 之外的空值判断，避免 `= ''` |
| 10 | `completion_start_time` 与 `time_to_first_chunk_ms` 语义不同 | 🟡 低 | 前端 TTFT 展示按"时长"重算 |

---

## 9. 附录：A 侧完整 DDL

### 9.1 `events_core`（75 列，后端主力表）

引擎与键：

```sql
ENGINE = ReplacingMergeTree(event_ts, is_deleted)
PARTITION BY toYYYYMM(start_time)
PRIMARY KEY (project_id, toStartOfMinute(start_time), xxHash32(trace_id))
ORDER BY (project_id, toStartOfMinute(start_time), xxHash32(trace_id), span_id, start_time)
SAMPLE BY xxHash32(trace_id)
SETTINGS enable_block_number_column = 1, enable_block_offset_column = 1,
         prewarm_mark_cache = 1, prewarm_primary_key_cache = 1, index_granularity = 8192
-- 无 TTL
```

列清单（按序）：

```
project_id, trace_id, span_id, parent_span_id, start_time, end_time, name, type,
environment, version, release, trace_name, user_id, session_id, tags, level,
status_message, completion_start_time, is_app_root, bookmarked, public,
prompt_id, prompt_name, prompt_version, model_id, provided_model_name, model_parameters,
provided_usage_details, usage_details, provided_cost_details, cost_details,
calculated_input_cost, calculated_output_cost, calculated_total_cost, total_cost,
usage_pricing_tier_id, usage_pricing_tier_name, tool_definitions, tool_calls, tool_call_names,
input, input_length, output, output_length, metadata_names, metadata_values,
experiment_id, experiment_name, experiment_metadata_names, experiment_metadata_values,
experiment_description, experiment_dataset_id, experiment_item_id, experiment_item_version,
experiment_item_expected_output, experiment_item_metadata_names, experiment_item_metadata_values,
experiment_item_root_span_id, source, service_name, service_version, scope_name, scope_version,
telemetry_sdk_language, telemetry_sdk_name, telemetry_sdk_version, blob_storage_file_path,
event_bytes, created_at, updated_at, event_ts, is_deleted,
ingestion_api_key, ingestion_sdk_name, ingestion_sdk_version
```

派生列（MATERIALIZED）：
`calculated_input_cost`、`calculated_output_cost`、`calculated_total_cost`（均由 `cost_details` 计算）；
`total_cost` 为 ALIAS（`cost_details['total']`）；`input_length` / `output_length` 为 MATERIALIZED。

### 9.2 `events_full`

与 `events_core` **列完全一致（75 列）**，差异仅在：`input` / `output` 带 `CODEC(ZSTD(3))`。
`events_core_mv` 从 `events_full` 物化写入 `events_core`。后端 Trace 详情查询走 `events_full FINAL`。

### 9.3 `traces`（19 列）/ `observations`（36 列）

均为 Langfuse 原生表，**当前 0 行且后端未查询**。若后续考虑复用，其列命名（`parent_observation_id`、`provided_model_name` 等）与 B 侧更接近，可作为改造时的命名参考。

### 9.4 `scores`（27 列）

后端按 `trace_id` 查询，`WHERE is_deleted = 0`，`ORDER BY created_at, id`。

---

## 10. 待确认问题（需业务/上游答复）

1. **7 天 TTL 是否是硬性要求？** 若否，30d/90d 保留；若是，前端须下线长期范围。
2. **上游是否会产生 Trace/Observation 的删除记录？** 决定 `is_deleted = 0` 能否直接去掉。
3. **`release`、`model_id`、`tool_definitions`/`tool_calls` 三组筛选能力能否放弃？** B 侧无对应列。
4. **Trace 级 tokens / cost / span 数是列表必需列吗？** 决定是否必须建预聚合表。
5. **B 侧是否上集群？** 决定阶段 1 是"单机化改造"还是"保留 Replicated + 配 Keeper"。
