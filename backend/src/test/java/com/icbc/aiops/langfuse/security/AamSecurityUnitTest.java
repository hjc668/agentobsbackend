package com.icbc.aiops.langfuse.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.stream.Collectors;
import javax.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class AamSecurityUnitTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void principalExposesIdentityAuthoritiesAndUserDetailsFlags() {
        AamUserPrincipal admin = new AamUserPrincipal(
                "u001", "Alice", "notes", "dept", "AIOps", AamRole.ADMIN);
        assertEquals("u001", admin.getAamId());
        assertEquals("Alice", admin.getDisplayName());
        assertEquals("notes", admin.getNotesId());
        assertEquals("dept", admin.getDepartmentId());
        assertEquals("AIOps", admin.getDepartmentName());
        assertEquals(AamRole.ADMIN, admin.getRole());
        assertEquals("u001", admin.getUsername());
        assertEquals("", admin.getPassword());
        assertTrue(admin.isAccountNonExpired());
        assertTrue(admin.isAccountNonLocked());
        assertTrue(admin.isCredentialsNonExpired());
        assertTrue(admin.isEnabled());
        assertEquals(java.util.Arrays.asList("ROLE_VIEW", "ROLE_ADMIN"), authorityNames(admin.getAuthorities()));

        AamUserPrincipal viewer = new AamUserPrincipal("u002", "Bob", "dept", "Support", AamRole.VIEW);
        assertNull(viewer.getNotesId());
        assertEquals(java.util.Collections.singletonList("ROLE_VIEW"), authorityNames(viewer.getAuthorities()));
    }

    @Test
    void mockVerifierRejectsMissingCredentialsAndKeepsDevelopmentIdentity() {
        MockAamCredentialVerifier verifier = new MockAamCredentialVerifier("Developer", "AIOps");
        assertThrows(AamAuthenticationException.class, () -> verifier.verify(null, "ticket"));
        assertThrows(AamAuthenticationException.class, () -> verifier.verify("user", "  "));

        AamVerifiedIdentity identity = verifier.verify("  local-user  ", "ticket");
        assertEquals("local-user", identity.getRawUserNo());
        assertEquals("Developer", identity.getDisplayName());
        assertNull(identity.getNotesId());
        assertEquals("", identity.getDepartmentId());
        assertEquals("AIOps", identity.getDepartmentName());

        AamVerifiedIdentity rawOnly = new AamVerifiedIdentity("raw");
        assertEquals("raw", rawOnly.getRawUserNo());
        assertNull(rawOnly.getDisplayName());
        assertNull(rawOnly.getDepartmentId());
        assertNull(rawOnly.getDepartmentName());
    }

    @Test
    void currentUserRequiresAnAamPrincipal() {
        assertThrows(AamAuthenticationException.class, CurrentAamUser::aamId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("plain-user", null));
        assertThrows(AamAuthenticationException.class, CurrentAamUser::aamId);

        AamUserPrincipal principal = new AamUserPrincipal(
                "u001", "Alice", "dept", "AIOps", AamRole.VIEW);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        assertEquals("u001", CurrentAamUser.aamId());
    }

    @Test
    void sessionServiceEstablishesAndInvalidatesSecureSession() {
        RecordingCsrfRepository csrf = new RecordingCsrfRepository();
        AamSessionService service = new AamSessionService(user -> AamRole.ADMIN, csrf, 600);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/langfuse");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AamEstablishedSession established = service.establishSession(
                new AamVerifiedIdentity("  u001  ", " ", null, null, null), request, response);

        assertEquals("u001", established.getPrincipal().getAamId());
        assertEquals("u001", established.getPrincipal().getDisplayName());
        assertEquals("", established.getPrincipal().getNotesId());
        assertEquals("", established.getPrincipal().getDepartmentId());
        assertEquals("", established.getPrincipal().getDepartmentName());
        assertEquals(AamRole.ADMIN, established.getPrincipal().getRole());
        assertEquals("csrf-value", established.getCsrfToken());
        assertSame(csrf.generated, csrf.saved);
        assertEquals(600, request.getSession().getMaxInactiveInterval());
        assertNotNull(request.getSession().getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        assertSame(established.getPrincipal(), SecurityContextHolder.getContext().getAuthentication().getPrincipal());

        service.invalidateSession(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(2, response.getCookies().length);
        assertExpiredSecureCookie(response.getCookies()[0], "/langfuse");
        assertExpiredSecureCookie(response.getCookies()[1], "/langfuse");
    }

    @Test
    void sessionServiceRejectsMissingIdentityAndUsesRootCookiePath() {
        AamSessionService service = new AamSessionService(user -> AamRole.VIEW,
                new RecordingCsrfRepository(), 0);
        assertThrows(AamAuthenticationException.class, () -> service.establishSession(
                new AamVerifiedIdentity(" "), new MockHttpServletRequest(), new MockHttpServletResponse()));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.invalidateSession(request, response);
        assertEquals("/", response.getCookies()[0].getPath());
    }

    private static java.util.List<String> authorityNames(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList());
    }

    private static void assertExpiredSecureCookie(Cookie cookie, String path) {
        assertEquals(0, cookie.getMaxAge());
        assertEquals(path, cookie.getPath());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.getSecure());
        assertFalse(cookie.getName().isEmpty());
    }

    private static final class RecordingCsrfRepository implements CsrfTokenRepository {
        private CsrfToken generated;
        private CsrfToken saved;

        @Override
        public CsrfToken generateToken(javax.servlet.http.HttpServletRequest request) {
            generated = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "csrf-value");
            return generated;
        }

        @Override
        public void saveToken(CsrfToken token, javax.servlet.http.HttpServletRequest request,
                javax.servlet.http.HttpServletResponse response) {
            saved = token;
        }

        @Override
        public CsrfToken loadToken(javax.servlet.http.HttpServletRequest request) {
            return saved;
        }
    }
}
