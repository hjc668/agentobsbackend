package com.icbc.aiops.langfuse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.icbc.aiops.langfuse.domain.DashboardWidget;
import com.icbc.aiops.langfuse.domain.WidgetMetricPoint;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockWidgetMetricExecutionServiceTest {

    @Test
    void createsSevenDailyPointsEndingAtRequestedTimestamp() {
        Instant to = Instant.parse("2026-09-29T00:00:00Z");
        MockWidgetMetricExecutionService service = serviceForView("TRACES");

        List<WidgetMetricPoint> points = service.execute("project", "widget", null, to);

        assertEquals(7, points.size());
        assertEquals(to.minusSeconds(6 * 24 * 60 * 60), points.get(0).bucket());
        assertEquals(to, points.get(6).bucket());
        assertNull(points.get(0).dimension());
        assertEquals(BigDecimal.valueOf(34), points.get(0).value());
        assertEquals(BigDecimal.valueOf(70), points.get(6).value());
    }

    @Test
    void supportsObservationViewAndDefaultEndTime() {
        List<WidgetMetricPoint> points = serviceForView("OBSERVATIONS")
                .execute("project", "widget", null, null);
        assertEquals(Instant.parse("2026-08-25T00:00:00Z"), points.get(6).bucket());
    }

    @Test
    void rejectsDeferredEvaluationView() {
        assertThrows(InvalidRequestException.class,
                () -> serviceForView("EVALUATIONS").execute("project", "widget", null, null));
    }

    private static MockWidgetMetricExecutionService serviceForView(String view) {
        DashboardWidget widget = new DashboardWidget("widget", "project", "Widget", null, view,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "LINE",
                Collections.emptyMap(), 1, "owner", "creator", "updater", Instant.EPOCH, Instant.EPOCH);
        DashboardWidgetCrudService crud = (DashboardWidgetCrudService) Proxy.newProxyInstance(
                DashboardWidgetCrudService.class.getClassLoader(),
                new Class<?>[] {DashboardWidgetCrudService.class},
                (proxy, method, args) -> {
                    if ("getWidget".equals(method.getName())) return widget;
                    throw new UnsupportedOperationException(method.getName());
                });
        return new MockWidgetMetricExecutionService(crud);
    }
}
