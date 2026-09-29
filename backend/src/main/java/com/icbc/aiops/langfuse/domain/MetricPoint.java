package com.icbc.aiops.langfuse.domain;

import java.math.BigDecimal;
import java.time.Instant;

@lombok.Value
public class MetricPoint {
    Instant timestamp;
    int traceCount;
    int errorCount;
    long totalTokens;
    BigDecimal totalCost;
    long averageLatencyMs;

    public Instant timestamp() { return timestamp; }
    public int traceCount() { return traceCount; }
    public int errorCount() { return errorCount; }
    public long totalTokens() { return totalTokens; }
    public BigDecimal totalCost() { return totalCost; }
    public long averageLatencyMs() { return averageLatencyMs; }

}

