package com.icbc.aiops.langfuse.service;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("mock")
public class MockObservabilityQueryService implements ObservabilityQueryService {

    private static final Pattern FILTER_TOKEN = Pattern.compile(
            "(?<!\\S)(-?)([A-Za-z][A-Za-z0-9_.-]*):(\"[^\"]*\"|\\([^)]*\\)|\\S+)");

    private final List<TraceDetail> traces = createTraces();
    private final List<Observation> observations = createObservations();
    private final List<SessionSummary> sessions = createSessions();

    @Override
    public DashboardSummary getSummary() {
        long totalTokens = traces.stream().mapToLong(TraceDetail::totalTokens).sum();
        BigDecimal totalCost = traces.stream()
                .map(TraceDetail::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long averageLatency = Math.round(traces.stream()
                .mapToLong(TraceDetail::latencyMs)
                .average()
                .orElse(0));
        int errors = (int) traces.stream().filter(trace -> trace.status() == TraceStatus.ERROR).count();
        return new DashboardSummary(traces.size(), observations.size(), errors,
                totalTokens, totalCost, averageLatency);
    }

    @Override
    public List<MetricPoint> getMetricTimeSeries() {
        return com.icbc.aiops.langfuse.util.Java8Collections.listOf(
                metric("2026-08-22T00:00:00Z", 46, 2, 38_420, "0.2184", 1_640),
                metric("2026-08-23T00:00:00Z", 58, 3, 47_180, "0.2671", 1_820),
                metric("2026-08-24T00:00:00Z", 53, 1, 45_830, "0.2415", 1_570),
                metric("2026-08-25T00:00:00Z", 71, 4, 62_940, "0.3542", 2_110),
                metric("2026-08-26T00:00:00Z", 68, 2, 57_120, "0.3198", 1_760),
                metric("2026-08-27T00:00:00Z", 82, 5, 73_260, "0.4027", 2_340),
                metric("2026-08-28T00:00:00Z", 77, 3, 69_440, "0.3816", 1_980));
    }

    @Override
    public PageResponse<TraceSummary> findTraces(TraceQuery query) {
        Comparator<TraceDetail> comparator;
        switch (query.sortBy()) {
            case LATENCY:
                comparator = Comparator.comparingLong(TraceDetail::latencyMs);
                break;
            case TOKENS:
                comparator = Comparator.comparingLong(TraceDetail::totalTokens);
                break;
            case COST:
                comparator = Comparator.comparing(TraceDetail::totalCost);
                break;
            case TIMESTAMP:
            default:
                comparator = Comparator.comparing(TraceDetail::timestamp);
                break;
        }
        if (query.direction() == TraceQuery.SortDirection.DESC) {
            comparator = comparator.reversed();
        }

        List<TraceSummary> matches = traces.stream()
                .filter(trace -> matches(query.search(), trace.id(), trace.name(), trace.userId(),
                        trace.sessionId(), trace.environment(), String.join(" ", trace.tags())))
                .filter(trace -> query.status() == null || trace.status() == query.status())
                .filter(trace -> query.environment() == null || query.environment().trim().isEmpty()
                        || trace.environment().equalsIgnoreCase(query.environment()))
                .filter(trace -> query.userId() == null || query.userId().trim().isEmpty()
                        || query.userId().equals(trace.userId()))
                .filter(trace -> query.sessionId() == null || query.sessionId().trim().isEmpty()
                        || query.sessionId().equals(trace.sessionId()))
                .filter(trace -> query.tag() == null || query.tag().trim().isEmpty()
                        || trace.tags().contains(query.tag()))
                .filter(trace -> query.fromTimestamp() == null || !trace.timestamp().isBefore(query.fromTimestamp()))
                .filter(trace -> query.toTimestamp() == null || trace.timestamp().isBefore(query.toTimestamp()))
                .sorted(comparator)
                .map(TraceDetail::toSummary)
                .collect(java.util.stream.Collectors.toList());
        return page(matches, query.page(), query.size());
    }

    @Override
    public TraceDetail getTrace(String traceId) {
        return traces.stream()
                .filter(trace -> trace.id().equals(traceId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Trace not found: " + traceId));
    }

    @Override
    public List<Observation> findTraceObservations(String traceId) {
        getTrace(traceId);
        return observations.stream()
                .filter(observation -> observation.traceId().equals(traceId))
                .sorted(Comparator.comparing(Observation::startTime))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<TraceScore> findTraceScores(String traceId) {
        getTrace(traceId);
        if (!traceId.equals("trace-rag-001")) {
            return com.icbc.aiops.langfuse.util.Java8Collections.listOf();
        }
        return com.icbc.aiops.langfuse.util.Java8Collections.listOf(
                new TraceScore("score-rag-quality", traceId, "obs-rag-gen", "quality", "NUMERIC", 0.92,
                        null, "API", "Grounded and concise", Instant.parse("2026-08-28T08:41:26Z")),
                new TraceScore("score-rag-helpful", traceId, null, "user-helpfulness", "BOOLEAN", 1.0,
                        null, "API", null, Instant.parse("2026-08-28T08:41:29Z")));
    }

    @Override
    public List<TraceComment> findTraceComments(String traceId) {
        getTrace(traceId);
        if (!traceId.equals("trace-rag-001")) {
            return com.icbc.aiops.langfuse.util.Java8Collections.listOf();
        }
        return com.icbc.aiops.langfuse.util.Java8Collections.listOf(new TraceComment("comment-rag-1", "OBSERVATION", "obs-rag-gen",
                "Answer is grounded in the retrieved policy document.", "reviewer-1", "output",
                Instant.parse("2026-08-28T08:42:10Z"), Instant.parse("2026-08-28T08:42:10Z")));
    }

    @Override
    public PageResponse<Observation> findObservations(ObservationQuery query) {
        Comparator<Observation> comparator;
        switch (query.sortBy()) {
            case LATENCY: comparator = Comparator.comparingLong(Observation::latencyMs); break;
            case TOKENS: comparator = Comparator.comparingLong(item -> item.inputTokens() + item.outputTokens()); break;
            case COST: comparator = Comparator.comparing(Observation::totalCost); break;
            case TIMESTAMP:
            default: comparator = Comparator.comparing(Observation::startTime); break;
        }
        if (query.direction() == TraceQuery.SortDirection.DESC) comparator = comparator.reversed();
        List<Observation> matches = filteredObservations(query).sorted(comparator)
                .collect(java.util.stream.Collectors.toList());
        return page(matches, query.page(), query.size());
    }

    @Override
    public List<ObservationFacetValue> findObservationFacets(
            ObservationQuery query, ObservationFacet field, int limit) {
        Function<Observation, String> value;
        switch (field) {
            case TYPE: value = item -> item.type().name(); break;
            case ROOT: value = item -> item.parentObservationId() == null ? "true" : "false"; break;
            case LEVEL: value = item -> item.level().name(); break;
            case NAME: value = Observation::name; break;
            case TRACE_NAME: value = Observation::traceName; break;
            case MODEL: value = Observation::model; break;
            case PROMPT_NAME: value = Observation::promptName; break;
            case ENVIRONMENT: value = item -> traceFor(item.traceId()).environment(); break;
            case USER_ID: value = item -> traceFor(item.traceId()).userId(); break;
            case SESSION_ID: value = item -> traceFor(item.traceId()).sessionId(); break;
            case TAG:
            default:
                value = item -> traceFor(item.traceId()).tags().stream().findFirst().orElse(null);
                break;
        }
        return filteredObservations(queryForFacet(query, field))
                .map(value)
                .filter(item -> item != null && !item.trim().isEmpty())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(entry -> new ObservationFacetValue(entry.getKey(), entry.getValue()))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<ObservationPulsePoint> findObservationPulse(
            ObservationQuery query, PulseBucket bucket) {
        ChronoUnit unit = bucket == PulseBucket.HOUR ? ChronoUnit.HOURS : ChronoUnit.DAYS;
        Map<Instant, List<Observation>> byBucket = filteredObservations(query)
                .collect(Collectors.groupingBy(item -> item.startTime().truncatedTo(unit), LinkedHashMap::new, Collectors.toList()));
        return byBucket.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            List<Observation> values = entry.getValue();
            BigDecimal cost = values.stream().map(Observation::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
            long latency = Math.round(values.stream().mapToLong(Observation::latencyMs).average().orElse(0));
            return new ObservationPulsePoint(entry.getKey(), values.size(), cost, latency);
        }).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public PageResponse<SessionSummary> findSessions(String search, int page, int size) {
        List<SessionSummary> matches = sessions.stream()
                .filter(session -> matches(search, session.id(), session.userId()))
                .sorted(Comparator.comparing(SessionSummary::createdAt).reversed())
                .collect(java.util.stream.Collectors.toList());
        return page(matches, page, size);
    }

    @Override
    public SessionDetail getSession(String sessionId) {
        SessionSummary summary = sessions.stream()
                .filter(session -> session.id().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        List<TraceSummary> sessionTraces = traces.stream()
                .filter(trace -> sessionId.equals(trace.sessionId()))
                .sorted(Comparator.comparing(TraceDetail::timestamp))
                .map(TraceDetail::toSummary)
                .collect(java.util.stream.Collectors.toList());
        List<String> traceIds = sessionTraces.stream().map(TraceSummary::id).collect(java.util.stream.Collectors.toList());
        List<Observation> sessionObservations = observations.stream()
                .filter(observation -> traceIds.contains(observation.traceId()))
                .sorted(Comparator.comparing(Observation::startTime))
                .collect(java.util.stream.Collectors.toList());
        return new SessionDetail(summary, sessionTraces, sessionObservations);
    }

    @Override
    public PageResponse<UserSummary> findUsers(String search, String environment, int page, int size) {
        List<UserSummary> users = traces.stream()
                .filter(trace -> trace.userId() != null)
                .filter(trace -> environment == null || environment.trim().isEmpty()
                        || environment.equals(trace.environment()))
                .collect(java.util.stream.Collectors.groupingBy(TraceDetail::userId))
                .entrySet().stream()
                .map(entry -> {
                    List<TraceDetail> userTraces = entry.getValue();
                    List<String> traceIds = userTraces.stream().map(TraceDetail::id).collect(java.util.stream.Collectors.toList());
                    List<Observation> userObservations = observations.stream()
                            .filter(item -> traceIds.contains(item.traceId())).collect(java.util.stream.Collectors.toList());
                    return new UserSummary(entry.getKey(), userTraces.get(0).environment(),
                            userTraces.stream().map(TraceDetail::timestamp).min(Comparator.naturalOrder())
                                    .orElseThrow(() -> new IllegalStateException("User has no traces")),
                            userTraces.stream().map(TraceDetail::timestamp).max(Comparator.naturalOrder())
                                    .orElseThrow(() -> new IllegalStateException("User has no traces")),
                            userTraces.size(), userObservations.size(),
                            userTraces.stream().mapToLong(TraceDetail::totalTokens).sum(),
                            userTraces.stream().map(TraceDetail::totalCost)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add));
                })
                .filter(user -> matches(search, user.id(), user.environment()))
                .sorted(Comparator.comparing(UserSummary::traceCount).reversed())
                .collect(java.util.stream.Collectors.toList());
        return page(users, page, size);
    }

    @Override
    public UserDetail getUser(String userId) {
        UserSummary summary = findUsers(userId, "", 0, 50).items().stream()
                .filter(user -> user.id().equals(userId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        List<TraceSummary> userTraces = traces.stream()
                .filter(trace -> userId.equals(trace.userId()))
                .sorted(Comparator.comparing(TraceDetail::timestamp).reversed())
                .map(TraceDetail::toSummary)
                .collect(java.util.stream.Collectors.toList());
        List<SessionSummary> userSessions = sessions.stream()
                .filter(session -> userId.equals(session.userId()))
                .sorted(Comparator.comparing(SessionSummary::createdAt).reversed())
                .collect(java.util.stream.Collectors.toList());
        return new UserDetail(summary, userTraces, userSessions);
    }

    private boolean matches(String search, String... values) {
        String normalized = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (normalized.trim().isEmpty()) return true;
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(normalized)) return true;
        }
        return false;
    }

    private Stream<Observation> filteredObservations(ObservationQuery query) {
        return observations.stream()
                .filter(item -> matchesObservationSearch(query.search(), item))
                .filter(item -> query.environment() == null || query.environment().trim().isEmpty()
                        || query.environment().equalsIgnoreCase(traceFor(item.traceId()).environment()))
                .filter(item -> query.type() == null || item.type() == query.type())
                .filter(item -> query.level() == null || item.level() == query.level())
                .filter(item -> query.model() == null || query.model().trim().isEmpty()
                        || equalsIgnoreCase(query.model(), item.model()))
                .filter(item -> query.traceId() == null || query.traceId().trim().isEmpty()
                        || query.traceId().trim().equals(item.traceId()))
                .filter(item -> query.tag() == null || query.tag().trim().isEmpty()
                        || containsIgnoreCase(traceFor(item.traceId()).tags(), query.tag()))
                .filter(item -> query.fromTimestamp() == null || !item.startTime().isBefore(query.fromTimestamp()))
                .filter(item -> query.toTimestamp() == null || item.startTime().isBefore(query.toTimestamp()));
    }

    private ObservationQuery queryForFacet(ObservationQuery query, ObservationFacet field) {
        return new ObservationQuery(removeFacetField(query.search(), field),
                field == ObservationFacet.ENVIRONMENT ? "" : query.environment(),
                field == ObservationFacet.SERVICE_NAME ? "" : query.serviceName(),
                field == ObservationFacet.TYPE ? null : query.type(),
                field == ObservationFacet.LEVEL ? null : query.level(),
                field == ObservationFacet.MODEL ? "" : query.model(),
                query.traceId(),
                field == ObservationFacet.TAG ? "" : query.tag(),
                query.fromTimestamp(), query.toTimestamp(), query.sortBy(), query.direction(), query.page(), query.size());
    }

    private String removeFacetField(String search, ObservationFacet facet) {
        if (search == null || search.trim().isEmpty()) return "";
        String expected;
        switch (facet) {
            case ENVIRONMENT: expected = "environment|env"; break;
            case TYPE: expected = "type"; break;
            case ROOT: expected = "root"; break;
            case LEVEL: expected = "level|status"; break;
            case NAME: expected = "name"; break;
            case TRACE_NAME: expected = "tracename"; break;
            case MODEL: expected = "model|modelid"; break;
            case PROMPT_NAME: expected = "prompt|promptname"; break;
            case USER_ID: expected = "user|userid"; break;
            case SESSION_ID: expected = "session|sessionid"; break;
            case TAG: expected = "tag|tags"; break;
            default: expected = ""; break;
        }
        Pattern target = Pattern.compile("(?i)^(?:" + expected + ")$");
        Matcher matcher = FILTER_TOKEN.matcher(search);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, target.matcher(matcher.group(2)).matches()
                    ? " " : Matcher.quoteReplacement(matcher.group(0)));
        }
        matcher.appendTail(result);
        return result.toString().replaceAll("(?i)\\s+(AND|OR)\\s*$", "").trim();
    }

    private boolean matchesObservationSearch(String search, Observation item) {
        String normalized = search == null ? "" : search.trim();
        if (normalized.isEmpty()) return true;
        Matcher matcher = FILTER_TOKEN.matcher(normalized);
        StringBuffer remaining = new StringBuffer();
        while (matcher.find()) {
            boolean matched = matchesObservationField(matcher.group(2), matcher.group(3), item);
            if ((matcher.group(1).isEmpty() && !matched) || (!matcher.group(1).isEmpty() && matched)) return false;
            matcher.appendReplacement(remaining, " ");
        }
        matcher.appendTail(remaining);
        String freeText = remaining.toString().replaceAll("(?i)\\b(AND|OR|NOT)\\b", " ").trim();
        if (freeText.isEmpty()) return true;
        TraceDetail trace = traceFor(item.traceId());
        return matches(freeText, item.id(), item.traceId(), item.name(), item.type().name(), item.level().name(),
                item.model(), item.statusMessage(), trace.name(), trace.userId(), trace.sessionId(),
                trace.environment(), String.join(" ", trace.tags()), String.valueOf(item.input()),
                String.valueOf(item.output()), String.valueOf(item.metadata()));
    }

    private boolean matchesObservationField(String field, String rawValue, Observation item) {
        TraceDetail trace = traceFor(item.traceId());
        String key = field.toLowerCase(Locale.ROOT);
        String value = unquote(rawValue);
        if ("root".equals(key)) return Boolean.parseBoolean(value) == (item.parentObservationId() == null);
        if ("latency".equals(key)) return compareNumber(item.latencyMs() / 1000d, value);
        if ("ttft".equals(key)) return compareNumber(item.timeToFirstTokenMs() == null ? 0 : item.timeToFirstTokenMs() / 1000d, value);
        if ("tokens".equals(key)) return compareNumber(item.inputTokens() + item.outputTokens(), value);
        if ("inputtokens".equals(key)) return compareNumber(item.inputTokens(), value);
        if ("outputtokens".equals(key)) return compareNumber(item.outputTokens(), value);
        if ("cost".equals(key) || "inputcost".equals(key) || "outputcost".equals(key)) {
            return compareNumber(item.totalCost().doubleValue(), value);
        }
        String candidate;
        switch (key) {
            case "environment": case "env": candidate = trace.environment(); break;
            case "type": candidate = item.type().name(); break;
            case "level": candidate = item.level().name(); break;
            case "name": candidate = item.name(); break;
            case "tracename": candidate = trace.name(); break;
            case "model": candidate = item.model(); break;
            case "modelid": candidate = item.modelId(); break;
            case "prompt": case "promptname": candidate = item.promptName(); break;
            case "trace": case "traceid": candidate = item.traceId(); break;
            case "session": case "sessionid": candidate = trace.sessionId(); break;
            case "user": case "userid": candidate = trace.userId(); break;
            case "status": case "statusmessage": candidate = item.statusMessage(); break;
            case "version": candidate = trace.version(); break;
            case "release": candidate = trace.release(); break;
            case "tags": candidate = String.join(" ", trace.tags()); break;
            case "input": candidate = String.valueOf(item.input()); break;
            case "output": candidate = String.valueOf(item.output()); break;
            case "metadata": candidate = String.valueOf(item.metadata()); break;
            default: return true;
        }
        return matchesAlternatives(candidate, value);
    }

    private boolean matchesAlternatives(String candidate, String expression) {
        if (candidate == null) return false;
        String cleaned = expression;
        if (cleaned.startsWith("(") && cleaned.endsWith(")")) cleaned = cleaned.substring(1, cleaned.length() - 1);
        for (String option : cleaned.split("(?i)\\s+OR\\s+")) {
            String expected = unquote(option.trim());
            boolean exact = expected.startsWith("=");
            if (exact) expected = expected.substring(1);
            String comparable = expected.replace("*", "");
            if (exact ? candidate.equalsIgnoreCase(expected)
                    : candidate.toLowerCase(Locale.ROOT).contains(comparable.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String unquote(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            return cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    private boolean compareNumber(double actual, String expression) {
        Matcher matcher = Pattern.compile("^(>=|<=|>|<|=)?\\s*(-?[0-9]+(?:\\.[0-9]+)?)$").matcher(expression.trim());
        if (!matcher.matches()) return false;
        double expected = Double.parseDouble(matcher.group(2));
        String operator = matcher.group(1) == null ? "=" : matcher.group(1);
        if (">".equals(operator)) return actual > expected;
        if (">=".equals(operator)) return actual >= expected;
        if ("<".equals(operator)) return actual < expected;
        if ("<=".equals(operator)) return actual <= expected;
        return Double.compare(actual, expected) == 0;
    }

    private boolean equalsIgnoreCase(String expected, String actual) {
        return actual != null && expected.trim().equalsIgnoreCase(actual);
    }

    private boolean containsIgnoreCase(List<String> values, String expected) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(expected.trim()));
    }

    private TraceDetail traceFor(String traceId) {
        return traces.stream().filter(trace -> trace.id().equals(traceId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Trace not found: " + traceId));
    }

    private <T> PageResponse<T> page(List<T> items, int page, int size) {
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), page, size, items.size());
    }

    private MetricPoint metric(String timestamp, int traceCount, int errorCount,
            long totalTokens, String totalCost, long averageLatencyMs) {
        return new MetricPoint(Instant.parse(timestamp), traceCount, errorCount,
                totalTokens, new BigDecimal(totalCost), averageLatencyMs);
    }

    private List<TraceDetail> createTraces() {
        return com.icbc.aiops.langfuse.util.Java8Collections.listOf(
                new TraceDetail("trace-rag-001", "customer-support-rag",
                        Instant.parse("2026-08-28T08:41:23Z"), "user-1842", "session-alpha",
                        "production", TraceStatus.SUCCESS, 1840, 1684,
                        new BigDecimal("0.00842"), 4, com.icbc.aiops.langfuse.util.Java8Collections.listOf("rag", "support"),
                        com.icbc.aiops.langfuse.util.Java8Collections.mapOf("question", "How do I reset my corporate banking token?"),
                        com.icbc.aiops.langfuse.util.Java8Collections.mapOf("answer", "Open Security Center and choose Reset token."),
                        com.icbc.aiops.langfuse.util.Java8Collections.mapOf("region", "cn", "channel", "web", "retrievalTopK", 5),
                        "2026.08.28", "v4"),
                new TraceDetail("trace-agent-002", "payment-risk-agent",
                        Instant.parse("2026-08-28T08:38:10Z"), "user-9921", "session-beta",
                        "production", TraceStatus.ERROR, 3120, 2310,
                        new BigDecimal("0.01891"), 3, com.icbc.aiops.langfuse.util.Java8Collections.listOf("agent", "risk"),
                        com.icbc.aiops.langfuse.util.Java8Collections.mapOf("transactionId", "tx-883021", "amount", 95000),
                        com.icbc.aiops.langfuse.util.Java8Collections.mapOf("status", "manual_review", "reason", "tool timeout"),
                        com.icbc.aiops.langfuse.util.Java8Collections.mapOf("region", "shanghai", "retryCount", 1),
                        "2026.08.28", "v12"),
                new TraceDetail("trace-chat-003", "wealth-assistant-chat",
                        Instant.parse("2026-08-28T08:34:52Z"), "user-1842", "session-alpha",
                        "staging", TraceStatus.SUCCESS, 965, 842,
                        new BigDecimal("0.00418"), 3, com.icbc.aiops.langfuse.util.Java8Collections.listOf("chat", "wealth"),
                        com.icbc.aiops.langfuse.util.Java8Collections.mapOf("message", "Summarize my portfolio risk."),
                        com.icbc.aiops.langfuse.util.Java8Collections.mapOf("summary", "Your portfolio has moderate concentration risk."),
                        com.icbc.aiops.langfuse.util.Java8Collections.mapOf("region", "cn", "language", "zh-CN"),
                        "2026.08.27", "v7"),
                new TraceDetail("trace-types-004", "mock-observation-types",
                        Instant.parse("2026-09-08T07:20:00Z"), "user-mock", "session-gamma",
                        "development", TraceStatus.SUCCESS, 2480, 740,
                        new BigDecimal("0.00360"), 5, com.icbc.aiops.langfuse.util.Java8Collections.listOf("mock", "types"),
                        com.icbc.aiops.langfuse.util.Java8Collections.mapOf("message", "Exercise all observation type renderers."),
                        com.icbc.aiops.langfuse.util.Java8Collections.mapOf("status", "verified", "types", 5),
                        com.icbc.aiops.langfuse.util.Java8Collections.mapOf("region", "local", "dataset", "frontend-integration"),
                        "2026.09.08", "mock-v1"));
    }

    private List<Observation> createObservations() {
        return com.icbc.aiops.langfuse.util.Java8Collections.listOf(
                observation("obs-rag-root", "trace-rag-001", null, "support-workflow", ObservationType.SPAN,
                        "2026-08-28T08:41:23Z", "2026-08-28T08:41:24.840Z", ObservationLevel.DEFAULT,
                        null, 1840, 0, 0, "0", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("question", "reset token"), com.icbc.aiops.langfuse.util.Java8Collections.mapOf("status", "ok")),
                observation("obs-rag-retrieve", "trace-rag-001", "obs-rag-root", "knowledge-retrieval", ObservationType.RETRIEVER,
                        "2026-08-28T08:41:23.090Z", "2026-08-28T08:41:23.410Z", ObservationLevel.DEFAULT,
                        null, 320, 0, 0, "0", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("topK", 5), com.icbc.aiops.langfuse.util.Java8Collections.listOf("Security Center Guide", "Token FAQ")),
                observation("obs-rag-gen", "trace-rag-001", "obs-rag-root", "generate-answer", ObservationType.GENERATION,
                        "2026-08-28T08:41:23.450Z", "2026-08-28T08:41:24.760Z", ObservationLevel.DEFAULT,
                        "gpt-5-mini", 1310, 1240, 444, "0.00842", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("messages", 4), com.icbc.aiops.langfuse.util.Java8Collections.mapOf("finishReason", "stop")),
                observation("obs-rag-event", "trace-rag-001", "obs-rag-root", "answer-delivered", ObservationType.EVENT,
                        "2026-08-28T08:41:24.810Z", "2026-08-28T08:41:24.810Z", ObservationLevel.DEFAULT,
                        null, 0, 0, 0, "0", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("channel", "web"), com.icbc.aiops.langfuse.util.Java8Collections.mapOf()),
                observation("obs-agent-root", "trace-agent-002", null, "risk-analysis", ObservationType.SPAN,
                        "2026-08-28T08:38:10Z", "2026-08-28T08:38:13.120Z", ObservationLevel.ERROR,
                        null, 3120, 0, 0, "0", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("amount", 95000), com.icbc.aiops.langfuse.util.Java8Collections.mapOf("status", "manual_review")),
                observation("obs-agent-gen", "trace-agent-002", "obs-agent-root", "decide-risk-actions", ObservationType.GENERATION,
                        "2026-08-28T08:38:10.180Z", "2026-08-28T08:38:11.490Z", ObservationLevel.DEFAULT,
                        "gpt-5", 1310, 1720, 590, "0.01891", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("transaction", "tx-883021"), com.icbc.aiops.langfuse.util.Java8Collections.mapOf("action", "verify_account")),
                observation("obs-agent-tool", "trace-agent-002", "obs-agent-root", "account-risk-api", ObservationType.TOOL,
                        "2026-08-28T08:38:11.540Z", "2026-08-28T08:38:13.100Z", ObservationLevel.ERROR,
                        null, 1560, 0, 0, "0", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("account", "masked"), com.icbc.aiops.langfuse.util.Java8Collections.mapOf("error", "upstream timeout")),
                observation("obs-chat-root", "trace-chat-003", null, "portfolio-chat", ObservationType.SPAN,
                        "2026-08-28T08:34:52Z", "2026-08-28T08:34:52.965Z", ObservationLevel.DEFAULT,
                        null, 965, 0, 0, "0", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("intent", "risk_summary"), com.icbc.aiops.langfuse.util.Java8Collections.mapOf("status", "ok")),
                observation("obs-chat-tool", "trace-chat-003", "obs-chat-root", "portfolio-service", ObservationType.TOOL,
                        "2026-08-28T08:34:52.060Z", "2026-08-28T08:34:52.250Z", ObservationLevel.DEFAULT,
                        null, 190, 0, 0, "0", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("portfolioId", "default"), com.icbc.aiops.langfuse.util.Java8Collections.mapOf("assets", 12)),
                observation("obs-chat-gen", "trace-chat-003", "obs-chat-root", "summarize-risk", ObservationType.GENERATION,
                        "2026-08-28T08:34:52.280Z", "2026-08-28T08:34:52.930Z", ObservationLevel.DEFAULT,
                        "gpt-5-mini", 650, 590, 252, "0.00418", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("language", "zh-CN"), com.icbc.aiops.langfuse.util.Java8Collections.mapOf("finishReason", "stop")),
                observation("obs-types-agent", "trace-types-004", null, "mock-agent", ObservationType.AGENT,
                        "2026-09-08T07:20:00Z", "2026-09-08T07:20:02.480Z", ObservationLevel.DEFAULT,
                        null, 2480, 0, 0, "0", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("goal", "verify observation UI"), com.icbc.aiops.langfuse.util.Java8Collections.mapOf("decision", "run child steps")),
                observation("obs-types-chain", "trace-types-004", "obs-types-agent", "mock-chain", ObservationType.CHAIN,
                        "2026-09-08T07:20:00.100Z", "2026-09-08T07:20:01.100Z", ObservationLevel.DEFAULT,
                        null, 1000, 0, 0, "0", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("steps", 3), com.icbc.aiops.langfuse.util.Java8Collections.mapOf("completed", true)),
                observation("obs-types-embedding", "trace-types-004", "obs-types-chain", "mock-embedding", ObservationType.EMBEDDING,
                        "2026-09-08T07:20:00.180Z", "2026-09-08T07:20:00.420Z", ObservationLevel.DEFAULT,
                        "text-embedding-3-small", 240, 280, 0, "0.00003", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("text", "corporate policy"), com.icbc.aiops.langfuse.util.Java8Collections.mapOf("dimensions", 1536)),
                observation("obs-types-evaluator", "trace-types-004", "obs-types-agent", "mock-evaluator", ObservationType.EVALUATOR,
                        "2026-09-08T07:20:01.180Z", "2026-09-08T07:20:01.760Z", ObservationLevel.DEFAULT,
                        "quality-rule-v1", 580, 460, 0, "0.00357", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("criterion", "groundedness"), com.icbc.aiops.langfuse.util.Java8Collections.mapOf("score", 0.94)),
                observation("obs-types-guardrail", "trace-types-004", "obs-types-agent", "mock-guardrail", ObservationType.GUARDRAIL,
                        "2026-09-08T07:20:01.800Z", "2026-09-08T07:20:02.080Z", ObservationLevel.WARNING,
                        null, 280, 0, 0, "0", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("policy", "pii"), com.icbc.aiops.langfuse.util.Java8Collections.mapOf("blocked", false, "redactions", 1)));
    }

    private Observation observation(String id, String traceId, String parentId, String name,
            ObservationType type, String start, String end, ObservationLevel level, String model,
            long latency, long inputTokens, long outputTokens, String cost, Object input, Object output) {
        String statusMessage = null;
        if (level == ObservationLevel.ERROR) {
            statusMessage = "Mock observation error";
        } else if (level == ObservationLevel.WARNING) {
            statusMessage = "Mock policy warning";
        }
        return new Observation(id, traceId, name, parentId, name, type, Instant.parse(start),
                end == null ? null : Instant.parse(end), level, model, latency, inputTokens,
                outputTokens, new BigDecimal(cost), input, output, null, null,
                statusMessage,
                model == null ? null : model + "-mock-id", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("temperature", 0.2),
                com.icbc.aiops.langfuse.util.Java8Collections.mapOf("input", inputTokens, "output", outputTokens, "total", inputTokens + outputTokens),
                com.icbc.aiops.langfuse.util.Java8Collections.mapOf("total", new BigDecimal(cost)), null, null,
                com.icbc.aiops.langfuse.util.Java8Collections.mapOf("mock", true, "service", "aiops-observability", "observationType", type.name()));
    }

    private List<SessionSummary> createSessions() {
        return com.icbc.aiops.langfuse.util.Java8Collections.listOf(
                new SessionSummary("session-alpha", Instant.parse("2026-08-28T08:34:52Z"),
                        "user-1842", 2, 7, 2526, new BigDecimal("0.01260"), 390_000),
                new SessionSummary("session-beta", Instant.parse("2026-08-28T08:38:10Z"),
                        "user-9921", 1, 3, 2310, new BigDecimal("0.01891"), 3_120),
                new SessionSummary("session-gamma", Instant.parse("2026-09-08T07:20:00Z"),
                        "user-mock", 1, 5, 740, new BigDecimal("0.00360"), 2_480));
    }
}
