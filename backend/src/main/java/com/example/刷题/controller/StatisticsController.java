package com.example.刷题.controller;

import com.example.刷题.entity.PracticeRecord;
import com.example.刷题.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计控制器
 */
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {
    
    @Autowired
    private StatisticsService statisticsService;
    
    /**
     * 获取用户统计概览
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        Long userId = getCurrentUserId();
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalQuestions", statisticsService.getTotalQuestions(userId));
        result.put("totalCorrectRate", statisticsService.getTotalCorrectRate(userId));
        result.put("continuousDays", statisticsService.getContinuousDays(userId));
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取各分类正确率统计
     */
    @GetMapping("/category-rate")
    public ResponseEntity<List<Map<String, Object>>> getCategoryCorrectRate() {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(statisticsService.getCorrectRateByCategory(userId));
    }
    
    /**
     * 获取近期正确率趋势
     */
    @GetMapping("/daily-rate")
    public ResponseEntity<List<Map<String, Object>>> getDailyCorrectRate(
            @RequestParam(defaultValue = "7") Integer days) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(statisticsService.getDailyCorrectRate(userId, days));
    }
    
    /**
     * 获取练习历史记录
     */
    @GetMapping("/history")
    public ResponseEntity<List<PracticeRecord>> getHistory(
            @RequestParam(defaultValue = "10") Integer limit) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(statisticsService.getRecentRecords(userId, limit));
    }
    
    /**
     * 创建练习记录
     */
    @PostMapping("/record")
    public ResponseEntity<Map<String, Object>> createRecord(@RequestBody PracticeRecord record) {
        Long userId = getCurrentUserId();
        record.setUserId(userId);
        statisticsService.createRecord(record);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "练习记录创建成功");
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 从Security上下文获取当前用户ID
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
