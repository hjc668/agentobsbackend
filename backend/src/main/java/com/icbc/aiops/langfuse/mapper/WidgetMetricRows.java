package com.icbc.aiops.langfuse.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class WidgetMetricRows {
    private WidgetMetricRows() { }

    @lombok.Value
    public static class WidgetMetricRow {
        LocalDateTime bucket;
        String dimension;
        BigDecimal value;

        public LocalDateTime bucket() { return bucket; }
        public String dimension() { return dimension; }
        public BigDecimal value() { return value; }

    }
}
