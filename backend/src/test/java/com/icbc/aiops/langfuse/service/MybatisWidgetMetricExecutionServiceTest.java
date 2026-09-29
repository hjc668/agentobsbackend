package com.icbc.aiops.langfuse.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.icbc.aiops.langfuse.domain.DashboardWidget;
import com.icbc.aiops.langfuse.mapper.WidgetMetricMapper;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MybatisWidgetMetricExecutionServiceTest {

    @Test
    void rejectsObservationCostBreakdownsThatAgentObsDoesNotStore() {
        MybatisWidgetMetricExecutionService service = service(widget(
                "OBSERVATIONS", null, "inputCost"));
        assertThrows(InvalidRequestException.class,
                () -> service.execute("workspace", "widget", null, null));
    }

    @Test
    void acceptsTraceNameGroupingForTraceGrainTotals() {
        DashboardWidget widget = widget("TRACES", "name", "totalTokens");
        final WidgetMetricQuery[] captured = new WidgetMetricQuery[1];

        service(widget, captured).execute("workspace", "widget", null, null);

        assertEquals("name", captured[0].dimension());
        assertEquals("totalTokens", captured[0].measure());
    }

    @Test
    void acceptsAndNormalizesSupportedFilters() {
        DashboardWidget base = widget("OBSERVATIONS", null, "count");
        Map<String, Object> filter = new HashMap<>();
        filter.put("column", "environment");
        filter.put("type", "string");
        filter.put("operator", "contains");
        filter.put("value", "prod");
        DashboardWidget filtered = new DashboardWidget(base.id(), base.projectId(), base.name(),
                base.description(), base.view(), base.dimensions(), base.metrics(),
                Collections.singletonList(filter), base.chartType(), base.chartConfig(), base.minVersion(),
                base.owner(), base.createdBy(), base.updatedBy(), base.createdAt(), base.updatedAt());
        final WidgetMetricQuery[] captured = new WidgetMetricQuery[1];

        service(filtered, captured).execute("workspace", "widget", null, null);

        assertEquals(1, captured[0].filters().size());
        assertEquals("environment", captured[0].filters().get(0).column());
    }

    private static MybatisWidgetMetricExecutionService service(DashboardWidget widget) {
        return service(widget, null);
    }

    private static MybatisWidgetMetricExecutionService service(
            DashboardWidget widget, WidgetMetricQuery[] captured) {
        DashboardWidgetCrudService widgets = (DashboardWidgetCrudService) Proxy.newProxyInstance(
                DashboardWidgetCrudService.class.getClassLoader(),
                new Class<?>[] {DashboardWidgetCrudService.class},
                (proxy, method, args) -> {
                    if ("getWidget".equals(method.getName())) return widget;
                    throw new UnsupportedOperationException(method.getName());
                });
        WidgetMetricMapper mapper = (WidgetMetricMapper) Proxy.newProxyInstance(
                WidgetMetricMapper.class.getClassLoader(), new Class<?>[] {WidgetMetricMapper.class},
                (proxy, method, args) -> {
                    if (captured == null) throw new AssertionError("mapper must not be called");
                    captured[0] = (WidgetMetricQuery) args[0];
                    return Collections.emptyList();
                });
        return new MybatisWidgetMetricExecutionService(widgets, mapper);
    }

    private static DashboardWidget widget(String view, String dimension, String measure) {
        Map<String, Object> metric = new HashMap<>();
        metric.put("measure", measure);
        metric.put("aggregation", "sum");
        Map<String, Object> dimensionValue = new HashMap<>();
        if (dimension != null) dimensionValue.put("field", dimension);
        return new DashboardWidget("widget", "workspace", "name", "", view,
                dimension == null ? Collections.emptyList() : Collections.singletonList(dimensionValue),
                Collections.singletonList(metric), Collections.emptyList(), "NUMBER",
                Collections.emptyMap(), 1, "", "", "", Instant.now(), Instant.now());
    }
}
