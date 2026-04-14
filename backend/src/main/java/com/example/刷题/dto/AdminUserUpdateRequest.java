package com.example.刷题.dto;

import lombok.Data;

@Data
public class AdminUserUpdateRequest {
    private String nickname;
    private Integer role;
    private Integer status;
}
