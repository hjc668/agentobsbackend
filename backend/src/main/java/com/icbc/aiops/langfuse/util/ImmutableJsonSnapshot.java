package com.icbc.aiops.langfuse.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Creates recursively immutable snapshots of JSON-compatible values. */
public final class ImmutableJsonSnapshot {

    private ImmutableJsonSnapshot() {
    }

    public static Object value(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                copy.put((String) entry.getKey(), value(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?>) {
            List<Object> copy = new ArrayList<Object>();
            for (Object item : (List<?>) value) {
                copy.add(value(item));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> value) {
        return (Map<String, Object>) value(value);
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> list(List<T> value) {
        return (List<T>) value(value);
    }
}
