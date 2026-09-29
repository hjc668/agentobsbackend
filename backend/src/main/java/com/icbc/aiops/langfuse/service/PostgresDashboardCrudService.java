package com.icbc.aiops.langfuse.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.aiops.langfuse.api.DashboardCreateRequest;
import com.icbc.aiops.langfuse.api.DashboardMetadataRequest;
import com.icbc.aiops.langfuse.api.PageResponse;
import com.icbc.aiops.langfuse.domain.Dashboard;
import com.icbc.aiops.langfuse.postgres.mapper.DashboardMapper;
import com.icbc.aiops.langfuse.postgres.mapper.DashboardRows.DashboardRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mybatis")
public class PostgresDashboardCrudService implements DashboardCrudService {

    private static final Map<String, Object> EMPTY_DEFINITION = com.icbc.aiops.langfuse.util.Java8Collections.mapOf("widgets", com.icbc.aiops.langfuse.util.Java8Collections.listOf());
    private final DashboardMapper mapper;
    private final ObjectMapper objectMapper;

    public PostgresDashboardCrudService(DashboardMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResponse<Dashboard> findDashboards(String projectId, int page, int size) {
        long offset = (long) page * size;
        List<Dashboard> dashboards = mapper.selectDashboards(projectId, size, offset)
                .stream().map(this::toDashboard).collect(java.util.stream.Collectors.toList());
        return PageResponse.of(dashboards, page, size, mapper.countDashboards(projectId));
    }

    @Override
    public Dashboard getDashboard(String projectId, String dashboardId) {
        return toDashboard(required(mapper.selectById(projectId, dashboardId), dashboardId));
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public Dashboard createDashboard(String projectId, DashboardCreateRequest request, String actor) {
        return insert(projectId, request.name().trim(), normalized(request.description()), EMPTY_DEFINITION,
                com.icbc.aiops.langfuse.util.Java8Collections.listOf(), actor);
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public Dashboard updateMetadata(
            String projectId, String dashboardId, DashboardMetadataRequest request, String actor) {
        if (mapper.updateMetadata(projectId, dashboardId, request.name().trim(), normalized(request.description()),
                resolveActorId(actor)) == 0) {
            throw notFound(dashboardId);
        }
        return getDashboard(projectId, dashboardId);
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public Dashboard updateDefinition(
            String projectId, String dashboardId, Map<String, Object> definition, String actor) {
        validateDefinition(definition);
        if (mapper.updateDefinition(projectId, dashboardId, json(definition), resolveActorId(actor)) == 0) {
            throw notFound(dashboardId);
        }
        return getDashboard(projectId, dashboardId);
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public Dashboard updateFilters(
            String projectId, String dashboardId, List<Map<String, Object>> filters, String actor) {
        if (mapper.updateFilters(projectId, dashboardId, json(filters), resolveActorId(actor)) == 0) {
            throw notFound(dashboardId);
        }
        return getDashboard(projectId, dashboardId);
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public Dashboard cloneDashboard(String projectId, String dashboardId, String actor) {
        Dashboard source = getDashboard(projectId, dashboardId);
        String cloneName = nextCloneName(projectId, source.name());
        return insert(projectId, cloneName, source.description(), source.definition(), source.filters(), actor);
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public void deleteDashboard(String projectId, String dashboardId) {
        if (mapper.deleteDashboard(projectId, dashboardId) == 0) throw notFound(dashboardId);
    }

    private Dashboard insert(String projectId, String name, String description,
            Map<String, Object> definition, List<Map<String, Object>> filters, String actor) {
        String id = UUID.randomUUID().toString();
        mapper.insert(id, projectId, name, description, json(definition), json(filters), normalizedActor(actor));
        return getDashboard(projectId, id);
    }

    private String nextCloneName(String projectId, String sourceName) {
        List<String> names = mapper.selectNamesStartingWith(projectId, sourceName + " (Clone");
        String candidate = sourceName + " (Clone)";
        if (!names.contains(candidate)) return candidate;
        int sequence = 2;
        while (names.contains(sourceName + " (Clone " + sequence + ")")) sequence++;
        return sourceName + " (Clone " + sequence + ")";
    }

    private Dashboard toDashboard(DashboardRow row) {
        return new Dashboard(row.id(), row.projectId(), row.name(), row.description(), parseDefinition(row.definitionJson()),
                parseFilters(row.filtersJson()), row.projectId() == null ? "LANGFUSE" : "PROJECT", row.createdBy(),
                row.updatedBy(), toInstant(row.createdAt()), toInstant(row.updatedAt()));
    }

    private Map<String, Object> parseDefinition(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) {
            return EMPTY_DEFINITION;
        }
    }

    private List<Map<String, Object>> parseFilters(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<List<Map<String, Object>>>() { });
        } catch (Exception exception) {
            return com.icbc.aiops.langfuse.util.Java8Collections.listOf();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new InvalidRequestException("Dashboard data is not valid JSON.");
        }
    }

    private static void validateDefinition(Map<String, Object> definition) {
        if (!(definition.get("widgets") instanceof List<?>)) {
            throw new InvalidRequestException("Dashboard definition must contain a widgets array.");
        }
    }

    private static DashboardRow required(DashboardRow row, String id) {
        if (row == null) throw notFound(id);
        return row;
    }

    private static ResourceNotFoundException notFound(String id) {
        return new ResourceNotFoundException("Dashboard not found: " + id);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizedActor(String value) {
        return value == null || value.trim().isEmpty() ? "migration-service" : value.trim();
    }

    private String resolveActorId(String actor) {
        return mapper.selectActorId(normalizedActor(actor));
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
