package com.icbc.aiops.langfuse.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MapperRowsTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 9, 29, 1, 2, 3);
    private static final BigDecimal COST = new BigDecimal("1.25");

    @Test
    void observabilityRowsExposeMappedColumns() {
        ObservabilityRows.TraceSummaryStatsRow traceStats =
                new ObservabilityRows.TraceSummaryStatsRow(1, 2, 3);
        assertEquals(1, traceStats.traceCount());
        assertEquals(2, traceStats.errorCount());
        assertEquals(3, traceStats.averageLatencyMs());

        ObservabilityRows.ObservationSummaryStatsRow observationStats =
                new ObservabilityRows.ObservationSummaryStatsRow(4, 5, COST);
        assertEquals(4, observationStats.observationCount());
        assertEquals(5, observationStats.totalTokens());
        assertEquals(COST, observationStats.totalCost());

        ObservabilityRows.TraceMetricRow traceMetric =
                new ObservabilityRows.TraceMetricRow(TIME, 6, 7, 8);
        assertEquals(TIME, traceMetric.timestamp());
        assertEquals(6, traceMetric.traceCount());
        assertEquals(7, traceMetric.errorCount());
        assertEquals(8, traceMetric.averageLatencyMs());

        ObservabilityRows.ObservationMetricRow observationMetric =
                new ObservabilityRows.ObservationMetricRow(TIME, 9, 10, COST);
        assertEquals(TIME, observationMetric.timestamp());
        assertEquals(9, observationMetric.observationCount());
        assertEquals(10, observationMetric.totalTokens());
        assertEquals(COST, observationMetric.totalCost());

        ObservabilityRows.FacetRow facet = new ObservabilityRows.FacetRow("facet", 11);
        assertEquals("facet", facet.value());
        assertEquals(11, facet.count());
        ObservabilityRows.PulseRow pulse = new ObservabilityRows.PulseRow(TIME, 12, COST, 13);
        assertEquals(TIME, pulse.timestamp());
        assertEquals(12, pulse.count());
        assertEquals(COST, pulse.totalCost());
        assertEquals(13, pulse.averageLatencyMs());

        ObservabilityRows.ScoreRow score = new ObservabilityRows.ScoreRow(
                "score", "trace", "observation", "quality", "NUMERIC", 0.9, "value", "API", "ok", TIME);
        assertEquals("score", score.id());
        assertEquals("trace", score.traceId());
        assertEquals("observation", score.observationId());
        assertEquals("quality", score.name());
        assertEquals("NUMERIC", score.dataType());
        assertEquals(Double.valueOf(0.9), score.numericValue());
        assertEquals("value", score.stringValue());
        assertEquals("API", score.source());
        assertEquals("ok", score.comment());
        assertEquals(TIME, score.createdAt());

        ObservabilityRows.SessionRow session =
                new ObservabilityRows.SessionRow("session", TIME, "user", 1, 2, 3, COST, 4);
        assertEquals("session", session.id());
        assertEquals(TIME, session.createdAt());
        assertEquals("user", session.userId());
        assertEquals(1, session.traceCount());
        assertEquals(2, session.observationCount());
        assertEquals(3, session.totalTokens());
        assertEquals(COST, session.totalCost());
        assertEquals(4, session.durationMs());

        ObservabilityRows.UserRow user =
                new ObservabilityRows.UserRow("user", "prod", TIME, TIME.plusSeconds(1), 1, 2, 3, COST);
        assertEquals("user", user.id());
        assertEquals("prod", user.environment());
        assertEquals(TIME, user.firstEvent());
        assertEquals(TIME.plusSeconds(1), user.lastEvent());
        assertEquals(1, user.traceCount());
        assertEquals(2, user.observationCount());
        assertEquals(3, user.totalTokens());
        assertEquals(COST, user.totalCost());
    }

    @Test
    void tracingRowsExposeMappedColumns() {
        TracingRows.ObservationRow observation = new TracingRows.ObservationRow(
                "o1", "t1", "trace", "parent", "generation", "GENERATION", TIME, TIME.plusSeconds(1),
                "ERROR", "model", 1000, 10, 20, COST, "{\"in\":1}", "{\"out\":2}",
                TIME.plusNanos(100000000), 100L, "status", "model-id", "{}", "{}", "{}",
                "prompt", 2, "{}");
        assertEquals("o1", observation.id());
        assertEquals("t1", observation.traceId());
        assertEquals("trace", observation.traceName());
        assertEquals("parent", observation.parentObservationId());
        assertEquals("generation", observation.name());
        assertEquals("GENERATION", observation.type());
        assertEquals(TIME, observation.startTime());
        assertEquals(TIME.plusSeconds(1), observation.endTime());
        assertEquals("ERROR", observation.level());
        assertEquals("model", observation.modelName());
        assertEquals(1000, observation.latencyMs());
        assertEquals(10, observation.inputTokens());
        assertEquals(20, observation.outputTokens());
        assertEquals(COST, observation.totalCost());
        assertEquals("{\"in\":1}", observation.inputJson());
        assertEquals("{\"out\":2}", observation.outputJson());
        assertEquals(TIME.plusNanos(100000000), observation.completionStartTime());
        assertEquals(Long.valueOf(100), observation.timeToFirstTokenMs());
        assertEquals("status", observation.statusMessage());
        assertEquals("model-id", observation.modelId());
        assertEquals("{}", observation.modelParametersJson());
        assertEquals("{}", observation.usageDetailsJson());
        assertEquals("{}", observation.costDetailsJson());
        assertEquals("prompt", observation.promptName());
        assertEquals(Integer.valueOf(2), observation.promptVersion());
        assertEquals("{}", observation.metadataJson());

        TracingRows.TraceRow trace = new TracingRows.TraceRow(
                "t1", "trace", TIME, "user", "session", "prod", "ERROR", 1, 2, COST, 3, "[]");
        assertEquals("t1", trace.id());
        assertEquals("trace", trace.name());
        assertEquals(TIME, trace.timestamp());
        assertEquals("user", trace.userId());
        assertEquals("session", trace.sessionId());
        assertEquals("prod", trace.environment());
        assertEquals("ERROR", trace.status());
        assertEquals(1, trace.latencyMs());
        assertEquals(2, trace.totalTokens());
        assertEquals(COST, trace.totalCost());
        assertEquals(3, trace.observationCount());
        assertEquals("[]", trace.tagsJson());

        TracingRows.TraceDetailRow detail = new TracingRows.TraceDetailRow(
                "t1", "trace", TIME, "user", "session", "prod", "OK", 1, 2, COST, 3, "[]",
                "input", "output", "{}", "release", "version");
        assertEquals("t1", detail.id());
        assertEquals("trace", detail.name());
        assertEquals(TIME, detail.timestamp());
        assertEquals("user", detail.userId());
        assertEquals("session", detail.sessionId());
        assertEquals("prod", detail.environment());
        assertEquals("OK", detail.status());
        assertEquals(1, detail.latencyMs());
        assertEquals(2, detail.totalTokens());
        assertEquals(COST, detail.totalCost());
        assertEquals(3, detail.observationCount());
        assertEquals("[]", detail.tagsJson());
        assertEquals("input", detail.inputJson());
        assertEquals("output", detail.outputJson());
        assertEquals("{}", detail.metadataJson());
        assertEquals("release", detail.release());
        assertEquals("version", detail.version());

        TracingRows.TraceLookupRow lookup = new TracingRows.TraceLookupRow("t1", "trace", "[]");
        assertEquals("t1", lookup.traceId());
        assertEquals("trace", lookup.traceName());
        assertEquals("[]", lookup.tagsJson());
        TracingRows.TraceMetricsRow metrics = new TracingRows.TraceMetricsRow("t1", 1, 2, COST);
        assertEquals("t1", metrics.traceId());
        assertEquals(1, metrics.observationCount());
        assertEquals(2, metrics.totalTokens());
        assertEquals(COST, metrics.totalCost());
        TracingRows.TraceLocatorRow locator = new TracingRows.TraceLocatorRow("service", TIME, TIME.plusSeconds(1));
        assertEquals("service", locator.serviceName());
        assertEquals(TIME, locator.minStartTime());
        assertEquals(TIME.plusSeconds(1), locator.maxEndTime());
        TracingRows.FacetRow facet = new TracingRows.FacetRow("facet", 4);
        assertEquals("facet", facet.value());
        assertEquals(4, facet.count());
        TracingRows.PulseRow pulse = new TracingRows.PulseRow(TIME, 5, COST, 6);
        assertEquals(TIME, pulse.timestamp());
        assertEquals(5, pulse.count());
        assertEquals(COST, pulse.totalCost());
        assertEquals(6, pulse.averageLatencyMs());
    }

    @Test
    void widgetMetricRowExposesMappedColumns() {
        WidgetMetricRows.WidgetMetricRow row = new WidgetMetricRows.WidgetMetricRow(TIME, "model", COST);
        assertEquals(TIME, row.bucket());
        assertEquals("model", row.dimension());
        assertEquals(COST, row.value());
    }
}
