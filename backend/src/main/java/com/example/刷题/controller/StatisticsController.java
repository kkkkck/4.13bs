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

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {
    
    @Autowired
    private StatisticsService statisticsService;
    
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        // 首页/个人中心的统计概览：累计题数、正确率、连续学习天数。
        Long userId = getCurrentUserId();
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalQuestions", statisticsService.getTotalQuestions(userId));
        result.put("totalCorrectRate", statisticsService.getTotalCorrectRate(userId));
        result.put("continuousDays", statisticsService.getContinuousDays(userId));
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/category-rate")
    public ResponseEntity<List<Map<String, Object>>> getCategoryCorrectRate() {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(statisticsService.getCorrectRateByCategory(userId));
    }
    
    @GetMapping("/daily-rate")
    public ResponseEntity<List<Map<String, Object>>> getDailyCorrectRate(
            @RequestParam(defaultValue = "7") Integer days) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(statisticsService.getDailyCorrectRate(userId, days));
    }
    
    @GetMapping("/history")
    public ResponseEntity<List<PracticeRecord>> getHistory(
            @RequestParam(defaultValue = "10") Integer limit) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(statisticsService.getRecentRecords(userId, limit));
    }
    
    @PostMapping("/record")
    public ResponseEntity<Map<String, Object>> createRecord(@RequestBody PracticeRecord record) {
        // 用户完成或离开练习页时，前端会写入一条练习记录，用来支撑学习统计图表。
        Long userId = getCurrentUserId();
        record.setUserId(userId);
        statisticsService.createRecord(record);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "练习记录创建成功");
        
        return ResponseEntity.ok(result);
    }
    
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
