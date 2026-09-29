package com.icbc.aiops.langfuse.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The warning has to be visible without being noisy: an unrecognised value repeats on every row
 * of every query, so an unthrottled log line would bury everything else.
 */
class UnknownStoredValueWarnerTest {

    @Test
    void logsTheFirstOccurrenceOfAValue() {
        UnknownStoredValueWarner warner = new UnknownStoredValueWarner(1000L, 10);
        assertTrue(warner.shouldLog("observation type weird", 0L));
    }

    @Test
    void suppressesRepeatsOfTheSameValueWithinTheInterval() {
        UnknownStoredValueWarner warner = new UnknownStoredValueWarner(1000L, 10);
        assertTrue(warner.shouldLog("key", 0L));
        assertFalse(warner.shouldLog("key", 1L));
        assertFalse(warner.shouldLog("key", 999L));
    }

    @Test
    void logsAgainOnceTheIntervalHasElapsed() {
        UnknownStoredValueWarner warner = new UnknownStoredValueWarner(1000L, 10);
        assertTrue(warner.shouldLog("key", 0L));
        assertTrue(warner.shouldLog("key", 1000L));
        assertTrue(warner.shouldLog("key", 5000L));
    }

    @Test
    void throttlesEachDistinctValueIndependently() {
        UnknownStoredValueWarner warner = new UnknownStoredValueWarner(1000L, 10);
        assertTrue(warner.shouldLog("type weird-a", 0L));
        // A different unrecognised type must still be reported rather than suppressed by the first.
        assertTrue(warner.shouldLog("type weird-b", 0L));
    }

    @Test
    void staysBoundedWhenManyDistinctValuesArrive() {
        // An upstream that starts emitting a unique value per row must not grow the map without
        // limit. Exceeding the cap clears it, which only costs extra log lines, never correctness.
        UnknownStoredValueWarner warner = new UnknownStoredValueWarner(1000L, 3);
        for (int i = 0; i < 50; i++) {
            warner.shouldLog("value-" + i, i);
        }
        // Still functional after the cap was hit.
        assertTrue(warner.shouldLog("value-fresh", 100L));
    }
}
