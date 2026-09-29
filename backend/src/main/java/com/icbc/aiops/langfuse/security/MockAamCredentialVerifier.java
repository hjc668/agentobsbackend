package com.icbc.aiops.langfuse.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 仅供本地开发使用的登录校验器。只在 {@code app.auth.mode=mock}（或未配置）时启用，
 * 因此不会注册到 {@code aam} 部署中。
 *
 * <p>该实现有意不接受客户端提交的角色；调用方通过配置白名单解析 VIEW/ADMIN。
 * {@code AamProfileIsolationTest} 用于验证生产隔离性，如果该 Bean 出现在 {@code aam}
 * 模式中，测试会直接失败。
 */
@Component
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "mock", matchIfMissing = true)
public class MockAamCredentialVerifier {

    private final String displayName;
    private final String department;

    public MockAamCredentialVerifier(
            @Value("${app.auth.mock.default-display-name:AAM User}") String displayName,
            @Value("${app.auth.mock.default-department:AIOps}") String department) {
        this.displayName = displayName;
        this.department = department;
    }

    /**
     * 接受任意非空的用户号和票据组合。用户号按原值返回；10 位 AAM 用户号标准化属于
     * AAM 领域规则，不应用于开发环境 ID。
     */
    public AamVerifiedIdentity verify(String aamId, String ticket) {
        if (!StringUtils.hasText(aamId) || !StringUtils.hasText(ticket)) {
            throw new AamAuthenticationException("AAM authentication number and ticket are required");
        }
        // mock 模式不查询用户目录：配置的机构仅作为名称展示，机构 ID 保持为空，
        // 不使用机构名称冒充标识符。
        return new AamVerifiedIdentity(aamId.trim(), displayName, "", department);
    }
}
