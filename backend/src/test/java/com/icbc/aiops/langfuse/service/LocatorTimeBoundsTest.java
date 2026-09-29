package com.icbc.aiops.langfuse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LocatorTimeBoundsTest {
    @Test
    void retainsSixMicrosecondsAndInclusiveEqualBoundsInUtc() {
        LocalDateTime value = LocalDateTime.of(2026, 9, 7, 8, 20, 19, 123456000);
        assertEquals("2026-09-07 08:20:19.123456",
                LocatorTimeBounds.format(LocatorTimeBounds.inColumnZone(value, "UTC", "UTC")));
    }

    @Test
    void convertsBetweenUtcAndEastEightAsAnInstant() {
        LocalDateTime utc = LocalDateTime.of(2026, 9, 7, 8, 20, 19, 123456000);
        LocalDateTime eastEight = LocatorTimeBounds.inColumnZone(utc, "UTC", "Asia/Shanghai");
        assertEquals("2026-09-07 16:20:19.123456", LocatorTimeBounds.format(eastEight));
        assertEquals(utc, LocatorTimeBounds.inColumnZone(eastEight, "Asia/Shanghai", "UTC"));
    }

    @Test
    void rejectsUnapprovedSqlTimezone() {
        assertThrows(IllegalArgumentException.class,
                () -> LocatorTimeBounds.checkedZone("UTC'); DROP TABLE observations; --"));
    }
}
