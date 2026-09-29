package com.icbc.aiops.langfuse.service;

import com.icbc.aiops.langfuse.api.PageResponse;
import com.icbc.aiops.langfuse.domain.DashboardSummary;
import com.icbc.aiops.langfuse.domain.MetricPoint;
import com.icbc.aiops.langfuse.domain.Observation;
import com.icbc.aiops.langfuse.domain.ObservationFacetValue;
import com.icbc.aiops.langfuse.domain.ObservationPulsePoint;
import com.icbc.aiops.langfuse.domain.SessionDetail;
import com.icbc.aiops.langfuse.domain.SessionSummary;
import com.icbc.aiops.langfuse.domain.TraceDetail;
import com.icbc.aiops.langfuse.domain.TraceComment;
import com.icbc.aiops.langfuse.domain.TraceScore;
import com.icbc.aiops.langfuse.domain.TraceSummary;
import com.icbc.aiops.langfuse.domain.TraceView;
import com.icbc.aiops.langfuse.domain.UserSummary;
import com.icbc.aiops.langfuse.domain.UserDetail;
import java.util.List;

public interface ObservabilityQueryService {
    DashboardSummary getSummary();
    List<MetricPoint> getMetricTimeSeries();
    PageResponse<TraceSummary> findTraces(TraceQuery query);
    TraceDetail getTrace(String traceId);
    List<Observation> findTraceObservations(String traceId);
    default TraceView getTraceView(String traceId) {
        return new TraceView(getTrace(traceId), findTraceObservations(traceId));
    }
    List<TraceScore> findTraceScores(String traceId);
    List<TraceComment> findTraceComments(String traceId);
    PageResponse<Observation> findObservations(ObservationQuery query);
    List<ObservationFacetValue> findObservationFacets(
            ObservationQuery query, ObservationFacet field, int limit);
    List<ObservationPulsePoint> findObservationPulse(
            ObservationQuery query, PulseBucket bucket);
    PageResponse<SessionSummary> findSessions(String search, int page, int size);
    SessionDetail getSession(String sessionId);
    PageResponse<UserSummary> findUsers(String search, String environment, int page, int size);
    UserDetail getUser(String userId);
}
