package com.icbc.aiops.langfuse.domain;

@lombok.Value
public class ObservationFacetValue {
    String value;
    long count;

    public String value() { return value; }
    public long count() { return count; }

}
