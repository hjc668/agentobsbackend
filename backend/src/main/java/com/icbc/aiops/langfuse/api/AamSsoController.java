package com.icbc.aiops.langfuse.api;

import com.icbc.aiops.langfuse.security.AamAuthenticationException;
import com.icbc.aiops.langfuse.security.AamEstablishedSession;
import com.icbc.aiops.langfuse.security.AamSessionService;
import com.icbc.aiops.langfuse.security.AamTicketAuthenticator;
import com.icbc.aiops.langfuse.security.AamUserIdNormalizer;
import com.icbc.aiops.langfuse.security.AamVerifiedIdentity;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 行内 AAM SSO 接口。仅在 {@code app.auth.mode=aam} 时启用；该模式下不会注册 mock
 * {@code /api/v1/auth/login} 接口，因此不存在认证回退路径。
 *
 * <p>{@code SSIAuth}/{@code SSISign} 及其对应私钥均属于认证凭据。禁止记录日志、回显，
 * 也不得用于验签之外的任何用途。
 */
@RestController
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "aam")
public class AamSsoController {

    private static final Logger log = LoggerFactory.getLogger(AamSsoController.class);

    private final AamTicketAuthenticator authenticator;
    private final AamSessionService sessionService;

    public AamSsoController(AamTicketAuthenticator authenticator, AamSessionService sessionService) {
        this.authenticator = authenticator;
        this.sessionService = sessionService;
    }

    /**
     * 校验 SSO 票据并将身份绑定到 Spring Security Session。
     *
     * <p>身份只能来自 {@link AamTicketAuthenticator#authenticate} 在 request 中留下的
     * 已验证凭据，绝不能来自仅承载两个票据参数的请求体。
     */
    @PostMapping("/aam/login/auth")
    public AamCommonResponse<AamLoginResponse> login(@Valid @RequestBody AamSsoLoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        AamEstablishedSession established;
        try {
            // 解码前拒绝空输入，确保空票据不会进入验签流程。
            if (!StringUtils.hasText(body.getSsiAuth()) || !StringUtils.hasText(body.getSsiSign())) {
                throw new AamAuthenticationException("AAM ticket parameters are missing");
            }
            // 只解码一次；二次解码会把 %2541 转换为 %41，破坏合法转义后的签名。
            String ssiAuth = decodeOnce(body.getSsiAuth());
            String ssiSign = decodeOnce(body.getSsiSign());
            AamVerifiedIdentity identity = authenticator.authenticate(request, response, ssiAuth, ssiSign);

            // 在此处而不是共享 Session 服务中应用规则：10 位规则只适用于 AAM 用户号，
            // 不能改写开发环境身份。
            AamVerifiedIdentity normalized = new AamVerifiedIdentity(
                    AamUserIdNormalizer.normalize(identity.getRawUserNo()),
                    identity.getDisplayName(), identity.getNotesId(), identity.getDepartmentId(),
                    identity.getDepartmentName());
            established = sessionService.establishSession(normalized, request, response);
        } catch (AamAuthenticationException rejected) {
            // 仅记录异常信息，票据参数不得进入日志。由统一处理器返回稳定的 401 JSON，
            // 禁止返回可能被误判为登录成功的 Portal 风格 HTTP 200。
            log.warn("AAM login rejected: {}", rejected.getMessage());
            throw rejected;
        }

        return AamCommonResponse.success("login success",
                AamLoginResponse.of(established.getPrincipal(), established.getCsrfToken()));
    }

    /**
     * 先清理 AAM 侧状态，再清理本地 Session。AAM 退出采用 best-effort 策略；
     * 即使远端退出失败，也不能让浏览器继续持有有效的本地 Session。
     */
    @PostMapping("/aamlogout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            authenticator.logout(request, response);
        } catch (RuntimeException failure) {
            log.warn("AAM logout failed; clearing the local session anyway", failure);
        }
        sessionService.invalidateSession(request, response);
    }

    private static String decodeOnce(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is always supported", impossible);
        } catch (IllegalArgumentException malformed) {
            // 例如不完整的百分号转义；应直接拒绝，而不是继续传递半解码的票据。
            throw new AamAuthenticationException("AAM login parameters are malformed");
        }
    }
}
