package com.example.刷题.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminUserBatchStatusRequest {

    private List<Long> userIds;

    private Integer status;
}

