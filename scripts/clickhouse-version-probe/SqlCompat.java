import com.icbc.aiops.langfuse.domain.ObservationLevel;
import com.icbc.aiops.langfuse.domain.ObservationType;
import com.icbc.aiops.langfuse.domain.TraceStatus;
import com.icbc.aiops.langfuse.mapper.TracingSqlProvider;
import com.icbc.aiops.langfuse.mapper.ObservabilitySqlProvider;
import com.icbc.aiops.langfuse.mapper.WidgetMetricSqlProvider;
import com.icbc.aiops.langfuse.service.ObservationFacet;
import com.icbc.aiops.langfuse.service.ObservationQuery;
import com.icbc.aiops.langfuse.service.PulseBucket;
import com.icbc.aiops.langfuse.service.TraceQuery;
import com.icbc.aiops.langfuse.service.WidgetMetricQuery;
import com.icbc.aiops.langfuse.service.WidgetMetricFilter;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the AgentObs query providers and executes each statement against a target ClickHouse
 * over HTTP. Used to check generated SQL against the 21.8 deployment target, where several
 * newer functions are unavailable.
 */
public class SqlCompat {

    private static final String ENDPOINT = System.getProperty("ch.url", "http://127.0.0.1:8124/");
    private static final String USER = System.getProperty("ch.user", "default");
    private static final String PASSWORD = System.getProperty("ch.password", "");

    public static void main(String[] args) {
        Map<String, String> statements = new LinkedHashMap<>();
        statements.putAll(observationStatements());
        statements.putAll(traceStatements());
        statements.putAll(observabilityStatements());
        statements.putAll(widgetStatements());

        int ok = 0;
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> entry : statements.entrySet()) {
            String error = execute("EXPLAIN SYNTAX " + entry.getValue());
            if (error != null) {
                error = "EXPLAIN SYNTAX: " + error;
            } else {
                error = execute(entry.getValue());
            }
            if (error == null) {
                ok++;
                System.out.println("  [OK]   " + entry.getKey());
            } else {
                failures.add(entry.getKey());
                System.out.println("  [FAIL] " + entry.getKey());
                System.out.println("         SQL : " + entry.getValue());
                System.out.println("         ERR : " + error.replace("\n", " | "));
            }
        }
        System.out.println();
        System.out.println("  通过 " + ok + "/" + statements.size());
        if (!failures.isEmpty()) {
            System.out.println("  失败项: " + failures);
            System.exit(1);
        }
    }

    private static String firstLine(String error) {
        int nl = error.indexOf('\n');
        return nl < 0 ? error : error.substring(0, Math.min(nl, 400));
    }

    private static Map<String, String> observationStatements() {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, Object> list = params(observationQuery("level:ERROR type:SPAN"));
        add(out, "observations 列表", TracingSqlProvider.observations(list), list);

        Map<String, Object> count = params(observationQuery("name:build"));
        add(out, "observationCount", TracingSqlProvider.observationCount(count), count);

        Map<String, Object> traceName = params(observationQuery("traceName:=configRequirements/read"));
        add(out, "observations traceName 精确", TracingSqlProvider.observationCount(traceName), traceName);

        Map<String, Object> traceNameFuzzy = params(observationQuery("traceName:config"));
        add(out, "observationCount traceName 模糊", TracingSqlProvider.observationCount(traceNameFuzzy), traceNameFuzzy);

        Map<String, Object> metadata = params(observationQuery("metadata.env:=local -has:endTime"));
        add(out, "observationCount metadata+否定", TracingSqlProvider.observationCount(metadata), metadata);

        Map<String, Object> toolDefs = params(observationQuery("toolDefinitions:>0"));
        add(out, "observationCount toolDefinitions", TracingSqlProvider.observationCount(toolDefs), toolDefs);

        Map<String, Object> numeric = params(observationQuery("latency:>1 tokens:>10 cost:<0.5"));
        add(out, "observationCount 数值比较", TracingSqlProvider.observationCount(numeric), numeric);

        Map<String, Object> tags = params(observationQuery("tags:(a AND b)"));
        add(out, "observationCount tags", TracingSqlProvider.observationCount(tags), tags);

        Map<String, Object> facet = params(observationQuery(""));
        facet.put("field", ObservationFacet.TAG);
        facet.put("limit", 20);
        add(out, "Facet TAG", TracingSqlProvider.observationFacets(facet), facet);

        Map<String, Object> facetTRACE_NAME = params(observationQuery(""));
        facetTRACE_NAME.put("field", ObservationFacet.TRACE_NAME);
        facetTRACE_NAME.put("limit", 20);
        add(out, "Facet TRACE_NAME", TracingSqlProvider.observationFacets(facetTRACE_NAME), facetTRACE_NAME);

        Map<String, Object> facetSERVICE = params(observationQuery(""));
        facetSERVICE.put("field", ObservationFacet.SERVICE_NAME);
        facetSERVICE.put("limit", 20);
        add(out, "Facet SERVICE_NAME", TracingSqlProvider.observationFacets(facetSERVICE), facetSERVICE);

        for (PulseBucket bucket : PulseBucket.values()) {
            Map<String, Object> pulse = params(observationQuery(""));
            pulse.put("bucket", bucket);
            add(out, "Pulse " + bucket, TracingSqlProvider.observationPulse(pulse), pulse);
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("traceId", "0123456789abcdef0123456789abcdef");
        add(out, "traceObservations 无收窄", TracingSqlProvider.traceObservations(detail), detail);

        Map<String, Object> narrowed = new HashMap<>(detail);
        narrowed.put("locatorServiceName", "svc-a");
        narrowed.put("locatorMinStart", "2026-09-01 00:00:00.000000");
        narrowed.put("locatorMaxEnd", "2026-09-11 00:00:00.000000");
        add(out, "traceObservations locator 收窄", TracingSqlProvider.traceObservations(narrowed), narrowed);

        out.put("traceLocator", TracingSqlProvider.traceLocator()
                .replace("#{traceId}", "'0123456789abcdef0123456789abcdef'"));
        out.put("traceDetail", TracingSqlProvider.traceDetail()
                .replace("#{traceId}", "'0123456789abcdef0123456789abcdef'"));
        Map<String,Object> lookIds = ids("0123456789abcdef0123456789abcdef");
        add(out, "traceLookups", TracingSqlProvider.traceLookups(lookIds), lookIds);
        Map<String,Object> metricIds = ids("0123456789abcdef0123456789abcdef");
        add(out, "traceMetrics", TracingSqlProvider.traceMetrics(metricIds), metricIds);
        Map<String,Object> narrowedMetrics = new HashMap<>(metricIds);
        narrowedMetrics.put("locatorMinStart", "2026-09-01 00:00:00.000000");
        narrowedMetrics.put("locatorMaxEnd", "2026-09-11 00:00:00.000000");
        add(out, "traceMetrics locator UTC", TracingSqlProvider.traceMetrics(narrowedMetrics), narrowedMetrics);
        out.put("session observations", TracingSqlProvider.observationsBySession()
                .replace("#{sessionId}", "'session-a'").replace("#{limit}", "2001"));
        return out;
    }

    private static Map<String, String> traceStatements() {
        Map<String, String> out = new LinkedHashMap<>();
        for (TraceQuery.SortBy sort : TraceQuery.SortBy.values()) {
            TraceQuery query = new TraceQuery("", null, "", "", "", "", null, null,
                    sort, TraceQuery.SortDirection.DESC, 0, 50);
            Map<String,Object> tp = params(query);
            add(out, "traces 排序 " + sort, TracingSqlProvider.traces(tp), tp);
        }
        TraceQuery dsl = new TraceQuery("name:build serviceName:svc-a status:ERROR latency:>1 metadata.env:=local",
                null, "", "", "", "", null, null,
                TraceQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 50);
        Map<String,Object> dp = params(dsl);
        add(out, "traces Trace DSL", TracingSqlProvider.traceCount(dp), dp);
        Map<String,Object> tc = params(new TraceQuery("", null, "", "", "", "",
                null, null, TraceQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 50));
        add(out, "traceCount", TracingSqlProvider.traceCount(tc), tc);
        out.put("session traces", TracingSqlProvider.tracesBySession()
                .replace("#{sessionId}", "'session-a'").replace("#{limit}", "201"));
        out.put("user traces", TracingSqlProvider.tracesByUser()
                .replace("#{userId}", "'user-a'"));
        return out;
    }

    private static Map<String, String> observabilityStatements() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("summary traces", ObservabilitySqlProvider.traceSummaryStats());
        out.put("summary observations", ObservabilitySqlProvider.observationSummaryStats());
        out.put("summary trend traces", ObservabilitySqlProvider.traceMetricTimeSeries());
        out.put("summary trend observations", ObservabilitySqlProvider.observationMetricTimeSeries());
        out.put("trace scores", ObservabilitySqlProvider.traceScores()
                .replace("#{traceId}", "'0123456789abcdef0123456789abcdef'"));

        Map<String, Object> filters = new HashMap<>();
        filters.put("search", "a");
        filters.put("environment", "production");
        filters.put("size", 20);
        filters.put("offset", 0);
        add(out, "sessions", ObservabilitySqlProvider.sessions(filters), filters);
        add(out, "session count", ObservabilitySqlProvider.sessionCount(filters), filters);
        out.put("session summary", ObservabilitySqlProvider.session()
                .replace("#{sessionId}", "'session-a'"));
        add(out, "users", ObservabilitySqlProvider.users(filters), filters);
        add(out, "user count", ObservabilitySqlProvider.userCount(filters), filters);
        out.put("user summary", ObservabilitySqlProvider.user()
                .replace("#{userId}", "'user-a'"));
        out.put("user sessions", ObservabilitySqlProvider.userSessions()
                .replace("#{userId}", "'user-a'"));
        return out;
    }

    private static Map<String, String> widgetStatements() {
        Map<String, String> out = new LinkedHashMap<>();
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-15T00:00:00Z");
        for (WidgetMetricQuery query : Arrays.asList(
                new WidgetMetricQuery("compat", "TRACES", "name", "count", "count", true, from, to),
                new WidgetMetricQuery("compat", "TRACES", "environment", "latency", "p95", false, from, to),
                new WidgetMetricQuery("compat", "TRACES", "userId", "totalTokens", "sum", false, from, to),
                new WidgetMetricQuery("compat", "TRACES", null, "totalCost", "sum", true, from, to),
                new WidgetMetricQuery("compat", "OBSERVATIONS", "providedModelName", "totalCost", "avg", false, from, to),
                new WidgetMetricQuery("compat", "OBSERVATIONS", "level", "totalTokens", "p90", true, from, to))) {
            Map<String, Object> parameters = params(query);
            add(out, "widget " + query.view() + " " + query.measure() + " " + query.aggregation(),
                    WidgetMetricSqlProvider.metric(parameters), parameters);
        }
        WidgetMetricQuery filtered = new WidgetMetricQuery("compat", "OBSERVATIONS", null,
                "count", "count", false, from, to, Arrays.asList(new WidgetMetricFilter(
                        "environment", "stringOptions", "any of", Arrays.asList("prod", "staging"))));
        Map<String, Object> filteredParameters = params(filtered);
        add(out, "widget OBSERVATIONS filtered", WidgetMetricSqlProvider.metric(filteredParameters),
                filteredParameters);
        return out;
    }

    /** Renders via reflection so the parameter map is available for placeholder substitution. */
    private static String render(Class<?> type, String method, Map<String, Object> parameters) {
        try {
            Object sql = type.getMethod(method, Map.class).invoke(null, parameters);
            return substitute(String.valueOf(sql), parameters);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void add(Map<String, String> out, String name, String sql, Map<String, Object> parameters) {
        out.put(name, substitute(sql, parameters));
    }

    private static Map<String, Object> params(Object query) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query);
        return parameters;
    }

    private static Map<String, Object> ids(String... traceIds) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("traceIds", Arrays.asList(traceIds));
        return parameters;
    }

    private static ObservationQuery observationQuery(String search) {
        return new ObservationQuery(search, "", "", null, null, "", "", "", null, null,
                ObservationQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 50);
    }

    /** Replaces MyBatis placeholders with the values the provider actually bound. */
    private static String substitute(String sql, Map<String, Object> parameters) {
        Map<String, String> scalars = new HashMap<>();
        scalars.put("offset", "0");
        scalars.put("candidateLimit", "51");
        scalars.put("limit", "20");
        scalars.put("query.size", "50");
        scalars.put("query.page", "0");
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getValue() != null) {
                scalars.put(entry.getKey(), literal(entry.getValue()));
            }
        }
        // Fields of the query objects that MyBatis resolves lazily.
        Object query = parameters.get("query");
        if (query instanceof ObservationQuery) {
            ObservationQuery q = (ObservationQuery) query;
            scalars.put("query.environment", lit(q.environment()));
            scalars.put("query.serviceName", lit(q.serviceName()));
            scalars.put("query.level", lit(q.level() == null ? null : q.level().name()));
            scalars.put("query.type", lit(q.type() == null ? null : q.type().name()));
            scalars.put("query.model", lit(q.model()));
            scalars.put("query.traceId", lit(q.traceId()));
            scalars.put("query.tag", lit(q.tag()));
            scalars.put("query.search", lit(q.search()));
            scalars.put("query.fromTimestamp", literal(q.fromTimestamp()));
            scalars.put("query.toTimestamp", literal(q.toTimestamp()));
        } else if (query instanceof TraceQuery) {
            TraceQuery q = (TraceQuery) query;
            scalars.put("query.environment", lit(q.environment()));
            scalars.put("query.userId", lit(q.userId()));
            scalars.put("query.sessionId", lit(q.sessionId()));
            scalars.put("query.tag", lit(q.tag()));
            scalars.put("query.status", lit(q.status() == null ? null : q.status().name()));
            scalars.put("query.fromTimestamp", literal(q.fromTimestamp()));
            scalars.put("query.toTimestamp", literal(q.toTimestamp()));
        } else if (query instanceof WidgetMetricQuery) {
            WidgetMetricQuery q = (WidgetMetricQuery) query;
            scalars.put("query.fromTimestamp", literal(q.fromTimestamp()));
            scalars.put("query.toTimestamp", literal(q.toTimestamp()));
        }
        String result = sql;
        for (Map.Entry<String, String> entry : scalars.entrySet()) {
            result = result.replace("#{" + entry.getKey() + "}", entry.getValue());
        }
        // traceMetrics binds ids as traceIds0, traceIds1, ...
        for (int i = 0; i < 8; i++) {
            if (result.contains("#{traceIds" + i + "}")) {
                result = result.replace("#{traceIds" + i + "}", "'0123456789abcdef0123456789abcdef'");
            }
        }
        if (result.contains("#{")) {
            throw new IllegalStateException("unsubstituted placeholder in: " + result);
        }
        return result;
    }

    private static String literal(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Instant) {
            return "toDateTime64('" + value.toString().replace("T", " ").replace("Z", "") + "', 6)";
        }
        if (value instanceof java.math.BigDecimal) {
            return value.toString();
        }
        return lit(String.valueOf(value));
    }

    private static String lit(Object value) {
        if (value == null) {
            return "NULL";
        }
        return "'" + String.valueOf(value).replace("'", "\\'") + "'";
    }

    private static String execute(String sql) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(ENDPOINT + "?user=" + USER
                    + (PASSWORD.isEmpty() ? "" : "&password=" + PASSWORD));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            try (OutputStream body = connection.getOutputStream()) {
                body.write(sql.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (status == 200) {
                connection.getInputStream().close();
                return null;
            }
            java.io.InputStream stream = connection.getErrorStream();
            if (stream == null) {
                return "HTTP " + status;
            }
            byte[] buffer = new byte[1024];
            int read = stream.read(buffer);
            return read < 0 ? "HTTP " + status : new String(buffer, 0, read, StandardCharsets.UTF_8);
        } catch (Exception failure) {
            return failure.getClass().getSimpleName() + ": " + failure.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
