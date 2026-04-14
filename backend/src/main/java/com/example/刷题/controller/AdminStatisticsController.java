package com.example.刷题.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.刷题.entity.Category;
import com.example.刷题.mapper.CategoryMapper;
import com.example.刷题.mapper.QuestionMapper;
import com.example.刷题.mapper.UserMapper;
import com.example.刷题.service.UserActivityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/statistics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatisticsController {

    private final QuestionMapper questionMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final UserActivityService userActivityService;

    public AdminStatisticsController(
            QuestionMapper questionMapper,
            CategoryMapper categoryMapper,
            UserMapper userMapper,
            UserActivityService userActivityService
    ) {
        this.questionMapper = questionMapper;
        this.categoryMapper = categoryMapper;
        this.userMapper = userMapper;
        this.userActivityService = userActivityService;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getQuestionOverview() {
        Map<String, Object> result = new HashMap<>();

        Long totalQuestions = questionMapper.selectCount(null);
        result.put("totalQuestions", totalQuestions);
        result.put("categoryStats", questionMapper.selectQuestionCountByCategory());
        result.put("typeStats", questionMapper.selectQuestionCountByType());

        Long totalCategories = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>().and(wrapper ->
                        wrapper.isNull(Category::getParentId).or().eq(Category::getParentId, 0L)
                )
        );
        result.put("totalCategories", totalCategories);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUserStatistics() {
        Map<String, Object> result = new HashMap<>();

        Long totalUsers = userMapper.selectCount(null);
        result.put("totalUsers", totalUsers);

        Integer averageActiveDurationSeconds = userActivityService.getAverageDurationSecondsPerUser();
        Integer trackedUsers = userActivityService.getTrackedUserCount();
        List<Map<String, Object>> dailyActiveDuration = userActivityService.getDailyDuration(7);
        List<Map<String, Object>> durationDistribution = userActivityService.getDurationDistribution();

        result.put("averageActiveDurationSeconds", averageActiveDurationSeconds == null ? 0 : averageActiveDurationSeconds);
        result.put("trackedUsers", trackedUsers == null ? 0 : trackedUsers);
        result.put("dailyActiveDuration", dailyActiveDuration);
        result.put("durationDistribution", durationDistribution);

        return ResponseEntity.ok(result);
    }
}
