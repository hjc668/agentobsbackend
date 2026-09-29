package com.icbc.aiops.langfuse.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class AamSecurityIntegrationTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestsAreRejectedAndViewCannotWrite() throws Exception {
        mockMvc.perform(get("/api/v1/observability/traces")).andExpect(status().isUnauthorized());
        Login view = login("viewer-001");
        mockMvc.perform(get("/api/v1/observability/traces").session(view.session))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/workspace/dashboards").session(view.session).cookie(view.csrfCookie)
                        .header("X-XSRF-TOKEN", view.csrfToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Not allowed\",\"description\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewCanLogoutAndInvalidatedSessionCannotReadAgain() throws Exception {
        Login view = login("viewer-logout");
        mockMvc.perform(post("/api/v1/auth/logout").session(view.session).cookie(view.csrfCookie)
                        .header("X-XSRF-TOKEN", view.csrfToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/observability/traces").session(view.session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminRoleComesOnlyFromServerConfiguration() throws Exception {
        Login admin = login("38971135");
        mockMvc.perform(get("/api/v1/auth/me").session(admin.session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mockMvc.perform(post("/api/v1/workspace/dashboards").session(admin.session).cookie(admin.csrfCookie)
                        .header("X-XSRF-TOKEN", admin.csrfToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"AAM admin dashboard\",\"description\":\"created by test\",\"role\":\"VIEW\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void mockModeReportsItselfAndHidesTheAamEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("mock"))
                .andExpect(jsonPath("$.loginUrl").value("/api/v1/auth/login"))
                // No portal in mock mode: the front-end must not navigate anywhere.
                .andExpect(jsonPath("$.logoutUrl").doesNotExist());

        // The AAM callback belongs to aam mode only. A valid CSRF token is supplied so the
        // request reaches handler mapping rather than being stopped by the CSRF filter.
        MockHttpSession session = new MockHttpSession();
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        String token = csrf.getResponse().getCookie("XSRF-TOKEN").getValue();
        mockMvc.perform(post("/aam/login/auth").session(session)
                        .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"SSIAuth\":\"a\",\"SSISign\":\"b\"}"))
                .andExpect(status().isNotFound());
    }

    private Login login(String aamId) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String anonymousSessionId = session.getId();
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        Cookie cookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").session(session).cookie(cookie)
                        .header("X-XSRF-TOKEN", cookie.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aamId\":\"" + aamId + "\",\"ticket\":\"mock-ticket\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession authenticatedSession = (MockHttpSession) login.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotEquals(anonymousSessionId, authenticatedSession.getId());
        return new Login(authenticatedSession, cookie, cookie.getValue());
    }

    private static final class Login {
        private final MockHttpSession session; private final Cookie csrfCookie; private final String csrfToken;
        private Login(MockHttpSession session, Cookie csrfCookie, String csrfToken) {
            this.session = session; this.csrfCookie = csrfCookie; this.csrfToken = csrfToken;
        }
    }
}
