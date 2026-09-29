package com.icbc.aiops.langfuse.mapper;

import com.icbc.aiops.langfuse.service.WidgetMetricQuery;
import com.icbc.aiops.langfuse.service.WidgetMetricFilter;
import java.util.Collection;
import java.util.Map;

/** 仅使用服务层已校验的 enum 类值构建 AgentObs 指标 SQL。 */
public final class WidgetMetricSqlProvider {
    private static final String TRACES = "default.hmp_agentobs_traces_all FINAL";
    private static final String OBSERVATIONS = "default.hmp_agentobs_observations_all FINAL";
    private static final String TOTAL_TOKENS_EXPRESSION =
            "if(usage_total_tokens > 0, usage_total_tokens, usage_input_tokens + usage_output_tokens)";

    private WidgetMetricSqlProvider() { }

    public static String metric(Map<String, Object> parameters) {
        WidgetMetricQuery query = (WidgetMetricQuery) parameters.get("query");
        boolean traceRollup = usesTraceRollup(query);
        boolean traceFact = usesTraceFact(query);
        String source;
        if (traceRollup) {
            source = traceRollupSource();
        } else if (traceFact) {
            source = TRACES;
        } else {
            source = OBSERVATIONS;
        }
        String bucket = query.timeSeries()
                ? "toStartOfDay(start_time)"
                : "CAST(NULL AS Nullable(DateTime64(3)))";
        String dimension = dimension(query, traceFact || traceRollup);
        String value = aggregate(query, traceFact, traceRollup);
        StringBuilder sql = new StringBuilder("SELECT ").append(bucket).append(" AS bucket, ")
                .append(dimension).append(" AS dimension, toDecimal64(").append(value)
                .append(", 12) AS value FROM ").append(source)
                .append(traceRollup ? " WHERE 1 = 1" : " WHERE start_time >= #{query.fromTimestamp}"
                        + " AND start_time < #{query.toTimestamp}");
        appendFilters(sql, parameters, query, traceFact || traceRollup);
        if (query.timeSeries() || query.dimension() != null) {
            sql.append(" GROUP BY ");
            if (query.timeSeries()) sql.append("bucket");
            if (query.timeSeries() && query.dimension() != null) sql.append(", ");
            if (query.dimension() != null) sql.append("dimension");
        }
        sql.append(" ORDER BY bucket ASC NULLS FIRST, value DESC LIMIT 500");
        return sql.toString();
    }

    /** 每条 Trace 一行，确保 avg/percentiles/count 不会按 Span 粒度计算。 */
    private static String traceRollupSource() {
        return "(SELECT toString(t.trace_id) AS trace_id, t.start_time AS start_time, "
                + "t.trace_name AS trace_name, t.environment AS environment, t.user_id AS user_id, "
                + "ifNull(m.total_tokens, 0) AS total_tokens, ifNull(m.total_cost, 0) AS total_cost "
                + "FROM default.hmp_agentobs_traces_all AS t FINAL LEFT JOIN (SELECT toString(trace_id) AS trace_id, "
                + "sum(" + TOTAL_TOKENS_EXPRESSION + ") AS total_tokens, "
                + "sum(ifNull(cost, 0)) AS total_cost FROM " + OBSERVATIONS
                + " WHERE start_time >= #{query.fromTimestamp} AND start_time < #{query.toTimestamp} "
                + "GROUP BY trace_id) AS m ON m.trace_id = toString(t.trace_id) "
                + "WHERE t.start_time >= #{query.fromTimestamp} AND t.start_time < #{query.toTimestamp}) "
                + "AS trace_facts";
    }

    private static boolean usesTraceFact(WidgetMetricQuery query) {
        return "TRACES".equals(query.view())
                && ("count".equals(query.measure()) || "latency".equals(query.measure()));
    }

    private static boolean usesTraceRollup(WidgetMetricQuery query) {
        return "TRACES".equals(query.view())
                && ("totalTokens".equals(query.measure()) || "totalCost".equals(query.measure()));
    }

    private static String dimension(WidgetMetricQuery query, boolean traceView) {
        if (query.dimension() == null) return "CAST(NULL AS Nullable(String))";
        return field(query.dimension(), traceView);
    }

    private static String field(String name, boolean traceView) {
        if ("name".equals(name)) return traceView ? "trace_name" : "name";
        if ("environment".equals(name)) return "environment";
        if ("userId".equals(name)) return "user_id";
        if ("providedModelName".equals(name)) return "coalesce(nullIf(model, ''), request_model)";
        if ("level".equals(name)) return "level";
        throw new IllegalArgumentException("Unsupported validated widget field");
    }

    private static String aggregate(WidgetMetricQuery query, boolean traceFact, boolean traceRollup) {
        String expression = measure(query, traceFact, traceRollup);
        String aggregation = query.aggregation();
        if ("count".equals(aggregation)) return "count()";
        if ("sum".equals(aggregation)) return "sum(" + expression + ")";
        if ("avg".equals(aggregation)) return "avg(" + expression + ")";
        if ("min".equals(aggregation)) return "min(" + expression + ")";
        if ("max".equals(aggregation)) return "max(" + expression + ")";
        if ("p50".equals(aggregation)) return "quantile(0.50)(" + expression + ")";
        if ("p75".equals(aggregation)) return "quantile(0.75)(" + expression + ")";
        if ("p90".equals(aggregation)) return "quantile(0.90)(" + expression + ")";
        if ("p95".equals(aggregation)) return "quantile(0.95)(" + expression + ")";
        if ("p99".equals(aggregation)) return "quantile(0.99)(" + expression + ")";
        throw new IllegalArgumentException("Unsupported validated aggregation");
    }

    private static String measure(WidgetMetricQuery query, boolean traceFact, boolean traceRollup) {
        String measure = query.measure();
        if ("count".equals(measure)) return "1";
        if ("latency".equals(measure)) return "duration_ms";
        if (traceRollup && "totalCost".equals(measure)) return "total_cost";
        if (traceRollup && "totalTokens".equals(measure)) return "total_tokens";
        if (!traceFact && "totalCost".equals(measure)) return "ifNull(cost, 0)";
        if (!traceFact && "totalTokens".equals(measure)) return TOTAL_TOKENS_EXPRESSION;
        throw new IllegalArgumentException("Unsupported validated measure");
    }

    private static void appendFilters(StringBuilder sql, Map<String, Object> parameters,
            WidgetMetricQuery query, boolean traceView) {
        int index = 0;
        for (WidgetMetricFilter filter : query.filters()) {
            sql.append(" AND ").append(predicate(filter, parameters, index++, traceView));
        }
    }

    private static String predicate(WidgetMetricFilter filter, Map<String, Object> parameters,
            int index, boolean traceView) {
        String expression = field(filter.column(), traceView);
        String operator = filter.operator();
        if ("null".equals(filter.type())) {
            return ("is not null".equals(operator) ? "isNotNull(" : "isNull(")
                    + expression + ")";
        }
        if ("stringOptions".equals(filter.type()) || "categoryOptions".equals(filter.type())) {
            Collection<?> values = (Collection<?>) filter.value();
            StringBuilder placeholders = new StringBuilder();
            int valueIndex = 0;
            for (Object value : values) {
                if (valueIndex > 0) placeholders.append(", ");
                String key = "widgetFilter" + index + "_" + valueIndex++;
                parameters.put(key, String.valueOf(value));
                placeholders.append("#{").append(key).append("}");
            }
            return expression + ("none of".equals(operator) ? " NOT IN (" : " IN (")
                    + placeholders + ")";
        }
        String key = "widgetFilter" + index;
        parameters.put(key, filter.value());
        String placeholder = "#{" + key + "}";
        if ("=".equals(operator)) return expression + " = " + placeholder;
        if ("contains".equals(operator)) return "positionCaseInsensitiveUTF8(" + expression + ", " + placeholder + ") > 0";
        if ("does not contain".equals(operator)) return "positionCaseInsensitiveUTF8(" + expression + ", " + placeholder + ") = 0";
        if ("starts with".equals(operator)) return "startsWith(" + expression + ", " + placeholder + ")";
        if ("ends with".equals(operator)) return "endsWith(" + expression + ", " + placeholder + ")";
        throw new IllegalArgumentException("Unsupported validated widget filter operator");
    }
}
