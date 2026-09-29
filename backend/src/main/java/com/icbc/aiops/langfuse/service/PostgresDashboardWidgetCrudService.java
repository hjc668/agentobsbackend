package com.icbc.aiops.langfuse.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.aiops.langfuse.api.DashboardWidgetRequest;
import com.icbc.aiops.langfuse.api.PageResponse;
import com.icbc.aiops.langfuse.domain.DashboardWidget;
import com.icbc.aiops.langfuse.postgres.mapper.DashboardWidgetMapper;
import com.icbc.aiops.langfuse.postgres.mapper.DashboardWidgetRows.DashboardWidgetRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mybatis")
public class PostgresDashboardWidgetCrudService implements DashboardWidgetCrudService {

    private static final Set<String> VIEWS = com.icbc.aiops.langfuse.util.Java8Collections.setOf("TRACES", "OBSERVATIONS");
    private static final Set<String> CHART_TYPES = com.icbc.aiops.langfuse.util.Java8Collections.setOf(
            "LINE_TIME_SERIES", "AREA_TIME_SERIES", "BAR_TIME_SERIES", "HORIZONTAL_BAR",
            "VERTICAL_BAR", "PIE", "NUMBER", "HISTOGRAM", "PIVOT_TABLE");
    private final DashboardWidgetMapper mapper;
    private final ObjectMapper objectMapper;

    public PostgresDashboardWidgetCrudService(DashboardWidgetMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResponse<DashboardWidget> findWidgets(String projectId, int page, int size) {
        long offset = (long) page * size;
        List<DashboardWidget> items = mapper.selectWidgets(projectId, size, offset).stream()
                .map(this::toWidget).collect(java.util.stream.Collectors.toList());
        return PageResponse.of(items, page, size, mapper.countWidgets(projectId));
    }

    @Override
    public DashboardWidget getWidget(String projectId, String widgetId) {
        DashboardWidgetRow row = mapper.selectById(projectId, widgetId);
        if (row == null) throw notFound(widgetId);
        return toWidget(row);
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public DashboardWidget createWidget(String projectId, DashboardWidgetRequest request, String actor) {
        validate(request);
        String id = UUID.randomUUID().toString();
        mapper.insert(id, projectId, request.name().trim(), normalized(request.description()), request.view(),
                json(request.dimensions()), json(request.metrics()), json(request.filters()), request.chartType(),
                json(request.chartConfig()), actor(actor));
        return getWidget(projectId, id);
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public DashboardWidget updateWidget(
            String projectId, String widgetId, DashboardWidgetRequest request, String actor) {
        validate(request);
        if (mapper.update(projectId, widgetId, request.name().trim(), normalized(request.description()),
                request.view(), json(request.dimensions()), json(request.metrics()), json(request.filters()),
                request.chartType(), json(request.chartConfig()), resolveActorId(actor)) == 0) throw notFound(widgetId);
        return getWidget(projectId, widgetId);
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public DashboardWidget cloneWidget(String projectId, String widgetId, String actor) {
        DashboardWidget source = getWidget(projectId, widgetId);
        DashboardWidgetRequest request = new DashboardWidgetRequest(source.name() + " (Clone)", source.description(),
                source.view(), source.dimensions(), source.metrics(), source.filters(), source.chartType(),
                source.chartConfig());
        return createWidget(projectId, request, actor);
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public void deleteWidget(String projectId, String widgetId) {
        if (mapper.delete(projectId, widgetId) == 0) throw notFound(widgetId);
    }

    private DashboardWidget toWidget(DashboardWidgetRow row) {
        return new DashboardWidget(row.id(), row.projectId(), row.name(), row.description(), row.view(),
                list(row.dimensionsJson()), list(row.metricsJson()), list(row.filtersJson()), row.chartType(),
                map(row.chartConfigJson()), row.minVersion(), row.projectId() == null ? "LANGFUSE" : "PROJECT",
                row.createdBy(), row.updatedBy(), instant(row.createdAt()), instant(row.updatedAt()));
    }

    private void validate(DashboardWidgetRequest request) {
        if (!VIEWS.contains(request.view())) throw new InvalidRequestException("Widget view must be TRACES or OBSERVATIONS.");
        if (!CHART_TYPES.contains(request.chartType())) throw new InvalidRequestException("Unsupported widget chart type.");
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new InvalidRequestException("Widget configuration is not valid JSON."); }
    }

    private List<Map<String, Object>> list(String value) {
        try { return objectMapper.readValue(value, new TypeReference<List<Map<String, Object>>>() { }); }
        catch (Exception exception) { return com.icbc.aiops.langfuse.util.Java8Collections.listOf(); }
    }

    private Map<String, Object> map(String value) {
        try { return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() { }); }
        catch (Exception exception) { return com.icbc.aiops.langfuse.util.Java8Collections.mapOf(); }
    }

    private static String normalized(String value) { return value == null ? "" : value.trim(); }
    private static String actor(String value) { return value == null || value.trim().isEmpty() ? "migration-service" : value.trim(); }
    private String resolveActorId(String value) { return mapper.selectActorId(actor(value)); }
    private static Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
    private static ResourceNotFoundException notFound(String id) { return new ResourceNotFoundException("Dashboard widget not found: " + id); }
}
