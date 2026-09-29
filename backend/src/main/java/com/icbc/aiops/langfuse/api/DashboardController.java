package com.icbc.aiops.langfuse.api;

import com.icbc.aiops.langfuse.domain.Dashboard;
import com.icbc.aiops.langfuse.service.DashboardCrudService;
import com.icbc.aiops.langfuse.config.WorkspaceProperties;
import com.icbc.aiops.langfuse.security.CurrentAamUser;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
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
@RequestMapping({"/api/v1/workspace/dashboards", "/api/v1/projects/{ignoredProjectId}/dashboards"})
public class DashboardController {

    private final DashboardCrudService service;
    private final WorkspaceProperties workspace;

    public DashboardController(DashboardCrudService service, WorkspaceProperties workspace) {
        this.service = service; this.workspace = workspace;
    }

    @GetMapping
    public PageResponse<Dashboard> findDashboards(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        return service.findDashboards(workspace.getProjectId(), page, size);
    }

    @GetMapping("/{dashboardId}")
    public Dashboard getDashboard(
            @PathVariable String dashboardId) {
        return service.getDashboard(workspace.getProjectId(), dashboardId);
    }

    @PostMapping
    public ResponseEntity<Dashboard> createDashboard(
            @Valid @RequestBody DashboardCreateRequest request) {
        Dashboard created = service.createDashboard(workspace.getProjectId(), request, CurrentAamUser.aamId());
        return ResponseEntity.created(resourceUri(created.id())).body(created);
    }

    @PutMapping("/{dashboardId}/metadata")
    public Dashboard updateMetadata(
            @PathVariable String dashboardId,
            @Valid @RequestBody DashboardMetadataRequest request) {
        return service.updateMetadata(workspace.getProjectId(), dashboardId, request, CurrentAamUser.aamId());
    }

    @PutMapping("/{dashboardId}/definition")
    public Dashboard updateDefinition(
            @PathVariable String dashboardId,
            @Valid @RequestBody DashboardDefinitionRequest request) {
        return service.updateDefinition(workspace.getProjectId(), dashboardId, request.definition(), CurrentAamUser.aamId());
    }

    @PutMapping("/{dashboardId}/filters")
    public Dashboard updateFilters(
            @PathVariable String dashboardId,
            @Valid @RequestBody DashboardFiltersRequest request) {
        return service.updateFilters(workspace.getProjectId(), dashboardId, request.filters(), CurrentAamUser.aamId());
    }

    @PostMapping("/{dashboardId}/clone")
    public ResponseEntity<Dashboard> cloneDashboard(
            @PathVariable String dashboardId) {
        Dashboard cloned = service.cloneDashboard(workspace.getProjectId(), dashboardId, CurrentAamUser.aamId());
        return ResponseEntity.created(resourceUri(cloned.id())).body(cloned);
    }

    @DeleteMapping("/{dashboardId}")
    public ResponseEntity<Void> deleteDashboard(
            @PathVariable String dashboardId) {
        service.deleteDashboard(workspace.getProjectId(), dashboardId);
        return ResponseEntity.noContent().build();
    }

    private static java.net.URI resourceUri(String id) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/workspace/dashboards/{id}").buildAndExpand(id).toUri();
    }
}
