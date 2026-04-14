package com.example.刷题.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提交答案请求DTO
 */
@Data
public class SubmitAnswerRequest {
    
    /**
     * 用户提交的答案
     */
    @NotBlank(message = "答案不能为空")
    private String answer;
}
