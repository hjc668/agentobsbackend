package com.icbc.aiops.langfuse.postgres.mapper;

import java.time.LocalDateTime;

public final class DashboardWidgetRows {

    private DashboardWidgetRows() {
    }

    @lombok.Value
    public static class DashboardWidgetRow {
        String id;
        String projectId;
        String name;
        String description;
        String view;
        String dimensionsJson;
        String metricsJson;
        String filtersJson;
        String chartType;
        String chartConfigJson;
        int minVersion;
        String createdBy;
        String updatedBy;
        LocalDateTime createdAt;
        LocalDateTime updatedAt;

        public String id() { return id; }
        public String projectId() { return projectId; }
        public String name() { return name; }
        public String description() { return description; }
        public String view() { return view; }
        public String dimensionsJson() { return dimensionsJson; }
        public String metricsJson() { return metricsJson; }
        public String filtersJson() { return filtersJson; }
        public String chartType() { return chartType; }
        public String chartConfigJson() { return chartConfigJson; }
        public int minVersion() { return minVersion; }
        public String createdBy() { return createdBy; }
        public String updatedBy() { return updatedBy; }
        public LocalDateTime createdAt() { return createdAt; }
        public LocalDateTime updatedAt() { return updatedAt; }

    }
}
