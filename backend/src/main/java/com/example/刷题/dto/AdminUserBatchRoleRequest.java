package com.example.刷题.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminUserBatchRoleRequest {

    private List<Long> userIds;

    private Integer role;
}

