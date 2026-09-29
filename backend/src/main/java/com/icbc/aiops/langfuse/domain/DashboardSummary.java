package com.icbc.aiops.langfuse.domain;

import java.math.BigDecimal;

@lombok.Value
public class DashboardSummary {
    int traceCount;
    int observationCount;
    int errorCount;
    long totalTokens;
    BigDecimal totalCost;
    long averageLatencyMs;

    public int traceCount() { return traceCount; }
    public int observationCount() { return observationCount; }
    public int errorCount() { return errorCount; }
    public long totalTokens() { return totalTokens; }
    public BigDecimal totalCost() { return totalCost; }
    public long averageLatencyMs() { return averageLatencyMs; }

}

