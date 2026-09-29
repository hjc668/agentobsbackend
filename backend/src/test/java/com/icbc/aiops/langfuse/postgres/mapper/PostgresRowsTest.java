package com.icbc.aiops.langfuse.postgres.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PostgresRowsTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 9, 29, 1, 2, 3);

    @Test
    void dashboardAndWidgetRowsExposeMappedColumns() {
        DashboardRows.DashboardRow dashboard = new DashboardRows.DashboardRow(
                "d1", "p1", "dashboard", "description", "{}", "[]", "creator", "updater", TIME, TIME);
        assertEquals("d1", dashboard.id());
        assertEquals("p1", dashboard.projectId());
        assertEquals("dashboard", dashboard.name());
        assertEquals("description", dashboard.description());
        assertEquals("{}", dashboard.definitionJson());
        assertEquals("[]", dashboard.filtersJson());
        assertEquals("creator", dashboard.createdBy());
        assertEquals("updater", dashboard.updatedBy());
        assertEquals(TIME, dashboard.createdAt());
        assertEquals(TIME, dashboard.updatedAt());

        DashboardWidgetRows.DashboardWidgetRow widget = new DashboardWidgetRows.DashboardWidgetRow(
                "w1", "p1", "widget", "description", "TRACES", "[]", "[]", "[]", "LINE", "{}",
                1, "creator", "updater", TIME, TIME);
        assertEquals("w1", widget.id());
        assertEquals("p1", widget.projectId());
        assertEquals("widget", widget.name());
        assertEquals("description", widget.description());
        assertEquals("TRACES", widget.view());
        assertEquals("[]", widget.dimensionsJson());
        assertEquals("[]", widget.metricsJson());
        assertEquals("[]", widget.filtersJson());
        assertEquals("LINE", widget.chartType());
        assertEquals("{}", widget.chartConfigJson());
        assertEquals(1, widget.minVersion());
        assertEquals("creator", widget.createdBy());
        assertEquals("updater", widget.updatedBy());
        assertEquals(TIME, widget.createdAt());
        assertEquals(TIME, widget.updatedAt());
    }

    @Test
    void promptAndCommentRowsExposeMappedColumns() {
        PromptRows.PromptRow prompt = new PromptRows.PromptRow(
                "pr1", "p1", "prompt", 2, "text", "\"hello\"", "{}", "[]", "[]", "commit",
                "creator", TIME, TIME);
        assertEquals("pr1", prompt.id());
        assertEquals("p1", prompt.projectId());
        assertEquals("prompt", prompt.name());
        assertEquals(2, prompt.version());
        assertEquals("text", prompt.type());
        assertEquals("\"hello\"", prompt.promptJson());
        assertEquals("{}", prompt.configJson());
        assertEquals("[]", prompt.labelsJson());
        assertEquals("[]", prompt.tagsJson());
        assertEquals("commit", prompt.commitMessage());
        assertEquals("creator", prompt.createdBy());
        assertEquals(TIME, prompt.createdAt());
        assertEquals(TIME, prompt.updatedAt());

        CommentRows.CommentRow comment = new CommentRows.CommentRow(
                "c1", "TRACE", "t1", "content", "author", "input", TIME, TIME);
        assertEquals("c1", comment.id());
        assertEquals("TRACE", comment.objectType());
        assertEquals("t1", comment.objectId());
        assertEquals("content", comment.content());
        assertEquals("author", comment.authorUserId());
        assertEquals("input", comment.dataField());
        assertEquals(TIME, comment.createdAt());
        assertEquals(TIME, comment.updatedAt());
    }
}
