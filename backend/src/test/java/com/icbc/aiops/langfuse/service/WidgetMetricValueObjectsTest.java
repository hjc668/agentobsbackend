package com.icbc.aiops.langfuse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class WidgetMetricValueObjectsTest {

    @Test
    void queryAndFilterExposeValidatedValues() {
        WidgetMetricFilter filter = new WidgetMetricFilter("status", "string", "equals", "ok");
        assertEquals("status", filter.column());
        assertEquals("string", filter.type());
        assertEquals("equals", filter.operator());
        assertEquals("ok", filter.value());

        Instant from = Instant.parse("2026-09-28T00:00:00Z");
        Instant to = Instant.parse("2026-09-29T00:00:00Z");
        List<WidgetMetricFilter> filters = Collections.singletonList(filter);
        WidgetMetricQuery query = new WidgetMetricQuery(
                "project", "TRACES", "timestamp", "count", "SUM", true, from, to, filters);
        assertEquals("project", query.projectId());
        assertEquals("TRACES", query.view());
        assertEquals("timestamp", query.dimension());
        assertEquals("count", query.measure());
        assertEquals("SUM", query.aggregation());
        assertTrue(query.timeSeries());
        assertEquals(from, query.fromTimestamp());
        assertEquals(to, query.toTimestamp());
        assertSame(filters, query.filters());

        WidgetMetricQuery withoutFilters = new WidgetMetricQuery(
                "project", "TRACES", null, "count", "SUM", false, from, to);
        assertEquals(Collections.emptyList(), withoutFilters.filters());
    }
}
