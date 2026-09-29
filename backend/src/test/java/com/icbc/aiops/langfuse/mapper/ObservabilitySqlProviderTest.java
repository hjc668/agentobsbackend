package com.icbc.aiops.langfuse.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObservabilitySqlProviderTest {

    @Test
    void everyStatementUsesAgentObsFinalAndNoRetiredColumns() {
        for (String sql : everyStatement()) {
            assertTrue(sql.contains("default.hmp_agentobs_"), sql);
            assertTrue(sql.contains("FINAL"), sql);
            for (String retired : new String[] {
                    "events_" + "core", "events_" + "full", "FROM " + "scores",
                    "project_" + "id", "is_" + "deleted", "event_" + "ts",
                    "usage_" + "details", "cost_" + "details", "calculated_" + "total_cost",
                    "provided_" + "model_name"}) {
                assertFalse(sql.contains(retired), "retired token " + retired + ": " + sql);
            }
        }
    }

    @Test
    void summaryAndTrendKeepTraceAndObservationFactsSeparate() {
        String traceSummary = ObservabilitySqlProvider.traceSummaryStats();
        String observationSummary = ObservabilitySqlProvider.observationSummaryStats();
        assertTrue(traceSummary.contains("hmp_agentobs_traces_all FINAL"));
        assertFalse(traceSummary.contains("hmp_agentobs_observations_all"));
        assertTrue(observationSummary.contains("hmp_agentobs_observations_all FINAL"));
        assertFalse(observationSummary.contains("hmp_agentobs_traces_all"));
        assertTrue(ObservabilitySqlProvider.traceMetricTimeSeries().contains("toStartOfDay(start_time)"));
        assertTrue(ObservabilitySqlProvider.observationMetricTimeSeries().contains("toStartOfDay(start_time)"));
    }

    @Test
    void sessionAndUserSearchValuesStayBound() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("search", "x' OR 1 = 1 --");
        parameters.put("environment", "prod' --");
        String sessions = ObservabilitySqlProvider.sessions(parameters);
        String users = ObservabilitySqlProvider.users(parameters);
        assertTrue(sessions.contains("#{search}"));
        assertTrue(users.contains("#{search}"));
        assertTrue(users.contains("#{environment}"));
        assertFalse(sessions.contains("OR 1 = 1 --"));
        assertFalse(users.contains("prod' --"));
    }

    @Test
    void scoresUseAgentObsFieldMapping() {
        String sql = ObservabilitySqlProvider.traceScores();
        assertTrue(sql.contains("score_id AS id"));
        assertTrue(sql.contains("nullIf(value_string, '') AS stringValue"));
        assertTrue(sql.contains("WHERE trace_id = #{traceId}"));
        assertTrue(sql.contains("ORDER BY created_at ASC, score_id ASC"));
    }

    private static List<String> everyStatement() {
        Map<String, Object> populated = new HashMap<>();
        populated.put("search", "needle");
        populated.put("environment", "production");
        List<String> statements = new ArrayList<>();
        statements.add(ObservabilitySqlProvider.traceSummaryStats());
        statements.add(ObservabilitySqlProvider.observationSummaryStats());
        statements.add(ObservabilitySqlProvider.traceMetricTimeSeries());
        statements.add(ObservabilitySqlProvider.observationMetricTimeSeries());
        statements.add(ObservabilitySqlProvider.traceScores());
        statements.add(ObservabilitySqlProvider.sessions(populated));
        statements.add(ObservabilitySqlProvider.sessionCount(populated));
        statements.add(ObservabilitySqlProvider.session());
        statements.add(ObservabilitySqlProvider.users(populated));
        statements.add(ObservabilitySqlProvider.userCount(populated));
        statements.add(ObservabilitySqlProvider.user());
        statements.add(ObservabilitySqlProvider.userSessions());
        return statements;
    }
}
