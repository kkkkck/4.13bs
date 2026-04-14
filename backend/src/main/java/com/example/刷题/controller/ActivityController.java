package com.example.刷题.controller;

import com.example.support.SecurityUtils;
import com.example.刷题.dto.ActivityHeartbeatRequest;
import com.example.刷题.service.UserActivityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    private final UserActivityService userActivityService;
    private final SecurityUtils securityUtils;

    public ActivityController(UserActivityService userActivityService, SecurityUtils securityUtils) {
        this.userActivityService = userActivityService;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(@Valid @RequestBody ActivityHeartbeatRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        userActivityService.recordHeartbeat(userId, request.getSessionId(), request.getPath(), request.getActiveSeconds());
        return ResponseEntity.ok(Map.of("success", true));
    }
}
