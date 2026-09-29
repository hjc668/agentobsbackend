package com.icbc.aiops.langfuse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.aiops.langfuse.config.WorkspaceProperties;
import com.icbc.aiops.langfuse.domain.DashboardSummary;
import com.icbc.aiops.langfuse.domain.MetricPoint;
import com.icbc.aiops.langfuse.domain.SessionDetail;
import com.icbc.aiops.langfuse.mapper.ObservabilityMapper;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.ObservationMetricRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.ObservationSummaryStatsRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.SessionRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.TraceMetricRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.TraceSummaryStatsRow;
import com.icbc.aiops.langfuse.mapper.TracingMapper;
import com.icbc.aiops.langfuse.mapper.TracingRows.TraceMetricsRow;
import com.icbc.aiops.langfuse.mapper.TracingRows.TraceLocatorRow;
import com.icbc.aiops.langfuse.mapper.TracingRows.TraceRow;
import com.icbc.aiops.langfuse.postgres.mapper.CommentMapper;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MybatisObservabilityMigrationTest {

    @Test
    void mergesSeparateSummaryAndUtcTrendQueries() {
        LocalDateTime today = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        ObservabilityMapper observability = proxy(ObservabilityMapper.class, (method, args) -> {
            if ("selectTraceSummaryStats".equals(method)) return new TraceSummaryStatsRow(3, 1, 12);
            if ("selectObservationSummaryStats".equals(method)) {
                return new ObservationSummaryStatsRow(7, 99, new BigDecimal("1.25"));
            }
            if ("selectTraceMetricTimeSeries".equals(method)) {
                return Collections.singletonList(new TraceMetricRow(today, 2, 1, 15));
            }
            if ("selectObservationMetricTimeSeries".equals(method)) {
                return Collections.singletonList(new ObservationMetricRow(today, 6, 88, new BigDecimal("0.75")));
            }
            throw new UnsupportedOperationException(method);
        });
        MybatisObservabilityQueryService service = service(observability,
                proxy(TracingMapper.class, unsupported()));

        DashboardSummary summary = service.getSummary();
        assertEquals(3, summary.traceCount());
        assertEquals(7, summary.observationCount());
        assertEquals(99, summary.totalTokens());
        assertEquals(new BigDecimal("1.25"), summary.totalCost());

        List<MetricPoint> trend = service.getMetricTimeSeries();
        assertEquals(7, trend.size());
        MetricPoint current = trend.get(6);
        assertEquals(2, current.traceCount());
        assertEquals(1, current.errorCount());
        assertEquals(88, current.totalTokens());
        assertEquals(new BigDecimal("0.75"), current.totalCost());
    }

    @Test
    void sessionTraceRowsReceiveMetricsFromTheBoundedSecondPhase() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        ObservabilityMapper observability = proxy(ObservabilityMapper.class, (method, args) -> {
            if ("selectSession".equals(method)) {
                return new SessionRow("session-a", now, "user-a", 1, 2, 12,
                        new BigDecimal("0.50"), 30);
            }
            throw new UnsupportedOperationException(method);
        });
        TracingMapper tracing = proxy(TracingMapper.class, (method, args) -> {
            if ("selectTracesBySession".equals(method)) {
                return Collections.singletonList(new TraceRow("trace-a", "trace", now, "user-a",
                        "session-a", "production", "SUCCESS", 30, 0, BigDecimal.ZERO, 0, "[]"));
            }
            if ("selectTraceMetrics".equals(method)) {
                return Collections.singletonList(new TraceMetricsRow(
                        "trace-a", 2, 12, new BigDecimal("0.50")));
            }
            if ("selectObservationsBySession".equals(method)) return Collections.emptyList();
            throw new UnsupportedOperationException(method);
        });

        SessionDetail detail = service(observability, tracing).getSession("session-a");
        assertEquals(1, detail.traces().size());
        assertEquals(2, detail.traces().get(0).observationCount());
        assertEquals(12, detail.traces().get(0).totalTokens());
        assertEquals(new BigDecimal("0.50"), detail.traces().get(0).totalCost());
    }

    @Test
    void rejectsOversizedSessionBeforeBuildingTraceMetricsQuery() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        ObservabilityMapper observability = proxy(ObservabilityMapper.class, (method, args) -> {
            if ("selectSession".equals(method)) {
                return new SessionRow("session-a", now, "user-a", 201, 0, 0,
                        BigDecimal.ZERO, 0);
            }
            throw new UnsupportedOperationException(method);
        });
        TraceRow row = new TraceRow("trace-a", "trace", now, "user-a", "session-a",
                "production", "SUCCESS", 30, 0, BigDecimal.ZERO, 0, "[]");
        TracingMapper tracing = proxy(TracingMapper.class, (method, args) -> {
            if ("selectTracesBySession".equals(method)) {
                assertEquals(201, args[1]);
                return Collections.nCopies(201, row);
            }
            throw new AssertionError("must stop before " + method);
        });

        assertThrows(InvalidRequestException.class,
                () -> service(observability, tracing).getSession("session-a"));
    }

    @Test
    void traceDetailBindsLocatorWallClockAtSixMicroseconds() {
        AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
        LocalDateTime bound = LocalDateTime.of(2026, 9, 7, 8, 20, 19, 123456000);
        TracingMapper tracing = proxy(TracingMapper.class, (method, args) -> {
            if ("selectTraceLocator".equals(method)) {
                return Collections.singletonList(new TraceLocatorRow("svc-a", bound, bound));
            }
            if ("selectTraceObservations".equals(method)) {
                captured.set((Map<String, Object>) args[0]);
                return Collections.emptyList();
            }
            throw new UnsupportedOperationException(method);
        });
        MybatisObservabilityQueryService service = service(
                proxy(ObservabilityMapper.class, unsupported()), tracing);

        service.findTraceObservations("trace-a");

        assertEquals("2026-09-07 08:20:19.123456", captured.get().get("locatorMinStart"));
        assertEquals(captured.get().get("locatorMinStart"), captured.get().get("locatorMaxEnd"));
        assertEquals("UTC", captured.get().get("locatorColumnTimeZone"));

        ReflectionTestUtils.setField(service, "observationColumnTimeZone", "Asia/Shanghai");
        service.findTraceObservations("trace-a");
        assertEquals("2026-09-07 16:20:19.123456", captured.get().get("locatorMinStart"));
        assertEquals("Asia/Shanghai", captured.get().get("locatorColumnTimeZone"));
    }

    @Test
    void invalidLocatorRangeFailsInsteadOfDroppingTimePredicates() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 7, 8, 20, 20);
        LocalDateTime end = start.minusSeconds(1);
        TracingMapper tracing = proxy(TracingMapper.class, (method, args) -> {
            if ("selectTraceLocator".equals(method)) {
                return Collections.singletonList(new TraceLocatorRow("svc-a", start, end));
            }
            throw new AssertionError("must not query observations with an invalid locator");
        });

        assertThrows(IllegalStateException.class,
                () -> service(proxy(ObservabilityMapper.class, unsupported()), tracing)
                        .findTraceObservations("trace-a"));
    }

    private static MybatisObservabilityQueryService service(
            ObservabilityMapper observability, TracingMapper tracing) {
        CommentMapper comments = proxy(CommentMapper.class, unsupported());
        WorkspaceProperties workspace = new WorkspaceProperties();
        workspace.setProjectId("workspace");
        return new MybatisObservabilityQueryService(
                observability, tracing, comments, new ObjectMapper(), workspace);
    }

    private interface Invocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }

    private static Invocation unsupported() {
        return (method, args) -> { throw new UnsupportedOperationException(method); };
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> invocation.invoke(method.getName(), args));
    }
}
