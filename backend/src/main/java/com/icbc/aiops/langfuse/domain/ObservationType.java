package com.icbc.aiops.langfuse.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Observation 类型。
 *
 * <p>类型值来自两种语义完全不同的数据源，必须分别处理：
 *
 * <ul>
 *   <li><b>请求参数</b>（{@code ?type=span}）：传入未知类型属于调用方错误，因此
 *       {@link #tryParse(String)} 返回 {@code null} 后转换为 HTTP 400。</li>
 *   <li><b>数据库记录</b>：当前版本无法识别的存储值不能导致整页失败，也不能伪装成其他类型。
 *       {@link #fromStorage(String)} 会将其映射为 {@link #UNKNOWN}，并由调用方记录警告。</li>
 * </ul>
 *
 * <p>全程使用不区分大小写的比较（先 trim，再按 {@link Locale#ROOT} 转为大写）：
 * AgentObs 在不同部署中可能存储 {@code span} 或 {@code SPAN}，两者含义相同。
 *
 * <p>{@link #UNKNOWN} 仅用于<em>展示</em>，既不作为合法筛选值，也不会写入任何存储；
 * {@link #tryParse(String)} 有意不返回该值。
 */
public enum ObservationType {
    SPAN,
    GENERATION,
    EVENT,
    AGENT,
    TOOL,
    CHAIN,
    RETRIEVER,
    EVALUATOR,
    EMBEDDING,
    GUARDRAIL,

    /**
     * 表示已存储但不在已知集合中的值。{@link #tryParse(String)} 永远不会返回它，
     * 因而不能用于筛选；该值用于把未知记录展示为 unknown，避免页面崩溃或伪装成 {@link #SPAN}。
     */
    UNKNOWN;

    /** 调用方实际允许使用的筛选类型。 */
    private static final List<ObservationType> FILTERABLE = Arrays.asList(
            SPAN, GENERATION, EVENT, AGENT, TOOL, CHAIN, RETRIEVER, EVALUATOR, EMBEDDING, GUARDRAIL);

    /**
     * 不区分大小写地解析类型值。
     *
     * @return 匹配的类型；当 {@code raw} 为 null、空白或未知类型时返回 {@code null}。
     *         永远不会返回 {@link #UNKNOWN}。
     */
    public static ObservationType tryParse(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        for (ObservationType type : FILTERABLE) {
            if (type.name().equals(normalized)) return type;
        }
        return null;
    }

    /** @return {@code raw} 表示当前版本可识别的类型时返回 true，不区分大小写。 */
    public static boolean isKnown(String raw) {
        return tryParse(raw) != null;
    }

    /**
     * 映射从存储中读取的类型值。
     *
     * @return 匹配的类型；null、空白或未知值返回 {@link #UNKNOWN}。
     *         该方法不抛出异常，避免单条异常记录导致整页失败。
     */
    public static ObservationType fromStorage(String raw) {
        ObservationType parsed = tryParse(raw);
        return parsed == null ? UNKNOWN : parsed;
    }

    /** @return 可筛选类型，即除 {@link #UNKNOWN} 之外的全部类型。 */
    public static List<ObservationType> filterable() {
        return FILTERABLE;
    }
}
