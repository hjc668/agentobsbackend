package com.icbc.aiops.langfuse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.icbc.aiops.langfuse.security.AamRole;
import com.icbc.aiops.langfuse.security.AamUserPrincipal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiValueObjectsTest {

    @Test
    void aamResponsesExposeTheAuthenticatedUserAndPayload() {
        AamUserPrincipal admin = new AamUserPrincipal(
                "u001", "Alice", "notes-1", "dept-1", "AIOps", AamRole.ADMIN);
        AamLoginResponse login = AamLoginResponse.of(admin, "csrf-token");

        assertEquals("u001", login.getUser().getUserId());
        assertEquals("Alice", login.getUser().getUserName());
        assertEquals("notes-1", login.getUser().getNotesId());
        assertEquals("dept-1", login.getUser().getDepartmentId());
        assertEquals("AIOps", login.getUser().getDepartmentName());
        assertEquals("ADMIN", login.getUser().getCurrentRoleId());
        assertEquals("ADMIN", login.getUser().getRoles().get(0).getRoleId());
        assertEquals("管理员", login.getUser().getRoles().get(0).getRoleName());
        assertEquals("csrf-token", login.getUser().getCsrfToken());
        assertTrue(login.getMenu().isEmpty());

        AamLoginResponse viewer = AamLoginResponse.of(new AamUserPrincipal(
                "u002", "Bob", "dept-2", "Support", AamRole.VIEW), "viewer-token");
        assertEquals("查看者", viewer.getUser().getRoles().get(0).getRoleName());

        AamCommonResponse<AamLoginResponse> response = AamCommonResponse.success("ok", login);
        assertEquals(AamCommonResponse.SUCCESS_CODE, response.getCode());
        assertEquals("ok", response.getMsg());
        assertSame(login, response.getResult());
    }

    @Test
    void requestObjectsExposeConstructorValues() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("key", "value");
        List<Map<String, Object>> maps = Collections.singletonList(map);
        List<String> strings = Arrays.asList("one", "two");

        DashboardCreateRequest create = new DashboardCreateRequest("name", "description");
        assertEquals("name", create.name());
        assertEquals("description", create.description());

        DashboardMetadataRequest metadata = new DashboardMetadataRequest("renamed", "updated");
        assertEquals("renamed", metadata.name());
        assertEquals("updated", metadata.description());

        DashboardDefinitionRequest definition = new DashboardDefinitionRequest(map);
        assertSame(map, definition.definition());
        DashboardFiltersRequest filters = new DashboardFiltersRequest(maps);
        assertSame(maps, filters.filters());

        DashboardWidgetRequest widget = new DashboardWidgetRequest(
                "widget", "description", "TRACES", maps, maps, maps, "LINE", map);
        assertEquals("widget", widget.name());
        assertEquals("description", widget.description());
        assertEquals("TRACES", widget.view());
        assertSame(maps, widget.dimensions());
        assertSame(maps, widget.metrics());
        assertSame(maps, widget.filters());
        assertEquals("LINE", widget.chartType());
        assertSame(map, widget.chartConfig());

        PromptCreateRequest prompt = new PromptCreateRequest(
                "prompt", "text", "hello", map, strings, strings, "commit");
        assertEquals("prompt", prompt.name());
        assertEquals("text", prompt.type());
        assertEquals("hello", prompt.prompt());
        assertSame(map, prompt.config());
        assertSame(strings, prompt.labels());
        assertSame(strings, prompt.tags());
        assertEquals("commit", prompt.commitMessage());
        assertSame(strings, new PromptLabelsRequest(strings).labels());
        assertSame(strings, new PromptTagsRequest(strings).tags());
    }

    @Test
    void pageResponseCalculatesPagesAndSupportsProbedHasNext() {
        List<String> items = Arrays.asList("a", "b");
        PageResponse<String> first = PageResponse.of(items, 0, 2, 5);
        assertSame(items, first.items());
        assertEquals(0, first.page());
        assertEquals(2, first.size());
        assertEquals(5, first.total());
        assertEquals(3, first.totalPages());
        assertTrue(first.hasNext());

        PageResponse<String> last = PageResponse.of(items, 2, 2, 5);
        assertFalse(last.hasNext());
        assertEquals(3, last.totalPages());

        PageResponse<String> empty = PageResponse.of(Collections.<String>emptyList(), 0, 10, 0);
        assertEquals(0, empty.totalPages());
        assertFalse(empty.hasNext());

        PageResponse<String> probed = PageResponse.of(items, 4, 2, 5, true);
        assertTrue(probed.hasNext());
    }

    @Test
    void apiErrorExposesItsDetails() {
        Instant timestamp = Instant.parse("2026-09-29T00:00:00Z");
        ApiError error = new ApiError("invalid", "bad request", timestamp);
        assertEquals("invalid", error.code());
        assertEquals("bad request", error.message());
        assertEquals(timestamp, error.timestamp());
    }
}
