package com.icbc.aiops.langfuse.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 将 locator 的本地时间值转换为 Observation 时间列使用的时区。 */
final class LocatorTimeBounds {
    private static final DateTimeFormatter MICROS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private LocatorTimeBounds() { }

    static LocalDateTime inColumnZone(LocalDateTime value, String locatorZone, String columnZone) {
        if (value == null) return null;
        return value.atZone(zone(locatorZone)).withZoneSameInstant(zone(columnZone)).toLocalDateTime();
    }

    static String format(LocalDateTime value) {
        return value == null ? null : value.format(MICROS);
    }

    static String checkedZone(String value) {
        // 仅允许这些时区作为 SQL 常量，禁止拼接任何请求数据。
        if ("UTC".equals(value) || "Asia/Shanghai".equals(value)) return value;
        throw new IllegalArgumentException("Unsupported locator column timezone: " + value);
    }

    private static ZoneId zone(String value) {
        return ZoneId.of(checkedZone(value));
    }
}
