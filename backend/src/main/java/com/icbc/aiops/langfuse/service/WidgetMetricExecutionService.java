package com.icbc.aiops.langfuse.service;

import com.icbc.aiops.langfuse.domain.WidgetMetricPoint;
import java.time.Instant;
import java.util.List;

public interface WidgetMetricExecutionService {
    List<WidgetMetricPoint> execute(String projectId, String widgetId, Instant fromTimestamp, Instant toTimestamp);
}
