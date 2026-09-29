package com.icbc.aiops.langfuse.service;

import com.icbc.aiops.langfuse.api.DashboardCreateRequest;
import com.icbc.aiops.langfuse.api.DashboardMetadataRequest;
import com.icbc.aiops.langfuse.api.PageResponse;
import com.icbc.aiops.langfuse.domain.Dashboard;
import com.icbc.aiops.langfuse.util.ImmutableJsonSnapshot;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("mock")
public class MockDashboardCrudService implements DashboardCrudService {

    private final CopyOnWriteArrayList<Dashboard> dashboards = new CopyOnWriteArrayList<>(com.icbc.aiops.langfuse.util.Java8Collections.listOf(
            new Dashboard("langfuse-home", null, "Langfuse Home", "Default observability overview",
                    com.icbc.aiops.langfuse.util.Java8Collections.mapOf("widgets", com.icbc.aiops.langfuse.util.Java8Collections.listOf()), com.icbc.aiops.langfuse.util.Java8Collections.listOf(), "LANGFUSE", null, null,
                    Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T08:00:00Z")),
            new Dashboard("dashboard-support", "demo-project", "Support quality", "RAG quality and cost metrics",
                    com.icbc.aiops.langfuse.util.Java8Collections.mapOf("widgets", com.icbc.aiops.langfuse.util.Java8Collections.listOf()), com.icbc.aiops.langfuse.util.Java8Collections.listOf(), "PROJECT", "local-user", "local-user",
                    Instant.parse("2026-08-20T08:00:00Z"), Instant.parse("2026-08-30T08:00:00Z"))));

    @Override
    public synchronized PageResponse<Dashboard> findDashboards(String projectId, int page, int size) {
        List<Dashboard> visible = dashboards.stream()
                .filter(dashboard -> dashboard.projectId() == null || dashboard.projectId().equals(projectId))
                .sorted(Comparator.comparing(Dashboard::updatedAt).reversed())
                .collect(java.util.stream.Collectors.toList());
        int from = Math.min(page * size, visible.size());
        int to = Math.min(from + size, visible.size());
        return PageResponse.of(visible.subList(from, to), page, size, visible.size());
    }

    @Override
    public synchronized Dashboard getDashboard(String projectId, String dashboardId) {
        return dashboards.stream()
                .filter(dashboard -> dashboard.id().equals(dashboardId))
                .filter(dashboard -> dashboard.projectId() == null || dashboard.projectId().equals(projectId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Dashboard not found: " + dashboardId));
    }

    @Override
    public synchronized Dashboard createDashboard(String projectId, DashboardCreateRequest request, String actor) {
        Instant now = Instant.now();
        Dashboard created = new Dashboard(UUID.randomUUID().toString(), projectId, request.name().trim(),
                normalized(request.description()), com.icbc.aiops.langfuse.util.Java8Collections.mapOf("widgets", com.icbc.aiops.langfuse.util.Java8Collections.listOf()), com.icbc.aiops.langfuse.util.Java8Collections.listOf(), "PROJECT", actor, actor,
                now, now);
        dashboards.add(created);
        return created;
    }

    @Override
    public synchronized Dashboard updateMetadata(
            String projectId, String dashboardId, DashboardMetadataRequest request, String actor) {
        Dashboard current = requiredProjectDashboard(projectId, dashboardId);
        Dashboard updated = copy(current, request.name().trim(), normalized(request.description()),
                current.definition(), current.filters(), actor);
        replace(current, updated);
        return updated;
    }

    @Override
    public synchronized Dashboard updateDefinition(
            String projectId, String dashboardId, Map<String, Object> definition, String actor) {
        validateDefinition(definition);
        Dashboard current = requiredProjectDashboard(projectId, dashboardId);
        Dashboard updated = copy(current, current.name(), current.description(), ImmutableJsonSnapshot.map(definition),
                current.filters(), actor);
        replace(current, updated);
        return updated;
    }

    @Override
    public synchronized Dashboard updateFilters(
            String projectId, String dashboardId, List<Map<String, Object>> filters, String actor) {
        Dashboard current = requiredProjectDashboard(projectId, dashboardId);
        Dashboard updated = copy(current, current.name(), current.description(), current.definition(),
                ImmutableJsonSnapshot.list(filters), actor);
        replace(current, updated);
        return updated;
    }

    @Override
    public synchronized Dashboard cloneDashboard(String projectId, String dashboardId, String actor) {
        Dashboard source = getDashboard(projectId, dashboardId);
        String cloneName = nextCloneName(projectId, source.name());
        Instant now = Instant.now();
        Dashboard clone = new Dashboard(UUID.randomUUID().toString(), projectId, cloneName, source.description(),
                source.definition(), source.filters(), "PROJECT", actor, actor, now, now);
        dashboards.add(clone);
        return clone;
    }

    @Override
    public synchronized void deleteDashboard(String projectId, String dashboardId) {
        Dashboard current = requiredProjectDashboard(projectId, dashboardId);
        dashboards.removeIf(item -> item.id().equals(current.id()));
    }

    private Dashboard requiredProjectDashboard(String projectId, String dashboardId) {
        return dashboards.stream()
                .filter(dashboard -> dashboard.id().equals(dashboardId) && projectId.equals(dashboard.projectId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Dashboard not found: " + dashboardId));
    }

    private String nextCloneName(String projectId, String sourceName) {
        List<String> names = dashboards.stream().filter(dashboard -> projectId.equals(dashboard.projectId()))
                .map(Dashboard::name).collect(java.util.stream.Collectors.toList());
        String candidate = sourceName + " (Clone)";
        if (!names.contains(candidate)) return candidate;
        int sequence = 2;
        while (names.contains(sourceName + " (Clone " + sequence + ")")) sequence++;
        return sourceName + " (Clone " + sequence + ")";
    }

    private void replace(Dashboard current, Dashboard updated) {
        dashboards.replaceAll(item -> item.id().equals(current.id()) ? updated : item);
    }

    private static Dashboard copy(Dashboard source, String name, String description,
            Map<String, Object> definition, List<Map<String, Object>> filters, String actor) {
        return new Dashboard(source.id(), source.projectId(), name, description, definition, filters,
                source.owner(), source.createdBy(), actor, source.createdAt(), Instant.now());
    }

    private static void validateDefinition(Map<String, Object> definition) {
        if (!(definition.get("widgets") instanceof List<?>)) {
            throw new InvalidRequestException("Dashboard definition must contain a widgets array.");
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
