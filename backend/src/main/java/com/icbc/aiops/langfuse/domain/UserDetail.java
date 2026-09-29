package com.icbc.aiops.langfuse.domain;

import java.util.List;

@lombok.Value
public class UserDetail {
    UserSummary summary;
    List<TraceSummary> traces;
    List<SessionSummary> sessions;

    public UserSummary summary() { return summary; }
    public List<TraceSummary> traces() { return traces; }
    public List<SessionSummary> sessions() { return sessions; }

}
