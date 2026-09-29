package com.icbc.aiops.langfuse.security;

/** 行内 UniformTeller 服务返回的可信用户信息。 */
public final class AamUserInfo {
    private final String userId;
    private final String displayName;
    private final String notesId;
    private final String departmentId;
    private final String departmentName;

    public AamUserInfo(String userId, String displayName, String notesId,
            String departmentId, String departmentName) {
        this.userId = userId;
        this.displayName = displayName;
        this.notesId = notesId;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
    }

    public String getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getNotesId() { return notesId; }
    public String getDepartmentId() { return departmentId; }
    public String getDepartmentName() { return departmentName; }
}
