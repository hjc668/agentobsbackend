package com.icbc.aiops.langfuse.api;

import com.icbc.aiops.langfuse.security.AamSessionService;
import com.icbc.aiops.langfuse.security.AamTicketAuthenticator;
import com.icbc.aiops.langfuse.security.AamUserPrincipal;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 所有认证模式共用的 Session 接口。登录入口按模式分别位于
 * {@link MockLoginController}（本地）或 {@link AamSsoController}（行内 AAM），
 * 因此任意时刻只会注册其中一个。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationController.class);

    private final AamSessionService sessionService;
    private final ObjectProvider<AamTicketAuthenticator> aamAuthenticator;
    private final String portalLoginUrl;
    private final String portalLogoutUrl;

    public AuthenticationController(AamSessionService sessionService,
            ObjectProvider<AamTicketAuthenticator> aamAuthenticator,
            @Value("${app.auth.portal.login-url:/api/aam/login}") String portalLoginUrl,
            @Value("${app.auth.portal.logout-url:/api/aam/logout}") String portalLogoutUrl) {
        this.sessionService = sessionService;
        this.aamAuthenticator = aamAuthenticator;
        this.portalLoginUrl = portalLoginUrl;
        this.portalLogoutUrl = portalLogoutUrl;
    }

    /** 签发浏览器在写请求中必须回传的 CSRF token。 */
    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) { return token; }

    /**
     * 匿名配置发现接口：告知前端当前启用的登录流程及需要跳转的 Portal 接口。
     * 响应中不包含密钥或用户数据。
     *
     * <p>mock 模式下 {@code logoutUrl} 为 {@code null}，因为不存在需要退出的 Portal，
     * 前端只需回到自身登录状态。
     */
    @GetMapping("/config")
    public AuthConfigResponse config(HttpServletRequest request) {
        boolean aam = aamAuthenticator.getIfAvailable() != null;
        return new AuthConfigResponse(aam ? "aam" : "mock",
                contextUrl(request, aam ? portalLoginUrl : "/api/v1/auth/login"),
                aam ? contextUrl(request, portalLogoutUrl) : null);
    }

    /** 为应用内地址补充 context path，绝对 Portal URL 保持不变。 */
    static String contextUrl(HttpServletRequest request, String configuredUrl) {
        if (configuredUrl == null || configuredUrl.trim().isEmpty()) return configuredUrl;
        String value = configuredUrl.trim();
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return value;

        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isEmpty() || "/".equals(contextPath)) {
            return value.startsWith("/") ? value : "/" + value;
        }
        if (value.equals(contextPath) || value.startsWith(contextPath + "/")) return value;
        return contextPath + (value.startsWith("/") ? value : "/" + value);
    }

    /** 页面刷新后恢复已登录身份。 */
    @GetMapping("/me")
    public CurrentUserResponse me(
            @org.springframework.security.core.annotation.AuthenticationPrincipal AamUserPrincipal principal) {
        return new CurrentUserResponse(principal);
    }

    /**
     * 统一退出接口。{@code aam} 模式下还会通过 SSO 接口使用的同一认证器清理 AAM 侧状态，
     * 避免 Portal Session 比本地 Session 存活更久。
     *
     * <p>此接口只完成退出流程的一半。随后前端必须整页跳转到
     * {@link AuthConfigResponse#getLogoutUrl()}，由 Portal 清理自身 SSO Cookie；
     * 否则下一次加载页面时会被自动重新登录。Portal 接口由 Hermes 提供，此处有意不做代理，
     * 因为它必须通过顶层页面跳转访问，而不能使用 XHR。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        AamTicketAuthenticator authenticator = aamAuthenticator.getIfAvailable();
        if (authenticator != null) {
            try {
                authenticator.logout(request, response);
            } catch (RuntimeException failure) {
                // best-effort：无论 AAM 退出结果如何都清理本地 Session，并照常提供 Portal 退出地址。
                log.warn("AAM logout failed; clearing the local session anyway", failure);
            }
        }
        sessionService.invalidateSession(request, response);
        return ResponseEntity.noContent().build();
    }
}
