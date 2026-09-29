package com.icbc.aiops.langfuse.api;

import com.icbc.aiops.langfuse.security.AamUserPrincipal;

public class CurrentUserResponse {
    private final boolean authenticated;
    private final String aamId;
    private final String displayName;
    private final String notesId;
    private final String departmentId;
    private final String departmentName;
    private final String role;

    public CurrentUserResponse(AamUserPrincipal principal) {
        this.authenticated = true;
        this.aamId = principal.getAamId();
        this.displayName = principal.getDisplayName();
        this.notesId = principal.getNotesId();
        this.departmentId = principal.getDepartmentId();
        this.departmentName = principal.getDepartmentName();
        this.role = principal.getRole().name();
    }

    public boolean isAuthenticated() { return authenticated; }
    public String getAamId() { return aamId; }
    public String getDisplayName() { return displayName; }
    public String getNotesId() { return notesId; }
    public String getDepartmentId() { return departmentId; }
    public String getDepartmentName() { return departmentName; }
    public String getRole() { return role; }
}
