package com.icbc.aiops.langfuse.service;

import com.icbc.aiops.langfuse.domain.ObservationLevel;
import com.icbc.aiops.langfuse.domain.ObservationType;
import java.time.Instant;

@lombok.Value
public class ObservationQuery {
    String search;
    String environment;
    /**
     * AgentObs 将 service_name 作为第一标识维度，因此按服务筛选是缩小查询范围的最低成本方式。
     * 该字段可选；留空时保持前端现有的跨全部服务查询行为。
     */
    String serviceName;
    ObservationType type;
    ObservationLevel level;
    String model;
    String traceId;
    String tag;
    Instant fromTimestamp;
    Instant toTimestamp;
    SortBy sortBy;
    TraceQuery.SortDirection direction;
    int page;
    int size;

    public String search() { return search; }
    public String environment() { return environment; }
    public String serviceName() { return serviceName; }
    public ObservationType type() { return type; }
    public ObservationLevel level() { return level; }
    public String model() { return model; }
    public String traceId() { return traceId; }
    public String tag() { return tag; }
    public Instant fromTimestamp() { return fromTimestamp; }
    public Instant toTimestamp() { return toTimestamp; }
    public SortBy sortBy() { return sortBy; }
    public TraceQuery.SortDirection direction() { return direction; }
    public int page() { return page; }
    public int size() { return size; }

    /** 复制查询并设置明确的左闭右开时间窗口，供服务层强制限制查询范围。 */
    public ObservationQuery withWindow(Instant from, Instant to) {
        return new ObservationQuery(search, environment, serviceName, type, level, model, traceId, tag,
                from, to, sortBy, direction, page, size);
    }

    public enum SortBy { TIMESTAMP, LATENCY, TOKENS, COST }
}
