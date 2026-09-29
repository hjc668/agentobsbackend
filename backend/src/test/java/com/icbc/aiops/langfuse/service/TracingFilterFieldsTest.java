package com.icbc.aiops.langfuse.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A field the API never offered and a field AgentObs cannot serve used to look identical -
 * both were dropped without a trace, so a request could report success while filtering nothing.
 * These tests pin the split: a typo fails loudly, a known-but-unsupported field is ignored.
 */
class TracingFilterFieldsTest {

    @Test
    void acceptsEverySupportedField() {
        String query = "environment:prod serviceName:svc-a type:SPAN level:ERROR name:auth "
                + "traceName:=configRequirements/read tags:critical model:gpt-4 prompt:p1 "
                + "traceId:abc session:s1 user:u1 status:ok version:1.0 input:x output:y "
                + "root:true has:endTime startTime:>=2026-09-01 latency:>2 ttft:>0.5 "
                + "tokens:>10 inputTokens:>1 outputTokens:>1 cost:<0.05 toolDefinitions:>0 "
                + "metadata.env:=local";
        assertDoesNotThrow(() -> TracingFilterFields.validate(query));
    }

    @Test
    void rejectsAnUnknownFieldInsteadOfSilentlyIgnoringIt() {
        InvalidRequestException failure = assertThrows(InvalidRequestException.class,
                () -> TracingFilterFields.validate("environmnet:prod"));
        assertTrue(failure.getMessage().contains("environmnet"),
                "the error must name the offending field: " + failure.getMessage());
    }

    @Test
    void rejectsAnUnknownFieldEvenWhenOtherTokensAreValid() {
        assertThrows(InvalidRequestException.class,
                () -> TracingFilterFields.validate("type:SPAN notarealfield:x level:ERROR"));
    }

    @Test
    void unknownFieldErrorListsTheSupportedFields() {
        InvalidRequestException failure = assertThrows(InvalidRequestException.class,
                () -> TracingFilterFields.validate("bogus:1"));
        assertTrue(failure.getMessage().contains("traceName"),
                "the error should tell the caller what it can use: " + failure.getMessage());
    }

    /**
     * These fields exist in the legacy API but have no AgentObs column. Rejecting them would
     * break a whole request because of one stale preset, so they are ignored - but the caller
     * can see it in the logs rather than getting silently different numbers.
     */
    @Test
    void ignoresKnownFieldsWithoutAnAgentObsSource() {
        assertDoesNotThrow(() -> TracingFilterFields.validate("toolCalls:=0"));
        assertDoesNotThrow(() -> TracingFilterFields.validate("modelId:gpt-4"));
        assertDoesNotThrow(() -> TracingFilterFields.validate("release:2026.1"));
        assertDoesNotThrow(() -> TracingFilterFields.validate("inputCost:<0.01"));
        assertDoesNotThrow(() -> TracingFilterFields.validate("outputCost:<0.01"));
        assertDoesNotThrow(() -> TracingFilterFields.validate("tps:>10"));
        assertDoesNotThrow(() -> TracingFilterFields.validate("sdkName:java"));
        assertDoesNotThrow(() -> TracingFilterFields.validate("sdkVersion:1"));
        assertDoesNotThrow(() -> TracingFilterFields.validate("scores.accuracy:>=0.8"));
    }

    @Test
    void ignoresNoSourceFieldsInsideAMultiTokenQuery() {
        assertDoesNotThrow(() -> TracingFilterFields.validate("type:GENERATION toolCalls:=0 release:2026.1"));
    }

    /**
     * A bare {@code metadata:} token is a known field used incompletely, not an unknown one.
     * It is ignored with a warning rather than rejected: the UI's own field suggestion inserts
     * exactly that bare form, so a 400 here would turn picking the suggestion into a failed
     * request. The keyed form is the supported one.
     */
    @Test
    void bareMetadataIsIgnoredWhileTheKeyedFormIsSupported() {
        assertDoesNotThrow(() -> TracingFilterFields.validate("metadata:value"));
        assertDoesNotThrow(() -> TracingFilterFields.validate("metadata.env:=local"));
        assertDoesNotThrow(() -> TracingFilterFields.validate("metadata.\"deployment region\":=eu"));
    }

    @Test
    void emptyAndBlankSearchesAreAccepted() {
        assertDoesNotThrow(() -> TracingFilterFields.validate(null));
        assertDoesNotThrow(() -> TracingFilterFields.validate(""));
        assertDoesNotThrow(() -> TracingFilterFields.validate("   "));
    }

    @Test
    void freeTextWithoutAColonIsNotAField() {
        assertDoesNotThrow(() -> TracingFilterFields.validate("refund failed"));
        assertDoesNotThrow(() -> TracingFilterFields.validate("type:SPAN refund failed"));
    }

    @Test
    void fieldNamesAreCaseInsensitiveAndIgnoreSeparators() {
        assertDoesNotThrow(() -> TracingFilterFields.validate("TraceName:x"));
        assertDoesNotThrow(() -> TracingFilterFields.validate("trace_name:x"));
        assertDoesNotThrow(() -> TracingFilterFields.validate("trace-name:x"));
        assertDoesNotThrow(() -> TracingFilterFields.validate("InputTokens:>1"));
    }

    @Test
    void normalisationMatchesTheSqlBuilder() {
        assertTrue(TracingFilterFields.isSupported(TracingFilterFields.normalize("trace_name")));
        assertTrue(TracingFilterFields.isSupported(TracingFilterFields.normalize("Trace-Name")));
        assertFalse(TracingFilterFields.isSupported(TracingFilterFields.normalize("tool_calls")));
        assertTrue(TracingFilterFields.hasNoSource(TracingFilterFields.normalize("tool_calls")));
    }

    @Test
    void prefixOfStripsTheNestedKey() {
        assertEquals("metadata", TracingFilterFields.prefixOf("metadata.env"));
        assertEquals("scores", TracingFilterFields.prefixOf("scores.accuracy"));
        assertEquals("type", TracingFilterFields.prefixOf("type"));
    }

    // -- Endpoint scopes ---------------------------------------------------------------

    /**
     * The two endpoints serve different tables, so the same field name can be supported by one
     * and unservable by the other. Validating both against one vocabulary was how
     * {@code name:x} came to filter on /observations and match literal text on /traces.
     */
    @Test
    void theTwoEndpointsHaveDifferentVocabularies() {
        // Observation-only fields: the trace row has no token/cost columns.
        assertTrue(TracingFilterFields.isSupported("tokens", TracingFilterFields.Scope.OBSERVATION));
        assertTrue(TracingFilterFields.hasNoSource("tokens", TracingFilterFields.Scope.TRACE));
        assertTrue(TracingFilterFields.hasNoSource("cost", TracingFilterFields.Scope.TRACE));
        assertTrue(TracingFilterFields.hasNoSource("ttft", TracingFilterFields.Scope.TRACE));

        // Trace-only field: observations do not carry a trace name column.
        assertTrue(TracingFilterFields.isSupported("tracename", TracingFilterFields.Scope.TRACE));
        assertTrue(TracingFilterFields.isSupported("servicename", TracingFilterFields.Scope.TRACE));

        // Shared fields.
        for (String shared : new String[]{"name", "traceid", "tags", "environment", "latency"}) {
            assertTrue(TracingFilterFields.isSupported(shared, TracingFilterFields.Scope.OBSERVATION), shared);
            assertTrue(TracingFilterFields.isSupported(shared, TracingFilterFields.Scope.TRACE), shared);
        }
    }

    @Test
    void traceValidationAcceptsItsOwnVocabulary() {
        assertDoesNotThrow(() -> TracingFilterFields.validate(
                "name:turn_context.build traceName:x serviceName:svc status:ERROR latency:>2 metadata.env:=local",
                TracingFilterFields.Scope.TRACE));
    }

    @Test
    void traceValidationIgnoresFieldsTheTraceRowCannotServe() {
        // release/root are real fields with no trace-level source: ignored, not rejected.
        assertDoesNotThrow(() -> TracingFilterFields.validate("release:2026.1", TracingFilterFields.Scope.TRACE));
        assertDoesNotThrow(() -> TracingFilterFields.validate("root:true", TracingFilterFields.Scope.TRACE));
        assertDoesNotThrow(() -> TracingFilterFields.validate("tokens:>100", TracingFilterFields.Scope.TRACE));
    }

    @Test
    void traceValidationStillRejectsUnknownFields() {
        InvalidRequestException failure = assertThrows(InvalidRequestException.class,
                () -> TracingFilterFields.validate("bogus:1", TracingFilterFields.Scope.TRACE));
        assertTrue(failure.getMessage().contains("bogus"));
        assertTrue(failure.getMessage().contains("traceName"),
                "trace scope should list trace fields: " + failure.getMessage());
    }

    /**
     * metadata is advertised with its required key, so the message never claims the bare form
     * works while the implementation ignores it.
     */
    @Test
    void supportedFieldListSpellsOutTheMetadataKeyRequirement() {
        try {
            TracingFilterFields.validate("bogus:1");
            throw new AssertionError("expected InvalidRequestException");
        } catch (InvalidRequestException expected) {
            assertTrue(expected.getMessage().contains("metadata.<key>"),
                    "expected metadata.<key> in: " + expected.getMessage());
            assertFalse(expected.getMessage().contains("metadata, "),
                    "bare metadata must not be advertised: " + expected.getMessage());
        }
    }

    @Test
    void bareMetadataIsNotCountedAsAFieldWithoutASource() {
        // It is ignored because the usage is incomplete, not because the column is missing -
        // the distinction matters when reading the warning.
        assertFalse(TracingFilterFields.hasNoSource("metadata", TracingFilterFields.Scope.OBSERVATION));
        assertTrue(TracingFilterFields.isSupported("metadata", TracingFilterFields.Scope.OBSERVATION));
    }
}
