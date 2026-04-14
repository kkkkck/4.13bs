package com.example.刷题.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "邮箱或昵称不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;
}
