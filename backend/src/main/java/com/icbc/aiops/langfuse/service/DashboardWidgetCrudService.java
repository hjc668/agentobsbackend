package com.icbc.aiops.langfuse.service;

import com.icbc.aiops.langfuse.api.DashboardWidgetRequest;
import com.icbc.aiops.langfuse.api.PageResponse;
import com.icbc.aiops.langfuse.domain.DashboardWidget;

public interface DashboardWidgetCrudService {
    PageResponse<DashboardWidget> findWidgets(String projectId, int page, int size);
    DashboardWidget getWidget(String projectId, String widgetId);
    DashboardWidget createWidget(String projectId, DashboardWidgetRequest request, String actor);
    DashboardWidget updateWidget(String projectId, String widgetId, DashboardWidgetRequest request, String actor);
    DashboardWidget cloneWidget(String projectId, String widgetId, String actor);
    void deleteWidget(String projectId, String widgetId);
}
