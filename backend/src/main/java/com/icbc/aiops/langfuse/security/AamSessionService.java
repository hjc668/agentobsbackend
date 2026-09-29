package com.icbc.aiops.langfuse.security;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * 将已验证的 AAM 身份绑定到 Spring Security Session。
 *
 * <p>参考工程使用空密码的 {@code subject.login(token)} 延续 Shiro Session。
 * 本项目基于 Spring Security，因此在这里显式完成等价交接：轮换 Session ID、创建新的
 * {@link SecurityContext}、将其保存到 {@link HttpSession}，然后重新签发 CSRF token，
 * 确保交给浏览器的 token 绑定到登录后的 Session。
 */
public class AamSessionService {

    private static final Logger log = LoggerFactory.getLogger(AamSessionService.class);

    /** 与 {@code CookieCsrfTokenRepository} 默认值一致，用于退出时清理 Cookie。 */
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

    private final AamRoleResolver roleResolver;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final int sessionTimeoutSeconds;

    public AamSessionService(AamRoleResolver roleResolver, CsrfTokenRepository csrfTokenRepository,
            int sessionTimeoutSeconds) {
        this.roleResolver = roleResolver;
        this.csrfTokenRepository = csrfTokenRepository;
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
        this.sessionAuthenticationStrategy = new SessionFixationProtectionStrategy();
    }

    /**
     * 创建认证后的 Session 并返回已绑定的 principal。
     *
     * <p>调用方必须传入已经标准化的用户号。AAM 特有的 10 位用户号规则位于
     * {@link AamUserIdNormalizer}，且仅由 AAM 入口应用，避免开发环境 ID 被生产身份规则静默改写。
     */
    public AamEstablishedSession establishSession(AamVerifiedIdentity identity, HttpServletRequest request,
            HttpServletResponse response) {
        String userId = identity.getRawUserNo();
        if (userId == null || userId.trim().isEmpty()) {
            throw new AamAuthenticationException("AAM user identity is missing");
        }
        userId = userId.trim();
        AamRole role = roleResolver.resolve(userId);
        AamUserPrincipal principal = new AamUserPrincipal(userId,
                displayNameOrFallback(identity.getDisplayName(), userId),
                emptyIfNull(identity.getNotesId()),
                emptyIfNull(identity.getDepartmentId()),
                emptyIfNull(identity.getDepartmentName()),
                role);

        // CSRF 初始化可能已经创建匿名 Session；如果尚未创建则先创建，并在绑定认证身份前轮换 ID，
        // 避免登录前获取到的 Session ID 在登录后被重放。
        request.getSession(true);
        request.changeSessionId();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // 此逻辑运行在 Controller 内，此时 SecurityContextPersistenceFilter 已提交响应包装器，
        // 因此需要显式持久化，而不能依赖该过滤器。
        request.getSession().setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        if (sessionTimeoutSeconds > 0) {
            request.getSession().setMaxInactiveInterval(sessionTimeoutSeconds);
        }

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        CsrfToken csrfToken = reissueCsrfToken(request, response);
        return new AamEstablishedSession(principal, csrfToken.getToken());
    }

    /**
     * 清理本地 Session。调用方必须先清理 AAM 侧状态，避免远端退出失败时浏览器仍持有有效的本地 Session。
     */
    public void invalidateSession(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalidated) {
                log.debug("Session was already invalidated during logout");
            }
        }
        SecurityContextHolder.clearContext();
        clearCookie(request, response, "JSESSIONID");
        clearCookie(request, response, CSRF_COOKIE_NAME);
    }

    /** 签发绑定到新 Session 的 token，替换登录前的匿名 token。 */
    private CsrfToken reissueCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);
        return token;
    }

    private static void clearCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        String contextPath = request.getContextPath();
        cookie.setPath(contextPath == null || contextPath.isEmpty() ? "/" : contextPath);
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        response.addCookie(cookie);
    }

    private static String displayNameOrFallback(String displayName, String userId) {
        return displayName != null && !displayName.trim().isEmpty() ? displayName : userId;
    }

    private static String emptyIfNull(String value) {
        return value != null ? value : "";
    }
}
