package com.icbc.aiops.langfuse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.icbc.aiops.langfuse.domain.ObservationType;
import com.icbc.aiops.langfuse.domain.TraceStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The query window must never be open-ended.
 *
 * <p>A client that omits the timestamps used to turn every list, count, facet and pulse call
 * into a full-table scan. These tests pin the replacement behaviour: an absent range becomes
 * the last 24 hours, and anything wider than the widest option the UI offers is rejected.
 */
class TracingQueryBoundsTest {

    private static ObservationQuery observationQuery(Instant from, Instant to) {
        return new ObservationQuery("", "", "", null, null, "", "", "", from, to,
                ObservationQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 50);
    }

    private static TraceQuery traceQuery(Instant from, Instant to) {
        return new TraceQuery("", null, "", "", "", "", from, to,
                TraceQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 50);
    }

    @Test
    void missingRangeBecomesTheLastTwentyFourHours() {
        ObservationQuery bounded = MybatisObservabilityQueryService.bounded(observationQuery(null, null));

        assertNotNull(bounded.fromTimestamp());
        assertNotNull(bounded.toTimestamp());
        assertEquals(Duration.ofHours(24).toMillis(),
                Duration.between(bounded.fromTimestamp(), bounded.toTimestamp()).toMillis(),
                1000L);
    }

    @Test
    void missingFromIsFilledRelativeToTheGivenTo() {
        Instant to = Instant.parse("2026-09-11T00:00:00Z");
        ObservationQuery bounded = MybatisObservabilityQueryService.bounded(observationQuery(null, to));

        assertEquals(to, bounded.toTimestamp());
        assertEquals(Instant.parse("2026-09-10T00:00:00Z"), bounded.fromTimestamp());
    }

    @Test
    void missingToIsFilledWithNow() {
        Instant from = Instant.now().minus(Duration.ofHours(3));
        ObservationQuery bounded = MybatisObservabilityQueryService.bounded(observationQuery(from, null));

        assertEquals(from, bounded.fromTimestamp());
        assertNotNull(bounded.toTimestamp());
        assertTrue(bounded.toTimestamp().isAfter(from));
    }

    @Test
    void explicitRangeIsPreserved() {
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-08T00:00:00Z");
        ObservationQuery bounded = MybatisObservabilityQueryService.bounded(observationQuery(from, to));

        assertEquals(from, bounded.fromTimestamp());
        assertEquals(to, bounded.toTimestamp());
    }

    @Test
    void rangeWiderThanNinetyDaysIsRejected() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-11T00:00:00Z");

        assertThrows(InvalidRequestException.class,
                () -> MybatisObservabilityQueryService.bounded(observationQuery(from, to)));
        assertThrows(InvalidRequestException.class,
                () -> MybatisObservabilityQueryService.bounded(traceQuery(from, to)));
    }

    @Test
    void exactlyNinetyDaysIsAllowed() {
        Instant to = Instant.parse("2026-09-11T00:00:00Z");
        Instant from = to.minus(Duration.ofDays(90));

        assertEquals(from, MybatisObservabilityQueryService.bounded(observationQuery(from, to)).fromTimestamp());
    }

    @Test
    void invertedRangeIsRejected() {
        Instant from = Instant.parse("2026-09-11T00:00:00Z");
        Instant to = Instant.parse("2026-09-01T00:00:00Z");

        assertThrows(InvalidRequestException.class,
                () -> MybatisObservabilityQueryService.bounded(observationQuery(from, to)));
    }

    @Test
    void boundingKeepsEveryOtherFilterIntact() {
        ObservationQuery original = new ObservationQuery("type:SPAN", "prod", "svc-a",
                ObservationType.GENERATION, null, "gpt", "trace-1", "tag-1", null, null,
                ObservationQuery.SortBy.COST, TraceQuery.SortDirection.ASC, 3, 25);

        ObservationQuery bounded = MybatisObservabilityQueryService.bounded(original);

        assertEquals("type:SPAN", bounded.search());
        assertEquals("prod", bounded.environment());
        assertEquals("svc-a", bounded.serviceName());
        assertEquals(ObservationType.GENERATION, bounded.type());
        assertEquals("gpt", bounded.model());
        assertEquals("trace-1", bounded.traceId());
        assertEquals("tag-1", bounded.tag());
        assertEquals(ObservationQuery.SortBy.COST, bounded.sortBy());
        assertEquals(TraceQuery.SortDirection.ASC, bounded.direction());
        assertEquals(3, bounded.page());
        assertEquals(25, bounded.size());
    }

    @Test
    void traceBoundBoundingAlsoPreservesFilters() {
        TraceQuery original = new TraceQuery("name:x", TraceStatus.ERROR, "prod", "u1", "s1", "t1",
                null, null, TraceQuery.SortBy.LATENCY, TraceQuery.SortDirection.ASC, 1, 10);

        TraceQuery bounded = MybatisObservabilityQueryService.bounded(original);

        assertEquals("name:x", bounded.search());
        assertEquals(TraceStatus.ERROR, bounded.status());
        assertEquals("prod", bounded.environment());
        assertEquals("u1", bounded.userId());
        assertEquals("s1", bounded.sessionId());
        assertEquals("t1", bounded.tag());
        assertEquals(1, bounded.page());
        assertEquals(10, bounded.size());
    }
}
