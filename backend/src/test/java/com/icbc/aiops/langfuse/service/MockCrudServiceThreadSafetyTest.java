package com.icbc.aiops.langfuse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.icbc.aiops.langfuse.api.DashboardCreateRequest;
import com.icbc.aiops.langfuse.api.DashboardWidgetRequest;
import com.icbc.aiops.langfuse.api.PromptCreateRequest;
import com.icbc.aiops.langfuse.domain.Dashboard;
import com.icbc.aiops.langfuse.domain.DashboardWidget;
import com.icbc.aiops.langfuse.domain.PromptVersion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MockCrudServiceThreadSafetyTest {

    @Test
    void dashboardDefinitionIsDeeplyIsolatedAndImmutable() {
        MockDashboardCrudService service = new MockDashboardCrudService();
        Map<String, Object> widget = mutableMap("id", "widget-1", "options",
                new ArrayList<String>(Arrays.asList("first")));
        List<Object> widgets = new ArrayList<Object>();
        widgets.add(widget);
        Map<String, Object> definition = mutableMap("widgets", widgets,
                "layout", mutableMap("columns", 2));

        Dashboard updated = service.updateDefinition("demo-project", "dashboard-support", definition, "tester");
        widget.put("id", "changed");
        widgets.add(mutableMap("id", "widget-2"));
        definition.put("layout", mutableMap("columns", 4));
        @SuppressWarnings("unchecked")
        List<String> originalOptions = (List<String>) widget.get("options");
        originalOptions.add("second");

        Dashboard stored = service.getDashboard("demo-project", updated.id());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> storedWidgets = (List<Map<String, Object>>) stored.definition().get("widgets");
        assertEquals(1, storedWidgets.size());
        assertEquals("widget-1", storedWidgets.get(0).get("id"));
        assertEquals(Collections.singletonList("first"), storedWidgets.get(0).get("options"));
        assertEquals(2, ((Map<?, ?>) stored.definition().get("layout")).get("columns"));
        assertThrows(UnsupportedOperationException.class,
                () -> stored.definition().put("new", "value"));
        assertThrows(UnsupportedOperationException.class,
                () -> storedWidgets.add(mutableMap("id", "forbidden")));
        assertThrows(UnsupportedOperationException.class,
                () -> storedWidgets.get(0).put("id", "forbidden"));
        @SuppressWarnings("unchecked")
        List<String> storedOptions = (List<String>) storedWidgets.get(0).get("options");
        assertThrows(UnsupportedOperationException.class, () -> storedOptions.add("forbidden"));
    }

    @Test
    void dashboardFiltersAreDeeplyIsolatedAndImmutable() {
        MockDashboardCrudService service = new MockDashboardCrudService();
        List<Object> values = new ArrayList<Object>(Arrays.<Object>asList("ok"));
        Map<String, Object> filter = mutableMap("field", "status", "values", values,
                "metadata", mutableMap("source", "request"));
        List<Map<String, Object>> filters = new ArrayList<Map<String, Object>>();
        filters.add(filter);

        Dashboard updated = service.updateFilters("demo-project", "dashboard-support", filters, "tester");
        values.add("error");
        filter.put("field", "changed");
        filters.clear();

        List<Map<String, Object>> stored = service.getDashboard("demo-project", updated.id()).filters();
        assertEquals(1, stored.size());
        assertEquals("status", stored.get(0).get("field"));
        assertEquals(Collections.singletonList("ok"), stored.get(0).get("values"));
        assertEquals("request", ((Map<?, ?>) stored.get(0).get("metadata")).get("source"));
        assertThrows(UnsupportedOperationException.class, () -> stored.clear());
        assertThrows(UnsupportedOperationException.class,
                () -> stored.get(0).put("field", "forbidden"));
        @SuppressWarnings("unchecked")
        List<Object> storedValues = (List<Object>) stored.get(0).get("values");
        assertThrows(UnsupportedOperationException.class, () -> storedValues.add("forbidden"));
    }

    @Test
    void widgetCreateAndUpdateSnapshotRequestsWithoutChangingPosition() {
        MockDashboardWidgetCrudService service = new MockDashboardWidgetCrudService();
        List<Object> dimensionValues = new ArrayList<Object>(Arrays.<Object>asList("hour"));
        Map<String, Object> dimension = mutableMap("field", "timestamp", "values", dimensionValues);
        Map<String, Object> metric = mutableMap("measure", "count", "settings",
                mutableMap("precision", 2));
        Map<String, Object> filter = mutableMap("field", "status", "values",
                new ArrayList<String>(Arrays.asList("ok")));
        List<Map<String, Object>> dimensions = mutableMapList(dimension);
        List<Map<String, Object>> metrics = mutableMapList(metric);
        List<Map<String, Object>> filters = mutableMapList(filter);
        List<Object> palette = new ArrayList<Object>(Arrays.<Object>asList("blue"));
        Map<String, Object> chartConfig = mutableMap("palette", palette,
                "axis", mutableMap("minimum", 0));

        DashboardWidget first = service.createWidget("project", widgetRequest(
                "first", dimensions, metrics, filters, chartConfig), "tester");
        DashboardWidget second = service.createWidget("project", widgetRequest(
                "second", mutableMapList(mutableMap("field", "name")),
                mutableMapList(mutableMap("measure", "count")),
                new ArrayList<Map<String, Object>>(), new LinkedHashMap<String, Object>()), "tester");

        dimension.put("field", "changed");
        dimensionValues.add("day");
        ((Map<String, Object>) metric.get("settings")).put("precision", 9);
        filters.clear();
        palette.add("red");
        chartConfig.put("new", true);

        DashboardWidget stored = service.getWidget("project", first.id());
        assertEquals("timestamp", stored.dimensions().get(0).get("field"));
        assertEquals(Collections.singletonList("hour"), stored.dimensions().get(0).get("values"));
        assertEquals(2, ((Map<?, ?>) stored.metrics().get(0).get("settings")).get("precision"));
        assertEquals(1, stored.filters().size());
        assertEquals(Collections.singletonList("blue"), stored.chartConfig().get("palette"));
        assertFalse(stored.chartConfig().containsKey("new"));
        assertThrows(UnsupportedOperationException.class, () -> stored.dimensions().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> stored.dimensions().get(0).put("field", "forbidden"));
        @SuppressWarnings("unchecked")
        List<Object> storedDimensionValues = (List<Object>) stored.dimensions().get(0).get("values");
        assertThrows(UnsupportedOperationException.class, () -> storedDimensionValues.add("forbidden"));
        @SuppressWarnings("unchecked")
        List<Object> storedPalette = (List<Object>) stored.chartConfig().get("palette");
        assertThrows(UnsupportedOperationException.class, () -> storedPalette.add("forbidden"));

        Map<String, Object> updateMetric = mutableMap("measure", "duration",
                "settings", mutableMap("unit", "ms"));
        List<Map<String, Object>> updateMetrics = mutableMapList(updateMetric);
        DashboardWidget updated = service.updateWidget("project", first.id(), widgetRequest(
                "first updated", mutableMapList(mutableMap("field", "timestamp")), updateMetrics,
                new ArrayList<Map<String, Object>>(), mutableMap("stacked", true)), "tester");
        updateMetric.put("measure", "changed");
        updateMetrics.clear();

        List<DashboardWidget> ordered = service.findWidgets("project", 0, 10).items();
        assertEquals(Arrays.asList(updated.id(), second.id()),
                Arrays.asList(ordered.get(0).id(), ordered.get(1).id()));
        assertEquals("duration", service.getWidget("project", first.id()).metrics().get(0).get("measure"));
    }

    @Test
    void textPromptConfigIsDeeplyIsolatedAndImmutable() {
        MockPromptCrudService service = new MockPromptCrudService();
        List<Object> stop = new ArrayList<Object>(Arrays.<Object>asList("END"));
        Map<String, Object> nested = mutableMap("stop", stop);
        Map<String, Object> config = mutableMap("model", nested);

        PromptVersion created = service.createVersion("project",
                new PromptCreateRequest("text-prompt", "text", "Hello {{name}}", config,
                        new ArrayList<String>(Arrays.asList("staging", "staging")),
                        new ArrayList<String>(Arrays.asList("test", "test")), "initial"), "tester");
        stop.add("HALT");
        nested.put("temperature", 1);
        config.clear();

        PromptVersion stored = service.getPromptVersion("project", created.id());
        Map<?, ?> storedModel = (Map<?, ?>) stored.config().get("model");
        assertEquals(Collections.singletonList("END"), storedModel.get("stop"));
        assertFalse(storedModel.containsKey("temperature"));
        assertEquals(Arrays.asList("staging", "latest"), stored.labels());
        assertEquals(Collections.singletonList("test"), stored.tags());
        assertThrows(UnsupportedOperationException.class,
                () -> stored.config().put("new", "value"));
        @SuppressWarnings("unchecked")
        List<Object> storedStop = (List<Object>) storedModel.get("stop");
        assertThrows(UnsupportedOperationException.class, () -> storedStop.add("forbidden"));
        assertThrows(UnsupportedOperationException.class, () -> stored.labels().add("forbidden"));
    }

    @Test
    void chatPromptIsDeeplyIsolatedAndImmutable() {
        MockPromptCrudService service = new MockPromptCrudService();
        List<Object> content = new ArrayList<Object>(Arrays.<Object>asList("Hello"));
        Map<String, Object> message = mutableMap("role", "system", "content", content);
        List<Map<String, Object>> prompt = mutableMapList(message);
        Map<String, Object> config = mutableMap("options", mutableMap("stream", false));

        PromptVersion created = service.createVersion("project",
                new PromptCreateRequest("chat-prompt", "chat", prompt, config, null, null, null), "tester");
        content.add("Changed");
        message.put("role", "user");
        prompt.clear();
        ((Map<String, Object>) config.get("options")).put("stream", true);

        PromptVersion stored = service.getPromptVersion("project", created.id());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> storedPrompt = (List<Map<String, Object>>) stored.prompt();
        assertEquals(1, storedPrompt.size());
        assertEquals("system", storedPrompt.get(0).get("role"));
        assertEquals(Collections.singletonList("Hello"), storedPrompt.get(0).get("content"));
        assertFalse((Boolean) ((Map<?, ?>) stored.config().get("options")).get("stream"));
        assertThrows(UnsupportedOperationException.class, () -> storedPrompt.clear());
        assertThrows(UnsupportedOperationException.class,
                () -> storedPrompt.get(0).put("role", "forbidden"));
        @SuppressWarnings("unchecked")
        List<Object> storedContent = (List<Object>) storedPrompt.get(0).get("content");
        assertThrows(UnsupportedOperationException.class, () -> storedContent.add("forbidden"));
        @SuppressWarnings("unchecked")
        Map<String, Object> storedOptions = (Map<String, Object>) stored.config().get("options");
        assertThrows(UnsupportedOperationException.class,
                () -> storedOptions.put("stream", true));
    }

    @Test
    void concurrentPromptCreationProducesContinuousVersionsAndOneLatestLabel() throws Exception {
        MockPromptCrudService service = new MockPromptCrudService();
        int taskCount = 12;
        ExecutorService pool = Executors.newFixedThreadPool(taskCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PromptVersion>> futures = new ArrayList<Future<PromptVersion>>();
        try {
            for (int index = 0; index < taskCount; index++) {
                final int task = index;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent start");
                    }
                    return service.createVersion("project",
                            new PromptCreateRequest("concurrent", "text", "prompt-" + task,
                                    null, null, null, null), "tester");
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<PromptVersion> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }

        List<PromptVersion> versions = service.findVersions("project", "concurrent");
        Set<Integer> actualVersions = new TreeSet<Integer>();
        long latestCount = 0;
        for (PromptVersion version : versions) {
            actualVersions.add(version.version());
            if (version.labels().contains("latest")) {
                latestCount++;
            }
        }
        Set<Integer> expectedVersions = new TreeSet<Integer>();
        for (int version = 1; version <= taskCount; version++) {
            expectedVersions.add(version);
        }
        assertEquals(taskCount, versions.size());
        assertEquals(expectedVersions, actualVersions);
        assertEquals(1, latestCount);
    }

    @Test
    void concurrentDashboardClonesHaveUniqueNames() throws Exception {
        MockDashboardCrudService service = new MockDashboardCrudService();
        Dashboard source = service.createDashboard("project",
                new DashboardCreateRequest("Concurrent dashboard", "test"), "tester");
        int taskCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(taskCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Dashboard>> futures = new ArrayList<Future<Dashboard>>();
        try {
            for (int index = 0; index < taskCount; index++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent start");
                    }
                    return service.cloneDashboard("project", source.id(), "tester");
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            Set<String> names = new HashSet<String>();
            for (Future<Dashboard> future : futures) {
                names.add(future.get(10, TimeUnit.SECONDS).name());
            }
            assertEquals(taskCount, names.size());
            assertTrue(names.contains("Concurrent dashboard (Clone)"));
            assertTrue(names.contains("Concurrent dashboard (Clone 10)"));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static DashboardWidgetRequest widgetRequest(String name,
            List<Map<String, Object>> dimensions, List<Map<String, Object>> metrics,
            List<Map<String, Object>> filters, Map<String, Object> chartConfig) {
        return new DashboardWidgetRequest(name, "description", "TRACES", dimensions, metrics,
                filters, "LINE_TIME_SERIES", chartConfig);
    }

    private static List<Map<String, Object>> mutableMapList(Map<String, Object> value) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        result.add(value);
        return result;
    }

    private static Map<String, Object> mutableMap(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index < keyValues.length; index += 2) {
            result.put((String) keyValues[index], keyValues[index + 1]);
        }
        return result;
    }
}
