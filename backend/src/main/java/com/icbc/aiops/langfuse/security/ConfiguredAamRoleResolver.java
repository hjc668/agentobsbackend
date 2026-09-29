package com.icbc.aiops.langfuse.security;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 根据用户号配置白名单解析 ADMIN 角色。
 *
 * <p>这是<strong>本地/mock</strong>角色解析器。{@code aam} 模式改用
 * {@link TableAamRoleResolver}，因为配置文件中的白名单不便于运维审计和动态调整。
 * 未知用户始终使用 {@link AamRole#VIEW}。
 */
public final class ConfiguredAamRoleResolver implements AamRoleResolver {

    private final Set<String> adminUsers;

    public ConfiguredAamRoleResolver(String commaSeparatedAdminUsers) {
        this.adminUsers = new LinkedHashSet<String>();
        if (commaSeparatedAdminUsers != null) {
            Arrays.stream(commaSeparatedAdminUsers.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .forEach(adminUsers::add);
        }
    }

    @Override
    public AamRole resolve(String aamUserNo) {
        return adminUsers.contains(aamUserNo) ? AamRole.ADMIN : AamRole.VIEW;
    }
}
