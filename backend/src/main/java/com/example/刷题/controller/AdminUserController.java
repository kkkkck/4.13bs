package com.example.刷题.controller;

import com.example.support.SecurityUtils;
import com.example.刷题.dto.AdminUserBatchRoleRequest;
import com.example.刷题.dto.AdminUserBatchStatusRequest;
import com.example.刷题.dto.AdminUserUpdateRequest;
import com.example.刷题.entity.User;
import com.example.刷题.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;
    private final SecurityUtils securityUtils;

    public AdminUserController(UserService userService, SecurityUtils securityUtils) {
        this.userService = userService;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String activityStatus,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String sortBy) {
        securityUtils.ensureSuperAdmin();
        String resolvedSortField = sortField;
        String resolvedSortOrder = sortOrder;
        if ((resolvedSortField == null || resolvedSortField.isBlank()) && sortBy != null && !sortBy.isBlank()) {
            String normalized = sortBy.trim();
            if (normalized.endsWith("Desc")) {
                resolvedSortOrder = "desc";
                resolvedSortField = normalized.substring(0, normalized.length() - 4);
            } else if (normalized.endsWith("Asc")) {
                resolvedSortOrder = "asc";
                resolvedSortField = normalized.substring(0, normalized.length() - 3);
            } else if (normalized.contains(":")) {
                String[] parts = normalized.split(":", 2);
                resolvedSortField = parts[0];
                resolvedSortOrder = parts.length > 1 ? parts[1] : resolvedSortOrder;
            } else {
                resolvedSortField = normalized;
            }
        }
        return ResponseEntity.ok(
                userService.getUserList(page, size, keyword, role, status, activityStatus, resolvedSortField, resolvedSortOrder)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody AdminUserUpdateRequest request) {
        securityUtils.ensureSuperAdmin();
        Long operatorId = securityUtils.getCurrentUserId();
        User user = userService.adminUpdateUser(
                id,
                request.getNickname(),
                request.getRole(),
                request.getStatus(),
                operatorId
        );
        return ResponseEntity.ok(user);
    }

    @PutMapping("/batch/status")
    public ResponseEntity<Map<String, Object>> batchUpdateStatus(@RequestBody AdminUserBatchStatusRequest request) {
        securityUtils.ensureSuperAdmin();
        Long operatorId = securityUtils.getCurrentUserId();
        int requestedCount = request.getUserIds() == null ? 0 : request.getUserIds().size();
        int updatedCount = userService.adminBatchUpdateStatus(request.getUserIds(), request.getStatus(), operatorId);

        Map<String, Object> result = new HashMap<>();
        result.put("updatedCount", updatedCount);
        result.put("requestedCount", requestedCount);
        result.put("skippedCount", Math.max(requestedCount - updatedCount, 0));
        result.put("status", request.getStatus());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/batch/role")
    public ResponseEntity<Map<String, Object>> batchUpdateRole(@RequestBody AdminUserBatchRoleRequest request) {
        securityUtils.ensureSuperAdmin();
        Long operatorId = securityUtils.getCurrentUserId();
        int requestedCount = request.getUserIds() == null ? 0 : request.getUserIds().size();
        int updatedCount = userService.adminBatchUpdateRole(request.getUserIds(), request.getRole(), operatorId);

        Map<String, Object> result = new HashMap<>();
        result.put("updatedCount", updatedCount);
        result.put("requestedCount", requestedCount);
        result.put("skippedCount", Math.max(requestedCount - updatedCount, 0));
        result.put("role", request.getRole());
        return ResponseEntity.ok(result);
    }
}
