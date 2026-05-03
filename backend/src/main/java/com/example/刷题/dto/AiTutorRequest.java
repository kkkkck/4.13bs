package com.example.刷题.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AiTutorRequest {
    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    private String userAnswer;

    @NotBlank(message = "提问内容不能为空")
    @Size(max = 1000, message = "提问内容不能超过1000字")
    private String message;

    private List<AiTutorMessage> history;
}
