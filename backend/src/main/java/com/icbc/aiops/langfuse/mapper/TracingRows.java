package com.icbc.aiops.langfuse.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AgentObs Tracing 查询使用的行模型。
 *
 * <p>这些模型有意保持与服务层现有公共 DTO 映射结构一致，因此将 Tracing 切换到拆分后的
 * AgentObs 表不会改变 HTTP 契约。Session 和 User 详情路径也复用这些行模型。
 *
 * <p>与 {@link ObservabilityRows} 使用相同的 Lombok 模式：不可变 value class，
 * 同时显式提供 record 风格的访问器。
 */
public final class TracingRows {

    private TracingRows() {
    }

    /**
     * 单条 Observation 记录。与旧扁平表不同，AgentObs 只在 Trace 表保存 Trace 级字段
     *（trace name、tags），因此 {@code traceName} 由服务层通过第二次有界查询补充。
     */
    @lombok.Value
    public static class ObservationRow {
        String id;
        String traceId;
        String traceName;
        String parentObservationId;
        String name;
        String type;
        LocalDateTime startTime;
        LocalDateTime endTime;
        String level;
        /**
         * Provider 返回的模型名称。通过 SQL alias {@code modelName} 而不是 {@code model}
         * 映射：如果表达式使用 {@code model} 作为 alias，ClickHouse 将 alias 替换进 WHERE
         * 后会与同名基础列冲突，并触发 "Code 352 Block structure mismatch"。
         */
        String modelName;
        long latencyMs;
        long inputTokens;
        long outputTokens;
        BigDecimal totalCost;
        String inputJson;
        String outputJson;
        LocalDateTime completionStartTime;
        Long timeToFirstTokenMs;
        String statusMessage;
        String modelId;
        String modelParametersJson;
        String usageDetailsJson;
        String costDetailsJson;
        String promptName;
        Integer promptVersion;
        String metadataJson;

        public String id() { return id; }
        public String traceId() { return traceId; }
        public String traceName() { return traceName; }
        public String parentObservationId() { return parentObservationId; }
        public String name() { return name; }
        public String type() { return type; }
        public LocalDateTime startTime() { return startTime; }
        public LocalDateTime endTime() { return endTime; }
        public String level() { return level; }
        public String modelName() { return modelName; }
        public long latencyMs() { return latencyMs; }
        public long inputTokens() { return inputTokens; }
        public long outputTokens() { return outputTokens; }
        public BigDecimal totalCost() { return totalCost; }
        public String inputJson() { return inputJson; }
        public String outputJson() { return outputJson; }
        public LocalDateTime completionStartTime() { return completionStartTime; }
        public Long timeToFirstTokenMs() { return timeToFirstTokenMs; }
        public String statusMessage() { return statusMessage; }
        public String modelId() { return modelId; }
        public String modelParametersJson() { return modelParametersJson; }
        public String usageDetailsJson() { return usageDetailsJson; }
        public String costDetailsJson() { return costDetailsJson; }
        public String promptName() { return promptName; }
        public Integer promptVersion() { return promptVersion; }
        public String metadataJson() { return metadataJson; }
    }

    /** Trace 列表/详情记录，指标从 {@link TraceMetricsRow} 合并。 */
    @lombok.Value
    public static class TraceRow {
        String id;
        String name;
        LocalDateTime timestamp;
        String userId;
        String sessionId;
        String environment;
        String status;
        long latencyMs;
        long totalTokens;
        BigDecimal totalCost;
        int observationCount;
        String tagsJson;

        public String id() { return id; }
        public String name() { return name; }
        public LocalDateTime timestamp() { return timestamp; }
        public String userId() { return userId; }
        public String sessionId() { return sessionId; }
        public String environment() { return environment; }
        public String status() { return status; }
        public long latencyMs() { return latencyMs; }
        public long totalTokens() { return totalTokens; }
        public BigDecimal totalCost() { return totalCost; }
        public int observationCount() { return observationCount; }
        public String tagsJson() { return tagsJson; }
    }

    @lombok.Value
    public static class TraceDetailRow {
        String id;
        String name;
        LocalDateTime timestamp;
        String userId;
        String sessionId;
        String environment;
        String status;
        long latencyMs;
        long totalTokens;
        BigDecimal totalCost;
        int observationCount;
        String tagsJson;
        String inputJson;
        String outputJson;
        String metadataJson;
        String release;
        String version;

        public String id() { return id; }
        public String name() { return name; }
        public LocalDateTime timestamp() { return timestamp; }
        public String userId() { return userId; }
        public String sessionId() { return sessionId; }
        public String environment() { return environment; }
        public String status() { return status; }
        public long latencyMs() { return latencyMs; }
        public long totalTokens() { return totalTokens; }
        public BigDecimal totalCost() { return totalCost; }
        public int observationCount() { return observationCount; }
        public String tagsJson() { return tagsJson; }
        public String inputJson() { return inputJson; }
        public String outputJson() { return outputJson; }
        public String metadataJson() { return metadataJson; }
        public String release() { return release; }
        public String version() { return version; }
    }

    /**
     * Observation 不包含的 Trace 级字段。只查询当前页，绝不 join 到无界 Observation 列表查询中。
     */
    @lombok.Value
    public static class TraceLookupRow {
        String traceId;
        String traceName;
        String tagsJson;

        public String traceId() { return traceId; }
        public String traceName() { return traceName; }
        public String tagsJson() { return tagsJson; }
    }

    /** 从 Observations 聚合得到的当前页逐 Trace 汇总指标。 */
    @lombok.Value
    public static class TraceMetricsRow {
        String traceId;
        long observationCount;
        long totalTokens;
        BigDecimal totalCost;

        public String traceId() { return traceId; }
        public long observationCount() { return observationCount; }
        public long totalTokens() { return totalTokens; }
        public BigDecimal totalCost() { return totalCost; }
    }

    /** 从 locator 索引读取的单条 Trace 最小/最大时间范围及所属服务。 */
    @lombok.Value
    public static class TraceLocatorRow {
        String serviceName;
        LocalDateTime minStartTime;
        LocalDateTime maxEndTime;

        public String serviceName() { return serviceName; }
        public LocalDateTime minStartTime() { return minStartTime; }
        public LocalDateTime maxEndTime() { return maxEndTime; }
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
}
