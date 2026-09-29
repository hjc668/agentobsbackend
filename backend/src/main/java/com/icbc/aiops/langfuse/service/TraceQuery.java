package com.icbc.aiops.langfuse.service;

import com.icbc.aiops.langfuse.domain.TraceStatus;
import java.time.Instant;

@lombok.Value
public class TraceQuery {
    String search;
    TraceStatus status;
    String environment;
    String userId;
    String sessionId;
    String tag;
    Instant fromTimestamp;
    Instant toTimestamp;
    SortBy sortBy;
    SortDirection direction;
    int page;
    int size;

    public String search() { return search; }
    public TraceStatus status() { return status; }
    public String environment() { return environment; }
    public String userId() { return userId; }
    public String sessionId() { return sessionId; }
    public String tag() { return tag; }
    public Instant fromTimestamp() { return fromTimestamp; }
    public Instant toTimestamp() { return toTimestamp; }
    public SortBy sortBy() { return sortBy; }
    public SortDirection direction() { return direction; }
    public int page() { return page; }
    public int size() { return size; }

    /** 复制查询并设置明确的左闭右开时间窗口，供服务层强制限制查询范围。 */
    public TraceQuery withWindow(Instant from, Instant to) {
        return new TraceQuery(search, status, environment, userId, sessionId, tag, from, to,
                sortBy, direction, page, size);
    }


    public enum SortBy {
        TIMESTAMP,
        LATENCY,
        TOKENS,
        COST
    }

    public enum SortDirection {
        ASC,
        DESC
    }
}
