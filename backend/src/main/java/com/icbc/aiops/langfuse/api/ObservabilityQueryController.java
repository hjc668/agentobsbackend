package com.icbc.aiops.langfuse.api;

import com.icbc.aiops.langfuse.domain.DashboardSummary;
import com.icbc.aiops.langfuse.domain.MetricPoint;
import com.icbc.aiops.langfuse.domain.Observation;
import com.icbc.aiops.langfuse.domain.ObservationFacetValue;
import com.icbc.aiops.langfuse.domain.ObservationPulsePoint;
import com.icbc.aiops.langfuse.domain.ObservationType;
import com.icbc.aiops.langfuse.domain.SessionDetail;
import com.icbc.aiops.langfuse.domain.ObservationLevel;
import com.icbc.aiops.langfuse.domain.SessionSummary;
import com.icbc.aiops.langfuse.domain.TraceStatus;
import com.icbc.aiops.langfuse.domain.TraceComment;
import com.icbc.aiops.langfuse.domain.TraceScore;
import com.icbc.aiops.langfuse.domain.TraceDetail;
import com.icbc.aiops.langfuse.domain.TraceSummary;
import com.icbc.aiops.langfuse.domain.TraceView;
import com.icbc.aiops.langfuse.domain.UserSummary;
import com.icbc.aiops.langfuse.domain.UserDetail;
import com.icbc.aiops.langfuse.service.ObservabilityQueryService;
import com.icbc.aiops.langfuse.service.InvalidRequestException;
import com.icbc.aiops.langfuse.service.ObservationQuery;
import com.icbc.aiops.langfuse.service.ObservationFacet;
import com.icbc.aiops.langfuse.service.PulseBucket;
import com.icbc.aiops.langfuse.service.TraceQuery;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/v1/observability", "/api/v1/projects/{ignoredProjectId}"})
public class ObservabilityQueryController {

    private final ObservabilityQueryService queryService;

    public ObservabilityQueryController(ObservabilityQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/summary")
    public DashboardSummary getSummary() {
        return queryService.getSummary();
    }

    @GetMapping("/summary/timeseries")
    public List<MetricPoint> getMetricTimeSeries() {
        return queryService.getMetricTimeSeries();
    }

    @GetMapping("/traces")
    public PageResponse<TraceSummary> findTraces(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) TraceStatus status,
            @RequestParam(defaultValue = "") String environment,
            @RequestParam(defaultValue = "") String userId,
            @RequestParam(defaultValue = "") String sessionId,
            @RequestParam(defaultValue = "") String tag,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromTimestamp,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toTimestamp,
            @RequestParam(defaultValue = "TIMESTAMP") TraceQuery.SortBy sortBy,
            @RequestParam(defaultValue = "DESC") TraceQuery.SortDirection direction,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return queryService.findTraces(new TraceQuery(search, status, environment, userId, sessionId, tag,
                        fromTimestamp, toTimestamp, sortBy, direction, page, size));
    }

    @GetMapping("/traces/{traceId}")
    public TraceDetail getTrace(
            @PathVariable String traceId) {
        return queryService.getTrace(traceId);
    }

    @GetMapping("/traces/{traceId}/view")
    public TraceView getTraceView(
            @PathVariable String traceId) {
        return queryService.getTraceView(traceId);
    }

    @GetMapping("/traces/{traceId}/observations")
    public List<Observation> findTraceObservations(
            @PathVariable String traceId) {
        return queryService.findTraceObservations(traceId);
    }

    @GetMapping("/traces/{traceId}/scores")
    public List<TraceScore> findTraceScores(
            @PathVariable String traceId) {
        return queryService.findTraceScores(traceId);
    }

    @GetMapping("/traces/{traceId}/comments")
    public List<TraceComment> findTraceComments(
            @PathVariable String traceId) {
        return queryService.findTraceComments(traceId);
    }

    @GetMapping("/observations")
    public PageResponse<Observation> findObservations(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String environment,
            @RequestParam(defaultValue = "") String serviceName,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "") String model,
            @RequestParam(defaultValue = "") String traceId,
            @RequestParam(defaultValue = "") String tag,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromTimestamp,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toTimestamp,
            @RequestParam(defaultValue = "TIMESTAMP") ObservationQuery.SortBy sortBy,
            @RequestParam(defaultValue = "DESC") TraceQuery.SortDirection direction,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return queryService.findObservations(new ObservationQuery(search, environment, serviceName,
                parseTypeParameter(type), parseLevelParameter(level),
                model, traceId, tag, fromTimestamp, toTimestamp, sortBy, direction, page, size));
    }

    @GetMapping("/observations/facets")
    public List<ObservationFacetValue> findObservationFacets(
            @RequestParam ObservationFacet field,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String environment,
            @RequestParam(defaultValue = "") String serviceName,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "") String model,
            @RequestParam(defaultValue = "") String traceId,
            @RequestParam(defaultValue = "") String tag,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromTimestamp,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toTimestamp) {
        ObservationQuery query = observationQuery(search, environment, serviceName, type, level, model, traceId, tag,
                fromTimestamp, toTimestamp);
        return queryService.findObservationFacets(query, field, limit);
    }

    @GetMapping("/observations/pulse")
    public List<ObservationPulsePoint> findObservationPulse(
            @RequestParam(defaultValue = "DAY") PulseBucket bucket,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String environment,
            @RequestParam(defaultValue = "") String serviceName,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "") String model,
            @RequestParam(defaultValue = "") String traceId,
            @RequestParam(defaultValue = "") String tag,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromTimestamp,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toTimestamp) {
        ObservationQuery query = observationQuery(search, environment, serviceName, type, level, model, traceId, tag,
                fromTimestamp, toTimestamp);
        return queryService.findObservationPulse(query, bucket);
    }

    private static ObservationQuery observationQuery(
            String search,
            String environment,
            String serviceName,
            String type,
            String level,
            String model,
            String traceId,
            String tag,
            Instant fromTimestamp,
            Instant toTimestamp) {
        return new ObservationQuery(search, environment, serviceName,
                parseTypeParameter(type), parseLevelParameter(level), model, traceId, tag,
                fromTimestamp, toTimestamp, ObservationQuery.SortBy.TIMESTAMP,
                TraceQuery.SortDirection.DESC, 0, 50);
    }

    /**
     * 解析 {@code type} 查询参数。
     *
     * <p>解析时不区分大小写，因为 AgentObs 可能存储 {@code span} 或 {@code SPAN}，
     * 调用方无需了解具体存储形式。参数声明为 {@link String} 而不是 {@link ObservationType}，
     * 原因是 Spring 内置 enum 转换区分大小写，会在进入本方法前直接拒绝 {@code ?type=span}。
     *
     * @return 参数不存在或为空时返回 {@code null}，表示“不按 type 筛选”。
     * @throws InvalidRequestException 传入未知类型时抛出。错误请求参数属于调用方错误，
     *         因此返回 400，而不是静默匹配不到数据；这与存储中的未知值处理有意不同，
     *         后者会展示为 UNKNOWN，而不会使请求失败。
     */
    private static ObservationType parseTypeParameter(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        ObservationType type = ObservationType.tryParse(raw);
        if (type == null) {
            throw new InvalidRequestException("Unknown observation type '" + raw + "'. Allowed values: "
                    + allowedNames(ObservationType.filterable()));
        }
        return type;
    }

    /** {@code level} 参数的解析约定与 {@link #parseTypeParameter} 相同。 */
    private static ObservationLevel parseLevelParameter(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        ObservationLevel level = ObservationLevel.tryParse(raw);
        if (level == null) {
            throw new InvalidRequestException("Unknown observation level '" + raw + "'. Allowed values: "
                    + allowedNames(ObservationLevel.filterable()));
        }
        return level;
    }

    private static String allowedNames(List<? extends Enum<?>> values) {
        List<String> names = new ArrayList<String>();
        for (Enum<?> value : values) names.add(value.name());
        return String.join(", ", names);
    }

    @GetMapping("/sessions")
    public PageResponse<SessionSummary> findSessions(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return queryService.findSessions(search, page, size);
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionDetail getSession(
            @PathVariable String sessionId) {
        return queryService.getSession(sessionId);
    }

    @GetMapping("/users")
    public PageResponse<UserSummary> findUsers(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String environment,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return queryService.findUsers(search, environment, page, size);
    }

    @GetMapping("/users/{userId}")
    public UserDetail getUser(
            @PathVariable String userId) {
        return queryService.getUser(userId);
    }
}
