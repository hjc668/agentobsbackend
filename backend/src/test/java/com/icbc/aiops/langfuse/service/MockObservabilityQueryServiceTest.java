package com.icbc.aiops.langfuse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.icbc.aiops.langfuse.api.PageResponse;
import com.icbc.aiops.langfuse.domain.Observation;
import com.icbc.aiops.langfuse.domain.ObservationLevel;
import com.icbc.aiops.langfuse.domain.ObservationType;
import com.icbc.aiops.langfuse.domain.TraceStatus;
import com.icbc.aiops.langfuse.domain.TraceSummary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockObservabilityQueryServiceTest {

    private final MockObservabilityQueryService service = new MockObservabilityQueryService();

    @Test
    void exposesDashboardTraceAndRelatedMockData() {
        assertEquals(4, service.getSummary().traceCount());
        assertEquals(7, service.getMetricTimeSeries().size());

        for (TraceQuery.SortBy sort : TraceQuery.SortBy.values()) {
            PageResponse<TraceSummary> page = service.findTraces(new TraceQuery(
                    "", null, "", "", "", "", null, null, sort,
                    TraceQuery.SortDirection.DESC, 0, 20));
            assertEquals(4, page.total());
            assertFalse(page.items().isEmpty());
        }
        assertEquals(1, service.findTraces(new TraceQuery(
                "support", TraceStatus.SUCCESS, "production", "user-1842", "session-alpha", "rag",
                Instant.parse("2026-08-28T00:00:00Z"), Instant.parse("2026-08-29T00:00:00Z"),
                TraceQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.ASC, 0, 20)).total());

        assertEquals("trace-rag-001", service.getTrace("trace-rag-001").id());
        assertFalse(service.findTraceObservations("trace-rag-001").isEmpty());
        assertEquals(2, service.findTraceScores("trace-rag-001").size());
        assertEquals(1, service.findTraceComments("trace-rag-001").size());
        assertTrue(service.findTraceScores("trace-chat-003").isEmpty());
        assertTrue(service.findTraceComments("trace-chat-003").isEmpty());
        assertEquals("trace-rag-001", service.getTraceView("trace-rag-001").trace().id());
        assertThrows(ResourceNotFoundException.class, () -> service.getTrace("missing"));
        assertThrows(ResourceNotFoundException.class, () -> service.findTraceObservations("missing"));
    }

    @Test
    void filtersSortsFacetsAndBucketsObservations() {
        for (ObservationQuery.SortBy sort : ObservationQuery.SortBy.values()) {
            PageResponse<Observation> page = service.findObservations(observationQuery(
                    "", null, null, null, null, null, sort, TraceQuery.SortDirection.DESC));
            assertTrue(page.total() > 0);
        }

        ObservationQuery filtered = observationQuery(
                "environment:production type:GENERATION latency:>1 model:gpt user:user-1842",
                "production", ObservationType.GENERATION, ObservationLevel.DEFAULT, "gpt-5-mini",
                "rag", ObservationQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.ASC);
        List<Observation> observations = service.findObservations(filtered).items();
        assertEquals(1, observations.size());
        assertEquals("obs-rag-gen", observations.get(0).id());

        ObservationQuery all = observationQuery("", "", null, null, "", "",
                ObservationQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.ASC);
        for (ObservationFacet facet : ObservationFacet.values()) {
            assertNotNull(service.findObservationFacets(all, facet, 10));
        }
        for (PulseBucket bucket : PulseBucket.values()) {
            assertFalse(service.findObservationPulse(all, bucket).isEmpty());
        }
    }

    @Test
    void exposesSessionAndUserViewsWithNotFoundSemantics() {
        assertEquals(3, service.findSessions("", 0, 20).total());
        assertEquals(1, service.findSessions("alpha", 0, 20).total());
        assertEquals("session-alpha", service.getSession("session-alpha").summary().id());
        assertThrows(ResourceNotFoundException.class, () -> service.getSession("missing"));

        assertEquals(3, service.findUsers("", "", 0, 20).total());
        assertEquals(2, service.findUsers("", "production", 0, 20).total());
        assertEquals("user-1842", service.getUser("user-1842").summary().id());
        assertThrows(ResourceNotFoundException.class, () -> service.getUser("missing"));
    }

    private static ObservationQuery observationQuery(String search, String environment,
            ObservationType type, ObservationLevel level, String model, String tag,
            ObservationQuery.SortBy sort, TraceQuery.SortDirection direction) {
        return new ObservationQuery(search, environment, "", type, level, model, "", tag,
                null, null, sort, direction, 0, 50);
    }
}
