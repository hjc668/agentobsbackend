package com.icbc.aiops.langfuse.security;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Tests the adapter guard that runs before any intranet Hermes SDK call. */
class HermesAamTicketAuthenticatorTest {

    @Test
    void rejectsMissingTicketsBeforeCallingHermes() {
        HermesAamTicketAuthenticator authenticator =
                new HermesAamTicketAuthenticator(null, null, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(AamAuthenticationException.class,
                () -> authenticator.authenticate(request, response, null, "signature"));
        assertThrows(AamAuthenticationException.class,
                () -> authenticator.authenticate(request, response, "ticket", " "));
    }
}
