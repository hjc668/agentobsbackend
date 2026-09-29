package com.icbc.aiops.langfuse.security;

/**
 * AAM 票据验证成功后的身份结果：包括从已验证凭据取得的用户号，以及加密 UniformTeller
 * 服务返回的用户属性。
 *
 * <p>只有 {@code rawUserNo} 是权威身份标识。显示名称和机构信息属于展示属性，
 * 按可信服务的返回值原样携带，用于生成登录响应。
 */
public final class AamVerifiedIdentity {

    private final String rawUserNo;
    private final String displayName;
    private final String notesId;
    private final String departmentId;
    private final String departmentName;

    public AamVerifiedIdentity(String rawUserNo, String displayName, String departmentId,
            String departmentName) {
        this(rawUserNo, displayName, null, departmentId, departmentName);
    }

    public AamVerifiedIdentity(String rawUserNo, String displayName, String notesId,
            String departmentId, String departmentName) {
        this.rawUserNo = rawUserNo;
        this.displayName = displayName;
        this.notesId = notesId;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
    }

    public AamVerifiedIdentity(String rawUserNo) {
        this(rawUserNo, null, null, null);
    }

    public String getRawUserNo() { return rawUserNo; }
    public String getDisplayName() { return displayName; }
    public String getNotesId() { return notesId; }
    public String getDepartmentId() { return departmentId; }
    public String getDepartmentName() { return departmentName; }
}
