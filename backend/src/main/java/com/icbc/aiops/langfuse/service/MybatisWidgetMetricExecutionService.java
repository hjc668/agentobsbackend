package com.icbc.aiops.langfuse.service;

import com.icbc.aiops.langfuse.domain.DashboardWidget;
import com.icbc.aiops.langfuse.domain.WidgetMetricPoint;
import com.icbc.aiops.langfuse.mapper.WidgetMetricMapper;
import com.icbc.aiops.langfuse.mapper.WidgetMetricRows.WidgetMetricRow;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("mybatis")
public class MybatisWidgetMetricExecutionService implements WidgetMetricExecutionService {
    private static final Set<String> VIEWS = com.icbc.aiops.langfuse.util.Java8Collections.setOf("TRACES", "OBSERVATIONS");
    private static final Set<String> AGGREGATIONS = com.icbc.aiops.langfuse.util.Java8Collections.setOf("count", "sum", "avg", "min", "max", "p50", "p75", "p90", "p95", "p99");
    private static final Set<String> TRACE_MEASURES = com.icbc.aiops.langfuse.util.Java8Collections.setOf("count", "totalCost", "totalTokens", "latency");
    private static final Set<String> OBSERVATION_MEASURES = com.icbc.aiops.langfuse.util.Java8Collections.setOf("count", "totalCost", "totalTokens", "latency");
    private static final Set<String> TRACE_DIMENSIONS = com.icbc.aiops.langfuse.util.Java8Collections.setOf("name", "environment", "userId");
    private static final Set<String> OBSERVATION_DIMENSIONS = com.icbc.aiops.langfuse.util.Java8Collections.setOf("name", "environment", "userId", "providedModelName", "level");
    private static final int MAX_FILTERS = 20;
    private static final Set<String> STRING_OPERATORS = com.icbc.aiops.langfuse.util.Java8Collections.setOf(
            "=", "contains", "does not contain", "starts with", "ends with");
    private static final Set<String> OPTION_OPERATORS = com.icbc.aiops.langfuse.util.Java8Collections.setOf(
            "any of", "none of");
    private static final Set<String> NULL_OPERATORS = com.icbc.aiops.langfuse.util.Java8Collections.setOf(
            "is null", "is not null");

    private final DashboardWidgetCrudService widgetService;
    private final WidgetMetricMapper mapper;

    public MybatisWidgetMetricExecutionService(DashboardWidgetCrudService widgetService, WidgetMetricMapper mapper) {
        this.widgetService = widgetService;
        this.mapper = mapper;
    }

    @Override
    public List<WidgetMetricPoint> execute(String projectId, String widgetId, Instant fromTimestamp, Instant toTimestamp) {
        DashboardWidget widget = widgetService.getWidget(projectId, widgetId);
        if (!VIEWS.contains(widget.view())) throw new InvalidRequestException("Evaluation widget execution is deferred.");
        Map<String, Object> metric = widget.metrics().stream().findFirst()
                .orElseThrow(() -> new InvalidRequestException("Widget requires one metric."));
        String measure = text(metric, "measure");
        String aggregation = metric.containsKey("agg") ? text(metric, "agg") : text(metric, "aggregation");
        Set<String> measures = "TRACES".equals(widget.view()) ? TRACE_MEASURES : OBSERVATION_MEASURES;
        if (!measures.contains(measure)) throw new InvalidRequestException("Unsupported widget measure: " + measure);
        if (!AGGREGATIONS.contains(aggregation)) throw new InvalidRequestException("Unsupported widget aggregation: " + aggregation);

        boolean timeSeries = widget.chartType().contains("TIME_SERIES") || widget.dimensions().stream()
                .anyMatch(item -> "timestamp".equals(item.get("field")));
        String dimension = widget.dimensions().stream().map(item -> item.get("field")).filter(String.class::isInstance)
                .map(String.class::cast).filter(field -> !"timestamp".equals(field)).findFirst().orElse(null);
        Set<String> dimensions = "TRACES".equals(widget.view()) ? TRACE_DIMENSIONS : OBSERVATION_DIMENSIONS;
        if (dimension != null && !dimensions.contains(dimension)) {
            throw new InvalidRequestException("Unsupported widget dimension: " + dimension);
        }
        List<WidgetMetricFilter> filters = filters(widget.filters(), dimensions);
        Instant to = toTimestamp == null ? Instant.now() : toTimestamp;
        Instant from = fromTimestamp == null ? to.minus(30, ChronoUnit.DAYS) : fromTimestamp;
        if (!from.isBefore(to)) throw new InvalidRequestException("fromTimestamp must be before toTimestamp.");
        WidgetMetricQuery query = new WidgetMetricQuery(projectId, widget.view(), dimension, measure,
                aggregation, timeSeries, from, to, filters);
        return mapper.selectMetric(query).stream().map(MybatisWidgetMetricExecutionService::point).collect(java.util.stream.Collectors.toList());
    }

    private static String text(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new InvalidRequestException("Widget metric requires " + key + ".");
        }
        return (String) value;
    }

    private static List<WidgetMetricFilter> filters(
            List<Map<String, Object>> source, Set<String> allowedColumns) {
        if (source.size() > MAX_FILTERS) {
            throw new InvalidRequestException("Widget supports at most " + MAX_FILTERS + " filters.");
        }
        List<WidgetMetricFilter> result = new ArrayList<>();
        for (Map<String, Object> item : source) {
            String column = requiredText(item, "column");
            String type = requiredText(item, "type");
            String operator = requiredText(item, "operator").toLowerCase();
            if (!allowedColumns.contains(column)) {
                throw new InvalidRequestException("Unsupported widget filter column: " + column);
            }
            Object value = item.get("value");
            if ("string".equals(type)) {
                requireOperator(operator, STRING_OPERATORS, type);
                if (!(value instanceof String)) invalidFilter("string value required");
            } else if ("stringOptions".equals(type) || "categoryOptions".equals(type)) {
                requireOperator(operator, OPTION_OPERATORS, type);
                if (!(value instanceof Collection<?>) || ((Collection<?>) value).isEmpty()) {
                    invalidFilter("non-empty option list required");
                }
            } else if ("null".equals(type)) {
                requireOperator(operator, NULL_OPERATORS, type);
                value = null;
            } else {
                throw new InvalidRequestException("Unsupported widget filter type: " + type);
            }
            result.add(new WidgetMetricFilter(column, type, operator, value));
        }
        return result;
    }

    private static String requiredText(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new InvalidRequestException("Widget filter requires " + key + ".");
        }
        return ((String) value).trim();
    }

    private static void requireOperator(String operator, Set<String> allowed, String type) {
        if (!allowed.contains(operator)) {
            throw new InvalidRequestException("Unsupported " + type + " filter operator: " + operator);
        }
    }

    private static void invalidFilter(String message) {
        throw new InvalidRequestException("Invalid widget filter: " + message + ".");
    }

    private static WidgetMetricPoint point(WidgetMetricRow row) {
        return new WidgetMetricPoint(row.bucket() == null ? null : row.bucket().toInstant(ZoneOffset.UTC), row.dimension(), row.value());
    }
}
