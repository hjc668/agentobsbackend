package com.icbc.aiops.langfuse.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@lombok.Value
public class PromptVersion {
    String id;
    String projectId;
    String name;
    int version;
    String type;
    Object prompt;
    Map<String, Object> config;
    List<String> labels;
    List<String> tags;
    String commitMessage;
    String createdBy;
    Instant createdAt;
    Instant updatedAt;

    public String id() { return id; }
    public String projectId() { return projectId; }
    public String name() { return name; }
    public int version() { return version; }
    public String type() { return type; }
    public Object prompt() { return prompt; }
    public Map<String, Object> config() { return config; }
    public List<String> labels() { return labels; }
    public List<String> tags() { return tags; }
    public String commitMessage() { return commitMessage; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

}
