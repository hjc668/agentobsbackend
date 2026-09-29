package com.icbc.aiops.langfuse.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Observation level。与 {@link ObservationType} 采用相同的双路径处理：AgentObs 在不同部署中
 * 可能存储 {@code default} 或 {@code DEFAULT}，过去小写值一旦出现在结果集中就会导致整页失败。
 *
 * <p>请求参数与存储值的区别参见 {@link ObservationType}。
 */
public enum ObservationLevel {
    DEBUG,
    DEFAULT,
    WARNING,
    ERROR,

    /** 仅用于展示未知存储值的占位类型，不能作为筛选值。 */
    UNKNOWN;

    private static final List<ObservationLevel> FILTERABLE = Arrays.asList(DEBUG, DEFAULT, WARNING, ERROR);

    /**
     * 不区分大小写地解析 level。
     *
     * @return 匹配的 level；未知时返回 {@code null}，永远不会返回 {@link #UNKNOWN}。
     */
    public static ObservationLevel tryParse(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        for (ObservationLevel level : FILTERABLE) {
            if (level.name().equals(normalized)) return level;
        }
        return null;
    }

    /** @return {@code raw} 表示当前版本可识别的 level 时返回 true，不区分大小写。 */
    public static boolean isKnown(String raw) {
        return tryParse(raw) != null;
    }

    /**
     * 映射从存储中读取的 level。
     *
     * @return 匹配的 level；null、空白或未知值返回 {@link #UNKNOWN}。
     */
    public static ObservationLevel fromStorage(String raw) {
        ObservationLevel parsed = tryParse(raw);
        return parsed == null ? UNKNOWN : parsed;
    }

    /** @return 可筛选 level，即除 {@link #UNKNOWN} 之外的全部值。 */
    public static List<ObservationLevel> filterable() {
        return FILTERABLE;
    }
}
