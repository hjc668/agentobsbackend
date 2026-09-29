package com.icbc.aiops.langfuse.api;

import com.icbc.aiops.langfuse.security.AamUserPrincipal;
import java.util.Collections;
import java.util.List;

/**
 * {@code /aam/login/auth} 成功响应的数据结构，与参考前端保持一致。
 *
 * <p>{@code menu} 始终为空：本应用导航由前端固定配置，VIEW/ADMIN 权限仍由服务端
 * Spring Security 强制校验。
 */
public class AamLoginResponse {

    private final AamLoginUser user;
    private final List<Object> menu;

    private AamLoginResponse(AamLoginUser user, List<Object> menu) {
        this.user = user;
        this.menu = menu;
    }

    public static AamLoginResponse of(AamUserPrincipal principal, String csrfToken) {
        return new AamLoginResponse(new AamLoginUser(principal, csrfToken), Collections.emptyList());
    }

    public AamLoginUser getUser() { return user; }
    public List<Object> getMenu() { return menu; }

    /** 登录响应中的用户信息。 */
    public static class AamLoginUser {
        private final String userId;
        private final String userName;
        private final String notesId;
        private final String departmentId;
        private final String departmentName;
        private final String currentRoleId;
        private final List<Role> roles;
        private final String csrfToken;

        AamLoginUser(AamUserPrincipal principal, String csrfToken) {
            this.userId = principal.getAamId();
            this.userName = principal.getDisplayName();
            this.notesId = principal.getNotesId();
            // 机构信息来自 UniformTeller 的加密响应。
            this.departmentId = principal.getDepartmentId();
            this.departmentName = principal.getDepartmentName();
            this.currentRoleId = principal.getRole().name();
            this.roles = Collections.singletonList(new Role(principal.getRole().name(),
                    principal.getRole() == com.icbc.aiops.langfuse.security.AamRole.ADMIN ? "管理员" : "查看者"));
            this.csrfToken = csrfToken;
        }

        public String getUserId() { return userId; }
        public String getUserName() { return userName; }
        public String getNotesId() { return notesId; }
        public String getDepartmentId() { return departmentId; }
        public String getDepartmentName() { return departmentName; }
        public String getCurrentRoleId() { return currentRoleId; }
        public List<Role> getRoles() { return roles; }
        public String getCsrfToken() { return csrfToken; }
    }

    /** 登录响应中的角色信息。 */
    public static class Role {
        private final String roleId;
        private final String roleName;

        Role(String roleId, String roleName) {
            this.roleId = roleId;
            this.roleName = roleName;
        }

        public String getRoleId() { return roleId; }
        public String getRoleName() { return roleName; }
    }
}
