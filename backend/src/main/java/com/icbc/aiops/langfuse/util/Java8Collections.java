package com.icbc.aiops.langfuse.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 对 Java 8 之后新增集合工厂方法的轻量兼容封装。 */
public final class Java8Collections {

    private Java8Collections() {
    }

    @SafeVarargs
    public static <T> List<T> listOf(T... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<T>(Arrays.asList(values)));
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> mapOf(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("mapOf requires key/value pairs");
        }
        Map<K, V> result = new LinkedHashMap<K, V>();
        for (int index = 0; index < keyValues.length; index += 2) {
            result.put((K) keyValues[index], (V) keyValues[index + 1]);
        }
        return Collections.unmodifiableMap(result);
    }

    public static <T> List<T> listCopyOf(java.util.Collection<? extends T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    public static <K, V> Map<K, V> mapCopyOf(Map<? extends K, ? extends V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<K, V>(values));
    }
}
