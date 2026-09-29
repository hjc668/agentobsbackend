package com.icbc.aiops.langfuse.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@lombok.Value
public class Dashboard {
    String id;
    String projectId;
    String name;
    String description;
    Map<String, Object> definition;
    List<Map<String, Object>> filters;
    String owner;
    String createdBy;
    String updatedBy;
    Instant createdAt;
    Instant updatedAt;

    public String id() { return id; }
    public String projectId() { return projectId; }
    public String name() { return name; }
    public String description() { return description; }
    public Map<String, Object> definition() { return definition; }
    public List<Map<String, Object>> filters() { return filters; }
    public String owner() { return owner; }
    public String createdBy() { return createdBy; }
    public String updatedBy() { return updatedBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

}
