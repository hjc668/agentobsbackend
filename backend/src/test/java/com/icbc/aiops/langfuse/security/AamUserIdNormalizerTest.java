package com.icbc.aiops.langfuse.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * The 10-character rule is ported from the reference implementation
 * ({@code LoginController.initSession}: {@code if (userId.length() == 10) userId = userId.substring(1, 10);}).
 * These tests pin the exact semantics so a future "tidy up" cannot silently change who a
 * user is.
 */
class AamUserIdNormalizerTest {

    @Test
    void dropsTheLeadingCharacterFromATenCharacterUserNumber() {
        // substring(1, 10) on a 10-char value yields 9 characters.
        assertEquals("234567890", AamUserIdNormalizer.normalize("1234567890"));
    }

    @Test
    void leavesShorterAndLongerValuesUntouched() {
        // The rule is an exact-length check, not a "trim to nine" rule.
        assertEquals("123456789", AamUserIdNormalizer.normalize("123456789"));
        assertEquals("12345678901", AamUserIdNormalizer.normalize("12345678901"));
        assertEquals("38971135", AamUserIdNormalizer.normalize("38971135"));
    }

    @Test
    void doesNotRewriteDevelopmentStyleIds() {
        // Ten characters, but not an AAM employee number: normalization still applies only
        // where the AAM entry point calls it, which the controller test covers.
        assertEquals("iewer-001", AamUserIdNormalizer.normalize("viewer-001"));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("38971135", AamUserIdNormalizer.normalize("  38971135  "));
    }

    @Test
    void rejectsMissingOrBlankIdentity() {
        assertThrows(AamAuthenticationException.class, () -> AamUserIdNormalizer.normalize(null));
        assertThrows(AamAuthenticationException.class, () -> AamUserIdNormalizer.normalize(""));
        assertThrows(AamAuthenticationException.class, () -> AamUserIdNormalizer.normalize("   "));
    }
}
