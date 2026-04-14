package com.example.刷题.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.刷题.common.Result;
import com.example.刷题.dto.QuestionBatchRequest;
import com.example.刷题.entity.Question;
import com.example.刷题.exception.BusinessException;
import com.example.刷题.service.QuestionService;
import com.example.刷题.dto.SubmitAnswerRequest;
import com.example.刷题.dto.SubmitAnswerResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 题目控制器
 * 实现刷题网站核心功能
 */
@Slf4j
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    /**
     * 获取题目详情（使用三级缓存）
     * GET /api/questions/{id}
     */
    @GetMapping("/{id}")
    public Result<Question> getQuestionById(@PathVariable Long id) {
        log.info("请求获取题目详情，id: {}", id);
        Question question = questionService.getByIdWithCache(id);
        if (question != null) {
            return Result.success(question);
        }
        return Result.fail("题目不存在");
    }
    
    /**
     * 按分类获取题目列表（使用三级缓存）
     * GET /api/questions?categoryId=xxx&page=1&size=20
     */
    @GetMapping("/batch")
    public Result<List<Question>> getQuestionsByIds(@RequestParam String ids) {
        return Result.success(questionService.getByIds(parseQuestionIds(ids)));
    }

    @PostMapping("/batch")
    public Result<List<Question>> getQuestionsByIds(@RequestBody QuestionBatchRequest request) {
        if (request == null || request.getIds() == null) {
            throw new BusinessException("题目ID不能为空");
        }
        return Result.success(questionService.getByIds(request.getIds().stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList())));
    }

    private List<Long> parseQuestionIds(String ids) {
        List<Long> questionIds;
        try {
            questionIds = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Long::valueOf)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (NumberFormatException exception) {
            throw new BusinessException("题目ID格式不正确");
        }
        return questionIds;
    }

    @GetMapping
    public Result<IPage<Question>> getQuestions(
            @RequestParam Long categoryId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Integer sourceType) {
        log.info("请求获取分类题目列表，categoryId: {}, page: {}, size: {}, sourceType: {}", categoryId, page, size, sourceType);
        IPage<Question> questions = questionService.getQuestionsByCategory(categoryId, page, size, sourceType);
        return Result.success(questions);
    }
    
    /**
     * 提交答案并判题
     * POST /api/questions/{id}/submit
     */
    @PostMapping("/{id}/submit")
    public Result<SubmitAnswerResponse> submitAnswer(
            @PathVariable Long id,
            @RequestBody SubmitAnswerRequest request) {
        log.info("请求提交答案，questionId: {}", id);
        SubmitAnswerResponse response = questionService.submitAnswer(id, request);
        return Result.success(response);
    }

    /**
     * 更新题目（自动清除缓存）
     */
    @PutMapping
    public Result<Boolean> updateQuestion(@RequestBody Question question) {
        log.info("请求更新题目，id: {}", question.getId());
        boolean result = questionService.updateByIdWithCache(question);
        if (result) {
            return Result.success(true);
        }
        return Result.fail("更新失败");
    }

    /**
     * 创建题目
     */
    @PostMapping
    public Result<Question> createQuestion(@RequestBody Question question) {
        log.info("请求创建题目");
        questionService.save(question);
        return Result.success(question);
    }

    /**
     * 删除题目
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteQuestion(@PathVariable Long id) {
        log.info("请求删除题目，id: {}", id);
        boolean result = questionService.removeById(id);
        if (result) {
            return Result.success(true);
        }
        return Result.fail("删除失败");
    }
}
