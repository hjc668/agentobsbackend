package com.icbc.aiops.langfuse.service;

import com.icbc.aiops.langfuse.api.DashboardWidgetRequest;
import com.icbc.aiops.langfuse.api.PageResponse;
import com.icbc.aiops.langfuse.domain.DashboardWidget;
import com.icbc.aiops.langfuse.util.ImmutableJsonSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("mock")
public class MockDashboardWidgetCrudService implements DashboardWidgetCrudService {
    private final CopyOnWriteArrayList<DashboardWidget> widgets = new CopyOnWriteArrayList<>();

    @Override public synchronized PageResponse<DashboardWidget> findWidgets(String projectId, int page, int size) {
        List<DashboardWidget> visible = widgets.stream().filter(item -> projectId.equals(item.projectId())).collect(java.util.stream.Collectors.toList());
        int from = Math.min(page * size, visible.size());
        int to = Math.min(from + size, visible.size());
        return PageResponse.of(visible.subList(from, to), page, size, visible.size());
    }
    @Override public synchronized DashboardWidget getWidget(String projectId, String widgetId) {
        return widgets.stream().filter(item -> item.id().equals(widgetId) && projectId.equals(item.projectId()))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Dashboard widget not found: " + widgetId));
    }
    @Override public synchronized DashboardWidget createWidget(String projectId, DashboardWidgetRequest request, String actor) {
        validate(request); Instant now = Instant.now();
        DashboardWidget item = new DashboardWidget(UUID.randomUUID().toString(), projectId, request.name().trim(),
                request.description(), request.view(), ImmutableJsonSnapshot.list(request.dimensions()),
                ImmutableJsonSnapshot.list(request.metrics()), ImmutableJsonSnapshot.list(request.filters()),
                request.chartType(), ImmutableJsonSnapshot.map(request.chartConfig()), 1, "PROJECT", actor, actor, now, now);
        widgets.add(item); return item;
    }
    @Override public synchronized DashboardWidget updateWidget(String projectId, String widgetId, DashboardWidgetRequest request, String actor) {
        validate(request); DashboardWidget old = getWidget(projectId, widgetId);
        DashboardWidget item = new DashboardWidget(old.id(), old.projectId(), request.name().trim(), request.description(),
                request.view(), ImmutableJsonSnapshot.list(request.dimensions()),
                ImmutableJsonSnapshot.list(request.metrics()), ImmutableJsonSnapshot.list(request.filters()), request.chartType(),
                ImmutableJsonSnapshot.map(request.chartConfig()), old.minVersion(), old.owner(), old.createdBy(), actor, old.createdAt(), Instant.now());
        widgets.replaceAll(widget -> widget.id().equals(old.id()) ? item : widget); return item;
    }
    @Override public synchronized DashboardWidget cloneWidget(String projectId, String widgetId, String actor) {
        DashboardWidget source = getWidget(projectId, widgetId);
        return createWidget(projectId, new DashboardWidgetRequest(source.name() + " (Clone)", source.description(),
                source.view(), source.dimensions(), source.metrics(), source.filters(), source.chartType(), source.chartConfig()), actor);
    }
    @Override public synchronized void deleteWidget(String projectId, String widgetId) {
        DashboardWidget current = getWidget(projectId, widgetId);
        widgets.removeIf(widget -> widget.id().equals(current.id()));
    }
    private static void validate(DashboardWidgetRequest request) {
        if (!com.icbc.aiops.langfuse.util.Java8Collections.listOf("TRACES", "OBSERVATIONS").contains(request.view())) throw new InvalidRequestException("Widget view must be TRACES or OBSERVATIONS.");
    }
}
