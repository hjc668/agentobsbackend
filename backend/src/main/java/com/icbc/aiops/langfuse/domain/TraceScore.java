package com.icbc.aiops.langfuse.domain;

import java.time.Instant;

@lombok.Value
public class TraceScore {
    String id;
    String traceId;
    String observationId;
    String name;
    String dataType;
    Double numericValue;
    String stringValue;
    String source;
    String comment;
    Instant createdAt;

    public String id() { return id; }
    public String traceId() { return traceId; }
    public String observationId() { return observationId; }
    public String name() { return name; }
    public String dataType() { return dataType; }
    public Double numericValue() { return numericValue; }
    public String stringValue() { return stringValue; }
    public String source() { return source; }
    public String comment() { return comment; }
    public Instant createdAt() { return createdAt; }

}
