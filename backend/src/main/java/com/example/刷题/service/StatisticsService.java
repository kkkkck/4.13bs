package com.example.刷题.service;

import com.example.刷题.entity.PracticeRecord;
import java.util.List;
import java.util.Map;

/**
 * 统计服务接口
 */
public interface StatisticsService {
    
    /**
     * 获取用户总刷题量
     */
    Integer getTotalQuestions(Long userId);
    
    /**
     * 获取用户总正确率
     */
    Double getTotalCorrectRate(Long userId);
    
    /**
     * 获取用户连续刷题天数
     */
    Integer getContinuousDays(Long userId);
    
    /**
     * 获取各分类正确率统计
     */
    List<Map<String, Object>> getCorrectRateByCategory(Long userId);
    
    /**
     * 获取近期练习记录
     */
    List<PracticeRecord> getRecentRecords(Long userId, Integer limit);
    
    /**
     * 获取近期正确率趋势
     */
    List<Map<String, Object>> getDailyCorrectRate(Long userId, Integer days);
    
    /**
     * 创建练习记录
     */
    void createRecord(PracticeRecord record);
}
