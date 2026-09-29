package com.icbc.aiops.langfuse.security;

/** 通过加密的 UniformTeller 服务查询已认证的 AAM 用户。 */
public interface AamUserInfoService {
    AamUserInfo query(String normalizedUserNo);
}
