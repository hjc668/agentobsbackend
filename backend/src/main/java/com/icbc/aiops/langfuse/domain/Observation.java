package com.icbc.aiops.langfuse.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@lombok.Value
public class Observation {
    String id;
    String traceId;
    String traceName;
    String parentObservationId;
    String name;
    ObservationType type;
    Instant startTime;
    Instant endTime;
    ObservationLevel level;
    String model;
    long latencyMs;
    long inputTokens;
    long outputTokens;
    BigDecimal totalCost;
    Object input;
    Object output;
    Instant completionStartTime;
    Long timeToFirstTokenMs;
    String statusMessage;
    String modelId;
    Object modelParameters;
    Map<String, Object> usageDetails;
    Map<String, Object> costDetails;
    String promptName;
    Integer promptVersion;
    Map<String, Object> metadata;

    public String id() { return id; }
    public String traceId() { return traceId; }
    public String traceName() { return traceName; }
    public String parentObservationId() { return parentObservationId; }
    public String name() { return name; }
    public ObservationType type() { return type; }
    public Instant startTime() { return startTime; }
    public Instant endTime() { return endTime; }
    public ObservationLevel level() { return level; }
    public String model() { return model; }
    public long latencyMs() { return latencyMs; }
    public long inputTokens() { return inputTokens; }
    public long outputTokens() { return outputTokens; }
    public BigDecimal totalCost() { return totalCost; }
    public Object input() { return input; }
    public Object output() { return output; }
    public Instant completionStartTime() { return completionStartTime; }
    public Long timeToFirstTokenMs() { return timeToFirstTokenMs; }
    public String statusMessage() { return statusMessage; }
    public String modelId() { return modelId; }
    public Object modelParameters() { return modelParameters; }
    public Map<String, Object> usageDetails() { return usageDetails; }
    public Map<String, Object> costDetails() { return costDetails; }
    public String promptName() { return promptName; }
    public Integer promptVersion() { return promptVersion; }
    public Map<String, Object> metadata() { return metadata; }

}
