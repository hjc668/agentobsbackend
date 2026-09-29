package com.icbc.aiops.langfuse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The two parsing paths carry opposite failure policies, and mixing them up is a security or
 * availability bug in the other direction, so both are pinned here.
 */
class ObservationTypeParsingTest {

    @Test
    void requestValuesAreCaseInsensitiveAndTrimmed() {
        // The reported failure: AgentObs stores 'span' and the request said 'span', but Spring's
        // enum converter is case-sensitive and rejected it with 400.
        assertEquals(ObservationType.SPAN, ObservationType.tryParse("span"));
        assertEquals(ObservationType.SPAN, ObservationType.tryParse("SPAN"));
        assertEquals(ObservationType.SPAN, ObservationType.tryParse("SpAn"));
        assertEquals(ObservationType.SPAN, ObservationType.tryParse("  span  "));
        assertEquals(ObservationType.GENERATION, ObservationType.tryParse("generation"));
        assertEquals(ObservationLevel.DEFAULT, ObservationLevel.tryParse("default"));
        assertEquals(ObservationLevel.ERROR, ObservationLevel.tryParse(" error "));
    }

    @Test
    void requestValuesReturnNullForAnythingUnknownSoTheCallerCanAnswer400() {
        assertNull(ObservationType.tryParse("not-a-type"));
        assertNull(ObservationType.tryParse(""));
        assertNull(ObservationType.tryParse("   "));
        assertNull(ObservationType.tryParse(null));
        assertNull(ObservationLevel.tryParse("verbose"));
        assertNull(ObservationLevel.tryParse(null));
    }

    @Test
    void unknownIsNeverAcceptedAsARequestValue() {
        // UNKNOWN is display-only. If tryParse ever returned it, a caller could filter on it and
        // silently get rows whose real type is something else entirely.
        assertNull(ObservationType.tryParse("UNKNOWN"));
        assertNull(ObservationType.tryParse("unknown"));
        assertNull(ObservationLevel.tryParse("UNKNOWN"));
        assertFalse(ObservationType.filterable().contains(ObservationType.UNKNOWN));
        assertFalse(ObservationLevel.filterable().contains(ObservationLevel.UNKNOWN));
    }

    @Test
    void storedValuesDegradeToUnknownInsteadOfThrowing() {
        // A single unrecognised row must not fail the whole page with HTTP 500, which is exactly
        // what ObservationType.valueOf("span") used to do.
        assertEquals(ObservationType.SPAN, ObservationType.fromStorage("span"));
        assertEquals(ObservationType.SPAN, ObservationType.fromStorage("SPAN"));
        assertEquals(ObservationType.UNKNOWN, ObservationType.fromStorage("brand-new-type"));
        assertEquals(ObservationType.UNKNOWN, ObservationType.fromStorage(""));
        assertEquals(ObservationType.UNKNOWN, ObservationType.fromStorage(null));
        assertEquals(ObservationLevel.UNKNOWN, ObservationLevel.fromStorage("verbose"));
        assertEquals(ObservationLevel.UNKNOWN, ObservationLevel.fromStorage(null));
    }

    @Test
    void unknownStoredValuesAreNeverCoercedOntoAKnownType() {
        // The dangerous alternative would be to guess. Assert it does not happen: an unrecognised
        // value must not come back as SPAN, or its counts would be silently folded into SPAN's.
        for (String raw : new String[] {"spans", "span ", "spanish", "SPANN", "0"}) {
            if ("span ".equals(raw)) continue; // trimmed to a real type on purpose
            assertFalse(ObservationType.SPAN == ObservationType.fromStorage(raw),
                    "'" + raw + "' must not be mapped onto SPAN");
        }
    }

    @Test
    void knownIsConsistentWithTryParse() {
        assertTrue(ObservationType.isKnown("span"));
        assertTrue(ObservationType.isKnown("GUARDRAIL"));
        assertFalse(ObservationType.isKnown("UNKNOWN"));
        assertFalse(ObservationType.isKnown("nope"));
        assertFalse(ObservationType.isKnown(null));
    }
}
