package com.icbc.aiops.langfuse.security;

import com.icbc.hermes.aam.AamConfig;
import com.icbc.hermes.aam.wrapper.AamWrapper;
import com.icbc.ssic.base.Credentials;
import com.icbc.ssic.base.SSICUser;
import com.icbc.ssic.base.ServerSideAuthenticator;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;

/**
 * {@link AamTicketAuthenticator} 的 Hermes（行内 AAM）实现。
 *
 * <p><strong>该类仅在 {@code aam} Maven profile 下编译</strong>
 *（{@code src/main/aam/java}）。它依赖仅在行内提供的 {@code com.icbc.hermes}、
 * {@code com.icbc.ssic} 和 {@code com.icbc.aam} 制品，这些制品未发布到 Maven Central，
 * 因此默认构建和本地测试不会包含该类。AAM 集成中只有这一部分必须在行内验证。
 *
 * <p>该类有意保持为轻量适配层：用户号标准化、角色解析、Session 和 CSRF 处理等策略均位于
 * 不依赖 profile 的类（{@link AamUserIdNormalizer}、{@link AamRoleResolver}、
 * {@link AamSessionService}）中，从而只有实际的 Hermes 验签过程无法在本地验证。
 *
 * <p>实现逻辑参考
 * {@code com.icbc.dsf.self.analysis.ctl.LoginController#loginAuth}。
 */
public class HermesAamTicketAuthenticator implements AamTicketAuthenticator {

    /** Hermes 写入已验证凭据的 Request attribute 名称。 */
    static final String CREDENTIALS_ATTRIBUTE = "ssiCredentials";

    private final AamWrapper aamWrapper;
    private final AamConfig aamConfig;
    private final AamUserInfoService userInfoService;

    public HermesAamTicketAuthenticator(AamWrapper aamWrapper, AamConfig aamConfig,
            AamUserInfoService userInfoService) {
        this.aamWrapper = aamWrapper;
        this.aamConfig = aamConfig;
        this.userInfoService = userInfoService;
    }

    @Override
    public AamVerifiedIdentity authenticate(HttpServletRequest request, HttpServletResponse response,
            String ssiAuth, String ssiSign) {
        if (!StringUtils.hasText(ssiAuth) || !StringUtils.hasText(ssiSign)) {
            throw new AamAuthenticationException("AAM ticket parameters are missing");
        }
        final boolean passed;
        try {
            ServerSideAuthenticator serverSideAuthenticator = aamConfig.getServerSideAuth();
            passed = aamWrapper.auth(request, response, ssiAuth, ssiSign, serverSideAuthenticator);
        } catch (RuntimeException sdkFailure) {
            // 不透传 SDK 异常信息，其中可能包含服务地址或票据数据。
            throw new AamAuthenticationException("AAM ticket verification service failed");
        }
        if (!passed) {
            throw new AamAuthenticationException("AAM ticket verification failed");
        }

        // 经过此处后，用户身份以 Hermes 已验证的凭据为准，绝不从请求体获取。
        Object value = request.getAttribute(CREDENTIALS_ATTRIBUTE);
        if (!(value instanceof Credentials)) {
            throw new AamAuthenticationException("AAM credentials are missing or invalid");
        }
        SSICUser ssicUser = ((Credentials) value).getSSICUser();
        if (ssicUser == null || !StringUtils.hasText(ssicUser.getUserName())) {
            throw new AamAuthenticationException("AAM user identity is missing");
        }

        String rawUserNo = ssicUser.getUserName().trim();
        String normalizedUserNo = AamUserIdNormalizer.normalize(rawUserNo);
        AamUserInfo user = userInfoService.query(normalizedUserNo);
        if (user == null) {
            throw new AamAuthenticationException("AAM user information is missing");
        }
        return new AamVerifiedIdentity(rawUserNo, user.getDisplayName(), user.getNotesId(),
                user.getDepartmentId(), user.getDepartmentName());
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        aamWrapper.logout(request, response, "", "", aamConfig.getServerSideAuth());
    }

}
