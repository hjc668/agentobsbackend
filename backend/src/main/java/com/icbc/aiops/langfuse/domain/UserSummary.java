package com.icbc.aiops.langfuse.domain;

import java.math.BigDecimal;
import java.time.Instant;

@lombok.Value
public class UserSummary {
    String id;
    String environment;
    Instant firstEvent;
    Instant lastEvent;
    int traceCount;
    int observationCount;
    long totalTokens;
    BigDecimal totalCost;

    public String id() { return id; }
    public String environment() { return environment; }
    public Instant firstEvent() { return firstEvent; }
    public Instant lastEvent() { return lastEvent; }
    public int traceCount() { return traceCount; }
    public int observationCount() { return observationCount; }
    public long totalTokens() { return totalTokens; }
    public BigDecimal totalCost() { return totalCost; }

}
