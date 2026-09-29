package com.icbc.aiops.langfuse.security;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 行内 AAM（Hermes）SSO 票据验签端口。
 *
 * <p>此接口有意暴露 servlet 类型：Hermes 验签不会直接返回身份，而是将已验证身份写入
 * servlet request（{@code request.getAttribute("ssiCredentials")}），因此适配器必须访问真实请求。
 * 唯一的生产实现是 {@code HermesAamTicketAuthenticator}。该实现位于 {@code aam} Maven
 * 源码目录，因为它依赖仅在行内提供的 {@code com.icbc.hermes} 和 {@code com.icbc.ssic}
 * 制品，从而保证缺少这些依赖时本模块仍可构建。
 *
 * <p><strong>约定：</strong>返回的用户号只能来自已验证凭据，绝不能来自请求体。
 * 实现必须返回<em>原始</em>用户号；标准化由调用方负责，使该逻辑在没有 Hermes 制品时仍可测试
 *（参见 {@link AamUserIdNormalizer}）。
 */
public interface AamTicketAuthenticator {

    /**
     * 校验 {@code SSIAuth}/{@code SSISign} 并返回可信身份。
     *
     * @throws AamAuthenticationException 票据被拒绝，或已验证凭据中没有可用用户号时抛出。
     *         实现不得通过返回 {@code null} 表示失败。
     */
    AamVerifiedIdentity authenticate(HttpServletRequest request, HttpServletResponse response,
            String ssiAuth, String ssiSign);

    /** 清理 AAM 侧 Session，在本地 Session 失效之前调用。 */
    void logout(HttpServletRequest request, HttpServletResponse response);
}
