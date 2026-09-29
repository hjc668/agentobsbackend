package com.icbc.aiops.langfuse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.aiops.langfuse.api.DashboardCreateRequest;
import com.icbc.aiops.langfuse.api.DashboardMetadataRequest;
import com.icbc.aiops.langfuse.api.DashboardWidgetRequest;
import com.icbc.aiops.langfuse.api.PromptCreateRequest;
import com.icbc.aiops.langfuse.postgres.mapper.DashboardMapper;
import com.icbc.aiops.langfuse.postgres.mapper.DashboardRows.DashboardRow;
import com.icbc.aiops.langfuse.postgres.mapper.DashboardWidgetMapper;
import com.icbc.aiops.langfuse.postgres.mapper.DashboardWidgetRows.DashboardWidgetRow;
import com.icbc.aiops.langfuse.postgres.mapper.PromptMapper;
import com.icbc.aiops.langfuse.postgres.mapper.PromptRows.PromptRow;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PostgresCrudServicesTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 9, 29, 1, 2, 3);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dashboardServiceMapsAndExecutesCrudOperations() {
        DashboardHandler handler = new DashboardHandler();
        PostgresDashboardCrudService service = new PostgresDashboardCrudService(
                proxy(DashboardMapper.class, handler), objectMapper);

        assertEquals(1, service.findDashboards("project", 0, 20).total());
        assertEquals("PROJECT", service.getDashboard("project", "dashboard").owner());
        assertEquals(TIME.toInstant(java.time.ZoneOffset.UTC),
                service.getDashboard("project", "dashboard").createdAt());

        assertEquals("New", service.createDashboard("project",
                new DashboardCreateRequest("  New  ", "  description  "), "  creator  ").name());
        assertEquals("creator", handler.lastActor);
        assertEquals("Updated", service.updateMetadata("project", handler.row.id(),
                new DashboardMetadataRequest(" Updated ", " changed "), "updater").name());

        Map<String, Object> definition = new LinkedHashMap<String, Object>();
        definition.put("widgets", Collections.singletonList("widget"));
        assertEquals(definition, service.updateDefinition(
                "project", handler.row.id(), definition, "updater").definition());
        assertThrows(InvalidRequestException.class, () -> service.updateDefinition(
                "project", handler.row.id(), Collections.<String, Object>emptyMap(), "updater"));

        List<Map<String, Object>> filters = Collections.singletonList(
                Collections.<String, Object>singletonMap("field", "status"));
        assertEquals(filters, service.updateFilters("project", handler.row.id(), filters, "updater").filters());

        handler.cloneNames.add(handler.row.name() + " (Clone)");
        assertEquals(handler.row.name() + " (Clone 2)",
                service.cloneDashboard("project", handler.row.id(), "creator").name());
        service.deleteDashboard("project", handler.row.id());
        assertNull(handler.row);
        assertThrows(ResourceNotFoundException.class, () -> service.getDashboard("project", "missing"));
        assertThrows(ResourceNotFoundException.class, () -> service.deleteDashboard("project", "missing"));
    }

    @Test
    void dashboardServiceFallsBackForInvalidStoredJsonAndMissingActor() {
        DashboardHandler handler = new DashboardHandler();
        handler.row = new DashboardRow("system", null, "System", "", "not-json", "not-json",
                null, null, null, null);
        PostgresDashboardCrudService service = new PostgresDashboardCrudService(
                proxy(DashboardMapper.class, handler), objectMapper);
        assertEquals("LANGFUSE", service.getDashboard("project", "system").owner());
        assertTrue(service.getDashboard("project", "system").definition().containsKey("widgets"));
        assertTrue(service.getDashboard("project", "system").filters().isEmpty());
        assertNull(service.getDashboard("project", "system").createdAt());

        service.createDashboard("project", new DashboardCreateRequest("Name", null), " ");
        assertEquals("migration-service", handler.lastActor);
        handler.updateCount = 0;
        assertThrows(ResourceNotFoundException.class, () -> service.updateMetadata(
                "project", handler.row.id(), new DashboardMetadataRequest("Name", null), "actor"));
    }

    @Test
    void widgetServiceValidatesMapsAndExecutesCrudOperations() {
        WidgetHandler handler = new WidgetHandler();
        PostgresDashboardWidgetCrudService service = new PostgresDashboardWidgetCrudService(
                proxy(DashboardWidgetMapper.class, handler), objectMapper);
        assertEquals(1, service.findWidgets("project", 0, 20).total());
        assertEquals("PROJECT", service.getWidget("project", "widget").owner());

        DashboardWidgetRequest request = widgetRequest(" New Widget ", "TRACES", "LINE_TIME_SERIES");
        assertEquals("New Widget", service.createWidget("project", request, " creator ").name());
        assertEquals("creator", handler.lastActor);
        assertEquals("New Widget", service.updateWidget(
                "project", handler.row.id(), request, "updater").name());
        assertEquals("New Widget (Clone)", service.cloneWidget(
                "project", handler.row.id(), "creator").name());
        service.deleteWidget("project", handler.row.id());
        assertNull(handler.row);

        assertThrows(InvalidRequestException.class, () -> service.createWidget(
                "project", widgetRequest("bad", "EVALUATIONS", "LINE_TIME_SERIES"), "actor"));
        assertThrows(InvalidRequestException.class, () -> service.createWidget(
                "project", widgetRequest("bad", "TRACES", "UNKNOWN"), "actor"));
        assertThrows(ResourceNotFoundException.class, () -> service.getWidget("project", "missing"));
        assertThrows(ResourceNotFoundException.class, () -> service.deleteWidget("project", "missing"));
    }

    @Test
    void widgetServiceFallsBackForInvalidStoredJsonAndFailedUpdate() {
        WidgetHandler handler = new WidgetHandler();
        handler.row = new DashboardWidgetRow("system", null, "System", "", "TRACES", "bad", "bad",
                "bad", "LINE_TIME_SERIES", "bad", 1, null, null, null, null);
        PostgresDashboardWidgetCrudService service = new PostgresDashboardWidgetCrudService(
                proxy(DashboardWidgetMapper.class, handler), objectMapper);
        assertEquals("LANGFUSE", service.getWidget("project", "system").owner());
        assertTrue(service.getWidget("project", "system").dimensions().isEmpty());
        assertTrue(service.getWidget("project", "system").chartConfig().isEmpty());
        assertNull(service.getWidget("project", "system").createdAt());

        handler.updateCount = 0;
        assertThrows(ResourceNotFoundException.class, () -> service.updateWidget(
                "project", "missing", widgetRequest("name", "TRACES", "LINE_TIME_SERIES"), "actor"));
    }

    @Test
    void promptServiceCreatesUpdatesAndDeletesVersions() {
        PromptHandler handler = new PromptHandler();
        PostgresPromptCrudService service = new PostgresPromptCrudService(
                proxy(PromptMapper.class, handler), objectMapper);
        PromptCreateRequest request = new PromptCreateRequest(" Prompt ", null, "hello", null,
                Arrays.asList("staging", "staging", " "), Arrays.asList("tag", "tag"), "  commit  ");

        assertEquals(1, service.createVersion("project", request, " ").version());
        assertEquals("migration-service", handler.lastCreatedBy);
        assertEquals(Arrays.asList("staging", "latest"), service.getPromptVersion("project", handler.row.id()).labels());
        assertEquals(Collections.singletonList("tag"), service.getPromptVersion("project", handler.row.id()).tags());
        assertEquals(1, service.findPrompts("project", "  Prompt ", 0, 20).total());
        assertEquals(1, service.findVersions("project", "Prompt").size());

        assertEquals(Collections.singletonList("production"),
                service.setLabels("project", handler.row.id(), Arrays.asList("production", "production")).labels());
        assertEquals(Collections.singletonList("new-tag"),
                service.updateTags("project", handler.row.id(), Arrays.asList("new-tag", "new-tag")).get(0).tags());

        service.deleteVersion("project", handler.row.id());
        assertNull(handler.row);
        handler.row = promptRow("second", 2, "[\"latest\"]", "[]");
        service.deletePrompt("project", "Prompt");
        assertNull(handler.row);
        assertThrows(ResourceNotFoundException.class, () -> service.getPromptVersion("project", "missing"));
        assertThrows(ResourceNotFoundException.class, () -> service.deletePrompt("project", "missing"));
    }

    @Test
    void promptServiceEnforcesTypeDependencyAndProtectedLabelRules() {
        PromptHandler handler = new PromptHandler();
        PostgresPromptCrudService service = new PostgresPromptCrudService(
                proxy(PromptMapper.class, handler), objectMapper);
        handler.row = promptRow("existing", 1, "[\"latest\"]", "[\"old\"]");

        assertThrows(ConflictException.class, () -> service.createVersion("project",
                new PromptCreateRequest("Prompt", "chat", Collections.emptyList(), null,
                        null, null, null), "actor"));
        handler.protectedLabels = 1;
        assertThrows(ForbiddenOperationException.class, () -> service.setLabels(
                "project", handler.row.id(), Collections.singletonList("protected")));
        handler.protectedLabels = 0;
        handler.labelDependents = 1;
        assertThrows(ConflictException.class, () -> service.setLabels(
                "project", handler.row.id(), Collections.<String>emptyList()));
        handler.labelDependents = 0;
        handler.versionDependents = 1;
        assertThrows(ConflictException.class, () -> service.deleteVersion("project", handler.row.id()));
        handler.versionDependents = 0;
        handler.nameDependents = 1;
        assertThrows(ConflictException.class, () -> service.deletePrompt("project", "Prompt"));
    }

    private static DashboardWidgetRequest widgetRequest(String name, String view, String chartType) {
        List<Map<String, Object>> values = Collections.singletonList(
                Collections.<String, Object>singletonMap("field", "value"));
        return new DashboardWidgetRequest(name, null, view, values, values,
                Collections.<Map<String, Object>>emptyList(), chartType,
                Collections.<String, Object>singletonMap("stacked", false));
    }

    private static PromptRow promptRow(String id, int version, String labels, String tags) {
        return new PromptRow(id, "project", "Prompt", version, "text", "\"hello\"", "{}",
                labels, tags, "commit", "creator", TIME, TIME);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static final class DashboardHandler implements InvocationHandler {
        private DashboardRow row = new DashboardRow("dashboard", "project", "Dashboard", "description",
                "{\"widgets\":[]}", "[]", "creator", "updater", TIME, TIME);
        private final List<String> cloneNames = new ArrayList<String>();
        private int updateCount = 1;
        private String lastActor;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("selectDashboards".equals(name)) return row == null ? Collections.emptyList() : Collections.singletonList(row);
            if ("countDashboards".equals(name)) return row == null ? 0L : 1L;
            if ("selectById".equals(name)) return row != null && row.id().equals(args[1]) ? row : null;
            if ("selectNamesStartingWith".equals(name)) return cloneNames;
            if ("selectActorId".equals(name)) return args[0];
            if ("insert".equals(name)) {
                lastActor = (String) args[6];
                row = new DashboardRow((String) args[0], (String) args[1], (String) args[2], (String) args[3],
                        (String) args[4], (String) args[5], lastActor, lastActor, TIME, TIME);
                return 1;
            }
            if ("updateMetadata".equals(name)) {
                if (updateCount == 0) return 0;
                row = new DashboardRow(row.id(), row.projectId(), (String) args[2], (String) args[3],
                        row.definitionJson(), row.filtersJson(), row.createdBy(), (String) args[4], TIME, TIME);
                return 1;
            }
            if ("updateDefinition".equals(name)) {
                row = new DashboardRow(row.id(), row.projectId(), row.name(), row.description(), (String) args[2],
                        row.filtersJson(), row.createdBy(), (String) args[3], TIME, TIME);
                return updateCount;
            }
            if ("updateFilters".equals(name)) {
                row = new DashboardRow(row.id(), row.projectId(), row.name(), row.description(), row.definitionJson(),
                        (String) args[2], row.createdBy(), (String) args[3], TIME, TIME);
                return updateCount;
            }
            if ("deleteDashboard".equals(name)) {
                if (row == null || !row.id().equals(args[1])) return 0;
                row = null;
                return 1;
            }
            return objectMethod(proxy, method, args);
        }
    }

    private static final class WidgetHandler implements InvocationHandler {
        private DashboardWidgetRow row = new DashboardWidgetRow("widget", "project", "Widget", "description",
                "TRACES", "[]", "[{\"measure\":\"count\"}]", "[]", "LINE_TIME_SERIES", "{}", 1,
                "creator", "updater", TIME, TIME);
        private int updateCount = 1;
        private String lastActor;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("selectWidgets".equals(name)) return row == null ? Collections.emptyList() : Collections.singletonList(row);
            if ("countWidgets".equals(name)) return row == null ? 0L : 1L;
            if ("selectById".equals(name)) return row != null && row.id().equals(args[1]) ? row : null;
            if ("selectActorId".equals(name)) return args[0];
            if ("insert".equals(name)) {
                lastActor = (String) args[10];
                row = new DashboardWidgetRow((String) args[0], (String) args[1], (String) args[2], (String) args[3],
                        (String) args[4], (String) args[5], (String) args[6], (String) args[7], (String) args[8],
                        (String) args[9], 1, lastActor, lastActor, TIME, TIME);
                return 1;
            }
            if ("update".equals(name)) {
                if (updateCount == 0) return 0;
                row = new DashboardWidgetRow(row.id(), row.projectId(), (String) args[2], (String) args[3],
                        (String) args[4], (String) args[5], (String) args[6], (String) args[7], (String) args[8],
                        (String) args[9], row.minVersion(), row.createdBy(), (String) args[10], TIME, TIME);
                return 1;
            }
            if ("delete".equals(name)) {
                if (row == null || !row.id().equals(args[1])) return 0;
                row = null;
                return 1;
            }
            return objectMethod(proxy, method, args);
        }
    }

    private static final class PromptHandler implements InvocationHandler {
        private PromptRow row;
        private int protectedLabels;
        private int labelDependents;
        private int versionDependents;
        private int nameDependents;
        private String lastCreatedBy;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("selectLatestPrompts".equals(name) || "selectVersionsByName".equals(name)) {
                return row == null ? Collections.emptyList() : Collections.singletonList(row);
            }
            if ("countPromptNames".equals(name)) return row == null ? 0L : 1L;
            if ("selectById".equals(name)) return row != null && row.id().equals(args[1]) ? row : null;
            if ("selectLatestByName".equals(name)) return row;
            if ("ensurePromptLock".equals(name) || "removeLabelFromOtherVersions".equals(name)
                    || "deleteVersion".equals(name) || "deletePrompt".equals(name) || "addLabel".equals(name)) {
                if ("deleteVersion".equals(name) || "deletePrompt".equals(name)) row = null;
                return 1;
            }
            if ("lockPromptName".equals(name)) return args[1];
            if ("selectNextVersion".equals(name)) return row == null ? 1 : row.version() + 1;
            if ("countProtectedLabels".equals(name)) return protectedLabels;
            if ("countLabelDependents".equals(name)) return labelDependents;
            if ("countDependentsForVersion".equals(name)) return versionDependents;
            if ("countDependentsForName".equals(name)) return nameDependents;
            if ("insert".equals(name)) {
                lastCreatedBy = (String) args[2];
                row = new PromptRow((String) args[0], (String) args[1], (String) args[4], (Integer) args[5],
                        (String) args[6], (String) args[3], (String) args[7], (String) args[9], (String) args[8],
                        (String) args[10], lastCreatedBy, TIME, TIME);
                return 1;
            }
            if ("updateLabels".equals(name)) {
                row = copyPrompt(row, (String) args[2], row.tagsJson());
                return 1;
            }
            if ("updateTagsForName".equals(name)) {
                row = copyPrompt(row, row.labelsJson(), (String) args[2]);
                return 1;
            }
            return objectMethod(proxy, method, args);
        }

        private static PromptRow copyPrompt(PromptRow value, String labels, String tags) {
            return new PromptRow(value.id(), value.projectId(), value.name(), value.version(), value.type(),
                    value.promptJson(), value.configJson(), labels, tags, value.commitMessage(), value.createdBy(),
                    value.createdAt(), value.updatedAt());
        }
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        if ("toString".equals(method.getName())) return proxy.getClass().getName();
        if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
        if ("equals".equals(method.getName())) return proxy == args[0];
        throw new UnsupportedOperationException(method.getName());
    }
}
