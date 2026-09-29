package com.icbc.aiops.langfuse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DomainValueObjectsTest {

    private static final Instant TIME = Instant.parse("2026-09-29T01:02:03Z");
    private static final BigDecimal COST = new BigDecimal("1.25");
    private static final Map<String, Object> MAP = Collections.<String, Object>singletonMap("key", "value");
    private static final List<String> TAGS = Collections.singletonList("tag");

    @Test
    void dashboardModelsExposeConstructorValues() {
        Dashboard dashboard = new Dashboard("d1", "p1", "Dashboard", "description", MAP,
                Collections.singletonList(MAP), "owner", "creator", "updater", TIME, TIME);
        assertEquals("d1", dashboard.id());
        assertEquals("p1", dashboard.projectId());
        assertEquals("Dashboard", dashboard.name());
        assertEquals("description", dashboard.description());
        assertSame(MAP, dashboard.definition());
        assertEquals(Collections.singletonList(MAP), dashboard.filters());
        assertEquals("owner", dashboard.owner());
        assertEquals("creator", dashboard.createdBy());
        assertEquals("updater", dashboard.updatedBy());
        assertEquals(TIME, dashboard.createdAt());
        assertEquals(TIME, dashboard.updatedAt());

        DashboardWidget widget = new DashboardWidget("w1", "p1", "Widget", "description", "TRACES",
                Collections.singletonList(MAP), Collections.singletonList(MAP), Collections.singletonList(MAP),
                "LINE", MAP, 1, "owner", "creator", "updater", TIME, TIME);
        assertEquals("w1", widget.id());
        assertEquals("p1", widget.projectId());
        assertEquals("Widget", widget.name());
        assertEquals("description", widget.description());
        assertEquals("TRACES", widget.view());
        assertEquals(Collections.singletonList(MAP), widget.dimensions());
        assertEquals(Collections.singletonList(MAP), widget.metrics());
        assertEquals(Collections.singletonList(MAP), widget.filters());
        assertEquals("LINE", widget.chartType());
        assertSame(MAP, widget.chartConfig());
        assertEquals(1, widget.minVersion());
        assertEquals("owner", widget.owner());
        assertEquals("creator", widget.createdBy());
        assertEquals("updater", widget.updatedBy());
        assertEquals(TIME, widget.createdAt());
        assertEquals(TIME, widget.updatedAt());

        DashboardSummary summary = new DashboardSummary(1, 2, 3, 4, COST, 5);
        assertEquals(1, summary.traceCount());
        assertEquals(2, summary.observationCount());
        assertEquals(3, summary.errorCount());
        assertEquals(4, summary.totalTokens());
        assertEquals(COST, summary.totalCost());
        assertEquals(5, summary.averageLatencyMs());
    }

    @Test
    void observationModelsExposeConstructorValues() {
        Observation observation = observation();
        assertEquals("o1", observation.id());
        assertEquals("t1", observation.traceId());
        assertEquals("trace", observation.traceName());
        assertEquals("parent", observation.parentObservationId());
        assertEquals("generation", observation.name());
        assertEquals(ObservationType.GENERATION, observation.type());
        assertEquals(TIME, observation.startTime());
        assertEquals(TIME.plusSeconds(1), observation.endTime());
        assertEquals(ObservationLevel.ERROR, observation.level());
        assertEquals("model", observation.model());
        assertEquals(1000, observation.latencyMs());
        assertEquals(10, observation.inputTokens());
        assertEquals(20, observation.outputTokens());
        assertEquals(COST, observation.totalCost());
        assertEquals("input", observation.input());
        assertEquals("output", observation.output());
        assertEquals(TIME.plusMillis(100), observation.completionStartTime());
        assertEquals(Long.valueOf(100), observation.timeToFirstTokenMs());
        assertEquals("status", observation.statusMessage());
        assertEquals("model-id", observation.modelId());
        assertSame(MAP, observation.modelParameters());
        assertSame(MAP, observation.usageDetails());
        assertSame(MAP, observation.costDetails());
        assertEquals("prompt", observation.promptName());
        assertEquals(Integer.valueOf(2), observation.promptVersion());
        assertSame(MAP, observation.metadata());

        ObservationFacetValue facet = new ObservationFacetValue("value", 3);
        assertEquals("value", facet.value());
        assertEquals(3, facet.count());
        ObservationPulsePoint pulse = new ObservationPulsePoint(TIME, 4, COST, 5);
        assertEquals(TIME, pulse.timestamp());
        assertEquals(4, pulse.count());
        assertEquals(COST, pulse.totalCost());
        assertEquals(5, pulse.averageLatencyMs());
    }

    @Test
    void traceModelsExposeConstructorValuesAndConvertToSummary() {
        TraceDetail detail = new TraceDetail("t1", "trace", TIME, "user", "session", "prod",
                TraceStatus.ERROR, 11, 22, COST, 2, TAGS, "input", "output", MAP, "release", "version");
        assertEquals("t1", detail.id());
        assertEquals("trace", detail.name());
        assertEquals(TIME, detail.timestamp());
        assertEquals("user", detail.userId());
        assertEquals("session", detail.sessionId());
        assertEquals("prod", detail.environment());
        assertEquals(TraceStatus.ERROR, detail.status());
        assertEquals(11, detail.latencyMs());
        assertEquals(22, detail.totalTokens());
        assertEquals(COST, detail.totalCost());
        assertEquals(2, detail.observationCount());
        assertSame(TAGS, detail.tags());
        assertEquals("input", detail.input());
        assertEquals("output", detail.output());
        assertSame(MAP, detail.metadata());
        assertEquals("release", detail.release());
        assertEquals("version", detail.version());

        TraceSummary summary = detail.toSummary();
        assertEquals(detail.id(), summary.id());
        assertEquals(detail.name(), summary.name());
        assertEquals(detail.timestamp(), summary.timestamp());
        assertEquals(detail.userId(), summary.userId());
        assertEquals(detail.sessionId(), summary.sessionId());
        assertEquals(detail.environment(), summary.environment());
        assertEquals(detail.status(), summary.status());
        assertEquals(detail.latencyMs(), summary.latencyMs());
        assertEquals(detail.totalTokens(), summary.totalTokens());
        assertEquals(detail.totalCost(), summary.totalCost());
        assertEquals(detail.observationCount(), summary.observationCount());
        assertEquals(detail.tags(), summary.tags());

        TraceView view = new TraceView(detail, Collections.singletonList(observation()));
        assertSame(detail, view.trace());
        assertEquals(1, view.observations().size());

        TraceScore score = new TraceScore("s1", "t1", "o1", "quality", "NUMERIC",
                0.9, null, "API", "good", TIME);
        assertEquals("s1", score.id());
        assertEquals("t1", score.traceId());
        assertEquals("o1", score.observationId());
        assertEquals("quality", score.name());
        assertEquals("NUMERIC", score.dataType());
        assertEquals(Double.valueOf(0.9), score.numericValue());
        assertNull(score.stringValue());
        assertEquals("API", score.source());
        assertEquals("good", score.comment());
        assertEquals(TIME, score.createdAt());

        TraceComment comment = new TraceComment("c1", "TRACE", "t1", "content", "author",
                "input", TIME, TIME);
        assertEquals("c1", comment.id());
        assertEquals("TRACE", comment.objectType());
        assertEquals("t1", comment.objectId());
        assertEquals("content", comment.content());
        assertEquals("author", comment.authorUserId());
        assertEquals("input", comment.dataField());
        assertEquals(TIME, comment.createdAt());
        assertEquals(TIME, comment.updatedAt());
    }

    @Test
    void promptSessionUserAndMetricModelsExposeConstructorValues() {
        PromptVersion prompt = new PromptVersion("pr1", "p1", "name", 2, "text", "hello", MAP,
                TAGS, TAGS, "commit", "creator", TIME, TIME);
        assertEquals("pr1", prompt.id());
        assertEquals("p1", prompt.projectId());
        assertEquals("name", prompt.name());
        assertEquals(2, prompt.version());
        assertEquals("text", prompt.type());
        assertEquals("hello", prompt.prompt());
        assertSame(MAP, prompt.config());
        assertSame(TAGS, prompt.labels());
        assertSame(TAGS, prompt.tags());
        assertEquals("commit", prompt.commitMessage());
        assertEquals("creator", prompt.createdBy());
        assertEquals(TIME, prompt.createdAt());
        assertEquals(TIME, prompt.updatedAt());

        SessionSummary session = new SessionSummary("session", TIME, "user", 1, 2, 3, COST, 4);
        assertEquals("session", session.id());
        assertEquals(TIME, session.createdAt());
        assertEquals("user", session.userId());
        assertEquals(1, session.traceCount());
        assertEquals(2, session.observationCount());
        assertEquals(3, session.totalTokens());
        assertEquals(COST, session.totalCost());
        assertEquals(4, session.durationMs());
        SessionDetail sessionDetail = new SessionDetail(session, Collections.<TraceSummary>emptyList(),
                Collections.<Observation>emptyList());
        assertSame(session, sessionDetail.summary());
        assertEquals(Collections.emptyList(), sessionDetail.traces());
        assertEquals(Collections.emptyList(), sessionDetail.observations());

        UserSummary user = new UserSummary("user", "prod", TIME, TIME.plusSeconds(1), 1, 2, 3, COST);
        assertEquals("user", user.id());
        assertEquals("prod", user.environment());
        assertEquals(TIME, user.firstEvent());
        assertEquals(TIME.plusSeconds(1), user.lastEvent());
        assertEquals(1, user.traceCount());
        assertEquals(2, user.observationCount());
        assertEquals(3, user.totalTokens());
        assertEquals(COST, user.totalCost());
        UserDetail userDetail = new UserDetail(user, Collections.<TraceSummary>emptyList(),
                Collections.singletonList(session));
        assertSame(user, userDetail.summary());
        assertEquals(Collections.emptyList(), userDetail.traces());
        assertEquals(Collections.singletonList(session), userDetail.sessions());

        MetricPoint metric = new MetricPoint(TIME, 1, 2, 3, COST, 4);
        assertEquals(TIME, metric.timestamp());
        assertEquals(1, metric.traceCount());
        assertEquals(2, metric.errorCount());
        assertEquals(3, metric.totalTokens());
        assertEquals(COST, metric.totalCost());
        assertEquals(4, metric.averageLatencyMs());
        WidgetMetricPoint widgetMetric = new WidgetMetricPoint(TIME, "model", COST);
        assertEquals(TIME, widgetMetric.bucket());
        assertEquals("model", widgetMetric.dimension());
        assertEquals(COST, widgetMetric.value());
    }

    private static Observation observation() {
        return new Observation("o1", "t1", "trace", "parent", "generation", ObservationType.GENERATION,
                TIME, TIME.plusSeconds(1), ObservationLevel.ERROR, "model", 1000, 10, 20, COST,
                "input", "output", TIME.plusMillis(100), 100L, "status", "model-id", MAP, MAP, MAP,
                "prompt", 2, MAP);
    }
}
