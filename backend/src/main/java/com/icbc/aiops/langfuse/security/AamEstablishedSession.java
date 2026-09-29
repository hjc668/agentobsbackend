package com.icbc.aiops.langfuse.security;

/**
 * {@link AamSessionService#establishSession} 的结果：包含已绑定的 principal 和为新 Session
 * 签发的 CSRF token，供登录响应返回给前端。
 */
public final class AamEstablishedSession {

    private final AamUserPrincipal principal;
    private final String csrfToken;

    public AamEstablishedSession(AamUserPrincipal principal, String csrfToken) {
        this.principal = principal;
        this.csrfToken = csrfToken;
    }

    public AamUserPrincipal getPrincipal() { return principal; }
    public String getCsrfToken() { return csrfToken; }
}
