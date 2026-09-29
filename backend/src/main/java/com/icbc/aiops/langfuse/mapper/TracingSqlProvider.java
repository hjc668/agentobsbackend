package com.icbc.aiops.langfuse.mapper;

import com.icbc.aiops.langfuse.service.ObservationFacet;
import com.icbc.aiops.langfuse.service.ObservationQuery;
import com.icbc.aiops.langfuse.service.PulseBucket;
import com.icbc.aiops.langfuse.service.TraceQuery;
import com.icbc.aiops.langfuse.service.TracingFilterFields;
import com.icbc.aiops.langfuse.util.Java8Collections;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 AgentObs 表实现的 Tracing MyBatis SQL Provider。
 *
 * <p>该设计由数据模型差异决定：旧 schema 使用扁平 event 表，每一行都会重复 Trace 字段；
 * AgentObs 将 Trace 摘要（每条 Trace 一行）与 Observation 明细拆分。因此 trace name、tags
 * 等 Trace 级字段只存在于 Trace 表，tokens、cost、observation count 等指标也不直接存储在
 * Trace 表中，而是在有界时间窗口内从 Observations 聚合得到。
 *
 * <p>所有用户输入都通过 {@code #{}} 绑定；表名、排序列和 bucket 表达式均为从 enum
 * 中选择的编译期常量。
 */
public final class TracingSqlProvider {

    private static final String OBSERVATIONS = "default.hmp_agentobs_observations_all";
    private static final String TRACES = "default.hmp_agentobs_traces_all";
    private static final String LOCATOR = "default.hmp_agentobs_trace_locator_all";

    /**
     * enum 类字段统一转为大写后比较，使同一筛选条件能够同时匹配 AgentObs 中的 'span' 和 'SPAN'。
     * 列表条件、DSL 精确匹配和 facet 分组共用这些表达式，以保证三者行为一致。
     */
    private static final String ENUM_UPPER_TYPE = "upper(type)";
    private static final String ENUM_UPPER_LEVEL = "upper(level)";

    /**
     * Observations 查询始终使用 FINAL。
     *
     * <p>相关表使用 {@code ReplacingMergeTree(ingestion_version)}，后台合并不会立即完成。
     * {@code schema3-design.md} 已明确说明：“Kafka 重试和 sinker 重放允许重复……
     * ReplacingMergeTree 后台合并不提供即时唯一性；需要精确结果时，在有界筛选下使用 FINAL。”
     * 旧版本曾错误地把快照视为只追加数据并省略 FINAL，导致 list、count、pulse 和 facet
     * 对重放数据重复计数，而这四项数据恰好会在 Tracing 页面同时展示。
     *
     * <p>这里的每个查询都有明确边界（时间窗口、trace id 或两者同时存在），满足设计文档中
     * 使用 FINAL 的前提条件。
     */
    private static final String OBSERVATIONS_FINAL = OBSERVATIONS + " FINAL";

    /** 同上，供需要使用 {@code o} alias 限定列名的语句使用。 */
    private static final String OBSERVATIONS_ALIASED_FINAL = OBSERVATIONS + " AS o FINAL";

    /**
     * 与校验器共享，确保查询进入本构建器前后的 token 拆分方式完全一致；不同的正则会导致
     * 已通过校验的字段随后被丢弃，或出现相反情况。
     */
    private static final Pattern SEARCH_TOKEN = com.icbc.aiops.langfuse.service.TracingFilterFields.SEARCH_TOKEN;

    /** Observation 列表仅查询小字段，大字段通过详情接口加载。 */
    private static final String OBSERVATION_LIST_COLUMNS =
            "toString(span_id) AS id,\n"
            + "toString(trace_id) AS traceId,\n"
            + "'' AS traceName,\n"
            + "nullIf(parent_span_id, '') AS parentObservationId,\n"
            + "name,\n"
            + "type,\n"
            + "start_time AS startTime,\n"
            + "end_time AS endTime,\n"
            + "level,\n"
            // 有意不使用 "model" 作为 alias：schema 已有同名基础列，ClickHouse 会把 alias
            // 替换进 WHERE，两个定义发生冲突并触发 "Code 352 Block structure mismatch"。
            // 输出 alias 改为 modelName 可规避该问题；行模型和 DSL 筛选仍使用 "model"。
            + "nullIf(coalesce(nullIf(model, ''), request_model), '') AS modelName,\n"
            + "toInt64(round(duration_ms)) AS latencyMs,\n"
            + "toInt64(usage_input_tokens) AS inputTokens,\n"
            + "toInt64(usage_output_tokens) AS outputTokens,\n"
            + "ifNull(cost, 0.0) AS totalCost,\n"
            + "CAST(NULL AS Nullable(String)) AS inputJson,\n"
            + "CAST(NULL AS Nullable(String)) AS outputJson,\n"
            + "CAST(NULL AS Nullable(DateTime64(6))) AS completionStartTime,\n"
            + "ttftMs AS timeToFirstTokenMs,\n"
            + "nullIf(status_message, '') AS statusMessage,\n"
            + "CAST(NULL AS Nullable(String)) AS modelId,\n"
            + "CAST(NULL AS Nullable(String)) AS modelParametersJson,\n"
            + "'{}' AS usageDetailsJson,\n"
            + "costDetailsJson AS costDetailsJson,\n"
            + "nullIf(prompt_name, '') AS promptName,\n"
            + "toInt32OrNull(prompt_version) AS promptVersion,\n"
            + "'{}' AS metadataJson\n";

    /** Observation 详情查询，增加抽屉展示所需的大字段。 */
    private static final String OBSERVATION_DETAIL_COLUMNS =
            "toString(span_id) AS id,\n"
            + "toString(trace_id) AS traceId,\n"
            + "'' AS traceName,\n"
            + "nullIf(parent_span_id, '') AS parentObservationId,\n"
            + "name,\n"
            + "type,\n"
            + "start_time AS startTime,\n"
            + "end_time AS endTime,\n"
            + "level,\n"
            // 有意不使用 "model" 作为 alias：schema 已有同名基础列，ClickHouse 会把 alias
            // 替换进 WHERE，两个定义发生冲突并触发 "Code 352 Block structure mismatch"。
            // 输出 alias 改为 modelName 可规避该问题；行模型和 DSL 筛选仍使用 "model"。
            + "nullIf(coalesce(nullIf(model, ''), request_model), '') AS modelName,\n"
            + "toInt64(round(duration_ms)) AS latencyMs,\n"
            + "toInt64(usage_input_tokens) AS inputTokens,\n"
            + "toInt64(usage_output_tokens) AS outputTokens,\n"
            + "ifNull(cost, 0.0) AS totalCost,\n"
            + "nullIf(input, '') AS inputJson,\n"
            + "nullIf(output, '') AS outputJson,\n"
            + "CAST(NULL AS Nullable(DateTime64(6))) AS completionStartTime,\n"
            + "ttftMs AS timeToFirstTokenMs,\n"
            + "nullIf(status_message, '') AS statusMessage,\n"
            + "CAST(NULL AS Nullable(String)) AS modelId,\n"
            + "nullIf(model_parameters, '') AS modelParametersJson,\n"
            + "usageDetailsJson AS usageDetailsJson,\n"
            + "costDetailsJson AS costDetailsJson,\n"
            + "nullIf(prompt_name, '') AS promptName,\n"
            + "toInt32OrNull(prompt_version) AS promptVersion,\n"
            + "if(empty(metadata), '{}', metadata) AS metadataJson\n";

    /**
     * 两种 Observation 查询结构共用的派生列。
     *
     * <p>{@code ttftMs}：AgentObs 将 time-to-first-chunk 存储为时长，而旧 schema 暴露的是
     * 绝对时间 completion_start_time。DTO 保留时长字段，在当前 schema 下
     * completionStartTime 为 null。
     *
     * <p>{@code costDetailsJson}：NULL cost 表示“未知”，必须与明确的 0 区分，
     * 因此映射为 '{}' 而不是 {"total":0}。
     */
    /**
     * AgentObs 中存在独立列的 Usage key。
     *
     * <p>列表之外的 key 没有来源列，因此直接省略，不能映射到其他计数器。旧版本曾把所有
     * 未识别 key 回退到 {@code usage_total_tokens}，导致 cache-read、cache-write、reasoning、
     * audio tokens 被静默误报为 total。
     */
    private static final String USAGE_KEYS_WITH_SOURCE =
            "['input','input_tokens','prompt','prompt_tokens',"
            + "'output','output_tokens','completion','completion_tokens',"
            + "'total','total_tokens',"
            + "'cache_read','cache_read_input_tokens','cache_read_tokens',"
            + "'cache_write','cache_creation_input_tokens','cache_write_tokens',"
            + "'reasoning','reasoning_tokens',"
            + "'audio','audio_output','audio_output_tokens']";

    private static final String OBSERVATION_DERIVED =
            "if(time_to_first_chunk_ms IS NULL, CAST(NULL AS Nullable(Int64)), "
            + "CAST(round(time_to_first_chunk_ms) AS Nullable(Int64))) AS ttftMs,\n"
            + "if(cost IS NULL, '{}', concat('{\"total\":', toString(cost), '}')) AS costDetailsJson,\n"
            // 先移除没有来源列的 key，再将剩余 key 映射到各自计数器。usage_present 已记录
            // 生产方实际发送的 key，因此未上报的值会保持缺失，而不是变成 0。
            // multiIf 的兜底分支按结构不会到达，仅用于保证表达式完整，并且有意不回退到 total-token。
            + "concat('{', arrayStringConcat(arrayMap(k -> concat('\"', k, '\":', toString(multiIf("
            + "k IN ('input','input_tokens','prompt','prompt_tokens'), usage_input_tokens, "
            + "k IN ('output','output_tokens','completion','completion_tokens'), usage_output_tokens, "
            + "k IN ('total','total_tokens'), usage_total_tokens, "
            + "k IN ('cache_read','cache_read_input_tokens','cache_read_tokens'), usage_cache_read_tokens, "
            + "k IN ('cache_write','cache_creation_input_tokens','cache_write_tokens'), usage_cache_write_tokens, "
            + "k IN ('reasoning','reasoning_tokens'), usage_reasoning_tokens, "
            + "k IN ('audio','audio_output','audio_output_tokens'), usage_audio_output_tokens, "
            + "0))), arrayFilter(k -> has(" + USAGE_KEYS_WITH_SOURCE + ", k), usage_present)), ','), '}') AS usageDetailsJson\n";

    /** OBSERVATION_DERIVED 生成的字段名，上方列清单会查询这些字段。 */
    static final List<String> OBSERVATION_DERIVED_NAMES =
            Java8Collections.listOf("ttftMs", "costDetailsJson", "usageDetailsJson");

    private TracingSqlProvider() {
    }

    // ------------------------------------------------------------------
    // Observation 查询
    // ------------------------------------------------------------------

    public static String observations(Map<String, Object> parameters) {
        ObservationQuery query = (ObservationQuery) parameters.get("query");
        return "SELECT " + OBSERVATION_LIST_COLUMNS
                + "FROM (SELECT *, " + OBSERVATION_DERIVED + " FROM " + OBSERVATIONS_FINAL
                + " WHERE " + observationPredicate(parameters, query) + ") "
                + "ORDER BY " + observationOrderColumn(query) + " " + query.direction().name()
                + ", id ASC LIMIT #{query.size} OFFSET #{offset}";
    }

    public static String observationCount(Map<String, Object> parameters) {
        ObservationQuery query = (ObservationQuery) parameters.get("query");
        return "SELECT toInt64(count()) FROM " + OBSERVATIONS_FINAL
                + " WHERE " + observationPredicate(parameters, query);
    }

    /** 查询单条 Trace 的 Observation，可选择通过 locator 时间窗口缩小范围。 */
    public static String traceObservations(Map<String, Object> parameters) {
        String traceId = (String) parameters.get("traceId");
        return "SELECT " + OBSERVATION_DETAIL_COLUMNS
                + "FROM (SELECT *, " + OBSERVATION_DERIVED + " FROM " + OBSERVATIONS_FINAL
                + " WHERE trace_id = " + bind(parameters, traceId)
                + locatorNarrowing(parameters) + ") "
                + "ORDER BY startTime ASC, id ASC";
    }

    /**
     * 增加 locator 为当前 Trace 解析出的服务和时间边界。
     *
     * <p>locator 中没有当前 Trace 记录时返回空约束；这通常表示 locator MV 尚未追上写入进度，
     * 调用方会回退到普通 trace_id 查询。locator 一旦存在，其服务和时间边界始终保留；
     * 有界查询返回空结果时不会再发起第二次无界查询。
     */
    private static String locatorNarrowing(Map<String, Object> parameters) {
        StringBuilder clause = new StringBuilder();
        Object service = parameters.get("locatorServiceName");
        if (service instanceof String && !((String) service).isEmpty()) {
            clause.append(" AND service_name = ").append(bind(parameters, service));
        }
        Object min = parameters.get("locatorMinStart");
        String columnZone = locatorColumnTimeZone(parameters);
        if (min != null) {
            clause.append(" AND start_time >= toDateTime64(#{locatorMinStart}, 6, '")
                    .append(columnZone).append("')");
        }
        Object max = parameters.get("locatorMaxEnd");
        if (max != null) {
            clause.append(" AND start_time <= toDateTime64(#{locatorMaxEnd}, 6, '")
                    .append(columnZone).append("')");
        }
        return clause.toString();
    }

    private static String locatorColumnTimeZone(Map<String, Object> parameters) {
        Object value = parameters.get("locatorColumnTimeZone");
        if (value == null || "UTC".equals(value)) return "UTC";
        if ("Asia/Shanghai".equals(value)) return "Asia/Shanghai";
        throw new IllegalArgumentException("Unsupported locator column timezone");
    }

    public static String observationFacets(Map<String, Object> parameters) {
        ObservationQuery query = (ObservationQuery) parameters.get("query");
        ObservationFacet field = (ObservationFacet) parameters.get("field");
        String where = observationPredicate(parameters, query);
        if (field == ObservationFacet.TAG) {
            // Tags 只存在于 Trace 表；只处理当前窗口内存在的 Trace，避免扫描全部 tags。
            return "SELECT value, toInt64(count()) AS count FROM ("
                    + "SELECT arrayJoin(tags) AS value FROM " + TRACES + " FINAL WHERE trace_id IN ("
                    + "SELECT DISTINCT trace_id FROM " + OBSERVATIONS_FINAL + " WHERE " + where + "))"
                    + " WHERE value != '' GROUP BY value ORDER BY count DESC, value ASC LIMIT #{limit}";
        }
        if (field == ObservationFacet.TRACE_NAME) {
            return "SELECT value, toInt64(count()) AS count FROM ("
                    + "SELECT DISTINCT trace_id, trace_name AS value FROM " + TRACES + " FINAL WHERE trace_id IN ("
                    + "SELECT DISTINCT trace_id FROM " + OBSERVATIONS_FINAL + " WHERE " + where + "))"
                    + " WHERE value != '' GROUP BY value ORDER BY count DESC, value ASC LIMIT #{limit}";
        }
        String expression = facetExpression(field);
        return "SELECT toString(" + expression + ") AS value, toInt64(count()) AS count "
                + "FROM " + OBSERVATIONS_FINAL + " WHERE " + where
                + " AND notEmpty(toString(" + expression + ")) "
                + "GROUP BY value ORDER BY count DESC, value ASC LIMIT #{limit}";
    }

    public static String observationPulse(Map<String, Object> parameters) {
        ObservationQuery query = (ObservationQuery) parameters.get("query");
        PulseBucket bucket = (PulseBucket) parameters.get("bucket");
        // 与列表共用同一个条件构建器，确保趋势图与表格筛选结果不会偏离。
        return "SELECT " + bucketExpression(bucket) + " AS timestamp, "
                + "toInt64(count()) AS count, "
                + "sum(ifNull(cost, 0)) AS totalCost, "
                + "toInt64(if(count() = 0, 0, round(avg(duration_ms)))) AS averageLatencyMs "
                + "FROM " + OBSERVATIONS_FINAL + " WHERE " + observationPredicate(parameters, query)
                + " GROUP BY timestamp ORDER BY timestamp ASC";
    }

    /** 查询一页有界 Observation 对应的 Trace 级字段，绝不执行无界 join。 */
    public static String traceLookups(Map<String, Object> parameters) {
        return "SELECT toString(trace_id) AS traceId, trace_name AS traceName, "
                + "toJSONString(tags) AS tagsJson FROM " + TRACES + " FINAL "
                + "WHERE trace_id IN (" + bindIdList(parameters, "traceIds") + ")";
    }

    /** 查询单个 Session 的 Trace，指标由第二阶段的有界查询补充。 */
    public static String tracesBySession() {
        return "SELECT " + TRACE_LIST_COLUMNS + " FROM " + TRACES + " AS t FINAL"
                + " WHERE t.session_id = #{sessionId} ORDER BY timestamp ASC, id ASC LIMIT #{limit}";
    }

    /** 查询单个 Session 的 Observation，复用现有详情映射。 */
    public static String observationsBySession() {
        return "SELECT " + OBSERVATION_DETAIL_COLUMNS
                + "FROM (SELECT *, " + OBSERVATION_DERIVED + " FROM " + OBSERVATIONS_FINAL
                + " WHERE session_id = #{sessionId}) ORDER BY startTime ASC, id ASC LIMIT #{limit}";
    }

    /** 查询单个用户最近的 Trace，指标由第二阶段的有界查询补充。 */
    public static String tracesByUser() {
        return "SELECT " + TRACE_LIST_COLUMNS + " FROM " + TRACES + " AS t FINAL"
                + " WHERE t.user_id = #{userId} ORDER BY timestamp DESC, id ASC LIMIT 200";
    }

    // ------------------------------------------------------------------
    // Trace 查询
    // ------------------------------------------------------------------

    public static String traces(Map<String, Object> parameters) {
        TraceQuery query = (TraceQuery) parameters.get("query");
        boolean windowAggregate = sortNeedsWindowAggregate(query);
        return "SELECT " + (windowAggregate ? TRACE_LIST_COLUMNS_WITH_METRICS : TRACE_LIST_COLUMNS)
                + " FROM " + TRACES + " AS t FINAL"
                + (windowAggregate ? traceMetricsJoin(query) : "")
                + " WHERE " + tracePredicate(parameters, query)
                + " ORDER BY " + traceOrderColumn(query) + " " + query.direction().name() + ", id ASC"
                + " LIMIT #{candidateLimit} OFFSET #{offset}";
    }

    /**
     * 只有按 tokens 或 cost 排序时需要在分页前完成聚合，因为排序键本身就是聚合结果。
     * 其他排序先对 Trace 表分页，再由调用方仅聚合当前页 ID，参见 {@code selectTraceMetrics}。
     *
     * <p>如果反过来始终聚合整个窗口，那么每种排序的每一页都会重新读取并分组范围内的全部
     * Observation；该分支正是为了避免这部分成本。
     */
    public static boolean sortNeedsWindowAggregate(TraceQuery query) {
        return query.sortBy() == TraceQuery.SortBy.TOKENS || query.sortBy() == TraceQuery.SortBy.COST;
    }

    public static String traceCount(Map<String, Object> parameters) {
        TraceQuery query = (TraceQuery) parameters.get("query");
        return "SELECT toInt64(count()) FROM " + TRACES + " AS t FINAL"
                + " WHERE " + tracePredicate(parameters, query);
    }

    /**
     * 当前页各 Trace 的汇总指标。查询范围受当前页 ID 限制，因此无论时间窗口多大都能控制成本。
     */
    public static String traceMetrics(Map<String, Object> parameters) {
        return "SELECT toString(trace_id) AS traceId, "
                + "toInt64(count()) AS observationCount, "
                + "toInt64(sum(if(usage_total_tokens > 0, usage_total_tokens, "
                + "usage_input_tokens + usage_output_tokens))) AS totalTokens, "
                + "sum(ifNull(cost, 0)) AS totalCost "
                + "FROM " + OBSERVATIONS_FINAL
                + " WHERE trace_id IN (" + bindIdList(parameters, "traceIds") + ")"
                // 仅当调用方为单条 Trace 解析出 locator 时缩小范围；Trace 列表路径只传 ID，
                // 不追加 locator 约束。
                + locatorNarrowing(parameters)
                + " GROUP BY trace_id";
    }

    public static String traceDetail() {
        return "SELECT toString(trace_id) AS id, trace_name AS name, start_time AS timestamp, "
                + "nullIf(user_id, '') AS userId, nullIf(session_id, '') AS sessionId, environment, "
                + "if(status_code = 2, 'ERROR', 'SUCCESS') AS status, "
                + "toInt64(round(duration_ms)) AS latencyMs, "
                + "toInt64(0) AS totalTokens, toDecimal64(0, 12) AS totalCost, toInt32(0) AS observationCount, "
                + "toJSONString(tags) AS tagsJson, "
                + "nullIf(trace_input, '') AS inputJson, nullIf(trace_output, '') AS outputJson, "
                + "if(empty(metadata), '{}', metadata) AS metadataJson, "
                // AgentObs 没有 release 列，使用 null 保持 DTO 结构不变。
                + "CAST(NULL AS Nullable(String)) AS release, "
                + "nullIf(version, '') AS version "
                + "FROM " + TRACES + " AS t FINAL WHERE trace_id = #{traceId}";
    }

    /** 单条 Trace 的服务与时间边界，用于缩小详情查询范围。 */
    public static String traceLocator() {
        return "SELECT service_name AS serviceName, "
                + "minMerge(min_start_state) AS minStartTime, "
                + "maxMerge(max_end_state) AS maxEndTime "
                + "FROM " + LOCATOR + " WHERE trace_id = #{traceId} GROUP BY service_name";
    }

    // ------------------------------------------------------------------
    // 列清单
    // ------------------------------------------------------------------

    /** 指标子查询与 Trace 查询使用相同的有界窗口。 */
    private static final String TRACE_LIST_COLUMNS_WITH_METRICS =
            "toString(t.trace_id) AS id,\n"
            + "t.trace_name AS name,\n"
            + "t.start_time AS timestamp,\n"
            + "nullIf(t.user_id, '') AS userId,\n"
            + "nullIf(t.session_id, '') AS sessionId,\n"
            + "t.environment AS environment,\n"
            + "if(t.status_code = 2, 'ERROR', 'SUCCESS') AS status,\n"
            + "toInt64(round(t.duration_ms)) AS latencyMs,\n"
            + "toInt64(ifNull(m.totalTokens, 0)) AS totalTokens,\n"
            + "toDecimal64(ifNull(m.totalCost, 0), 12) AS totalCost,\n"
            + "toInt32(ifNull(m.observationCount, 0)) AS observationCount,\n"
            + "toJSONString(t.tags) AS tagsJson\n";

    /**
     * 仅分页的查询结构不执行聚合，因此指标初始为 0；调用方通过 {@code selectTraceMetrics}
     * 精确查询当前页 ID 后补充指标。
     */
    private static final String TRACE_LIST_COLUMNS =
            "toString(t.trace_id) AS id,\n"
            + "t.trace_name AS name,\n"
            + "t.start_time AS timestamp,\n"
            + "nullIf(t.user_id, '') AS userId,\n"
            + "nullIf(t.session_id, '') AS sessionId,\n"
            + "t.environment AS environment,\n"
            + "if(t.status_code = 2, 'ERROR', 'SUCCESS') AS status,\n"
            + "toInt64(round(t.duration_ms)) AS latencyMs,\n"
            + "toInt64(0) AS totalTokens,\n"
            + "toDecimal64(0, 12) AS totalCost,\n"
            + "toInt32(0) AS observationCount,\n"
            + "toJSONString(t.tags) AS tagsJson\n";

    /**
     * LEFT JOIN 有界的逐 Trace 聚合。聚合限制在查询时间窗口及窗口内可能包含的 Trace 中，
     * 避免退化为对 Observation 表的无界扫描。
     */
    private static String traceMetricsJoin(TraceQuery query) {
        StringBuilder metrics = new StringBuilder();
        metrics.append(" LEFT JOIN (SELECT toString(trace_id) AS traceId, count() AS observationCount, ")
                .append("sum(if(usage_total_tokens > 0, usage_total_tokens, usage_input_tokens + usage_output_tokens)) AS totalTokens, ")
                .append("sum(ifNull(cost, 0)) AS totalCost FROM ").append(OBSERVATIONS_FINAL)
                .append(" WHERE ").append(observationWindow(query)).append(" GROUP BY trace_id) AS m ON m.traceId = id");
        return metrics.toString();
    }

    private static String observationWindow(TraceQuery query) {
        StringBuilder where = new StringBuilder("1 = 1");
        if (query.fromTimestamp() != null) {
            where.append(" AND start_time >= #{query.fromTimestamp}");
        }
        if (query.toTimestamp() != null) {
            where.append(" AND start_time < #{query.toTimestamp}");
        }
        return where.toString();
    }

    // ------------------------------------------------------------------
    // 查询条件
    // ------------------------------------------------------------------

    /**
     * Observation list、count、facets 和 pulse 的统一筛选语义来源。
     * Facet 有意不排除自身维度，以保持前端已经依赖的既有行为。
     */
    private static String observationPredicate(Map<String, Object> parameters, ObservationQuery query) {
        StringBuilder where = new StringBuilder("1 = 1");
        if (hasText(query.search())) {
            appendSearchDsl(where, parameters, query.search());
        }
        if (hasText(query.environment())) where.append(" AND environment = #{query.environment}");
        if (hasText(query.serviceName())) where.append(" AND service_name = #{query.serviceName}");
        // 两个 enum 类字段均使用 upper(...)：AgentObs 可能存储 'span' 或 'SPAN'，普通等值比较
        // 可能静默匹配失败。绑定值已经是大写 enum 常量，因此只需标准化列侧。
        // 这些列不属于排序键，在此使用 upper() 不会造成索引损失。
        if (query.type() != null) where.append(" AND upper(type) = #{query.type}");
        if (query.level() != null) where.append(" AND upper(level) = #{query.level}");
        if (hasText(query.model())) {
            where.append(" AND coalesce(nullIf(model, ''), request_model) = #{query.model}");
        }
        if (hasText(query.traceId())) where.append(" AND trace_id = #{query.traceId}");
        if (hasText(query.tag())) {
            where.append(" AND trace_id IN (SELECT toString(trace_id) FROM ").append(TRACES)
                    .append(" FINAL WHERE has(tags, #{query.tag}))");
        }
        if (query.fromTimestamp() != null) where.append(" AND start_time >= #{query.fromTimestamp}");
        if (query.toTimestamp() != null) where.append(" AND start_time < #{query.toTimestamp}");
        return where.toString();
    }

    private static String tracePredicate(Map<String, Object> parameters, TraceQuery query) {
        StringBuilder where = new StringBuilder("1 = 1");
        if (hasText(query.environment())) where.append(" AND t.environment = #{query.environment}");
        if (hasText(query.userId())) where.append(" AND t.user_id = #{query.userId}");
        if (hasText(query.sessionId())) where.append(" AND t.session_id = #{query.sessionId}");
        if (query.status() != null) {
            where.append(" AND ").append("if(t.status_code = 2, 'ERROR', 'SUCCESS') = #{query.status}");
        }
        if (hasText(query.tag())) where.append(" AND has(t.tags, #{query.tag})");
        if (query.fromTimestamp() != null) where.append(" AND t.start_time >= #{query.fromTimestamp}");
        if (query.toTimestamp() != null) where.append(" AND t.start_time < #{query.toTimestamp}");
        if (hasText(query.search())) {
            appendTraceSearch(where, parameters, query.search());
        }
        return where.toString();
    }

    /**
     * 解析 Trace 接口使用的 DSL。
     *
     * <p>Trace 筛选字段位于 traces 表，字段集合与 observations 表不同：这里没有
     * {@code tokens}/{@code cost}/{@code ttft}，但存在 {@code traceName} 和
     * {@code serviceName}。在增加该解析前，{@code search} 会被整体按字面量匹配，导致
     * {@code name:x} 在 {@code /observations} 正常筛选，却在 {@code /traces} 匹配不到数据。
     */
    private static void appendTraceSearch(
            StringBuilder where, Map<String, Object> parameters, String search) {
        Matcher matcher = SEARCH_TOKEN.matcher(search);
        int end = 0;
        List<String> freeTextParts = new ArrayList<>();
        while (matcher.find()) {
            String before = search.substring(end, matcher.start()).trim();
            if (!before.isEmpty()) freeTextParts.add(before);
            appendTraceToken(where, parameters, matcher.group(2), matcher.group(3), !matcher.group(1).isEmpty());
            end = matcher.end();
        }
        String tail = search.substring(end).trim();
        if (!tail.isEmpty()) freeTextParts.add(tail);
        String freeText = String.join(" ", freeTextParts).replace("\"", "").trim();
        if (!freeText.isEmpty()) {
            String parameter = bind(parameters, freeText);
            where.append(" AND (positionCaseInsensitiveUTF8(t.trace_name, ").append(parameter).append(") > 0")
                    .append(" OR positionCaseInsensitiveUTF8(toString(t.trace_id), ").append(parameter).append(") > 0")
                    .append(" OR positionCaseInsensitiveUTF8(t.user_id, ").append(parameter).append(") > 0")
                    .append(" OR positionCaseInsensitiveUTF8(t.session_id, ").append(parameter).append(") > 0)");
        }
    }

    private static void appendTraceToken(
            StringBuilder where,
            Map<String, Object> parameters,
            String rawField,
            String rawValue,
            boolean negated) {
        String prefix = rawField.contains(".") ? rawField.substring(0, rawField.indexOf('.')) : rawField;
        String field = normalizeField(prefix);
        String nestedKey = nestedKey(rawField);
        String value = unwrap(rawValue);
        String predicate;
        if (field.equals("metadata") && nestedKey != null) {
            predicate = textPredicate("JSONExtractString(t.metadata, " + bind(parameters, nestedKey) + ")",
                    value, parameters);
        } else if (TracingFilterFields.hasNoSource(field, TracingFilterFields.Scope.TRACE)) {
            // Trace 记录中没有对应列。校验器已经记录告警；忽略该 token 可使表达式其余部分继续生效，
            // 而不是让整个请求失败。
            return;
        } else if (field.equals("latency")) {
            predicate = traceNumericPredicate("t.duration_ms", value, parameters);
        } else if (field.equals("starttime")) {
            predicate = traceDateTimePredicate(value, parameters);
        } else if (field.equals("tags")) {
            predicate = traceTagPredicate(value, parameters);
        } else if (field.equals("status")) {
            predicate = traceStatusPredicate(value, parameters);
        } else if (field.equals("has")) {
            predicate = tracePresencePredicate(value);
        } else {
            String expression = traceTextExpression(field);
            if (expression == null) return;
            predicate = textPredicate(expression, value, parameters);
        }
        if (predicate == null || predicate.isEmpty()) return;
        where.append(" AND ");
        if (negated) where.append("NOT (");
        where.append(predicate);
        if (negated) where.append(")");
    }

    private static String traceTextExpression(String field) {
        if ("name".equals(field) || "tracename".equals(field)) return "t.trace_name";
        if ("trace".equals(field) || "traceid".equals(field)) return "toString(t.trace_id)";
        if ("user".equals(field) || "userid".equals(field)) return "t.user_id";
        if ("session".equals(field) || "sessionid".equals(field)) return "t.session_id";
        if ("environment".equals(field) || "env".equals(field)) return "t.environment";
        if ("servicename".equals(field) || "service".equals(field)) return "t.service_name";
        if ("version".equals(field)) return "t.version";
        if ("level".equals(field)) return "t.level";
        if ("statusmessage".equals(field)) return "t.status_message";
        if ("input".equals(field)) return "t.trace_input";
        if ("output".equals(field)) return "t.trace_output";
        return null;
    }

    private static String traceTagPredicate(String rawValue, Map<String, Object> parameters) {
        String normalized = rawValue.trim();
        List<String> predicates = new ArrayList<>();
        if (normalized.matches("(?i).*\\s+AND\\s+.*")) {
            for (String item : normalized.split("(?i)\\s+AND\\s+")) {
                predicates.add("has(t.tags, " + bind(parameters, stripEquals(item.trim())) + ")");
            }
            return "(" + String.join(" AND ", predicates) + ")";
        }
        for (String item : splitAlternatives(rawValue)) {
            predicates.add("has(t.tags, " + bind(parameters, stripEquals(item.trim())) + ")");
        }
        return predicates.size() == 1 ? predicates.get(0) : "(" + String.join(" OR ", predicates) + ")";
    }

    /** Trace status 与输出列采用完全相同的规则从 status_code 派生。 */
    private static String traceStatusPredicate(String rawValue, Map<String, Object> parameters) {
        String value = unwrap(rawValue).trim();
        boolean negatedExact = value.startsWith("=");
        if (negatedExact) value = value.substring(1);
        if (value.equalsIgnoreCase("ERROR") || value.equalsIgnoreCase("SUCCESS")) {
            return "if(t.status_code = 2, 'ERROR', 'SUCCESS') = "
                    + bind(parameters, value.toUpperCase());
        }
        // AgentObs 的 end_time 不可为空，因此每条 Trace 都是终态，无法表达 RUNNING。
        // 此时应匹配不到数据，而不是等同于“不筛选”。
        return "0 = 1";
    }

    private static String tracePresencePredicate(String rawValue) {
        String field = normalizeField(unwrap(rawValue));
        if ("traceid".equals(field) || "trace".equals(field)) return "toString(t.trace_id) != ''";
        if ("session".equals(field) || "sessionid".equals(field)) return "t.session_id != ''";
        if ("user".equals(field) || "userid".equals(field)) return "t.user_id != ''";
        if ("tracename".equals(field) || "name".equals(field)) return "t.trace_name != ''";
        if ("version".equals(field)) return "t.version != ''";
        if ("statusmessage".equals(field)) return "t.status_message != ''";
        if ("input".equals(field)) return "t.trace_input != ''";
        if ("output".equals(field)) return "t.trace_output != ''";
        if ("tags".equals(field)) return "notEmpty(t.tags)";
        return null;
    }

    private static String traceNumericPredicate(String expression, String rawValue, Map<String, Object> parameters) {
        String operator = "=";
        String value = rawValue;
        for (String candidate : Java8Collections.listOf(">=", "<=", ">", "<", "=")) {
            if (value.startsWith(candidate)) {
                operator = candidate;
                value = value.substring(candidate.length());
                break;
            }
        }
        try {
            // latency 输入单位为秒，比较时按其他路径的约定换算为毫秒。
            return expression + " " + operator + " " + bind(parameters, Math.round(Double.parseDouble(value) * 1000));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String traceDateTimePredicate(String rawValue, Map<String, Object> parameters) {
        String operator = "=";
        String value = rawValue;
        for (String candidate : Java8Collections.listOf(">=", "<=", ">", "<", "=")) {
            if (value.startsWith(candidate)) {
                operator = candidate;
                value = value.substring(candidate.length());
                break;
            }
        }
        try {
            Instant instant = value.length() == 10 ? Instant.parse(value + "T00:00:00Z") : Instant.parse(value);
            return "t.start_time " + operator + " " + bind(parameters, instant);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void appendSearchDsl(
            StringBuilder where, Map<String, Object> parameters, String search) {
        Matcher matcher = SEARCH_TOKEN.matcher(search);
        int end = 0;
        List<String> freeTextParts = new ArrayList<>();
        while (matcher.find()) {
            String before = search.substring(end, matcher.start()).trim();
            if (!before.isEmpty()) freeTextParts.add(before);
            appendDslToken(where, parameters, matcher.group(2), matcher.group(3), !matcher.group(1).isEmpty());
            end = matcher.end();
        }
        String tail = search.substring(end).trim();
        if (!tail.isEmpty()) freeTextParts.add(tail);
        String freeText = String.join(" ", freeTextParts).replace("\"", "").trim();
        if (!freeText.isEmpty()) {
            String parameter = bind(parameters, freeText);
            where.append(" AND (positionCaseInsensitiveUTF8(name, ").append(parameter).append(") > 0")
                    .append(" OR positionCaseInsensitiveUTF8(toString(span_id), ").append(parameter).append(") > 0")
                    .append(" OR positionCaseInsensitiveUTF8(toString(trace_id), ").append(parameter).append(") > 0")
                    .append(" OR positionCaseInsensitiveUTF8(coalesce(nullIf(model, ''), request_model), ")
                    .append(parameter).append(") > 0")
                    .append(" OR positionCaseInsensitiveUTF8(input, ").append(parameter).append(") > 0")
                    .append(" OR positionCaseInsensitiveUTF8(output, ").append(parameter).append(") > 0")
                    .append(" OR positionCaseInsensitiveUTF8(metadata, ").append(parameter).append(") > 0)");
        }
    }

    private static void appendDslToken(
            StringBuilder where,
            Map<String, Object> parameters,
            String rawField,
            String rawValue,
            boolean negated) {
        String prefix = rawField.contains(".") ? rawField.substring(0, rawField.indexOf('.')) : rawField;
        String field = normalizeField(prefix);
        String nestedKey = nestedKey(rawField);
        String value = unwrap(rawValue);
        String predicate;
        if (field.equals("metadata") && nestedKey != null) {
            predicate = metadataPredicate(nestedKey, value, parameters);
        } else if (TracingFilterFields.hasNoSource(field)) {
            // AgentObs schema 没有来源列。忽略该 token 能让多 token 查询的其他部分继续工作，
            // 而不是让整个请求失败；校验器已经记录 WARN，因此不会静默忽略。
            return;
        } else if (NUMERIC_FIELDS.contains(field)) {
            predicate = numericPredicate(field, value, parameters);
        } else if (field.equals("starttime")) {
            predicate = dateTimePredicate(value, parameters);
        } else if (field.equals("root") || field.equals("isrootobservation")) {
            predicate = rootPredicate(value);
        } else if (field.equals("tags")) {
            predicate = tagPredicate(value, parameters);
        } else if (field.equals("tracename")) {
            predicate = traceNamePredicate(value, parameters);
        } else if (field.equals("has")) {
            predicate = presencePredicate(value);
        } else {
            String expression = textExpression(field);
            if (expression == null) return;
            predicate = textPredicate(expression, value, parameters);
        }
        if (predicate == null || predicate.isEmpty()) return;
        where.append(" AND ");
        if (negated) where.append("NOT (");
        where.append(predicate);
        if (negated) where.append(")");
    }

    private static String textPredicate(String expression, String rawValue, Map<String, Object> parameters) {
        List<String> values = splitAlternatives(rawValue);
        List<String> predicates = new ArrayList<>();
        for (String item : values) {
            String value = item.trim();
            boolean exact = value.startsWith("=");
            if (exact) value = value.substring(1);
            boolean startsWildcard = value.startsWith("*");
            boolean endsWildcard = value.endsWith("*") && value.length() > 1;
            value = value.replaceAll("^\\*|\\*$", "");
            // enum 比较的列侧已转为大写（参见 ENUM_UPPER_TYPE），因此绑定值也必须转为大写；
            // 否则 type:=span 会执行 upper(type)='span' 并匹配不到数据。其他字段保持原有大小写语义。
            if (isEnumUpperExpression(expression)) {
                value = value.toUpperCase(Locale.ROOT);
            }
            String parameter = bind(parameters, value);
            if (exact) predicates.add("toString(" + expression + ") = " + parameter);
            else if (startsWildcard && !endsWildcard) predicates.add("endsWith(lowerUTF8(toString(" + expression + ")), lowerUTF8(" + parameter + "))");
            else if (!startsWildcard && endsWildcard) predicates.add("startsWith(lowerUTF8(toString(" + expression + ")), lowerUTF8(" + parameter + "))");
            else predicates.add("positionCaseInsensitiveUTF8(toString(" + expression + "), " + parameter + ") > 0");
        }
        return predicates.size() == 1 ? predicates.get(0) : "(" + String.join(" OR ", predicates) + ")";
    }

    private static boolean isEnumUpperExpression(String expression) {
        return ENUM_UPPER_TYPE.equals(expression) || ENUM_UPPER_LEVEL.equals(expression);
    }

    /**
     * Trace name 位于 Trace 表，因此该筛选转换为 trace-id 子查询，而不是引用
     * observations 表中不存在的列。
     *
     * <p>支持与旧 Provider 相同的形式：{@code traceName:=exact}、{@code traceName:prefix*}、
     * {@code traceName:*suffix} 以及普通子串匹配。list、count、facet 和 pulse 均通过
     * {@link #observationPredicate} 构建条件，因此该筛选会同时作用于四类查询。
     */
    private static String traceNamePredicate(String rawValue, Map<String, Object> parameters) {
        List<String> values = splitAlternatives(rawValue);
        List<String> predicates = new ArrayList<>();
        for (String item : values) {
            String value = item.trim();
            boolean exact = value.startsWith("=");
            if (exact) value = value.substring(1);
            boolean startsWildcard = value.startsWith("*");
            boolean endsWildcard = value.endsWith("*") && value.length() > 1;
            value = value.replaceAll("^\\*|\\*$", "");
            String parameter = bind(parameters, value);
            if (exact) predicates.add("trace_name = " + parameter);
            else if (startsWildcard && !endsWildcard) predicates.add("endsWith(lowerUTF8(trace_name), lowerUTF8(" + parameter + "))");
            else if (!startsWildcard && endsWildcard) predicates.add("startsWith(lowerUTF8(trace_name), lowerUTF8(" + parameter + "))");
            else predicates.add("positionCaseInsensitiveUTF8(trace_name, " + parameter + ") > 0");
        }
        String combined = predicates.size() == 1 ? predicates.get(0)
                : "(" + String.join(" OR ", predicates) + ")";
        return "trace_id IN (SELECT toString(trace_id) FROM " + TRACES + " FINAL WHERE " + combined + ")";
    }

    /** Tags 位于 Trace 表，因此 tag 筛选转换为 trace-id 子查询。 */
    private static String tagPredicate(String rawValue, Map<String, Object> parameters) {
        String normalized = rawValue.trim();
        List<String> predicates = new ArrayList<>();
        if (normalized.matches("(?i).*\\s+AND\\s+.*")) {
            for (String item : normalized.split("(?i)\\s+AND\\s+")) {
                predicates.add("has(tags, " + bind(parameters, stripEquals(item.trim())) + ")");
            }
            return "trace_id IN (SELECT toString(trace_id) FROM " + TRACES + " FINAL WHERE "
                    + String.join(" AND ", predicates) + ")";
        }
        for (String item : splitAlternatives(rawValue)) {
            predicates.add("has(tags, " + bind(parameters, stripEquals(item.trim())) + ")");
        }
        String combined = predicates.size() == 1 ? predicates.get(0) : "(" + String.join(" OR ", predicates) + ")";
        return "trace_id IN (SELECT toString(trace_id) FROM " + TRACES + " FINAL WHERE " + combined + ")";
    }

    private static String numericPredicate(String field, String rawValue, Map<String, Object> parameters) {
        String operator = "=";
        String value = rawValue;
        for (String candidate : Java8Collections.listOf(">=", "<=", ">", "<", "=")) {
            if (value.startsWith(candidate)) {
                operator = candidate;
                value = value.substring(candidate.length());
                break;
            }
        }
        try {
            Object bound;
            String expression;
            switch (field) {
                case "latency":
                    expression = "duration_ms";
                    bound = Math.round(Double.parseDouble(value) * 1000);
                    break;
                case "ttft":
                    expression = "time_to_first_chunk_ms";
                    bound = Math.round(Double.parseDouble(value) * 1000);
                    break;
                case "tokens":
                    expression = "if(usage_total_tokens > 0, usage_total_tokens, usage_input_tokens + usage_output_tokens)";
                    bound = Long.parseLong(value);
                    break;
                case "inputtokens":
                    expression = "usage_input_tokens";
                    bound = Long.parseLong(value);
                    break;
                case "outputtokens":
                    expression = "usage_output_tokens";
                    bound = Long.parseLong(value);
                    break;
                case "tooldefinitions":
                    // 使用 JSONLength，而不是 length(JSONExtractKeys(...))：后者在目标版本 21.8
                    // 中不存在（已通过 clickhouse-server:21.8 验证，会抛出
                    // "Received exception from server"）。JSONLength 的兼容时间更长，
                    // 对象返回 key 数量、数组返回元素数量，均符合此筛选条件的语义。
                    expression = "JSONLength(tool_definitions)";
                    bound = Long.parseLong(value);
                    break;
                case "cost":
                    expression = "cost";
                    bound = new BigDecimal(value);
                    break;
                default:
                    return null;
            }
            return expression + " " + operator + " " + bind(parameters, bound);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String dateTimePredicate(String rawValue, Map<String, Object> parameters) {
        String operator = "=";
        String value = rawValue;
        for (String candidate : Java8Collections.listOf(">=", "<=", ">", "<", "=")) {
            if (value.startsWith(candidate)) {
                operator = candidate;
                value = value.substring(candidate.length());
                break;
            }
        }
        try {
            Instant instant = value.length() == 10 ? Instant.parse(value + "T00:00:00Z") : Instant.parse(value);
            return "start_time " + operator + " " + bind(parameters, instant);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 当前 schema 中 metadata 是 JSON 字符串，因此使用 JSONExtractString 查询 key。 */
    private static String metadataPredicate(String key, String rawValue, Map<String, Object> parameters) {
        String keyParameter = bind(parameters, key);
        String expression = "JSONExtractString(metadata, " + keyParameter + ")";
        return textPredicate(expression, rawValue, parameters);
    }

    private static String presencePredicate(String value) {
        String field = normalizeField(value);
        if ("endtime".equals(field)) return "end_time IS NOT NULL";
        if ("session".equals(field) || "sessionid".equals(field)) return "session_id != ''";
        if ("user".equals(field) || "userid".equals(field)) return "user_id != ''";
        if ("model".equals(field)) return "coalesce(nullIf(model, ''), request_model) != ''";
        if ("prompt".equals(field) || "promptname".equals(field)) return "prompt_name != ''";
        if ("status".equals(field) || "statusmessage".equals(field)) return "status_message != ''";
        if ("input".equals(field)) return "input != ''";
        if ("output".equals(field)) return "output != ''";
        if ("version".equals(field)) return "version != ''";
        // AgentObs 不存在 release 字段，按“缺失”处理而不是抛出异常。
        return null;
    }

    private static String rootPredicate(String value) {
        boolean root = value.equalsIgnoreCase("true") || value.equals("1");
        return root ? "(parent_span_id = '' OR is_product_root)"
                : "(parent_span_id != '' AND NOT is_product_root)";
    }

    private static String textExpression(String field) {
        if ("environment".equals(field) || "env".equals(field)) return "environment";
        if ("servicename".equals(field) || "service".equals(field)) return "service_name";
        // enum 类字段按大写形式比较，使 DSL 精确匹配（type:=SPAN）能够找到存储为 'span' 的记录。
        // textPredicate 会同步将绑定值转为大写；通配符和模糊匹配本身不区分大小写，不受影响。
        if ("type".equals(field)) return ENUM_UPPER_TYPE;
        if ("level".equals(field)) return ENUM_UPPER_LEVEL;
        if ("name".equals(field)) return "name";
        if ("model".equals(field)) return "coalesce(nullIf(model, ''), request_model)";
        if ("prompt".equals(field) || "promptname".equals(field)) return "prompt_name";
        if ("trace".equals(field) || "traceid".equals(field)) return "trace_id";
        if ("session".equals(field) || "sessionid".equals(field)) return "session_id";
        if ("user".equals(field) || "userid".equals(field)) return "user_id";
        if ("status".equals(field) || "statusmessage".equals(field)) return "status_message";
        if ("version".equals(field)) return "version";
        if ("input".equals(field)) return "input";
        if ("output".equals(field)) return "output";
        return null;
    }

    private static String facetExpression(ObservationFacet field) {
        switch (field) {
            case ENVIRONMENT: return "environment";
            case SERVICE_NAME: return "service_name";
            // 按大写形式分组，使 'span' 和 'SPAN' 合并为一个 facet，而不是显示为仅大小写不同的两个选项。
            case TYPE: return ENUM_UPPER_TYPE;
            case ROOT: return "if(parent_span_id = '' OR is_product_root, 'true', 'false')";
            // 与 TYPE 使用相同的大小写归一化规则，原因相同。
            case LEVEL: return ENUM_UPPER_LEVEL;
            case NAME: return "name";
            case MODEL: return "coalesce(nullIf(model, ''), request_model)";
            case PROMPT_NAME: return "prompt_name";
            case USER_ID: return "user_id";
            case SESSION_ID: return "session_id";
            case TRACE_NAME:
            case TAG:
                throw new IllegalArgumentException(field + " requires the Trace table lookup path");
            default: throw new IllegalArgumentException("Unsupported facet");
        }
    }

    private static String bucketExpression(PulseBucket bucket) {
        switch (bucket) {
            case HOUR: return "toStartOfHour(start_time)";
            case WEEK: return "toStartOfWeek(start_time)";
            case DAY:
            default: return "toStartOfDay(start_time)";
        }
    }

    private static String observationOrderColumn(ObservationQuery query) {
        switch (query.sortBy()) {
            case TIMESTAMP: return "startTime";
            case LATENCY: return "latencyMs";
            case TOKENS: return "inputTokens + outputTokens";
            case COST: return "totalCost";
            default: throw new IllegalArgumentException("Unsupported observation sort");
        }
    }

    private static String traceOrderColumn(TraceQuery query) {
        switch (query.sortBy()) {
            case LATENCY: return "latencyMs";
            case TOKENS: return "totalTokens";
            case COST: return "totalCost";
            case TIMESTAMP: return "timestamp";
            default: throw new IllegalArgumentException("Unsupported trace sort");
        }
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private static final List<String> NUMERIC_FIELDS = Java8Collections.listOf(
            "latency", "ttft", "tokens", "inputtokens", "outputtokens", "tooldefinitions", "cost");

    private static String normalizeField(String field) {
        return field.toLowerCase().replace("_", "").replace("-", "");
    }

    private static String nestedKey(String field) {
        int dot = field.indexOf('.');
        if (dot < 0 || dot == field.length() - 1) return null;
        String key = field.substring(dot + 1);
        if (key.startsWith("\"") && key.endsWith("\"") && key.length() > 1) {
            key = key.substring(1, key.length() - 1);
        }
        return key;
    }

    private static String unwrap(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("(") && value.endsWith(")"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String stripEquals(String value) {
        return value.startsWith("=") ? value.substring(1) : value;
    }

    private static List<String> splitAlternatives(String value) {
        return Java8Collections.listOf(value.split("\\s+OR\\s+"));
    }

    private static String bind(Map<String, Object> parameters, Object value) {
        String key = "dsl" + parameters.keySet().stream().filter(name -> name.startsWith("dsl")).count();
        parameters.put(key, value);
        return "#{" + key + "}";
    }

    /**
     * 将 ID 列表展开为逐项绑定的占位符。
     *
     * <p>基于注解的 Provider 无法表达 MyBatis {@code <foreach>}，将列表绑定到单个占位符
     * 也不会生成有效 SQL。因此每个 ID 都独立绑定而不是字符串插值，恶意构造的 ID 无法修改语句。
     * 空列表不得生成 "IN ()"，而是转换为永不匹配的条件。
     */
    private static String bindIdList(Map<String, Object> parameters, String key) {
        Object raw = parameters.get(key);
        StringBuilder in = new StringBuilder();
        if (raw instanceof Iterable) {
            int index = 0;
            for (Object id : (Iterable<?>) raw) {
                if (id == null) {
                    continue;
                }
                String placeholder = key + index++;
                parameters.put(placeholder, String.valueOf(id));
                if (in.length() > 0) {
                    in.append(", ");
                }
                in.append("#{").append(placeholder).append("}");
            }
        }
        return in.length() == 0 ? "CAST(NULL AS Nullable(String))" : in.toString();
    }

    private static boolean hasText(Object value) {
        return value instanceof String && !((String) value).trim().isEmpty();
    }
}
