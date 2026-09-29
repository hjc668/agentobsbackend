package com.icbc.aiops.langfuse.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentAamUser {
    private CurrentAamUser() { }
    public static String aamId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AamUserPrincipal)) {
            throw new AamAuthenticationException("AAM authentication is required");
        }
        return ((AamUserPrincipal) authentication.getPrincipal()).getAamId();
    }
}
