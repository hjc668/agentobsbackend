package com.icbc.aiops.langfuse.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.aiops.langfuse.api.PageResponse;
import com.icbc.aiops.langfuse.domain.DashboardSummary;
import com.icbc.aiops.langfuse.domain.MetricPoint;
import com.icbc.aiops.langfuse.domain.Observation;
import com.icbc.aiops.langfuse.domain.ObservationFacetValue;
import com.icbc.aiops.langfuse.domain.ObservationPulsePoint;
import com.icbc.aiops.langfuse.domain.ObservationLevel;
import com.icbc.aiops.langfuse.domain.ObservationType;
import com.icbc.aiops.langfuse.domain.SessionDetail;
import com.icbc.aiops.langfuse.domain.SessionSummary;
import com.icbc.aiops.langfuse.domain.TraceDetail;
import com.icbc.aiops.langfuse.domain.TraceComment;
import com.icbc.aiops.langfuse.domain.TraceScore;
import com.icbc.aiops.langfuse.domain.TraceStatus;
import com.icbc.aiops.langfuse.domain.TraceSummary;
import com.icbc.aiops.langfuse.domain.UserSummary;
import com.icbc.aiops.langfuse.domain.UserDetail;
import com.icbc.aiops.langfuse.mapper.ObservabilityMapper;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.ObservationMetricRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.ObservationSummaryStatsRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.ScoreRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.SessionRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.TraceMetricRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.TraceSummaryStatsRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.UserRow;
import com.icbc.aiops.langfuse.mapper.TracingMapper;
import com.icbc.aiops.langfuse.mapper.TracingRows;
import com.icbc.aiops.langfuse.postgres.mapper.CommentMapper;
import com.icbc.aiops.langfuse.postgres.mapper.CommentRows.CommentRow;
import com.icbc.aiops.langfuse.config.WorkspaceProperties;
import com.icbc.aiops.langfuse.mapper.TracingSqlProvider;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Profile("mybatis")
public class MybatisObservabilityQueryService implements ObservabilityQueryService {
    private static final int SESSION_TRACE_LIMIT = 200;
    private static final int SESSION_OBSERVATION_LIMIT = 2000;

    private static final Logger LOGGER = LoggerFactory.getLogger(MybatisObservabilityQueryService.class);
    private static final BigDecimal ZERO_COST = BigDecimal.ZERO;

    /** 调用方未传时间范围时使用的默认窗口，确保查询始终有界。 */
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);

    /** 单次查询窗口的硬上限，与 UI 提供的最大时间范围一致。 */
    private static final Duration MAX_WINDOW = Duration.ofDays(90);
    private final ObservabilityMapper mapper;
    private final TracingMapper tracingMapper;
    private final CommentMapper commentMapper;
    private final ObjectMapper objectMapper;
    private final WorkspaceProperties workspace;

    @Value("${app.trace.locator-source-time-zone:UTC}")
    private String locatorSourceTimeZone = "UTC";

    @Value("${app.trace.observation-column-time-zone:UTC}")
    private String observationColumnTimeZone = "UTC";

    /**
     * 有意不作为构造参数：该对象不携带配置，也没有外部协作依赖；不加入构造函数可避免修改
     * 所有现有实例化位置。
     */
    private final UnknownStoredValueWarner unknownValues = new UnknownStoredValueWarner();

    public MybatisObservabilityQueryService(
            ObservabilityMapper mapper,
            TracingMapper tracingMapper,
            CommentMapper commentMapper,
            ObjectMapper objectMapper,
            WorkspaceProperties workspace) {
        this.mapper = mapper;
        this.tracingMapper = tracingMapper;
        this.commentMapper = commentMapper;
        this.objectMapper = objectMapper;
        this.workspace = workspace;
    }

    @Override
    public DashboardSummary getSummary() {
        TraceSummaryStatsRow traces = mapper.selectTraceSummaryStats();
        ObservationSummaryStatsRow observations = mapper.selectObservationSummaryStats();
        return new DashboardSummary(
                traces == null ? 0 : toInt(traces.traceCount()),
                observations == null ? 0 : toInt(observations.observationCount()),
                traces == null ? 0 : toInt(traces.errorCount()),
                observations == null ? 0 : observations.totalTokens(),
                observations == null ? ZERO_COST : cost(observations.totalCost()),
                traces == null ? 0 : traces.averageLatencyMs());
    }

    @Override
    public List<MetricPoint> getMetricTimeSeries() {
        Map<LocalDate, TraceMetricRow> tracesByDay = mapper.selectTraceMetricTimeSeries().stream()
                .collect(Collectors.toMap(row -> row.timestamp().toLocalDate(), Function.identity()));
        Map<LocalDate, ObservationMetricRow> observationsByDay = mapper.selectObservationMetricTimeSeries().stream()
                .collect(Collectors.toMap(row -> row.timestamp().toLocalDate(), Function.identity()));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<MetricPoint> result = new ArrayList<>(7);
        for (int daysAgo = 6; daysAgo >= 0; daysAgo--) {
            LocalDate day = today.minusDays(daysAgo);
            TraceMetricRow traces = tracesByDay.get(day);
            ObservationMetricRow observations = observationsByDay.get(day);
            result.add(new MetricPoint(
                    day.atStartOfDay().toInstant(ZoneOffset.UTC),
                    traces == null ? 0 : toInt(traces.traceCount()),
                    traces == null ? 0 : toInt(traces.errorCount()),
                    observations == null ? 0 : observations.totalTokens(),
                    observations == null ? ZERO_COST : cost(observations.totalCost()),
                    traces == null ? 0 : traces.averageLatencyMs()));
        }
        return result;
    }

    @Override
    public PageResponse<TraceSummary> findTraces(TraceQuery query) {
        TraceQuery bounded = bounded(query);
        TracingFilterFields.validate(bounded.search(), TracingFilterFields.Scope.TRACE);
        long offset = (long) bounded.page() * bounded.size();
        List<TracingRows.TraceRow> rows = tracingMapper.selectTraces(bounded, offset, bounded.size() + 1);
        boolean hasNext = rows.size() > bounded.size();
        if (hasNext) rows = rows.subList(0, bounded.size());
        // 两阶段查询：按 token 或 cost 排序时必须先聚合再分页，因此该路径已在语句中携带指标。
        // 其他排序先对 Trace 表分页，再只查询当前页指标，避免每一页都重新聚合整个时间窗口。
        if (!TracingSqlProvider.sortNeedsWindowAggregate(bounded) && !rows.isEmpty()) {
            List<String> ids = new ArrayList<>(rows.size());
            for (TracingRows.TraceRow row : rows) {
                ids.add(row.id());
            }
            Map<String, TracingRows.TraceMetricsRow> metrics = traceMetricsFor(ids);
            List<TracingRows.TraceRow> merged = new ArrayList<>(rows.size());
            for (TracingRows.TraceRow row : rows) {
                merged.add(mergeMetrics(row, metrics.get(row.id())));
            }
            rows = merged;
        }
        List<TraceSummary> items = rows.stream()
                .map(this::toTraceSummary)
                .collect(java.util.stream.Collectors.toList());
        return PageResponse.of(items, bounded.page(), bounded.size(),
                tracingMapper.countTraces(bounded), hasNext);
    }

    private Map<String, TracingRows.TraceMetricsRow> traceMetricsFor(List<String> traceIds) {
        if (traceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("traceIds", traceIds);
        Map<String, TracingRows.TraceMetricsRow> result = new HashMap<>();
        for (TracingRows.TraceMetricsRow row : tracingMapper.selectTraceMetrics(parameters)) {
            result.put(row.traceId(), row);
        }
        return result;
    }

    private static TracingRows.TraceRow mergeMetrics(
            TracingRows.TraceRow row, TracingRows.TraceMetricsRow metrics) {
        if (metrics == null) {
            return row;
        }
        return new TracingRows.TraceRow(row.id(), row.name(), row.timestamp(), row.userId(), row.sessionId(),
                row.environment(), row.status(), row.latencyMs(), metrics.totalTokens(), metrics.totalCost(),
                toInt(metrics.observationCount()), row.tagsJson());
    }

    private List<TracingRows.TraceRow> enrichTraceMetrics(List<TracingRows.TraceRow> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        List<String> ids = rows.stream().map(TracingRows.TraceRow::id).collect(Collectors.toList());
        Map<String, TracingRows.TraceMetricsRow> metrics = traceMetricsFor(ids);
        return rows.stream().map(row -> mergeMetrics(row, metrics.get(row.id())))
                .collect(Collectors.toList());
    }

    @Override
    public TraceDetail getTrace(String traceId) {
        TracingRows.TraceDetailRow row = tracingMapper.selectTrace(traceId);
        if (row == null) {
            throw new ResourceNotFoundException("Trace not found: " + traceId);
        }
        // AgentObs 的 Trace 记录不保存 token/cost/span 汇总，因此指标从当前 Trace 的
        // Observations 聚合。locator 追上写入进度后，会按所属服务和时间范围缩小读取范围；
        // service_name 是 observations 表的首个排序键，加入它能帮助聚合裁剪 granule。
        // traceParameters 只在 locator 返回单一所属服务时添加该条件，跨服务 Trace 不会被错误缩小。
        List<TracingRows.TraceMetricsRow> metrics =
                tracingMapper.selectTraceMetrics(traceParameters(traceId, true));
        long totalTokens = 0;
        BigDecimal totalCost = ZERO_COST;
        long observationCount = 0;
        if (!metrics.isEmpty()) {
            totalTokens = metrics.get(0).totalTokens();
            totalCost = cost(metrics.get(0).totalCost());
            observationCount = metrics.get(0).observationCount();
        }
        TraceDetail detail = new TraceDetail(row.id(), row.name(), toInstant(row.timestamp()),
                emptyToNull(row.userId()), emptyToNull(row.sessionId()), row.environment(),
                TraceStatus.valueOf(row.status()), row.latencyMs(), totalTokens, totalCost,
                toInt(observationCount), parseTags(row.tagsJson()), parseJson(row.inputJson()),
                parseJson(row.outputJson()), parseMetadata(row.metadataJson()),
                emptyToNull(row.release()), emptyToNull(row.version()));
        // 摘要记录自身没有 tokens/cost/span count，已在上方补充；locator 存在时，
        // 这些指标查询也会使用相同边界缩小范围。
        return detail;
    }

    @Override
    public List<Observation> findTraceObservations(String traceId) {
        List<TracingRows.ObservationRow> rows =
                tracingMapper.selectTraceObservations(traceParameters(traceId, true));
        Map<String, TracingRows.TraceLookupRow> lookups = traceLookupsFor(rows);
        return rows.stream()
                .map(row -> toObservation(row, lookups.get(row.traceId())))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 构建单条 Trace 查询参数；locator 索引存在对应记录时追加范围缩小条件。
     *
     * <p>locator 是派生索引，可能落后于数据写入，因此查询不到记录属于可预期情况，只记录日志而不视为错误；
     * 此时回退到速度较慢但结果完整的普通 trace_id 查询。locator 一旦存在，就作为范围缩小的权威依据；
     * 有界查询结果为空时应排查对应物化视图，而不能通过移除时间条件重试。
     */
    private Map<String, Object> traceParameters(String traceId, boolean includeLocatorService) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("traceIds", Collections.singletonList(traceId));
        parameters.put("traceId", traceId);
        List<TracingRows.TraceLocatorRow> locators = tracingMapper.selectTraceLocator(traceId);
        if (locators.isEmpty()) {
            LOGGER.warn("Trace locator has no row for {}; falling back to a plain trace_id read. "
                    + "This is expected while the locator materialized view catches up.", traceId);
            return parameters;
        }
        String sourceZone = LocatorTimeBounds.checkedZone(locatorSourceTimeZone);
        String columnZone = LocatorTimeBounds.checkedZone(observationColumnTimeZone);
        LocalDateTime min = null;
        LocalDateTime max = null;
        for (TracingRows.TraceLocatorRow locator : locators) {
            LocalDateTime start = LocatorTimeBounds.inColumnZone(
                    locator.minStartTime(), sourceZone, columnZone);
            LocalDateTime end = LocatorTimeBounds.inColumnZone(
                    locator.maxEndTime(), sourceZone, columnZone);
            if (start != null && (min == null || start.isBefore(min))) {
                min = start;
            }
            if (end != null && (max == null || end.isAfter(max))) {
                max = end;
            }
        }
        if (min == null || max == null || min.isAfter(max)) {
            throw new IllegalStateException("Trace locator has invalid time bounds for trace " + traceId);
        }
        parameters.put("locatorMinStart", LocatorTimeBounds.format(min));
        parameters.put("locatorMaxEnd", LocatorTimeBounds.format(max));
        parameters.put("locatorColumnTimeZone", columnZone);
        if (includeLocatorService && locators.size() == 1) {
            // 仅在唯一所属服务时安全；跨多个服务的 Trace 不能被缩小到其中一个服务。
            parameters.put("locatorServiceName", locators.get(0).serviceName());
        }
        return parameters;
    }

    @Override
    public List<TraceScore> findTraceScores(String traceId) {
        getTrace(traceId);
        return mapper.selectTraceScores(traceId).stream().map(this::toTraceScore).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<TraceComment> findTraceComments(String traceId) {
        List<String> observationIds = findTraceObservations(traceId).stream()
                .map(Observation::id)
                .collect(java.util.stream.Collectors.toList());
        getTrace(traceId);
        // Comment 保存在 PolarDB-X workspace 中；Trace 查询来自全局 ClickHouse。
        return commentMapper.selectForTrace(workspace.getProjectId(), traceId, observationIds).stream()
                .map(this::toTraceComment)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public PageResponse<Observation> findObservations(ObservationQuery query) {
        ObservationQuery bounded = bounded(query);
        TracingFilterFields.validate(bounded.search());
        long offset = (long) bounded.page() * bounded.size();
        List<TracingRows.ObservationRow> rows = tracingMapper.selectObservations(bounded, offset);
        // 第二次有界查询：AgentObs 只在 Trace 表保存 trace name。
        Map<String, TracingRows.TraceLookupRow> lookups = traceLookupsFor(rows);
        List<Observation> items = rows.stream()
                .map(row -> toObservation(row, lookups.get(row.traceId())))
                .collect(java.util.stream.Collectors.toList());
        return PageResponse.of(items, bounded.page(), bounded.size(),
                tracingMapper.countObservations(bounded));
    }

    @Override
    public List<ObservationFacetValue> findObservationFacets(
            ObservationQuery query, ObservationFacet field, int limit) {
        ObservationQuery bounded = bounded(query);
        TracingFilterFields.validate(bounded.search());
        return tracingMapper.selectObservationFacets(bounded, field, limit).stream()
                .map(row -> new ObservationFacetValue(row.value(), row.count()))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<ObservationPulsePoint> findObservationPulse(
            ObservationQuery query, PulseBucket bucket) {
        ObservationQuery bounded = bounded(query);
        TracingFilterFields.validate(bounded.search());
        Map<Instant, ObservationPulsePoint> values = tracingMapper.selectObservationPulse(bounded, bucket).stream()
                .map(row -> new ObservationPulsePoint(toInstant(row.timestamp()), row.count(),
                        cost(row.totalCost()), row.averageLatencyMs()))
                .collect(Collectors.toMap(ObservationPulsePoint::timestamp, Function.identity()));
        // 此处时间窗口已经确定，因此趋势图中的空档会显式补 0，而不是折叠；图表依赖每个 bucket 都存在。
        Instant cursor = floorBucket(bounded.fromTimestamp(), bucket);
        List<ObservationPulsePoint> result = new ArrayList<>();
        while (cursor.isBefore(bounded.toTimestamp()) && result.size() < 500) {
            result.add(values.getOrDefault(cursor, new ObservationPulsePoint(cursor, 0, ZERO_COST, 0)));
            cursor = nextBucket(cursor, bucket);
        }
        return result;
    }

    private static Instant floorBucket(Instant value, PulseBucket bucket) {
        if (bucket == PulseBucket.HOUR) return value.truncatedTo(ChronoUnit.HOURS);
        LocalDate day = value.atZone(ZoneOffset.UTC).toLocalDate();
        if (bucket == PulseBucket.WEEK) day = day.minusDays(day.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
        return day.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static Instant nextBucket(Instant value, PulseBucket bucket) {
        long amount = 1;
        ChronoUnit unit = ChronoUnit.DAYS;
        if (bucket == PulseBucket.HOUR) {
            unit = ChronoUnit.HOURS;
        } else if (bucket == PulseBucket.WEEK) {
            amount = 7;
        }
        return value.plus(amount, unit);
    }

    @Override
    public PageResponse<SessionSummary> findSessions(String search, int page, int size) {
        long offset = (long) page * size;
        List<SessionSummary> items = mapper.selectSessions(search, size, offset).stream()
                .map(this::toSessionSummary)
                .collect(java.util.stream.Collectors.toList());
        return PageResponse.of(items, page, size, mapper.countSessions(search));
    }

    @Override
    public SessionDetail getSession(String sessionId) {
        SessionRow row = mapper.selectSession(sessionId);
        if (row == null) {
            throw new ResourceNotFoundException("Session not found: " + sessionId);
        }
        List<TracingRows.TraceRow> traceRows = tracingMapper.selectTracesBySession(
                sessionId, SESSION_TRACE_LIMIT + 1);
        if (traceRows.size() > SESSION_TRACE_LIMIT) {
            throw new InvalidRequestException("Session contains more than " + SESSION_TRACE_LIMIT
                    + " traces; use the trace list endpoint with a sessionId filter.");
        }
        List<TracingRows.ObservationRow> observationRows = tracingMapper.selectObservationsBySession(
                sessionId, SESSION_OBSERVATION_LIMIT + 1);
        if (observationRows.size() > SESSION_OBSERVATION_LIMIT) {
            throw new InvalidRequestException("Session contains more than " + SESSION_OBSERVATION_LIMIT
                    + " observations; use the observation list endpoint with a sessionId filter.");
        }
        List<TraceSummary> traces = enrichTraceMetrics(traceRows).stream()
                .map(this::toTraceSummary)
                .collect(java.util.stream.Collectors.toList());
        Map<String, TracingRows.TraceLookupRow> lookups = traceLookupsFor(observationRows);
        List<Observation> observations = observationRows.stream()
                .map(observation -> toObservation(observation, lookups.get(observation.traceId())))
                .collect(java.util.stream.Collectors.toList());
        return new SessionDetail(toSessionSummary(row), traces, observations);
    }

    @Override
    public PageResponse<UserSummary> findUsers(
            String search, String environment, int page, int size) {
        long offset = (long) page * size;
        List<UserSummary> items = mapper.selectUsers(search, environment, size, offset).stream()
                .map(this::toUserSummary)
                .collect(java.util.stream.Collectors.toList());
        return PageResponse.of(items, page, size, mapper.countUsers(search, environment));
    }

    @Override
    public UserDetail getUser(String userId) {
        UserRow row = mapper.selectUser(userId);
        if (row == null) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        List<TraceSummary> traces = enrichTraceMetrics(tracingMapper.selectTracesByUser(userId)).stream()
                .map(this::toTraceSummary)
                .collect(java.util.stream.Collectors.toList());
        List<SessionSummary> sessions = mapper.selectUserSessions(userId).stream()
                .map(this::toSessionSummary)
                .collect(java.util.stream.Collectors.toList());
        return new UserDetail(toUserSummary(row), traces, sessions);
    }

    private TraceSummary toTraceSummary(TracingRows.TraceRow row) {
        return new TraceSummary(row.id(), row.name(), toInstant(row.timestamp()), emptyToNull(row.userId()),
                emptyToNull(row.sessionId()), row.environment(), TraceStatus.valueOf(row.status()), row.latencyMs(),
                row.totalTokens(), cost(row.totalCost()), row.observationCount(), parseTags(row.tagsJson()));
    }

    private Observation toObservation(TracingRows.ObservationRow row, TracingRows.TraceLookupRow lookup) {
        String traceName = lookup != null ? lookup.traceName() : row.traceName();
        return new Observation(row.id(), row.traceId(), emptyToNull(traceName), emptyToNull(row.parentObservationId()),
                row.name(), storedType(row.type()), toInstant(row.startTime()), toInstant(row.endTime()),
                storedLevel(row.level()), emptyToNull(row.modelName()), row.latencyMs(), row.inputTokens(),
                row.outputTokens(), cost(row.totalCost()), parseJson(row.inputJson()), parseJson(row.outputJson()),
                toInstant(row.completionStartTime()), row.timeToFirstTokenMs(), emptyToNull(row.statusMessage()),
                emptyToNull(row.modelId()), parseJson(row.modelParametersJson()), parseMetadata(row.usageDetailsJson()),
                parseMetadata(row.costDetailsJson()), emptyToNull(row.promptName()), row.promptVersion(),
                parseMetadata(row.metadataJson()));
    }

    /**
     * 将存储的 {@code type} 映射为 enum。
     *
     * <p>有意不使用 {@code ObservationType.valueOf}：AgentObs 按生产方使用的大小写存储值，
     * 既可能是 {@code span}，也可能是 {@code SPAN}；过去一条未知记录就会抛出异常并导致整页
     * 返回 HTTP 500。未知值降级为 {@link ObservationType#UNKNOWN}，绝不强制映射到其他类型，
     * 从而保证对应数量仍能独立展示。
     */
    private ObservationType storedType(String rawType) {
        ObservationType type = ObservationType.fromStorage(rawType);
        if (type == ObservationType.UNKNOWN) {
            unknownValues.observe("observation type", rawType);
        }
        return type;
    }

    /** 与 {@link #storedType} 采用相同处理；过去小写 {@code level} 同样会导致整页失败。 */
    private ObservationLevel storedLevel(String rawLevel) {
        ObservationLevel level = ObservationLevel.fromStorage(rawLevel);
        if (level == ObservationLevel.UNKNOWN) {
            unknownValues.observe("observation level", rawLevel);
        }
        return level;
    }

    /**
     * 查询一页 Observations 对应 Trace 的 Trace 级字段（name、tags）。这是拆表读取的第二阶段：
     * ID 集合受当前页限制，因此不会退化为无界 join。
     */
    private Map<String, TracingRows.TraceLookupRow> traceLookupsFor(List<TracingRows.ObservationRow> rows) {
        List<String> traceIds = new ArrayList<>();
        for (TracingRows.ObservationRow row : rows) {
            if (row.traceId() != null && !traceIds.contains(row.traceId())) {
                traceIds.add(row.traceId());
            }
        }
        if (traceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("traceIds", traceIds);
        Map<String, TracingRows.TraceLookupRow> result = new HashMap<>();
        for (TracingRows.TraceLookupRow lookup : tracingMapper.selectTraceLookups(parameters)) {
            result.put(lookup.traceId(), lookup);
        }
        return result;
    }

    private static Map<String, Object> idParameters(String traceId) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("traceIds", Collections.singletonList(traceId));
        return parameters;
    }

    /**
     * 限制调用方传入的时间范围。未提供范围时使用“最近 24 小时”，而不是“全部历史数据”；
     * 超出 UI 最大选项的范围会被拒绝。
     *
     * <p>如果没有该限制，客户端只需省略时间参数，就会让每个 list、count、facet 和 pulse
     * 请求变成全表扫描，可能影响共享 ClickHouse 节点上的所有其他用户。
     */
    // 使用 package-private（而不是 private），使 TracingQueryBoundsTest 无需启动 Mapper
    // 和 ClickHouse 连接即可直接验证该保护逻辑。
    static ObservationQuery bounded(ObservationQuery query) {
        Instant to = query.toTimestamp() != null ? query.toTimestamp() : Instant.now();
        Instant from = query.fromTimestamp() != null ? query.fromTimestamp() : to.minus(DEFAULT_WINDOW);
        requireValidWindow(from, to);
        return query.withWindow(from, to);
    }

    static TraceQuery bounded(TraceQuery query) {
        Instant to = query.toTimestamp() != null ? query.toTimestamp() : Instant.now();
        Instant from = query.fromTimestamp() != null ? query.fromTimestamp() : to.minus(DEFAULT_WINDOW);
        requireValidWindow(from, to);
        return query.withWindow(from, to);
    }

    private static void requireValidWindow(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new InvalidRequestException("fromTimestamp must not be later than toTimestamp.");
        }
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new InvalidRequestException(
                    "Requested time range exceeds the maximum of " + MAX_WINDOW.toDays() + " days.");
        }
    }

    private TraceScore toTraceScore(ScoreRow row) {
        return new TraceScore(row.id(), row.traceId(), emptyToNull(row.observationId()), row.name(), row.dataType(),
                row.numericValue(), emptyToNull(row.stringValue()), row.source(), emptyToNull(row.comment()),
                toInstant(row.createdAt()));
    }

    private TraceComment toTraceComment(CommentRow row) {
        return new TraceComment(row.id(), row.objectType(), row.objectId(), row.content(),
                emptyToNull(row.authorUserId()), emptyToNull(row.dataField()), toInstant(row.createdAt()),
                toInstant(row.updatedAt()));
    }

    private SessionSummary toSessionSummary(SessionRow row) {
        return new SessionSummary(row.id(), toInstant(row.createdAt()), emptyToNull(row.userId()),
                toInt(row.traceCount()), toInt(row.observationCount()), row.totalTokens(), cost(row.totalCost()),
                row.durationMs());
    }

    private UserSummary toUserSummary(UserRow row) {
        return new UserSummary(row.id(), emptyToNull(row.environment()), toInstant(row.firstEvent()),
                toInstant(row.lastEvent()), toInt(row.traceCount()), toInt(row.observationCount()),
                row.totalTokens(), cost(row.totalCost()));
    }

    private List<String> parseTags(String value) {
        if (!hasText(value)) {
            return com.icbc.aiops.langfuse.util.Java8Collections.listOf();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() { });
        } catch (Exception ignored) {
            return com.icbc.aiops.langfuse.util.Java8Collections.listOf();
        }
    }

    private Map<String, Object> parseMetadata(String value) {
        if (!hasText(value)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private Object parseJson(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node == null ? value : node;
        } catch (Exception ignored) {
            return value;
        }
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static BigDecimal cost(BigDecimal value) {
        return value == null ? ZERO_COST : value;
    }

    private static String emptyToNull(String value) {
        return hasText(value) ? value : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static int toInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }
}
