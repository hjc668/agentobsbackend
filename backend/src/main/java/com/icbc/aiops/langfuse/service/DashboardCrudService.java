package com.icbc.aiops.langfuse.service;

import com.icbc.aiops.langfuse.api.DashboardCreateRequest;
import com.icbc.aiops.langfuse.api.DashboardMetadataRequest;
import com.icbc.aiops.langfuse.api.PageResponse;
import com.icbc.aiops.langfuse.domain.Dashboard;
import java.util.List;
import java.util.Map;

public interface DashboardCrudService {

    PageResponse<Dashboard> findDashboards(String projectId, int page, int size);

    Dashboard getDashboard(String projectId, String dashboardId);

    Dashboard createDashboard(String projectId, DashboardCreateRequest request, String actor);

    Dashboard updateMetadata(String projectId, String dashboardId, DashboardMetadataRequest request, String actor);

    Dashboard updateDefinition(String projectId, String dashboardId, Map<String, Object> definition, String actor);

    Dashboard updateFilters(String projectId, String dashboardId, List<Map<String, Object>> filters, String actor);

    Dashboard cloneDashboard(String projectId, String dashboardId, String actor);

    void deleteDashboard(String projectId, String dashboardId);
}
