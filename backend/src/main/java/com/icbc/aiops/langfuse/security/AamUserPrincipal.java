package com.icbc.aiops.langfuse.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AamUserPrincipal implements UserDetails {
    private final String aamId;
    private final String displayName;
    private final String notesId;
    private final String departmentId;
    private final String departmentName;
    private final AamRole role;

    public AamUserPrincipal(String aamId, String displayName, String departmentId,
            String departmentName, AamRole role) {
        this(aamId, displayName, null, departmentId, departmentName, role);
    }

    public AamUserPrincipal(String aamId, String displayName, String notesId,
            String departmentId, String departmentName, AamRole role) {
        this.aamId = aamId;
        this.displayName = displayName;
        this.notesId = notesId;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.role = role;
    }

    public String getAamId() { return aamId; }
    public String getDisplayName() { return displayName; }
    public String getNotesId() { return notesId; }
    public String getDepartmentId() { return departmentId; }
    public String getDepartmentName() { return departmentName; }
    public AamRole getRole() { return role; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> values = new ArrayList<GrantedAuthority>();
        values.add(new SimpleGrantedAuthority("ROLE_VIEW"));
        if (role == AamRole.ADMIN) values.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        return values;
    }
    @Override public String getPassword() { return ""; }
    @Override public String getUsername() { return getAamId(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
