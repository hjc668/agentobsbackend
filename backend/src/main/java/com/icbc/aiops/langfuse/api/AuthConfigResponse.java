package com.icbc.aiops.langfuse.api;

/**
 * 前端匿名获取的认证配置：说明当前启用的认证流程，以及浏览器需要跳转的 Portal 接口。
 * 该结构有意不包含密码、密钥或用户数据。
 */
public class AuthConfigResponse {

    private final String mode;
    private final String loginUrl;
    private final String logoutUrl;

    public AuthConfigResponse(String mode, String loginUrl, String logoutUrl) {
        this.mode = mode;
        this.loginUrl = loginUrl;
        this.logoutUrl = logoutUrl;
    }

    public String getMode() { return mode; }

    /** 未认证浏览器开始登录时跳转的地址。 */
    public String getLoginUrl() { return loginUrl; }

    /**
     * Portal 退出接口；不存在 Portal（mock 模式）时为 {@code null}。
     *
     * <p>调用 {@code POST /api/v1/auth/logout} 后，浏览器必须<em>整页跳转</em>到此地址。
     * XHR 无法清理 Portal 自身的 SSO Cookie；如果跳过这一步，用户会在下次请求时被直接重新登录。
     */
    public String getLogoutUrl() { return logoutUrl; }
}
