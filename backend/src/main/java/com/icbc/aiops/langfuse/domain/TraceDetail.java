package com.icbc.aiops.langfuse.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@lombok.Value
public class TraceDetail {
    String id;
    String name;
    Instant timestamp;
    String userId;
    String sessionId;
    String environment;
    TraceStatus status;
    long latencyMs;
    long totalTokens;
    BigDecimal totalCost;
    int observationCount;
    List<String> tags;
    Object input;
    Object output;
    Map<String, Object> metadata;
    String release;
    String version;

    public String id() { return id; }
    public String name() { return name; }
    public Instant timestamp() { return timestamp; }
    public String userId() { return userId; }
    public String sessionId() { return sessionId; }
    public String environment() { return environment; }
    public TraceStatus status() { return status; }
    public long latencyMs() { return latencyMs; }
    public long totalTokens() { return totalTokens; }
    public BigDecimal totalCost() { return totalCost; }
    public int observationCount() { return observationCount; }
    public List<String> tags() { return tags; }
    public Object input() { return input; }
    public Object output() { return output; }
    public Map<String, Object> metadata() { return metadata; }
    public String release() { return release; }
    public String version() { return version; }


    public TraceSummary toSummary() {
        return new TraceSummary(id, name, timestamp, userId, sessionId, environment,
                status, latencyMs, totalTokens, totalCost, observationCount, tags);
    }
}

