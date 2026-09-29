package com.icbc.aiops.langfuse.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ObservabilityRows {

    private ObservabilityRows() {
    }

    @lombok.Value
    public static class TraceSummaryStatsRow {
        long traceCount;
        long errorCount;
        long averageLatencyMs;

        public long traceCount() { return traceCount; }
        public long errorCount() { return errorCount; }
        public long averageLatencyMs() { return averageLatencyMs; }
    }

    @lombok.Value
    public static class ObservationSummaryStatsRow {
        long observationCount;
        long totalTokens;
        BigDecimal totalCost;

        public long observationCount() { return observationCount; }
        public long totalTokens() { return totalTokens; }
        public BigDecimal totalCost() { return totalCost; }
    }

    @lombok.Value
    public static class TraceMetricRow {
        LocalDateTime timestamp;
        long traceCount;
        long errorCount;
        long averageLatencyMs;

        public LocalDateTime timestamp() { return timestamp; }
        public long traceCount() { return traceCount; }
        public long errorCount() { return errorCount; }
        public long averageLatencyMs() { return averageLatencyMs; }
    }

    @lombok.Value
    public static class ObservationMetricRow {
        LocalDateTime timestamp;
        long observationCount;
        long totalTokens;
        BigDecimal totalCost;

        public LocalDateTime timestamp() { return timestamp; }
        public long observationCount() { return observationCount; }
        public long totalTokens() { return totalTokens; }
        public BigDecimal totalCost() { return totalCost; }
    }

    @lombok.Value
    public static class FacetRow {
        String value;
        long count;

        public String value() { return value; }
        public long count() { return count; }

    }

    @lombok.Value
    public static class PulseRow {
        LocalDateTime timestamp;
        long count;
        BigDecimal totalCost;
        long averageLatencyMs;

        public LocalDateTime timestamp() { return timestamp; }
        public long count() { return count; }
        public BigDecimal totalCost() { return totalCost; }
        public long averageLatencyMs() { return averageLatencyMs; }

    }

    @lombok.Value
    public static class ScoreRow {
        String id;
        String traceId;
        String observationId;
        String name;
        String dataType;
        Double numericValue;
        String stringValue;
        String source;
        String comment;
        LocalDateTime createdAt;

        public String id() { return id; }
        public String traceId() { return traceId; }
        public String observationId() { return observationId; }
        public String name() { return name; }
        public String dataType() { return dataType; }
        public Double numericValue() { return numericValue; }
        public String stringValue() { return stringValue; }
        public String source() { return source; }
        public String comment() { return comment; }
        public LocalDateTime createdAt() { return createdAt; }

    }

    @lombok.Value
    public static class SessionRow {
        String id;
        LocalDateTime createdAt;
        String userId;
        long traceCount;
        long observationCount;
        long totalTokens;
        BigDecimal totalCost;
        long durationMs;

        public String id() { return id; }
        public LocalDateTime createdAt() { return createdAt; }
        public String userId() { return userId; }
        public long traceCount() { return traceCount; }
        public long observationCount() { return observationCount; }
        public long totalTokens() { return totalTokens; }
        public BigDecimal totalCost() { return totalCost; }
        public long durationMs() { return durationMs; }

    }

    @lombok.Value
    public static class UserRow {
        String id;
        String environment;
        LocalDateTime firstEvent;
        LocalDateTime lastEvent;
        long traceCount;
        long observationCount;
        long totalTokens;
        BigDecimal totalCost;

        public String id() { return id; }
        public String environment() { return environment; }
        public LocalDateTime firstEvent() { return firstEvent; }
        public LocalDateTime lastEvent() { return lastEvent; }
        public long traceCount() { return traceCount; }
        public long observationCount() { return observationCount; }
        public long totalTokens() { return totalTokens; }
        public BigDecimal totalCost() { return totalCost; }

    }
}
