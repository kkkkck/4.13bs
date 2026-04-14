package com.example.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SecurityUtils {

    private final Long superAdminId;

    public SecurityUtils(@Value("${app.security.super-admin-id:1}") Long superAdminId) {
        this.superAdminId = superAdminId;
    }

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new InsufficientAuthenticationException("Unauthorized");
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            throw new InsufficientAuthenticationException("Invalid authentication state");
        }
    }

    public boolean isSuperAdmin() {
        try {
            return Objects.equals(getCurrentUserId(), superAdminId);
        } catch (InsufficientAuthenticationException ex) {
            return false;
        }
    }

    public void ensureSuperAdmin() {
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("Only super admin can manage user permissions");
        }
    }
}
