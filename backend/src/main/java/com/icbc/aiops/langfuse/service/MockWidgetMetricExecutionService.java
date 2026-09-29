package com.icbc.aiops.langfuse.service;

import com.icbc.aiops.langfuse.domain.DashboardWidget;
import com.icbc.aiops.langfuse.domain.WidgetMetricPoint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("mock")
public class MockWidgetMetricExecutionService implements WidgetMetricExecutionService {
    private final DashboardWidgetCrudService widgetService;

    public MockWidgetMetricExecutionService(DashboardWidgetCrudService widgetService) { this.widgetService = widgetService; }

    @Override
    public List<WidgetMetricPoint> execute(String projectId, String widgetId, Instant fromTimestamp, Instant toTimestamp) {
        DashboardWidget widget = widgetService.getWidget(projectId, widgetId);
        if (!"TRACES".equals(widget.view()) && !"OBSERVATIONS".equals(widget.view())) {
            throw new InvalidRequestException("Evaluation widget execution is deferred.");
        }
        Instant to = toTimestamp == null ? Instant.parse("2026-08-25T00:00:00Z") : toTimestamp;
        List<WidgetMetricPoint> points = new ArrayList<>();
        for (int index = 6; index >= 0; index--) {
            points.add(new WidgetMetricPoint(to.minus(index, ChronoUnit.DAYS), null, BigDecimal.valueOf(70 - index * 6L)));
        }
        return points;
    }
}
