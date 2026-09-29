package com.icbc.aiops.langfuse.mapper;

import com.icbc.aiops.langfuse.mapper.ObservabilityRows.ObservationMetricRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.ObservationSummaryStatsRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.ScoreRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.SessionRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.TraceMetricRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.TraceSummaryStatsRow;
import com.icbc.aiops.langfuse.mapper.ObservabilityRows.UserRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

/**
 * AgentObs 聚合及 score 查询。Trace 和 Observation 明细查询保留在 {@link TracingMapper}，
 * 确保本 Mapper 不需要执行无界事实表 join。
 */
@Mapper
public interface ObservabilityMapper {

    @SelectProvider(type = ObservabilitySqlProvider.class, method = "traceSummaryStats")
    TraceSummaryStatsRow selectTraceSummaryStats();

    @SelectProvider(type = ObservabilitySqlProvider.class, method = "observationSummaryStats")
    ObservationSummaryStatsRow selectObservationSummaryStats();

    @SelectProvider(type = ObservabilitySqlProvider.class, method = "traceMetricTimeSeries")
    List<TraceMetricRow> selectTraceMetricTimeSeries();

    @SelectProvider(type = ObservabilitySqlProvider.class, method = "observationMetricTimeSeries")
    List<ObservationMetricRow> selectObservationMetricTimeSeries();

    @SelectProvider(type = ObservabilitySqlProvider.class, method = "traceScores")
    List<ScoreRow> selectTraceScores(
            @Param("traceId") String traceId);

    @SelectProvider(type = ObservabilitySqlProvider.class, method = "sessions")
    List<SessionRow> selectSessions(
            @Param("search") String search,
            @Param("size") int size,
            @Param("offset") long offset);

    @SelectProvider(type = ObservabilitySqlProvider.class, method = "sessionCount")
    long countSessions(@Param("search") String search);

    @SelectProvider(type = ObservabilitySqlProvider.class, method = "session")
    SessionRow selectSession(@Param("sessionId") String sessionId);

    @SelectProvider(type = ObservabilitySqlProvider.class, method = "users")
    List<UserRow> selectUsers(
            @Param("search") String search,
            @Param("environment") String environment,
            @Param("size") int size,
            @Param("offset") long offset);

    @SelectProvider(type = ObservabilitySqlProvider.class, method = "userCount")
    long countUsers(
            @Param("search") String search,
            @Param("environment") String environment);

    @SelectProvider(type = ObservabilitySqlProvider.class, method = "user")
    UserRow selectUser(@Param("userId") String userId);

    @SelectProvider(type = ObservabilitySqlProvider.class, method = "userSessions")
    List<SessionRow> selectUserSessions(@Param("userId") String userId);
}
