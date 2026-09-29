package com.icbc.aiops.langfuse.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectionUtilitiesTest {

    @Test
    void immutableJsonSnapshotRecursivelyCopiesMapsAndLists() {
        List<Object> nestedList = new ArrayList<Object>(Arrays.<Object>asList("first"));
        Map<String, Object> nestedMap = new LinkedHashMap<String, Object>();
        nestedMap.put("items", nestedList);
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("nested", nestedMap);
        source.put("number", 3);

        Map<String, Object> snapshot = ImmutableJsonSnapshot.map(source);
        nestedList.add("second");
        nestedMap.put("changed", true);
        source.put("new", "value");

        Map<?, ?> storedNested = (Map<?, ?>) snapshot.get("nested");
        assertEquals(Arrays.asList("first"), storedNested.get("items"));
        assertEquals(2, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("x", "y"));
        assertThrows(UnsupportedOperationException.class,
                () -> ((Map<String, Object>) snapshot.get("nested")).put("x", "y"));
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<Object>) storedNested.get("items")).add("x"));
        assertNull(ImmutableJsonSnapshot.value(null));
        assertSame("text", ImmutableJsonSnapshot.value("text"));
        assertEquals(Arrays.asList("a", "b"), ImmutableJsonSnapshot.list(Arrays.asList("a", "b")));
    }

    @Test
    void java8CollectionFactoriesPreserveOrderAndAreImmutable() {
        List<String> list = Java8Collections.listOf("a", "b");
        Set<String> set = Java8Collections.setOf("b", "a", "b");
        Map<String, Integer> map = Java8Collections.mapOf("a", 1, "b", 2);
        assertEquals(Arrays.asList("a", "b"), list);
        assertEquals(Arrays.asList("b", "a"), new ArrayList<String>(set));
        assertEquals(Arrays.asList("a", "b"), new ArrayList<String>(map.keySet()));
        assertThrows(UnsupportedOperationException.class, () -> list.add("c"));
        assertThrows(UnsupportedOperationException.class, () -> set.add("c"));
        assertThrows(UnsupportedOperationException.class, () -> map.put("c", 3));
        assertThrows(IllegalArgumentException.class, () -> Java8Collections.mapOf("a"));

        List<String> copiedList = Java8Collections.listCopyOf(list);
        Map<String, Integer> copiedMap = Java8Collections.mapCopyOf(map);
        assertEquals(list, copiedList);
        assertEquals(map, copiedMap);
        assertThrows(UnsupportedOperationException.class, () -> copiedList.clear());
        assertThrows(UnsupportedOperationException.class, () -> copiedMap.clear());
    }
}
