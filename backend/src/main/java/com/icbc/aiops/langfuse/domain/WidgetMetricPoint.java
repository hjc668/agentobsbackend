package com.icbc.aiops.langfuse.domain;

import java.math.BigDecimal;
import java.time.Instant;

@lombok.Value
public class WidgetMetricPoint {
    Instant bucket;
    String dimension;
    BigDecimal value;

    public Instant bucket() { return bucket; }
    public String dimension() { return dimension; }
    public BigDecimal value() { return value; }

}
