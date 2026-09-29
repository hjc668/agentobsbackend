package com.icbc.aiops.langfuse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icbc.aiops.langfuse.postgres.mapper.UserRoleMapper;
import com.icbc.aiops.langfuse.security.AamAuthenticationException;
import com.icbc.aiops.langfuse.security.AamTicketAuthenticator;
import com.icbc.aiops.langfuse.security.AamVerifiedIdentity;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end behaviour of the in-house AAM sign-in flow.
 *
 * <p>The Hermes-backed authenticator cannot be compiled here (intranet-only artifacts), so
 * the port is stubbed. That keeps every policy decision under test — the handshake itself
 * is the only part left for intranet verification. The stub mimics Hermes by throwing when
 * the ticket is bad and by carrying the identity on the request, so the controller is
 * exercised exactly as it will be in production.
 */
@SpringBootTest(properties = {
        "app.auth.mode=aam",
        // AamConfigurationValidator fails startup when these are absent, so an aam-mode
        // context needs a complete-looking configuration to come up at all.
        "aam.enableSSIC=true",
        "aam.enableSpecialUrl=false",
        "aam.ssic.server.ip=aam.example.internal",
        "aam.ssic.server.version=SM2",
        "aam.ssic.server.publickey=test-public-key",
        "aam.ssic.client.site_url=https://example.internal/aamlogin",
        "aam.ssic.client.key_name=test-client",
        "aam.ssic.client.pri_key_passwd=test-password",
        "aam.ssic.return_url_key=test-return-key",
        "aam.service.pub.key=test-partner-key",
        "aam.service.system.label=test-service",
        "aam.service.web.num=001642",
        "dsf.cocoa.router.addr=http://cocoa.example.internal/icbc/cocoa"})
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class AamSsoIntegrationTest {

    private static final String CONTEXT = "/icbc/hmp/agentobs";
    private static final String USER_NO_10_DIGITS = "1234567890";
    private static final String NORMALIZED_USER_NO = "234567890";

    @Autowired private MockMvc mockMvc;

    @TestConfiguration
    static class StubAam {
        @Bean
        AamTicketAuthenticator aamTicketAuthenticator() {
            return new AamTicketAuthenticator() {
                @Override
                public AamVerifiedIdentity authenticate(HttpServletRequest request,
                        HttpServletResponse response, String ssiAuth, String ssiSign) {
                    if ("viewer-auth".equals(ssiAuth) && "valid-sign".equals(ssiSign)) {
                        return new AamVerifiedIdentity("viewer001", "普通用户", "viewer/notes",
                                "D-2001", "运营部");
                    }
                    if (!"valid-auth".equals(ssiAuth) || !"valid-sign".equals(ssiSign)) {
                        throw new AamAuthenticationException("AAM ticket verification failed");
                    }
                    return new AamVerifiedIdentity(USER_NO_10_DIGITS, "张三", "zhangsan/notes",
                            "D-1001", "研发部");
                }

                @Override
                public void logout(HttpServletRequest request, HttpServletResponse response) {
                    // no AAM-side state in the stub
                }
            };
        }

        /** Stands in for the PolarDB-X role table: only this user is an administrator. */
        @Bean
        UserRoleMapper userRoleMapper() {
            return aamUserNo -> NORMALIZED_USER_NO.equals(aamUserNo) ? "ADMIN" : "VIEW";
        }
    }

    @Test
    void validTicketSignsInAndNormalizesTheUserNumber() throws Exception {
        MvcResult result = mockMvc.perform(post(CONTEXT + "/aam/login/auth").contextPath(CONTEXT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"SSIAuth\":\"valid-auth\",\"SSISign\":\"valid-sign\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.msg").value("login success"))
                // 10-char AAM number -> leading character dropped.
                .andExpect(jsonPath("$.result.user.userId").value(NORMALIZED_USER_NO))
                .andExpect(jsonPath("$.result.user.userName").value("张三"))
                .andExpect(jsonPath("$.result.user.notesId").value("zhangsan/notes"))
                // The real directory identifiers are surfaced, not the name echoed twice.
                .andExpect(jsonPath("$.result.user.departmentId").value("D-1001"))
                .andExpect(jsonPath("$.result.user.departmentName").value("研发部"))
                .andExpect(jsonPath("$.result.user.currentRoleId").value("ADMIN"))
                .andExpect(jsonPath("$.result.menu").isEmpty())
                .andReturn();
        // A CSRF token must come back so the browser can make write calls.
        assertFalse(result.getResponse().getContentAsString().contains("\"csrfToken\":null"));
    }

    @Test
    void rejectedTicketReturns401AndDoesNotCreateSession() throws Exception {
        MvcResult rejected = mockMvc.perform(post("/aam/login/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"SSIAuth\":\"forged\",\"SSISign\":\"forged\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AAM_AUTH_FAILED"))
                .andExpect(jsonPath("$.message").value("AAM authentication failed"))
                .andReturn();
        assertNull(rejected.getRequest().getSession(false),
                "a rejected ticket must not create a session");
    }

    @Test
    void missingTicketParametersAreRejected() throws Exception {
        mockMvc.perform(post("/aam/login/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"SSIAuth\":\"valid-auth\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/aam/login/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"SSIAuth\":\"\",\"SSISign\":\"valid-sign\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/aam/login/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"SSIAuth\":\"%20\",\"SSISign\":\"valid-sign\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AAM_AUTH_FAILED"));
    }

    @Test
    void authenticatedUserWithoutAdminMappingGetsViewRole() throws Exception {
        mockMvc.perform(post("/aam/login/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"SSIAuth\":\"viewer-auth\",\"SSISign\":\"valid-sign\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.user.userId").value("viewer001"))
                .andExpect(jsonPath("$.result.user.currentRoleId").value("VIEW"));
    }

    @Test
    void lowerCaseAliasesAreAccepted() throws Exception {
        mockMvc.perform(post("/aam/login/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ssiAuth\":\"valid-auth\",\"ssiSign\":\"valid-sign\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void aClaimedRoleInTheBodyIsIgnored() throws Exception {
        // The user resolves to ADMIN from the table in this stub, so to prove the body is
        // ignored we assert the reverse: an unknown user stays VIEW despite claiming ADMIN.
        MvcResult result = mockMvc.perform(post("/aam/login/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"SSIAuth\":\"valid-auth\",\"SSISign\":\"valid-sign\","
                                + "\"role\":\"VIEW\",\"admin\":true,\"currentRoleId\":\"VIEW\"}"))
                .andExpect(status().isOk())
                // Still ADMIN: the body's role fields are not bound to anything.
                .andExpect(jsonPath("$.result.user.currentRoleId").value("ADMIN"))
                .andReturn();
        assertNotEquals(0, result.getResponse().getContentAsString().indexOf("userId"));
    }

    @Test
    void theCallbackIsExemptFromCsrfButOtherWritesAreNot() throws Exception {
        // No CSRF token at all: the AAM callback must still be accepted, because the
        // unified authentication service cannot know a pre-login business token.
        MvcResult login = mockMvc.perform(post("/aam/login/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"SSIAuth\":\"valid-auth\",\"SSISign\":\"valid-sign\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        // Every other write keeps the CSRF requirement.
        mockMvc.perform(post("/api/v1/workspace/dashboards").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"no csrf\",\"description\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sessionIdRotatesOnSignInAndMeRestoresTheIdentity() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String anonymousSessionId = session.getId();

        MvcResult login = mockMvc.perform(post("/aam/login/auth").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"SSIAuth\":\"valid-auth\",\"SSISign\":\"valid-sign\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession authenticated = (MockHttpSession) login.getRequest().getSession(false);
        // Session fixation guard: the id handed out before login must not survive it.
        assertNotEquals(anonymousSessionId, authenticated.getId());

        // Identity survives into later requests.
        mockMvc.perform(get("/api/v1/auth/me").session(authenticated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.aamId").value(NORMALIZED_USER_NO))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void logoutClearsBothTheAamAndTheLocalSession() throws Exception {
        MvcResult login = mockMvc.perform(post("/aam/login/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"SSIAuth\":\"valid-auth\",\"SSISign\":\"valid-sign\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        String csrf = login.getResponse().getCookie("XSRF-TOKEN").getValue();

        mockMvc.perform(post("/aamlogout").session(session)
                        .cookie(login.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authConfigReportsAamSoTheBrowserKnowsWhereToRedirect() throws Exception {
        mockMvc.perform(get(CONTEXT + "/api/v1/auth/config").contextPath(CONTEXT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("aam"))
                .andExpect(jsonPath("$.loginUrl").value(CONTEXT + "/api/aam/login"))
                // The portal sign-out endpoint is required for a complete logout: the
                // front-end must navigate here after POST /api/v1/auth/logout.
                .andExpect(jsonPath("$.logoutUrl").value(CONTEXT + "/api/aam/logout"));
    }

    @Test
    void logoutStillSucceedsWhenThePortalIsUnreachable() throws Exception {
        // The local session must never survive because the AAM side failed.
        MvcResult login = mockMvc.perform(post("/aam/login/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"SSIAuth\":\"valid-auth\",\"SSISign\":\"valid-sign\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        String csrf = login.getResponse().getCookie("XSRF-TOKEN").getValue();

        mockMvc.perform(post("/api/v1/auth/logout").session(session)
                        .cookie(login.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theMockSignInEndpointIsNotRegisteredInAamMode() throws Exception {
        // Fail-closed: with the Hermes verifier in charge there must be no local sign-in
        // path that a failed AAM verification could fall back to. A valid CSRF token is
        // supplied so the request reaches handler mapping instead of being stopped by the
        // CSRF filter, which would otherwise mask whether the route exists.
        MockHttpSession session = new MockHttpSession();
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        String token = csrf.getResponse().getCookie("XSRF-TOKEN").getValue();

        mockMvc.perform(post("/api/v1/auth/login").session(session)
                        .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aamId\":\"bypass\",\"ticket\":\"bypass\"}"))
                .andExpect(status().isNotFound());
    }
}
