package com.icbc.aiops.langfuse.security;

import org.springframework.util.StringUtils;

/**
 * 将 AAM 用户号标准化为角色查询和审计使用的内部格式。
 *
 * <p>该规则按参考实现 {@code LoginController.initSession} 迁移：长度为<strong>10 位</strong>
 * 的用户号移除首字符，得到 9 位用户号。更短或更长的值保持不变；这里明确执行精确长度判断，
 * 而不是通用的“截取为 9 位”规则。
 *
 * <p>保持为无外部依赖的独立类，以便在没有行内 Hermes 制品时也能测试该规则。
 */
public final class AamUserIdNormalizer {

    /** 需要移除首字符时的用户号长度。 */
    static final int PREFIXED_USER_NO_LENGTH = 10;

    private AamUserIdNormalizer() { }

    /**
     * @return 标准化后的用户号，保证不为空。
     * @throws AamAuthenticationException 输入为 null 或空白时抛出，避免缺失身份静默变成有效 principal。
     */
    public static String normalize(String rawUserNo) {
        if (!StringUtils.hasText(rawUserNo)) {
            throw new AamAuthenticationException("AAM user identity is missing");
        }
        String userNo = rawUserNo.trim();
        if (userNo.length() == PREFIXED_USER_NO_LENGTH) {
            // 与参考实现一致：substring(1, 10) 取索引 1 到 9 的字符。
            userNo = userNo.substring(1, PREFIXED_USER_NO_LENGTH);
        }
        if (!StringUtils.hasText(userNo)) {
            throw new AamAuthenticationException("AAM user identity is empty after normalization");
        }
        return userNo;
    }
}
