package com.icbc.aiops.langfuse.service;

import com.icbc.aiops.langfuse.util.Java8Collections;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracing DSL 可接受筛选字段的唯一事实来源。
 *
 * <p>过去两种不同的失败场景无法区分：字段名拼写错误，以及旧 schema 存在但 AgentObs
 * 无法提供的字段。两者都会被静默丢弃，导致请求虽然成功但筛选完全未生效。现在明确区分为：
 *
 * <ul>
 *   <li><b>unknown</b>：API 从未提供过的字段（拼写错误或用户自行构造），返回 HTTP 400，
 *       使调用方立即发现问题。</li>
 *   <li><b>no AgentObs source</b>：真实存在过的旧字段，但新 schema 没有对应列。
 *       记录 WARN 后忽略，避免 UI 尚未升级时，多条件查询中的一个旧 token 破坏整个请求。</li>
 * </ul>
 *
 * <p>{@link com.icbc.aiops.langfuse.mapper.TracingSqlProvider} 同样从这里引用 token 正则，
 * 确保校验器与 SQL 构建器始终以相同方式拆分查询。
 */
public final class TracingFilterFields {

    private static final Logger LOGGER = LoggerFactory.getLogger(TracingFilterFields.class);

    /**
     * DSL token 格式：field[:|=|&gt;|&gt;=|&lt;|&lt;=]value；可选的前导 '-' 表示取反，
     * metadata/scores 类字段还可带 {@code .key} 后缀。
     */
    public static final Pattern SEARCH_TOKEN = Pattern.compile(
            "(?<!\\S)(-?)([A-Za-z][A-Za-z0-9_-]*(?:\\.(?:\\\"[^\\\"]+\\\"|[A-Za-z0-9_.-]+))?):"
                    + "(\\\"[^\\\"]*\\\"|\\([^)]*\\)|\\S+)");

    /**
     * 由真实 AgentObs 列（或有界 trace-id 子查询）支持的字段，按调用方使用的拼写保存。
     * 错误信息会展示这些名称，因为标准化后的形式（如 "tracename"、"inputtokens"）
     * 并不是用户能够识别或输入的格式。
     */
    private static final List<String> SUPPORTED_NAMES = Java8Collections.listOf(
            "environment", "serviceName", "type", "level", "name", "traceName", "tags",
            "model", "promptName", "traceId", "sessionId", "userId", "status", "version",
            "input", "output", "root", "has", "startTime",
            "latency", "ttft", "tokens", "inputTokens", "outputTokens", "cost",
            "toolDefinitions", "metadata");

    private static final Set<String> SUPPORTED = buildSupportedSet();

    /** Trace 接口支持的字段词汇，保留调用方使用的拼写。 */
    private static final List<String> TRACE_SUPPORTED_NAMES = Java8Collections.listOf(
            "name", "traceName", "traceId", "userId", "sessionId", "tags",
            "environment", "serviceName", "version", "status", "level",
            "input", "output", "startTime", "latency", "has", "metadata");

    /** 对忽略字段的告警进行重复抑制，参见 {@link #logIgnored}。 */
    private static final long IGNORE_LOG_INTERVAL_MS = 300_000L;
    private static final int MAX_LOGGED_KEYS = 512;
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> IGNORED_LAST_LOGGED =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 旧 schema 中真实存在但 AgentObs 没有对应列的字段。记录告警后忽略而非拒绝，
     * 因为前端升级期间可能仍保留旧的筛选预设。
     *
     * <p>{@code scores} 暂时归入此集合：新 scores 表虽然存在，但当前可验证环境中均为空，
     * 因此暂时无法支持该筛选条件。
     */
    private static final Set<String> NO_SOURCE = Java8Collections.setOf(
            "toolcalls", "modelid", "release", "inputcost", "outputcost",
            "tps", "sdkname", "sdkversion", "scores");

    /** 当前校验的搜索表达式所属接口范围。 */
    public enum Scope {
        /** {@code /observations}：由 observations 表提供的字段。 */
        OBSERVATION,
        /**
         * {@code /traces}：由 traces 表提供的字段。
         *
         * <p>过去两个接口的行为不一致：搜索字符串在两处都按 DSL 校验，但只有 Observation
         * 构建器真正解析，因此 {@code name:x} 在一个接口能正确筛选，在另一个接口却按字面量匹配。
         * 所以每个 Scope 都声明自己的字段词汇，以及自身无法提供的字段集合。
         */
        TRACE
    }

    /** {@code hmp_agentobs_traces} 中真实列支持的 Trace 级字段。 */
    private static final Set<String> TRACE_SUPPORTED = Java8Collections.setOf(
            "name", "tracename", "trace", "traceid", "user", "userid", "session", "sessionid",
            "tags", "environment", "env", "servicename", "service", "version", "status",
            "statusmessage", "level", "input", "output", "metadata", "starttime", "latency", "has");

    /**
     * Trace 接口无法提供的字段。{@code tokens}/{@code cost}/{@code ttft} 位于 observations，
     * 而不是 trace 记录上；如果不执行窗口聚合，Trace 查询无法按这些字段筛选。
     * 明确忽略比静默返回空结果更符合实际能力。
     */
    private static final Set<String> TRACE_NO_SOURCE = Java8Collections.setOf(
            "tokens", "inputtokens", "outputtokens", "cost", "ttft", "tooldefinitions",
            "root", "isrootobservation", "toolcalls", "modelid", "release",
            "inputcost", "outputcost", "tps", "sdkname", "sdkversion", "scores");

    private TracingFilterFields() {
    }

    private static Set<String> buildSupportedSet() {
        Set<String> set = new LinkedHashSet<>();
        for (String name : SUPPORTED_NAMES) {
            set.add(normalize(name));
        }
        // 接受这些别名，但有意不在错误信息中对外展示。
        set.addAll(Java8Collections.listOf("env", "service", "prompt", "trace", "session",
                "user", "statusmessage", "isrootobservation", "has"));
        return set;
    }

    /** 字段名比较不区分大小写，同时忽略分隔符风格。 */
    public static String normalize(String field) {
        return field.toLowerCase().replace("_", "").replace("-", "");
    }

    public static boolean isSupported(String normalizedField) {
        return SUPPORTED.contains(normalizedField);
    }

    public static boolean hasNoSource(String normalizedField) {
        return NO_SOURCE.contains(normalizedField);
    }

    public static boolean isSupported(String normalizedField, Scope scope) {
        return scope == Scope.TRACE
                ? TRACE_SUPPORTED.contains(normalizedField)
                : SUPPORTED.contains(normalizedField);
    }

    public static boolean hasNoSource(String normalizedField, Scope scope) {
        return scope == Scope.TRACE
                ? TRACE_NO_SOURCE.contains(normalizedField)
                : NO_SOURCE.contains(normalizedField);
    }

    /** 接口的标准字段名，用于生成 HTTP 400 信息。 */
    private static List<String> supportedNames(Scope scope) {
        return scope == Scope.TRACE ? TRACE_SUPPORTED_NAMES : SUPPORTED_NAMES;
    }

    /** SQL 构建器必须丢弃的字段；集中维护，避免校验与 SQL 生成逻辑不一致。 */
    public static Set<String> noSourceFields() {
        return NO_SOURCE;
    }

    /** {@code field.key} token 的字段前缀，也是 SQL 构建器执行分支判断的值。 */
    public static String prefixOf(String rawField) {
        int dot = rawField.indexOf('.');
        return dot < 0 ? rawField : rawField.substring(0, dot);
    }

    /**
     * 校验搜索表达式中的每个字段 token。
     *
     * @throws InvalidRequestException 包含 API 不支持的字段时抛出，REST 层会转换为
     *         HTTP 400 INVALID_REQUEST。
     */
    public static void validate(String search) {
        validate(search, Scope.OBSERVATION);
    }

    public static void validate(String search, Scope scope) {
        if (search == null || search.trim().isEmpty()) {
            return;
        }
        Matcher matcher = SEARCH_TOKEN.matcher(search);
        List<String> unknown = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        while (matcher.find()) {
            String rawField = matcher.group(2);
            String field = normalize(prefixOf(rawField));
            if (hasNoSource(field, scope)) {
                ignored.add(rawField);
            } else if (!isSupported(field, scope)) {
                unknown.add(rawField);
            } else if ("metadata".equals(field) && !rawField.contains(".")) {
                // 已知字段但用法不完整：metadata 必须带 "<key>" 后缀。它并非未知字段，
                // 且 UI 字段建议会先插入裸 "metadata:"，直接拒绝会导致选择建议后请求失败。
                // 因此这里忽略；支持字段列表展示为 "metadata.<key>"，不会宣称裸写法可用。
                ignored.add(rawField + " (metadata needs a <key> suffix)");
            }
        }
        if (!ignored.isEmpty()) {
            logIgnored(scope, ignored);
        }
        if (!unknown.isEmpty()) {
            throw new InvalidRequestException("Unknown filter field(s): " + String.join(", ", unknown)
                    + ". Supported fields for this endpoint are: " + String.join(", ", displayNames(scope)));
        }
    }

    /**
     * 输出包含调用方上下文的“字段已忽略”告警，并合并重复日志。
     *
     * <p>高频页面中的旧筛选预设会在每次请求时触发告警，既会淹没日志，也会掩盖真正需要关注的首次记录。
     * 每种场景在 {@link #IGNORE_LOG_INTERVAL_MS} 内记录一次即可满足诊断需求。
     */
    private static void logIgnored(Scope scope, List<String> ignored) {
        String key = scope + "|" + new TreeSet<>(ignored);
        long now = System.currentTimeMillis();
        Long last = IGNORED_LAST_LOGGED.get(key);
        if (last != null && now - last < IGNORE_LOG_INTERVAL_MS) {
            return;
        }
        // 设置容量上限，避免调用方产生无限种查询时该集合持续增长。
        if (IGNORED_LAST_LOGGED.size() > MAX_LOGGED_KEYS) {
            IGNORED_LAST_LOGGED.clear();
        }
        IGNORED_LAST_LOGGED.put(key, now);
        CallerContext caller = caller();
        LOGGER.warn("Ignoring filter field(s) with no source column for {} (suppressing repeats for {}s): "
                        + "fields={} user={} endpoint={} requestId={}",
                scope, IGNORE_LOG_INTERVAL_MS / 1000, ignored,
                caller.user, caller.endpoint, caller.requestId);
    }

    /** 尽力获取请求上下文；不在 HTTP 请求中时使用占位值。 */
    private static CallerContext caller() {
        String user = "";
        String endpoint = "";
        String requestId = "";
        try {
            org.springframework.security.core.Authentication authentication =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                user = authentication.getName();
            }
        } catch (RuntimeException ignored) {
            // 无可用 Security Context，不应因此中断查询。
        }
        try {
            org.springframework.web.context.request.RequestAttributes attributes =
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes) {
                javax.servlet.http.HttpServletRequest request =
                        ((org.springframework.web.context.request.ServletRequestAttributes) attributes).getRequest();
                endpoint = request.getMethod() + " " + request.getRequestURI();
                requestId = request.getHeader("X-Request-ID");
                if (requestId == null || requestId.trim().isEmpty()) {
                    requestId = request.getHeader("X-Request-Id");
                }
            }
        } catch (RuntimeException ignored) {
            // 当前不在 servlet 请求中，endpoint 保持为空。
        }
        return new CallerContext(emptyMarker(user), emptyMarker(endpoint), emptyMarker(requestId));
    }

    private static String emptyMarker(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private static final class CallerContext {
        private final String user;
        private final String endpoint;
        private final String requestId;

        private CallerContext(String user, String endpoint, String requestId) {
            this.user = user;
            this.endpoint = endpoint;
            this.requestId = requestId;
        }
    }

    /** 调用方使用的字段名；{@code metadata} 会展示其必需的 key 格式。 */
    private static List<String> displayNames(Scope scope) {
        List<String> names = new ArrayList<>(supportedNames(scope));
        for (int i = 0; i < names.size(); i++) {
            if ("metadata".equals(names.get(i))) {
                names.set(i, "metadata.<key>");
            }
        }
        return names;
    }
}
