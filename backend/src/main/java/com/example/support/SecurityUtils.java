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

    // 这个工具类专门从 Spring Security 上下文里取“当前登录用户”。
    // 上下文里的用户 id 是 JwtAuthenticationFilter 从 token 中解析后放进去的。
    private final Long superAdminId;

    public SecurityUtils(@Value("${app.security.super-admin-id:1}") Long superAdminId) {
        this.superAdminId = superAdminId;
    }

    public Long getCurrentUserId() {
        // Controller 不需要自己解析 token，直接调用这个方法就能拿到当前用户 id。
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
        // 用户管理等高危操作只允许超级管理员执行，避免普通管理员误改权限。
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("Only super admin can manage user permissions");
        }
    }
}
