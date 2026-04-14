package com.example.刷题.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.刷题.common.Result;
import com.example.刷题.entity.WrongQuestion;
import com.example.刷题.service.WrongQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.example.support.SecurityUtils;

/**
 * 错题控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/wrong-questions")
@RequiredArgsConstructor
public class WrongQuestionController {
    
    private final WrongQuestionService wrongQuestionService;
    private final SecurityUtils securityUtils;
    
    /**
     * 获取错题本列表
     * GET /api/wrong-questions?page=1
     */
    @GetMapping
    public Result<IPage<WrongQuestion>> getWrongQuestions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("请求获取错题本列表，page: {}, size: {}", page, size);
        Long userId = securityUtils.getCurrentUserId();
        IPage<WrongQuestion> wrongQuestions = wrongQuestionService.getWrongQuestions(userId, page, size);
        return Result.success(wrongQuestions);
    }
    
    /**
     * 添加错题
     * POST /api/wrong-questions
     */
    @PostMapping
    public Result<Boolean> addWrongQuestion(
            @RequestParam Long questionId,
            @RequestParam String userAnswer) {
        log.info("请求添加错题，questionId: {}", questionId);
        Long userId = securityUtils.getCurrentUserId();
        boolean result = wrongQuestionService.addWrongQuestion(userId, questionId, userAnswer);
        if (result) {
            return Result.success(true);
        }
        return Result.fail("添加错题失败");
    }
    
    /**
     * 移除错题
     * DELETE /api/wrong-questions/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> removeWrongQuestion(@PathVariable Long id) {
        log.info("请求移除错题，id: {}", id);
        Long userId = securityUtils.getCurrentUserId();
        boolean result = wrongQuestionService.removeWrongQuestion(userId, id);
        if (result) {
            return Result.success(true);
        }
        return Result.fail("移除错题失败");
    }
}
