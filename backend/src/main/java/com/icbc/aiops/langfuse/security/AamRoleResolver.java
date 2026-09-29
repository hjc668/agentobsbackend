package com.icbc.aiops.langfuse.security;

/**
 * 为已认证的 AAM 用户解析 VIEW/ADMIN 角色。
 *
 * <p>AAM 仅作为身份认证源，用于证明用户<em>是谁</em>，不决定用户可以执行哪些操作。
 * 因此角色必须来自系统内部可信数据源，登录请求中携带的角色会被有意忽略。
 */
public interface AamRoleResolver {

    /**
     * @param aamUserNo 已由 {@link AamUserIdNormalizer} 标准化的用户号。
     * @return 解析后的角色；找不到配置时实现必须默认返回 {@link AamRole#VIEW}，
     *         不得失败或提升权限。
     */
    AamRole resolve(String aamUserNo);
}
