package com.icbc.aiops.langfuse.api;

import com.icbc.aiops.langfuse.security.AamEstablishedSession;
import com.icbc.aiops.langfuse.security.AamSessionService;
import com.icbc.aiops.langfuse.security.AamVerifiedIdentity;
import com.icbc.aiops.langfuse.security.MockAamCredentialVerifier;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发环境登录接口，仅在 {@code app.auth.mode=mock}（或未配置）时注册。
 *
 * <p>{@code aam} 模式下不会注册该 Controller，因此 AAM 验签失败后不存在可访问的本地登录路径；
 * 从结构上杜绝迁移要求禁止的 mock 回退，而不只是约定不使用它。
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "mock", matchIfMissing = true)
public class MockLoginController {

    private final MockAamCredentialVerifier verifier;
    private final AamSessionService sessionService;

    public MockLoginController(MockAamCredentialVerifier verifier, AamSessionService sessionService) {
        this.verifier = verifier;
        this.sessionService = sessionService;
    }

    /**
     * 仅因为这是本地开发接口，才接收 {@code aamId} 和 {@code ticket}。
     * 角色绝不从请求中获取，而是由服务端解析。
     */
    @PostMapping("/login")
    public CurrentUserResponse login(@Valid @RequestBody AamLoginRequest request,
            HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        AamVerifiedIdentity identity = verifier.verify(request.getAamId(), request.getTicket());
        AamEstablishedSession established =
                sessionService.establishSession(identity, servletRequest, servletResponse);
        return new CurrentUserResponse(established.getPrincipal());
    }
}
