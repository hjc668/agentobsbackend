package com.icbc.aiops.langfuse.security;

import com.icbc.aiops.langfuse.postgres.mapper.UserRoleMapper;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * 从内部 {@code langfuse_user_roles} 表解析角色；该表是 {@code aam} 认证模式下的生产可信数据源。
 *
 * <p>所有不明确情况均按最小权限降级为 {@link AamRole#VIEW}，包括记录不存在、值不在
 * VIEW/ADMIN 白名单中或数据库不可达。该实现不会提升权限，也不会因授权查询失败而中断
 * 其他方面均有效的 AAM 登录。
 */
public final class TableAamRoleResolver implements AamRoleResolver {

    private static final Logger log = LoggerFactory.getLogger(TableAamRoleResolver.class);

    private final UserRoleMapper mapper;

    public TableAamRoleResolver(UserRoleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AamRole resolve(String aamUserNo) {
        String roleCode;
        try {
            roleCode = mapper.selectRoleCode(aamUserNo);
        } catch (DataAccessException failure) {
            // 遵循最小权限原则：角色表不可达时不能授予 ADMIN，也不应将有效的 AAM 登录变成认证失败。
            log.error("Role lookup failed for aam_user_no={}; falling back to VIEW", aamUserNo, failure);
            return AamRole.VIEW;
        }
        if (roleCode == null) {
            return AamRole.VIEW;
        }
        String normalized = roleCode.trim().toUpperCase(Locale.ROOT);
        if (AamRole.ADMIN.name().equals(normalized)) {
            return AamRole.ADMIN;
        }
        if (!AamRole.VIEW.name().equals(normalized)) {
            log.warn("Ignoring unknown role_code '{}' for aam_user_no={}; treated as VIEW",
                    roleCode, aamUserNo);
        }
        return AamRole.VIEW;
    }
}
