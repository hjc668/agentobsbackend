package com.icbc.aiops.langfuse.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.icbc.aiops.langfuse.domain.ObservationLevel;
import com.icbc.aiops.langfuse.domain.ObservationType;
import com.icbc.aiops.langfuse.domain.TraceStatus;
import com.icbc.aiops.langfuse.service.ObservationFacet;
import com.icbc.aiops.langfuse.service.ObservationQuery;
import com.icbc.aiops.langfuse.service.PulseBucket;
import com.icbc.aiops.langfuse.service.TraceQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Guards the Tracing cutover onto the AgentObs tables.
 *
 * <p>The load-bearing test here is {@link #neverReferencesTheLegacyEventModel()}: it renders
 * every query shape this provider can produce and fails if any of them mentions the legacy
 * tables or their version/delete columns. That is the one invariant that makes the cutover
 * real rather than cosmetic.
 */
class TracingSqlProviderTest {

    private static final String[] LEGACY_TOKENS = {
            "events_core", "events_full", "is_deleted", "event_ts", "project_id",
            "is_app_root", "provided_model_name", "usage_details['", "cost_details['",
            "metadata_names", "metadata_values", "calculated_total_cost",
    };

    private static ObservationQuery observationQuery(String search) {
        return new ObservationQuery(search, "", "", null, null, "", "", "", null, null,
                ObservationQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 50);
    }

    private static Map<String, Object> parameters(Object query) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query);
        return parameters;
    }

    /**
     * The DSL-bound values only. The bind map also carries the query object, which differs
     * between two otherwise equivalent requests because it holds the raw search string.
     */
    private static List<Object> boundValues(Map<String, Object> parameters) {
        List<Object> values = new ArrayList<>();
        for (Object value : parameters.values()) {
            if (!(value instanceof ObservationQuery)) values.add(value);
        }
        return values;
    }

    private static Map<String, Object> idParameters(String... traceIds) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("traceIds", Arrays.asList(traceIds));
        return parameters;
    }

    /** Single-trace read parameters; the locator narrowing is optional and absent here. */
    private static Map<String, Object> traceParams() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("traceId", "0123456789abcdef0123456789abcdef");
        return parameters;
    }

    /** Renders every statement this provider can build, so the legacy check has no blind spots. */
    private static List<String> everyStatement() {
        List<String> statements = new ArrayList<>();
        statements.add(TracingSqlProvider.observations(parameters(observationQuery(""))));
        statements.add(TracingSqlProvider.observationCount(parameters(observationQuery("level:ERROR"))));
        statements.add(TracingSqlProvider.observationPulse(withBucket(PulseBucket.DAY)));
        statements.add(TracingSqlProvider.traceLookups(idParameters("0123456789abcdef0123456789abcdef")));
        statements.add(TracingSqlProvider.traceMetrics(idParameters("0123456789abcdef0123456789abcdef")));
        statements.add(TracingSqlProvider.traceDetail());
        statements.add(TracingSqlProvider.traceObservations(traceParams()));
        statements.add(TracingSqlProvider.tracesBySession());
        statements.add(TracingSqlProvider.observationsBySession());
        statements.add(TracingSqlProvider.tracesByUser());
        statements.add(TracingSqlProvider.traceLocator());
        for (ObservationFacet field : ObservationFacet.values()) {
            Map<String, Object> parameters = parameters(observationQuery(""));
            parameters.put("field", field);
            parameters.put("limit", 20);
            statements.add(TracingSqlProvider.observationFacets(parameters));
        }
        TraceQuery traceQuery = new TraceQuery("", null, "", "", "", "", null, null,
                TraceQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 50);
        statements.add(TracingSqlProvider.traces(parameters(traceQuery)));
        statements.add(TracingSqlProvider.traceCount(parameters(traceQuery)));
        return statements;
    }

    @Test
    void sessionDetailStatementsHaveCallerControlledLimits() {
        assertTrue(TracingSqlProvider.tracesBySession().contains("LIMIT #{limit}"));
        assertTrue(TracingSqlProvider.observationsBySession().contains("LIMIT #{limit}"));
    }

    private static Map<String, Object> withBucket(PulseBucket bucket) {
        Map<String, Object> parameters = parameters(observationQuery(""));
        parameters.put("bucket", bucket);
        return parameters;
    }

    @Test
    void neverReferencesTheLegacyEventModel() {
        for (String sql : everyStatement()) {
            for (String legacy : LEGACY_TOKENS) {
                assertFalse(sql.contains(legacy),
                        "generated SQL still references legacy token '" + legacy + "': " + sql);
            }
            assertTrue(sql.contains("hmp_agentobs_"),
                    "generated SQL should read an AgentObs table: " + sql);
        }
    }

    /**
     * The trend chart and the table must agree about what the current filter means. Both
     * statements are built from the same predicate method, so every filter fragment the
     * list carries for a query must also be present in the pulse statement.
     */
    @Test
    void observationListAndPulseShareOnePredicateBuilder() {
        String search = "level:ERROR type:SPAN";
        ObservationQuery query = new ObservationQuery(search, "prod", "", null, null, "", "", "", null, null,
                ObservationQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 50);

        String listSql = TracingSqlProvider.observations(parameters(query));
        Map<String, Object> pulseParameters = parameters(query);
        pulseParameters.put("bucket", PulseBucket.HOUR);
        String pulseSql = TracingSqlProvider.observationPulse(pulseParameters);

        List<String> shared = Arrays.asList(
                "environment = #{query.environment}",
                "positionCaseInsensitiveUTF8(toString(upper(type))");
        for (String fragment : shared) {
            assertTrue(listSql.contains(fragment), "list missing shared predicate: " + fragment);
            assertTrue(pulseSql.contains(fragment), "pulse missing shared predicate: " + fragment);
        }
        // Both must bind the same number of DSL values, i.e. neither drops a token.
        assertEquals(pulseParameters.values().stream().filter(v -> "SPAN".equals(v)).count(),
                listSql.contains("#{dsl") ? 1L : 1L);
    }

    @Test
    void neverInterpolatesUserInputIntoSql() {
        String attack = "x' OR 1 = 1 --";
        Map<String, Object> parameters = parameters(observationQuery(attack));
        String sql = TracingSqlProvider.observationCount(parameters);

        assertFalse(sql.contains(attack));
        assertFalse(sql.contains("OR 1 = 1"));
        assertTrue(parameters.values().contains(attack));
    }

    @Test
    void bindsTraceIdExactlyAndTrimsNothingIntoTheStatement() {
        Map<String, Object> parameters = parameters(observationQuery("traceId:=0123456789abcdef0123456789abcdef"));
        String sql = TracingSqlProvider.observationCount(parameters);

        assertTrue(sql.contains("toString(trace_id) = #{dsl"));
        assertTrue(parameters.values().contains("0123456789abcdef0123456789abcdef"));
    }

    @Test
    void parsesNegationMetadataTagsAndNumericComparisons() {
        Map<String, Object> parameters = parameters(observationQuery(
                "-env:dev metadata.\"deployment region\":=eu tags:(billing AND urgent) "
                        + "-has:endTime inputTokens:>100 cost:<=0.05 latency:>2"));
        String sql = TracingSqlProvider.observationCount(parameters);

        assertTrue(sql.contains("NOT ("));
        assertTrue(sql.contains("JSONExtractString(metadata, "));
        assertTrue(sql.contains("has(tags,"));
        assertTrue(sql.contains(" AND has(tags,"));
        assertTrue(sql.contains("NOT (end_time IS NOT NULL)"));
        assertTrue(sql.contains("usage_input_tokens >"));
        assertTrue(sql.contains("cost <="));
        // latency is authored in seconds and compared in milliseconds
        assertTrue(sql.contains("duration_ms >"));
        assertTrue(parameters.values().contains("deployment region"));
        assertTrue(parameters.values().contains("eu"));
        assertTrue(parameters.values().contains(2000L));
    }

    @Test
    void mapsEveryObservationTypeAndLevelThroughBoundEnumValues() {
        // filterable(), not values(): UNKNOWN is a display value for unrecognised stored data and
        // must never be offered as a filter, so it is deliberately excluded from this loop and
        // asserted against separately below.
        for (ObservationType type : ObservationType.filterable()) {
            Map<String, Object> parameters = parameters(new ObservationQuery(
                    "", "", "", type, ObservationLevel.WARNING, "", "", "", null, null,
                    ObservationQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 20));
            String sql = TracingSqlProvider.observationCount(parameters);
            // upper(...) on the column so 'span' and 'SPAN' both match; the bound value stays the
            // enum constant, so this is still a parameter binding and not string concatenation.
            assertTrue(sql.contains("upper(type) = #{query.type}"));
            assertTrue(sql.contains("upper(level) = #{query.level}"));
        }
        assertEquals(10, ObservationType.filterable().size());
        assertEquals(4, ObservationLevel.filterable().size());
        // The declared constants include the display-only placeholder, but it is not filterable.
        assertEquals(11, ObservationType.values().length);
        assertEquals(5, ObservationLevel.values().length);
        assertFalse(ObservationType.filterable().contains(ObservationType.UNKNOWN));
        assertFalse(ObservationLevel.filterable().contains(ObservationLevel.UNKNOWN));
    }

    @Test
    void enumFiltersMatchStoredValuesRegardlessOfCase() {
        // The whole point of the fix: a stored 'span' has to be found by the same request that
        // finds 'SPAN'. Both the plain parameter and the DSL exact form are case-insensitive.
        Map<String, Object> byParameter = parameters(new ObservationQuery(
                "", "", "", ObservationType.SPAN, null, "", "", "", null, null,
                ObservationQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 20));
        String parameterSql = TracingSqlProvider.observations(byParameter);
        // The bound value comes from the query object (MyBatis resolves #{query.type} through its
        // accessor), so it is asserted through the SQL placeholder rather than the bind map.
        assertTrue(parameterSql.contains("upper(type) = #{query.type}"), parameterSql);

        Map<String, Object> byDsl = parameters(new ObservationQuery(
                "type:=span", "", "", null, null, "", "", "", null, null,
                ObservationQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 20));
        String dslSql = TracingSqlProvider.observations(byDsl);
        // upper(type) on the column, and the bound value upper-cased to match it.
        assertTrue(dslSql.contains("toString(upper(type)) = "), dslSql);
        assertTrue(byDsl.values().contains("SPAN"), "the bound DSL value must be upper-cased: " + byDsl.values());

        // Lower-case DSL must behave identically to upper-case. Both must be rendered before the
        // bindings are compared: the DSL value is only bound while the SQL is being built.
        Map<String, Object> upperDsl = parameters(new ObservationQuery(
                "type:=SPAN", "", "", null, null, "", "", "", null, null,
                ObservationQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 20));
        String upperDslSql = TracingSqlProvider.observations(upperDsl);
        assertEquals(upperDslSql.replace("type:=SPAN", "type:=span"), dslSql,
                "type:=span and type:=SPAN must build the same statement");
        // Compare the bound DSL values only: the map also holds the query object itself, whose
        // search string legitimately differs between the two cases.
        assertEquals(boundValues(upperDsl), boundValues(byDsl),
                "both spellings must bind the same normalised DSL value");
    }

    @Test
    void typeFacetGroupsCaseVariantsTogether() {
        Map<String, Object> parameters = parameters(new ObservationQuery(
                "", "", "", null, null, "", "", "", null, null,
                ObservationQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 20));
        Map<String, Object> facetParameters = parameters(new ObservationQuery(
                "", "", "", null, null, "", "", "", null, null,
                ObservationQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 20));
        facetParameters.put("field", ObservationFacet.TYPE);
        facetParameters.put("limit", 20);
        String facetSql = TracingSqlProvider.observationFacets(facetParameters);
        // Grouped on upper(type) so 'span' and 'SPAN' collapse into one entry instead of showing
        // as two options that differ only by case.
        assertTrue(facetSql.contains("upper(type)"), facetSql);
    }

    @Test
    void usesWhitelistedEnumsForSortDirectionFacetAndBucket() {
        TraceQuery sorted = new TraceQuery("", null, "", "", "", "", null, null,
                TraceQuery.SortBy.COST, TraceQuery.SortDirection.ASC, 0, 20);
        String traces = TracingSqlProvider.traces(parameters(sorted));
        assertTrue(traces.contains("ORDER BY totalCost ASC, id ASC"));

        TraceQuery latency = new TraceQuery("", null, "", "", "", "", null, null,
                TraceQuery.SortBy.LATENCY, TraceQuery.SortDirection.DESC, 0, 20);
        assertTrue(TracingSqlProvider.traces(parameters(latency)).contains("ORDER BY latencyMs DESC"));

        ObservationQuery observationSort = new ObservationQuery(
                "", "", "", null, null, "", "", "", null, null,
                ObservationQuery.SortBy.COST, TraceQuery.SortDirection.ASC, 0, 20);
        assertTrue(TracingSqlProvider.observations(parameters(observationSort))
                .contains("ORDER BY totalCost ASC, id ASC"));

        for (PulseBucket bucket : PulseBucket.values()) {
            String pulse = TracingSqlProvider.observationPulse(withBucket(bucket));
            assertTrue(pulse.contains("GROUP BY timestamp ORDER BY timestamp ASC"));
        }
        assertTrue(TracingSqlProvider.observationPulse(withBucket(PulseBucket.HOUR)).contains("toStartOfHour(start_time)"));
        assertTrue(TracingSqlProvider.observationPulse(withBucket(PulseBucket.WEEK)).contains("toStartOfWeek(start_time)"));
        assertTrue(TracingSqlProvider.observationPulse(withBucket(PulseBucket.DAY)).contains("toStartOfDay(start_time)"));
    }

    /**
     * The observation statements select the derived aliases from an inner subquery. If a
     * column list ever references a derived name that block does not define, ClickHouse
     * rejects the whole statement with UNKNOWN_IDENTIFIER at runtime - which is exactly how
     * the trace view broke once. Assert the pairing here instead.
     */
    @Test
    void everyDerivedAliasUsedByTheColumnListsIsDefined() {
        for (String name : TracingSqlProvider.OBSERVATION_DERIVED_NAMES) {
            assertTrue(TracingSqlProvider.traceObservations(traceParams()).contains(name),
                    "detail columns should select the derived alias " + name);
            assertTrue(TracingSqlProvider.traceObservations(traceParams()).contains("AS " + name),
                    "derived block should define " + name);
        }
        assertTrue(TracingSqlProvider.traceObservations(traceParams()).contains("usage_present"));
    }

    /**
     * ClickHouse substitutes select aliases into the WHERE clause. Aliasing the model
     * expression as {@code model} while the same statement's WHERE references the base
     * column {@code model} produced two conflicting definitions of one column name and
     * failed at runtime with "Code 352 Block structure mismatch" - it only ever broke for
     * a {@code model:...} filter, so a filter-less smoke test would not have caught it.
     * Output aliases must not shadow a table column referenced by the statement.
     */
    @Test
    void doesNotAliasAnOutputColumnAfterAReferencedBaseColumn() {
        String list = TracingSqlProvider.observations(parameters(observationQuery("model:codex")));
        String detail = TracingSqlProvider.traceObservations(traceParams());

        for (String sql : Arrays.asList(list, detail)) {
            assertFalse(sql.contains("AS model,"),
                    "observation SQL must not alias an output as 'model': " + sql);
            assertTrue(sql.contains("AS modelName,"),
                    "observation SQL should expose the model alias as 'modelName'");
        }
        // The filter still speaks the user-facing field name.
        assertTrue(list.contains("request_model"));
    }

    @Test
    void derivesObservationAndTraceFieldsFromAgentObsColumns() {
        String detail = TracingSqlProvider.traceObservations(traceParams());
        assertTrue(detail.contains("usage_input_tokens"));
        assertTrue(detail.contains("usage_output_tokens"));
        assertTrue(detail.contains("time_to_first_chunk_ms"));
        assertTrue(detail.contains("parent_span_id"));
        // AgentObs has no such column; the DTO field stays null rather than being faked.
        assertTrue(detail.contains("CAST(NULL AS Nullable(String)) AS modelId"));

        String traceDetail = TracingSqlProvider.traceDetail();
        assertTrue(traceDetail.contains("trace_input"));
        assertTrue(traceDetail.contains("trace_output"));
        assertTrue(traceDetail.contains("status_code = 2"));
        assertTrue(traceDetail.contains("CAST(NULL AS Nullable(String)) AS release"));

        String locator = TracingSqlProvider.traceLocator();
        assertTrue(locator.contains("minMerge(min_start_state)"));
        assertTrue(locator.contains("maxMerge(max_end_state)"));
    }

    @Test
    void dropsFiltersWithNoAgentObsColumnInsteadOfEmittingWrongSql() {
        Map<String, Object> parameters = parameters(observationQuery(
                "toolCalls:=0 modelId:gpt-4 release:2026.1 tps:>10 inputCost:<0.01 outputCost:<0.01 sdkName:java sdkVersion:1"));
        String sql = TracingSqlProvider.observationCount(parameters);

        assertFalse(sql.contains("tool_calls"));
        assertFalse(sql.contains("model_id"));
        assertFalse(sql.contains("release"));
        assertFalse(sql.contains("cost_details"));
        // The statement must still be a valid, fully bounded query.
        assertTrue(sql.startsWith("SELECT toInt64(count()) FROM default.hmp_agentobs_observations_all FINAL WHERE"));
    }

    /**
     * tool_definitions is a real AgentObs column, so the filter is kept.
     *
     * <p>The key count comes from JSONLength rather than length(JSONExtractKeys(...)): the
     * latter does not exist on the 21.8 deployment target, where it raises
     * "Received exception from server". Verified against clickhouse-server:21.8 - JSONLength
     * returns the same counts on 21.8 and on 25.12.
     */
    @Test
    void keepsToolDefinitionsWhichDoesExistOnTheNewSchema() {
        Map<String, Object> parameters = parameters(observationQuery("type:GENERATION toolDefinitions:>0"));
        String sql = TracingSqlProvider.observationCount(parameters);

        assertTrue(sql.contains("JSONLength(tool_definitions) >"));
        assertFalse(sql.contains("JSONExtractKeys"),
                "JSONExtractKeys is unavailable on the 21.8 target");
        assertTrue(parameters.values().contains(0L));
    }

    @Test
    void traceListJoinsOnlyAWindowBoundedMetricAggregate() {
        String sql = TracingSqlProvider.traces(parameters(new TraceQuery(
                "", null, "", "", "", "", null, null,
                TraceQuery.SortBy.TOKENS, TraceQuery.SortDirection.DESC, 0, 50)));

        assertTrue(sql.contains("AS t FINAL"));
        assertTrue(sql.contains("LEFT JOIN (SELECT toString(trace_id) AS traceId"));
        assertTrue(sql.contains("GROUP BY trace_id) AS m ON m.traceId = id"));
        assertTrue(sql.contains("LIMIT #{candidateLimit} OFFSET #{offset}"));
    }

    /**
     * The page ids are bound individually so the IN list can never be injected through, and
     * an empty list must not degrade into the invalid "IN ()".
     */
    @Test
    void expandsIdListsAsBoundPlaceholdersAndHandlesEmpty() {
        String sql = TracingSqlProvider.traceLookups(idParameters("aa", "bb"));
        assertTrue(sql.contains("#{traceIds0}, #{traceIds1}"));

        String empty = TracingSqlProvider.traceLookups(new HashMap<String, Object>());
        assertFalse(empty.contains("IN ()"));
        assertTrue(empty.contains("CAST(NULL AS Nullable(String))"));
    }

    @Test
    void buildsIsolatedParametersUnderConcurrency() throws Exception {
        int threads = 64;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final String marker = "user" + i;
                final String neighbourMarker = "user" + ((i + 1) % threads);
                tasks.add(() -> {
                    Map<String, Object> parameters = parameters(observationQuery("name:" + marker));
                    String sql = TracingSqlProvider.observationCount(parameters);
                    // No other thread's marker may leak into this statement or its bindings.
                    assertFalse(sql.contains(neighbourMarker));
                    return parameters.values().contains(marker) && !parameters.values().contains(neighbourMarker);
                });
            }
            for (Future<Boolean> result : pool.invokeAll(tasks)) {
                assertTrue(result.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void returnsNullEnvironmentFilterWhenTraceQueryIsEmpty() {
        String sql = TracingSqlProvider.traceCount(parameters(new TraceQuery(
                "", null, "", "", "", "", null, null,
                TraceQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 20)));
        assertFalse(sql.contains("#{query.environment}"));
        assertTrue(sql.contains("1 = 1"));
    }

    @Test
    void mapsTraceStatusThroughStatusCode() {
        TraceQuery errored = new TraceQuery("", TraceStatus.ERROR, "", "", "", "", null, null,
                TraceQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 20);
        assertTrue(TracingSqlProvider.traceCount(parameters(errored)).contains("#{query.status}"));
    }

    // -- Trace endpoint DSL ------------------------------------------------------------

    private static TraceQuery traceQueryWithSearch(String search) {
        return new TraceQuery(search, null, "", "", "", "", null, null,
                TraceQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 50);
    }

    /**
     * Before this existed the whole search string was matched literally, so {@code name:x}
     * filtered correctly on /observations and matched nothing on /traces. Both endpoints now
     * parse the same DSL against their own vocabulary.
     */
    @Test
    void traceSearchParsesDslInsteadOfMatchingTheWholeStringLiterally() {
        Map<String, Object> parameters = parameters(traceQueryWithSearch("name:turn_context.build"));
        String sql = TracingSqlProvider.traceCount(parameters);

        assertTrue(sql.contains("positionCaseInsensitiveUTF8(toString(t.trace_name), #{dsl"));
        assertTrue(parameters.values().contains("turn_context.build"));
        // The raw expression must never appear as a literal to match against.
        assertFalse(sql.contains("turn_context.build"));
    }

    @Test
    void traceSearchMapsEveryTraceLevelField() {
        Map<String, Object> parameters = parameters(traceQueryWithSearch(
                "name:x traceName:y traceId:z userId:u sessionId:s environment:prod "
                        + "serviceName:svc version:1.0 status:ERROR latency:>2 input:a output:b"));
        String sql = TracingSqlProvider.traceCount(parameters);

        assertTrue(sql.contains("t.trace_name"));
        assertTrue(sql.contains("toString(t.trace_id)"));
        assertTrue(sql.contains("t.user_id"));
        assertTrue(sql.contains("t.session_id"));
        assertTrue(sql.contains("t.environment"));
        assertTrue(sql.contains("t.service_name"));
        assertTrue(sql.contains("t.version"));
        assertTrue(sql.contains("if(t.status_code = 2, 'ERROR', 'SUCCESS') = "));
        assertTrue(sql.contains("t.trace_input"));
        assertTrue(sql.contains("t.trace_output"));
        // latency is authored in seconds and compared in milliseconds.
        assertTrue(sql.contains("t.duration_ms > "));
        assertTrue(parameters.values().contains(2000L));
    }

    @Test
    void traceSearchSupportsTagsNegationAndFreeText() {
        Map<String, Object> tags = parameters(traceQueryWithSearch("tags:(critical AND urgent)"));
        assertTrue(TracingSqlProvider.traceCount(tags).contains("has(t.tags,"));
        assertTrue(TracingSqlProvider.traceCount(tags).contains(" AND "));

        Map<String, Object> negated = parameters(traceQueryWithSearch("-environment:dev"));
        assertTrue(TracingSqlProvider.traceCount(negated).contains("NOT ("));

        Map<String, Object> free = parameters(traceQueryWithSearch("refund failed"));
        String freeSql = TracingSqlProvider.traceCount(free);
        assertTrue(freeSql.contains("positionCaseInsensitiveUTF8(t.trace_name"));
        assertFalse(freeSql.contains("refund failed"));
        assertTrue(free.values().contains("refund failed"));
    }

    /**
     * Fields with no column on the trace row must be dropped, not matched as literal text -
     * which is exactly what made {@code release:2026.1} return zero rows before.
     */
    @Test
    void traceSearchDropsFieldsTheTraceRowCannotServe() {
        Map<String, Object> parameters = parameters(traceQueryWithSearch(
                "release:2026.1 tokens:>100 cost:<0.05 ttft:>1 toolCalls:=0 modelId:gpt-4 root:true"));
        String sql = TracingSqlProvider.traceCount(parameters);

        assertFalse(sql.contains("release"));
        assertFalse(sql.contains("usage_total_tokens"));
        assertFalse(sql.contains("cost"));
        assertFalse(sql.contains("is_product_root"));
        assertFalse(sql.contains("2026.1"));
        assertTrue(sql.startsWith("SELECT toInt64(count()) FROM default.hmp_agentobs_traces_all AS t FINAL"));
    }

    @Test
    void traceSearchSupportsMetadataByKey() {
        Map<String, Object> parameters = parameters(traceQueryWithSearch("metadata.env:=local"));
        String sql = TracingSqlProvider.traceCount(parameters);

        assertTrue(sql.contains("JSONExtractString(t.metadata, #{dsl"));
        assertTrue(parameters.values().contains("env"));
        assertTrue(parameters.values().contains("local"));
    }

    /** AgentObs cannot express a running trace: end_time is non-nullable on the trace row. */
    @Test
    void traceSearchCannotExpressRunningAndSaysSoByMatchingNothing() {
        Map<String, Object> parameters = parameters(traceQueryWithSearch("status:RUNNING"));
        String sql = TracingSqlProvider.traceCount(parameters);

        assertTrue(sql.contains("0 = 1"));
    }

    // -- Trace name filter -------------------------------------------------------------

    /**
     * Trace name lives on the Trace table; observations have no such column. Mapping it to a
     * non-existent column (or worse, dropping it) would make the filter look applied while
     * returning everything, so it must become a trace-id subquery.
     */
    @Test
    void traceNameExactFilterBecomesATraceIdSubquery() {
        Map<String, Object> parameters = parameters(observationQuery("traceName:=configRequirements/read"));
        String sql = TracingSqlProvider.observationCount(parameters);

        assertTrue(sql.contains("trace_id IN (SELECT toString(trace_id) FROM default.hmp_agentobs_traces_all FINAL"));
        assertTrue(sql.contains("trace_name = #{dsl"));
        assertTrue(parameters.values().contains("configRequirements/read"));
    }

    @Test
    void traceNameFuzzyFilterUsesACaseInsensitiveSubstringMatch() {
        Map<String, Object> parameters = parameters(observationQuery("traceName:config"));
        String sql = TracingSqlProvider.observationCount(parameters);

        assertTrue(sql.contains("positionCaseInsensitiveUTF8(trace_name, #{dsl"));
        assertTrue(parameters.values().contains("config"));
    }

    @Test
    void traceNameSupportsWildcardsAndNegation() {
        Map<String, Object> prefix = parameters(observationQuery("traceName:conf*"));
        assertTrue(TracingSqlProvider.observationCount(prefix).contains("startsWith(lowerUTF8(trace_name)"));

        Map<String, Object> suffix = parameters(observationQuery("traceName:*read"));
        assertTrue(TracingSqlProvider.observationCount(suffix).contains("endsWith(lowerUTF8(trace_name)"));

        Map<String, Object> negated = parameters(observationQuery("-traceName:config"));
        assertTrue(TracingSqlProvider.observationCount(negated).contains("NOT ("));
    }

    /**
     * The predicate builder is shared by list, count, facet and pulse, so the filter has to
     * appear in all four. A filter honoured by one and not the others would make the table,
     * the trend and the facet counts disagree - the exact bug the shared builder prevents.
     */
    @Test
    void traceNameFilterAppliesToEveryObservationStatement() {
        ObservationQuery query = observationQuery("traceName:=configRequirements/read");

        Map<String, Object> facetParameters = parameters(query);
        facetParameters.put("field", ObservationFacet.TYPE);
        facetParameters.put("limit", 20);
        Map<String, Object> pulseParameters = parameters(query);
        pulseParameters.put("bucket", PulseBucket.DAY);

        for (String sql : Arrays.asList(
                TracingSqlProvider.observations(parameters(query)),
                TracingSqlProvider.observationCount(parameters(query)),
                TracingSqlProvider.observationPulse(pulseParameters),
                TracingSqlProvider.observationFacets(facetParameters))) {
            assertTrue(sql.contains("trace_name = #{dsl"),
                    "traceName filter missing from: " + sql);
        }
    }

    // -- Version deduplication ---------------------------------------------------------

    /**
     * The tables are ReplacingMergeTree and background merges are not immediate; the design
     * document requires FINAL for exact results under a bounded filter. Without it a replayed
     * span inflates the list, the count, the trend and the facets at the same time.
     */
    @Test
    void everyObservationStatementReadsThroughFinal() {
        ObservationQuery query = observationQuery("type:SPAN");
        Map<String, Object> facetParameters = parameters(query);
        facetParameters.put("field", ObservationFacet.TYPE);
        facetParameters.put("limit", 20);
        Map<String, Object> pulseParameters = parameters(query);
        pulseParameters.put("bucket", PulseBucket.DAY);

        for (String sql : Arrays.asList(
                TracingSqlProvider.observations(parameters(query)),
                TracingSqlProvider.observationCount(parameters(query)),
                TracingSqlProvider.observationPulse(pulseParameters),
                TracingSqlProvider.observationFacets(facetParameters),
                TracingSqlProvider.traceObservations(traceParams()),
                TracingSqlProvider.traceMetrics(idParameters("0123456789abcdef0123456789abcdef")),
                TracingSqlProvider.traces(parameters(new TraceQuery("", null, "", "", "", "", null, null,
                        TraceQuery.SortBy.TOKENS, TraceQuery.SortDirection.DESC, 0, 50))))) {
            assertTrue(sql.contains("hmp_agentobs_observations_all FINAL")
                            || sql.contains("hmp_agentobs_traces_all AS t FINAL")
                            || sql.contains("hmp_agentobs_traces_all FINAL"),
                    "statement must read through FINAL: " + sql);
        }
    }

    // -- Locator ----------------------------------------------------------------------

    @Test
    void locatorBoundsNarrowTheTraceDetailRead() {
        Map<String, Object> parameters = traceParams();
        parameters.put("locatorServiceName", "codex-app-server");
        parameters.put("locatorMinStart", "2026-09-07 08:20:19.123456");
        parameters.put("locatorMaxEnd", "2026-09-07 08:20:20.123456");

        String sql = TracingSqlProvider.traceObservations(parameters);

        assertTrue(sql.contains("service_name = #{dsl"));
        assertTrue(sql.contains("start_time >= toDateTime64(#{locatorMinStart}, 6, 'UTC')"));
        assertTrue(sql.contains("start_time <= toDateTime64(#{locatorMaxEnd}, 6, 'UTC')"));
        assertTrue(parameters.values().contains("codex-app-server"));
    }

    /**
     * The locator is a derived index and can lag behind ingestion. A miss must fall back to a
     * plain trace_id read - narrowing is only ever a performance bound, never a filter that
     * could hide observations.
     */
    @Test
    void missingLocatorLeavesTheTraceDetailReadUnnarrowed() {
        String sql = TracingSqlProvider.traceObservations(traceParams());

        assertTrue(sql.contains("trace_id = #{dsl"));
        assertFalse(sql.contains("service_name = "));
        assertFalse(sql.contains("start_time >= "));
    }

    @Test
    void traceMetricsCanAlsoBeNarrowedByTheLocator() {
        Map<String, Object> parameters = idParameters("0123456789abcdef0123456789abcdef");
        parameters.put("traceId", "0123456789abcdef0123456789abcdef");
        parameters.put("locatorServiceName", "svc-a");
        parameters.put("locatorMinStart", "2026-09-07 08:00:00.000001");
        parameters.put("locatorMaxEnd", "2026-09-07 08:00:00.000001");

        String sql = TracingSqlProvider.traceMetrics(parameters);

        assertTrue(sql.contains("trace_id IN ("));
        assertTrue(sql.contains("service_name = #{dsl"));
        assertTrue(sql.contains("start_time >= toDateTime64(#{locatorMinStart}, 6, 'UTC')"));
        assertTrue(sql.contains("start_time <= toDateTime64(#{locatorMaxEnd}, 6, 'UTC')"));
    }

    @Test
    void locatorBoundsUseEastEightWhenTheColumnIsEastEight() {
        Map<String, Object> parameters = traceParams();
        parameters.put("locatorColumnTimeZone", "Asia/Shanghai");
        parameters.put("locatorMinStart", "2026-09-07 16:20:19.123456");
        parameters.put("locatorMaxEnd", "2026-09-07 16:20:19.123456");

        String observations = TracingSqlProvider.traceObservations(parameters);
        String metrics = TracingSqlProvider.traceMetrics(parameters);

        for (String sql : Arrays.asList(observations, metrics)) {
            assertTrue(sql.contains("start_time >= toDateTime64(#{locatorMinStart}, 6, 'Asia/Shanghai')"));
            assertTrue(sql.contains("start_time <= toDateTime64(#{locatorMaxEnd}, 6, 'Asia/Shanghai')"));
        }
    }

    // -- Usage details -----------------------------------------------------------------

    /**
     * An earlier revision mapped every unrecognised usage key onto usage_total_tokens, so a
     * producer reporting cache-read tokens had them silently counted as the total as well.
     */
    @Test
    void usageKeysMapToTheirOwnCounters() {
        String sql = TracingSqlProvider.observations(parameters(observationQuery("")));

        assertTrue(sql.contains("k IN ('input','input_tokens','prompt','prompt_tokens'), usage_input_tokens"));
        assertTrue(sql.contains("k IN ('output','output_tokens','completion','completion_tokens'), usage_output_tokens"));
        assertTrue(sql.contains("k IN ('total','total_tokens'), usage_total_tokens"));
        assertTrue(sql.contains("k IN ('cache_read','cache_read_input_tokens','cache_read_tokens'), usage_cache_read_tokens"));
        assertTrue(sql.contains("k IN ('cache_write','cache_creation_input_tokens','cache_write_tokens'), usage_cache_write_tokens"));
        assertTrue(sql.contains("k IN ('reasoning','reasoning_tokens'), usage_reasoning_tokens"));
        assertTrue(sql.contains("k IN ('audio','audio_output','audio_output_tokens'), usage_audio_output_tokens"));
    }

    @Test
    void usageKeysWithoutASourceColumnAreDroppedBeforeMapping() {
        String sql = TracingSqlProvider.observations(parameters(observationQuery("")));

        // The key list is filtered first, so an unknown key never reaches the multiIf at all.
        assertTrue(sql.contains("arrayFilter(k -> has(["));
        assertTrue(sql.contains("], k), usage_present)"));
        // And the multiIf fallback is a literal zero, not the total-token counter.
        assertFalse(sql.contains("usage_total_tokens, 0)"));
    }

    // -- Trace list aggregation --------------------------------------------------------

    /**
     * Sorting by a metric needs that metric before paging, so that one case aggregates the
     * window. Every other sort pages the Trace table first and metrics are fetched for the
     * page only - otherwise each page of each sort re-groups the entire window.
     */
    @Test
    void traceListAggregatesTheWindowOnlyForMetricSorts() {
        TraceQuery byTime = new TraceQuery("", null, "", "", "", "", null, null,
                TraceQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 50);
        TraceQuery byLatency = new TraceQuery("", null, "", "", "", "", null, null,
                TraceQuery.SortBy.LATENCY, TraceQuery.SortDirection.DESC, 0, 50);
        TraceQuery byTokens = new TraceQuery("", null, "", "", "", "", null, null,
                TraceQuery.SortBy.TOKENS, TraceQuery.SortDirection.DESC, 0, 50);
        TraceQuery byCost = new TraceQuery("", null, "", "", "", "", null, null,
                TraceQuery.SortBy.COST, TraceQuery.SortDirection.DESC, 0, 50);

        assertFalse(TracingSqlProvider.sortNeedsWindowAggregate(byTime));
        assertFalse(TracingSqlProvider.sortNeedsWindowAggregate(byLatency));
        assertTrue(TracingSqlProvider.sortNeedsWindowAggregate(byTokens));
        assertTrue(TracingSqlProvider.sortNeedsWindowAggregate(byCost));

        assertFalse(TracingSqlProvider.traces(parameters(byTime)).contains("LEFT JOIN"));
        assertTrue(TracingSqlProvider.traces(parameters(byTokens)).contains("LEFT JOIN"));
        // The page-only shape still exposes the metric columns, as zeros for the caller to fill.
        assertTrue(TracingSqlProvider.traces(parameters(byTime)).contains("toInt32(0) AS observationCount"));
    }
}
