package com.icbc.aiops.langfuse.domain;

import java.util.List;

@lombok.Value
public class SessionDetail {
    SessionSummary summary;
    List<TraceSummary> traces;
    List<Observation> observations;

    public SessionSummary summary() { return summary; }
    public List<TraceSummary> traces() { return traces; }
    public List<Observation> observations() { return observations; }

}

