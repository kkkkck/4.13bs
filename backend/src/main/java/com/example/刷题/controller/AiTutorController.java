package com.example.刷题.controller;

import com.example.刷题.common.Result;
import com.example.刷题.dto.AiTutorRequest;
import com.example.刷题.dto.AiTutorResponse;
import com.example.刷题.service.AiTutorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiTutorController {
    private final AiTutorService aiTutorService;

    @PostMapping("/tutor")
    public Result<AiTutorResponse> askTutor(@Valid @RequestBody AiTutorRequest request) {
        // 前端在答题结果页点击 DeepSeek 时会请求这里；真正调用 Ollama 的逻辑在 AiTutorServiceImpl。
        return Result.success(aiTutorService.ask(request));
    }
}
