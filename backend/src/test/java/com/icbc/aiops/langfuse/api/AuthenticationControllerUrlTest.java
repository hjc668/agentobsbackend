package com.icbc.aiops.langfuse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthenticationControllerUrlTest {

    private static final String CONTEXT = "/icbc/hmp/agentobs";

    @Test
    void prefixesOnlyApplicationLocalAuthenticationUrls() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath(CONTEXT);

        assertEquals(CONTEXT + "/api/aam/login",
                AuthenticationController.contextUrl(request, "/api/aam/login"));
        assertEquals(CONTEXT + "/api/aam/logout",
                AuthenticationController.contextUrl(request, CONTEXT + "/api/aam/logout"));
        assertEquals("https://aam.example.internal/logout",
                AuthenticationController.contextUrl(request, "https://aam.example.internal/logout"));
        assertEquals("http://aam.example.internal/login",
                AuthenticationController.contextUrl(request, "http://aam.example.internal/login"));
    }
}
