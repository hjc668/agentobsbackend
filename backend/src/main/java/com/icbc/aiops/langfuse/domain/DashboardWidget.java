package com.icbc.aiops.langfuse.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@lombok.Value
public class DashboardWidget {
    String id;
    String projectId;
    String name;
    String description;
    String view;
    List<Map<String, Object>> dimensions;
    List<Map<String, Object>> metrics;
    List<Map<String, Object>> filters;
    String chartType;
    Map<String, Object> chartConfig;
    int minVersion;
    String owner;
    String createdBy;
    String updatedBy;
    Instant createdAt;
    Instant updatedAt;

    public String id() { return id; }
    public String projectId() { return projectId; }
    public String name() { return name; }
    public String description() { return description; }
    public String view() { return view; }
    public List<Map<String, Object>> dimensions() { return dimensions; }
    public List<Map<String, Object>> metrics() { return metrics; }
    public List<Map<String, Object>> filters() { return filters; }
    public String chartType() { return chartType; }
    public Map<String, Object> chartConfig() { return chartConfig; }
    public int minVersion() { return minVersion; }
    public String owner() { return owner; }
    public String createdBy() { return createdBy; }
    public String updatedBy() { return updatedBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

}
