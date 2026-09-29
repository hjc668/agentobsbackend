package com.icbc.aiops.langfuse.security;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Tests input rejection without contacting the in-house UniformTeller service. */
class UniformTellerInfoClientTest {

    @Test
    void rejectsMissingUserNumberBeforeUsingIntranetDependencies() {
        UniformTellerInfoClient client = new UniformTellerInfoClient(null, null, new ObjectMapper());

        assertThrows(AamAuthenticationException.class, () -> client.query(null));
        assertThrows(AamAuthenticationException.class, () -> client.query("  "));
    }
}
