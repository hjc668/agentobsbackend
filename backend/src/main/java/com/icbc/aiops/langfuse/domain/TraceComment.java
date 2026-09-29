package com.icbc.aiops.langfuse.domain;

import java.time.Instant;

@lombok.Value
public class TraceComment {
    String id;
    String objectType;
    String objectId;
    String content;
    String authorUserId;
    String dataField;
    Instant createdAt;
    Instant updatedAt;

    public String id() { return id; }
    public String objectType() { return objectType; }
    public String objectId() { return objectId; }
    public String content() { return content; }
    public String authorUserId() { return authorUserId; }
    public String dataField() { return dataField; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

}
