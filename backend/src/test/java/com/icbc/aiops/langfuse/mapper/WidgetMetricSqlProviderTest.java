package com.icbc.aiops.langfuse.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.aiops.langfuse.service.WidgetMetricQuery;
import com.icbc.aiops.langfuse.service.WidgetMetricFilter;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WidgetMetricSqlProviderTest {
    private static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-08T00:00:00Z");

    @Test
    void buildsTraceCountDirectlyFromTraceFacts() {
        String sql = sql(new WidgetMetricQuery("compatibility-only", "TRACES", "name",
                "count", "count", true, FROM, TO));
        assertThat(sql).contains("default.hmp_agentobs_traces_all FINAL", "trace_name AS dimension",
                        "toStartOfDay(start_time)", "count()", "GROUP BY bucket, dimension")
                .doesNotContain("compatibility-only", "project_" + "id");
    }

    @Test
    void buildsObservationMetricFromAgentObsColumns() {
        String sql = sql(new WidgetMetricQuery("compatibility-only", "OBSERVATIONS", "providedModelName",
                "totalCost", "sum", false, FROM, TO));
        assertThat(sql).contains("default.hmp_agentobs_observations_all FINAL")
                .contains("coalesce(nullIf(model, ''), request_model) AS dimension")
                .contains("sum(ifNull(cost, 0))", "GROUP BY dimension")
                .doesNotContain("cost_" + "details", "calculated_" + "total_cost");
    }

    @Test
    void traceTokenAndCostMetricsUseObservationFactsWithoutAJoin() {
        String tokens = sql(new WidgetMetricQuery("compatibility-only", "TRACES", "environment",
                "totalTokens", "sum", false, FROM, TO));
        assertThat(tokens).contains("default.hmp_agentobs_observations_all FINAL")
                .contains("usage_total_tokens > 0")
                .contains("GROUP BY trace_id", "sum(total_tokens)", "hmp_agentobs_traces_all AS t FINAL")
                .contains("LEFT JOIN");
    }

    @Test
    void traceAveragesAndPercentilesOperateOnOneRowPerTrace() {
        String average = sql(new WidgetMetricQuery("compatibility-only", "TRACES", "name",
                "totalCost", "avg", false, FROM, TO));
        assertThat(average).contains("GROUP BY trace_id", "avg(total_cost)", "trace_name AS dimension");

        String percentile = sql(new WidgetMetricQuery("compatibility-only", "TRACES", null,
                "totalTokens", "p95", false, FROM, TO));
        assertThat(percentile).contains("quantile(0.95)(total_tokens)");
    }

    @Test
    void bindsValidatedWidgetFiltersWithoutEmbeddingValues() {
        WidgetMetricFilter environment = new WidgetMetricFilter(
                "environment", "stringOptions", "any of", Arrays.asList("prod", "staging"));
        WidgetMetricQuery query = new WidgetMetricQuery("compatibility-only", "OBSERVATIONS", null,
                "count", "count", false, FROM, TO, Arrays.asList(environment));
        java.util.Map<String, Object> parameters = new java.util.HashMap<>();
        parameters.put("query", query);

        String sql = WidgetMetricSqlProvider.metric(parameters);

        assertThat(sql).contains("environment IN (#{widgetFilter0_0}, #{widgetFilter0_1})")
                .doesNotContain("prod", "staging");
        assertThat(parameters).containsEntry("widgetFilter0_0", "prod")
                .containsEntry("widgetFilter0_1", "staging");
    }

    private static String sql(WidgetMetricQuery query) {
        return WidgetMetricSqlProvider.metric(
                com.icbc.aiops.langfuse.util.Java8Collections.mapOf("query", query));
    }
}
