package com.icbc.aiops.langfuse.mapper;

import java.util.Map;

/**
 * 面向 AgentObs ClickHouse schema 的跨 Trace 聚合及 score 查询。
 *
 * <p>Trace 和 Observation 明细查询位于 {@link TracingSqlProvider}。本 Provider 有意将两个
 * Distributed 事实表拆分到不同语句中，确保 Dashboard、Session 和 User 查询不需要无界跨表 join。
 */
public final class ObservabilitySqlProvider {

    private static final String TRACES = "default.hmp_agentobs_traces_all FINAL";
    private static final String OBSERVATIONS = "default.hmp_agentobs_observations_all FINAL";
    private static final String SCORES = "default.hmp_agentobs_scores_all FINAL";
    private static final String TOTAL_TOKENS_EXPRESSION =
            "if(usage_total_tokens > 0, usage_total_tokens, usage_input_tokens + usage_output_tokens)";
    private static final String TOTAL_COST_EXPRESSION = "ifNull(cost, 0)";

    private ObservabilitySqlProvider() {
    }

    public static String traceSummaryStats() {
        return "SELECT toInt64(count()) AS traceCount, "
                + "toInt64(countIf(status_code = 2)) AS errorCount, "
                + "toInt64(if(count() = 0, 0, round(avg(duration_ms)))) AS averageLatencyMs "
                + "FROM " + TRACES;
    }

    public static String observationSummaryStats() {
        return "SELECT toInt64(count()) AS observationCount, "
                + "toInt64(sum(" + TOTAL_TOKENS_EXPRESSION + ")) AS totalTokens, "
                + "sum(" + TOTAL_COST_EXPRESSION + ") AS totalCost "
                + "FROM " + OBSERVATIONS;
    }

    public static String traceMetricTimeSeries() {
        return "SELECT toStartOfDay(start_time) AS timestamp, "
                + "toInt64(count()) AS traceCount, "
                + "toInt64(countIf(status_code = 2)) AS errorCount, "
                + "toInt64(if(count() = 0, 0, round(avg(duration_ms)))) AS averageLatencyMs "
                + "FROM " + TRACES + " "
                + "WHERE start_time >= toStartOfDay(now()) - INTERVAL 6 DAY "
                + "GROUP BY timestamp ORDER BY timestamp ASC";
    }

    public static String observationMetricTimeSeries() {
        return "SELECT toStartOfDay(start_time) AS timestamp, "
                + "toInt64(count()) AS observationCount, "
                + "toInt64(sum(" + TOTAL_TOKENS_EXPRESSION + ")) AS totalTokens, "
                + "sum(" + TOTAL_COST_EXPRESSION + ") AS totalCost "
                + "FROM " + OBSERVATIONS + " "
                + "WHERE start_time >= toStartOfDay(now()) - INTERVAL 6 DAY "
                + "GROUP BY timestamp ORDER BY timestamp ASC";
    }

    public static String traceScores() {
        return "SELECT score_id AS id,\n"
                + "       trace_id AS traceId,\n"
                + "       nullIf(observation_id, '') AS observationId,\n"
                + "       name,\n"
                + "       data_type AS dataType,\n"
                + "       value AS numericValue,\n"
                + "       nullIf(value_string, '') AS stringValue,\n"
                + "       source,\n"
                + "       nullIf(comment, '') AS comment,\n"
                + "       created_at AS createdAt\n"
                + "FROM " + SCORES + "\n"
                + "WHERE trace_id = #{traceId}\n"
                + "ORDER BY created_at ASC, score_id ASC";
    }

    public static String sessions(Map<String, Object> parameters) {
        return sessionQuery(hasText(parameters.get("search")))
                + " ORDER BY createdAt DESC LIMIT #{size} OFFSET #{offset}";
    }

    public static String sessionCount(Map<String, Object> parameters) {
        return "SELECT count() FROM (" + sessionQuery(hasText(parameters.get("search"))) + ")";
    }

    public static String session() {
        return sessionAggregate("session_id = #{sessionId}");
    }

    public static String users(Map<String, Object> parameters) {
        return userAggregate(hasText(parameters.get("search")),
                hasText(parameters.get("environment")), false)
                + " ORDER BY traceCount DESC, lastEvent DESC LIMIT #{size} OFFSET #{offset}";
    }

    public static String userCount(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("SELECT toInt64(uniqExact(user_id)) FROM ")
                .append(OBSERVATIONS).append(" WHERE user_id != ''");
        if (hasText(parameters.get("search"))) {
            sql.append(" AND positionCaseInsensitiveUTF8(user_id, #{search}) > 0");
        }
        if (hasText(parameters.get("environment"))) {
            sql.append(" AND environment = #{environment}");
        }
        return sql.toString();
    }

    public static String user() {
        return userAggregate(false, false, true);
    }

    public static String userSessions() {
        return sessionAggregate("user_id = #{userId} AND session_id != ''")
                + " ORDER BY createdAt DESC LIMIT 200";
    }

    private static String sessionQuery(boolean search) {
        String sql = sessionAggregate("session_id != ''");
        if (search) {
            sql += " HAVING positionCaseInsensitiveUTF8(id, #{search}) > 0"
                    + " OR positionCaseInsensitiveUTF8(ifNull(userId, ''), #{search}) > 0";
        }
        return sql;
    }

    private static String sessionAggregate(String where) {
        return "SELECT session_id AS id, "
                + "min(start_time) AS createdAt, "
                + "nullIf(argMaxIf(user_id, start_time, user_id != ''), '') AS userId, "
                + "toInt64(uniqExact(trace_id)) AS traceCount, "
                + "toInt64(count()) AS observationCount, "
                + "toInt64(sum(" + TOTAL_TOKENS_EXPRESSION + ")) AS totalTokens, "
                + "sum(" + TOTAL_COST_EXPRESSION + ") AS totalCost, "
                + "greatest(toInt64(0), toUnixTimestamp64Milli(max(end_time)) "
                + "- toUnixTimestamp64Milli(min(start_time))) AS durationMs "
                + "FROM " + OBSERVATIONS + " WHERE " + where + " GROUP BY session_id";
    }

    private static String userAggregate(boolean search, boolean environment, boolean exactUser) {
        StringBuilder sql = new StringBuilder("SELECT user_id AS id, ")
                .append("argMaxIf(environment, start_time, environment != '') AS environment, ")
                .append("min(start_time) AS firstEvent, max(start_time) AS lastEvent, ")
                .append("toInt64(uniqExact(trace_id)) AS traceCount, ")
                .append("toInt64(count()) AS observationCount, ")
                .append("toInt64(sum(").append(TOTAL_TOKENS_EXPRESSION).append(")) AS totalTokens, ")
                .append("sum(").append(TOTAL_COST_EXPRESSION).append(") AS totalCost ")
                .append("FROM ");
        if (environment) {
            // 将基础列筛选放在内层查询。ClickHouse 21.8 会把名为 environment 的 SELECT alias
            // 替换进 WHERE，导致上方 argMaxIf 聚合变成非法的 WHERE 聚合表达式。
            sql.append("(SELECT * FROM ").append(OBSERVATIONS)
                    .append(" WHERE environment = #{environment}) ");
        } else {
            sql.append(OBSERVATIONS).append(" ");
        }
        sql.append("WHERE user_id != ''");
        if (search) {
            sql.append(" AND positionCaseInsensitiveUTF8(user_id, #{search}) > 0");
        }
        if (exactUser) {
            sql.append(" AND user_id = #{userId}");
        }
        return sql.append(" GROUP BY user_id").toString();
    }

    private static boolean hasText(Object value) {
        return value instanceof String && !((String) value).trim().isEmpty();
    }
}
