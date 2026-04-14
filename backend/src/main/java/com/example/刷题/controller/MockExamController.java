package com.example.刷题.controller;

import com.example.刷题.common.Result;
import com.example.刷题.dto.MockExamPaperResponse;
import com.example.刷题.service.QuestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mock-exams")
public class MockExamController {

    private final QuestionService questionService;

    public MockExamController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping("/paper")
    public Result<MockExamPaperResponse> generatePaper(
            @RequestParam(defaultValue = "20") Integer totalQuestions) {
        return Result.success(questionService.generateMockExam(totalQuestions));
    }
}
