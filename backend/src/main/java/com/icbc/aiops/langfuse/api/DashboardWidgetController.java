package com.icbc.aiops.langfuse.api;

import com.icbc.aiops.langfuse.domain.DashboardWidget;
import com.icbc.aiops.langfuse.service.DashboardWidgetCrudService;
import com.icbc.aiops.langfuse.domain.WidgetMetricPoint;
import com.icbc.aiops.langfuse.service.WidgetMetricExecutionService;
import com.icbc.aiops.langfuse.config.WorkspaceProperties;
import com.icbc.aiops.langfuse.security.CurrentAamUser;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Validated
@RestController
@RequestMapping({"/api/v1/workspace/dashboard-widgets", "/api/v1/projects/{ignoredProjectId}/dashboard-widgets"})
public class DashboardWidgetController {
    private final DashboardWidgetCrudService service;
    private final WidgetMetricExecutionService metricService;
    private final WorkspaceProperties workspace;
    public DashboardWidgetController(DashboardWidgetCrudService service, WidgetMetricExecutionService metricService, WorkspaceProperties workspace) {
        this.service = service; this.metricService = metricService; this.workspace = workspace;
    }

    @GetMapping
    public PageResponse<DashboardWidget> findWidgets(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        return service.findWidgets(workspace.getProjectId(), page, size);
    }

    @GetMapping("/{widgetId}")
    public DashboardWidget getWidget(@PathVariable String widgetId) {
        return service.getWidget(workspace.getProjectId(), widgetId);
    }

    @GetMapping("/{widgetId}/metrics")
    public List<WidgetMetricPoint> executeWidget(@PathVariable String widgetId,
            @RequestParam(required = false) Instant fromTimestamp,
            @RequestParam(required = false) Instant toTimestamp) {
        return metricService.execute(workspace.getProjectId(), widgetId, fromTimestamp, toTimestamp);
    }

    @PostMapping
    public ResponseEntity<DashboardWidget> createWidget(
            @Valid @RequestBody DashboardWidgetRequest request) {
        DashboardWidget created = service.createWidget(workspace.getProjectId(), request, CurrentAamUser.aamId());
        return ResponseEntity.created(resourceUri(created.id())).body(created);
    }

    @PutMapping("/{widgetId}")
    public DashboardWidget updateWidget(@PathVariable String widgetId,
            @Valid @RequestBody DashboardWidgetRequest request) {
        return service.updateWidget(workspace.getProjectId(), widgetId, request, CurrentAamUser.aamId());
    }

    @PostMapping("/{widgetId}/clone")
    public ResponseEntity<DashboardWidget> cloneWidget(@PathVariable String widgetId) {
        DashboardWidget cloned = service.cloneWidget(workspace.getProjectId(), widgetId, CurrentAamUser.aamId());
        return ResponseEntity.created(resourceUri(cloned.id())).body(cloned);
    }

    @DeleteMapping("/{widgetId}")
    public ResponseEntity<Void> deleteWidget(@PathVariable String widgetId) {
        service.deleteWidget(workspace.getProjectId(), widgetId);
        return ResponseEntity.noContent().build();
    }

    private static java.net.URI resourceUri(String id) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/workspace/dashboard-widgets/{id}").buildAndExpand(id).toUri();
    }
}
