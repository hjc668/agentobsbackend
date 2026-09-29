package com.icbc.aiops.langfuse.domain;

import java.math.BigDecimal;
import java.time.Instant;

@lombok.Value
public class ObservationPulsePoint {
    Instant timestamp;
    long count;
    BigDecimal totalCost;
    long averageLatencyMs;

    public Instant timestamp() { return timestamp; }
    public long count() { return count; }
    public BigDecimal totalCost() { return totalCost; }
    public long averageLatencyMs() { return averageLatencyMs; }

}
