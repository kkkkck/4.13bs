package com.example.刷题.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActivityHeartbeatRequest {

    @NotBlank(message = "sessionId 不能为空")
    @Size(max = 64, message = "sessionId 长度不能超过 64")
    private String sessionId;

    @NotBlank(message = "path 不能为空")
    @Size(max = 255, message = "path 长度不能超过 255")
    private String path;

    @Min(value = 1, message = "activeSeconds 不能小于 1")
    @Max(value = 120, message = "activeSeconds 不能超过 120")
    private Integer activeSeconds;
}
