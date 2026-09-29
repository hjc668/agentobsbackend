package com.icbc.aiops.langfuse.domain;

import java.math.BigDecimal;
import java.time.Instant;

@lombok.Value
public class SessionSummary {
    String id;
    Instant createdAt;
    String userId;
    int traceCount;
    int observationCount;
    long totalTokens;
    BigDecimal totalCost;
    long durationMs;

    public String id() { return id; }
    public Instant createdAt() { return createdAt; }
    public String userId() { return userId; }
    public int traceCount() { return traceCount; }
    public int observationCount() { return observationCount; }
    public long totalTokens() { return totalTokens; }
    public BigDecimal totalCost() { return totalCost; }
    public long durationMs() { return durationMs; }

}

