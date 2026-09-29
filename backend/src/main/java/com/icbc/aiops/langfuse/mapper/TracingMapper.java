package com.icbc.aiops.langfuse.mapper;

import com.icbc.aiops.langfuse.mapper.TracingRows.FacetRow;
import com.icbc.aiops.langfuse.mapper.TracingRows.ObservationRow;
import com.icbc.aiops.langfuse.mapper.TracingRows.PulseRow;
import com.icbc.aiops.langfuse.mapper.TracingRows.TraceDetailRow;
import com.icbc.aiops.langfuse.mapper.TracingRows.TraceLocatorRow;
import com.icbc.aiops.langfuse.mapper.TracingRows.TraceLookupRow;
import com.icbc.aiops.langfuse.mapper.TracingRows.TraceMetricsRow;
import com.icbc.aiops.langfuse.mapper.TracingRows.TraceRow;
import com.icbc.aiops.langfuse.service.ObservationFacet;
import com.icbc.aiops.langfuse.service.ObservationQuery;
import com.icbc.aiops.langfuse.service.PulseBucket;
import com.icbc.aiops.langfuse.service.TraceQuery;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

/**
 * 面向 AgentObs ClickHouse schema 的 Tracing 查询。
 *
 * <p>所有 Trace 和 Observation 明细查询均位于此处，包括 Session 和 User 详情查询。
 * 跨 Trace 聚合继续保留在 {@link ObservabilityMapper}。
 */
@Mapper
public interface TracingMapper {

    @SelectProvider(type = TracingSqlProvider.class, method = "observations")
    List<ObservationRow> selectObservations(
            @Param("query") ObservationQuery query,
            @Param("offset") long offset);

    @SelectProvider(type = TracingSqlProvider.class, method = "observationCount")
    long countObservations(@Param("query") ObservationQuery query);

    @SelectProvider(type = TracingSqlProvider.class, method = "observationFacets")
    List<FacetRow> selectObservationFacets(
            @Param("query") ObservationQuery query,
            @Param("field") ObservationFacet field,
            @Param("limit") int limit);

    @SelectProvider(type = TracingSqlProvider.class, method = "observationPulse")
    List<PulseRow> selectObservationPulse(
            @Param("query") ObservationQuery query,
            @Param("bucket") PulseBucket bucket);

    /**
     * 查询一组有界 trace id 对应的 trace name 和 tags。
     *
     * <p>这里接收 Map 而不是 {@code @Param} 列表，因为 Provider 会将 ID 展开为独立绑定的占位符；
     * 裸列表参数需要 MyBatis {@code <foreach>}，注解 Provider 在不使用 script 时无法表达。
     */
    @SelectProvider(type = TracingSqlProvider.class, method = "traceLookups")
    List<TraceLookupRow> selectTraceLookups(Map<String, Object> parameters);

    @SelectProvider(type = TracingSqlProvider.class, method = "tracesBySession")
    List<TraceRow> selectTracesBySession(
            @Param("sessionId") String sessionId, @Param("limit") int limit);

    @SelectProvider(type = TracingSqlProvider.class, method = "observationsBySession")
    List<ObservationRow> selectObservationsBySession(
            @Param("sessionId") String sessionId, @Param("limit") int limit);

    @SelectProvider(type = TracingSqlProvider.class, method = "tracesByUser")
    List<TraceRow> selectTracesByUser(@Param("userId") String userId);

    @SelectProvider(type = TracingSqlProvider.class, method = "traces")
    List<TraceRow> selectTraces(
            @Param("query") TraceQuery query,
            @Param("offset") long offset,
            @Param("candidateLimit") int candidateLimit);

    @SelectProvider(type = TracingSqlProvider.class, method = "traceCount")
    long countTraces(@Param("query") TraceQuery query);

    @SelectProvider(type = TracingSqlProvider.class, method = "traceMetrics")
    List<TraceMetricsRow> selectTraceMetrics(Map<String, Object> parameters);

    @SelectProvider(type = TracingSqlProvider.class, method = "traceDetail")
    TraceDetailRow selectTrace(@Param("traceId") String traceId);

    /**
     * 查询单条 Trace 的 Observation。接收 Map 而不是裸 traceId，使服务层能在 locator
     * 索引存在时补充服务和时间边界；构建器将这些边界视为可选的范围缩小条件。
     */
    @SelectProvider(type = TracingSqlProvider.class, method = "traceObservations")
    List<ObservationRow> selectTraceObservations(Map<String, Object> parameters);

    @SelectProvider(type = TracingSqlProvider.class, method = "traceLocator")
    List<TraceLocatorRow> selectTraceLocator(@Param("traceId") String traceId);
}
