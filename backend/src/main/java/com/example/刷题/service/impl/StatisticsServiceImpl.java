package com.example.刷题.service.impl;

import com.example.刷题.entity.PracticeRecord;
import com.example.刷题.mapper.PracticeRecordMapper;
import com.example.刷题.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/**
 * 统计服务实现类
 */
@Service
public class StatisticsServiceImpl implements StatisticsService {
    
    @Autowired
    private PracticeRecordMapper practiceRecordMapper;
    
    @Override
    public Integer getTotalQuestions(Long userId) {
        return practiceRecordMapper.selectTotalQuestions(userId);
    }
    
    @Override
    public Double getTotalCorrectRate(Long userId) {
        Integer totalCorrect = practiceRecordMapper.selectTotalCorrect(userId);
        Integer totalQuestions = practiceRecordMapper.selectTotalQuestions(userId);
        
        if (totalQuestions == null || totalQuestions == 0) {
            return 0.0;
        }
        
        return Math.round(totalCorrect * 10000.0 / totalQuestions) / 100.0;
    }
    
    @Override
    public Integer getContinuousDays(Long userId) {
        return practiceRecordMapper.selectContinuousDays(userId);
    }
    
    @Override
    public List<Map<String, Object>> getCorrectRateByCategory(Long userId) {
        return practiceRecordMapper.selectCorrectRateByCategory(userId);
    }
    
    @Override
    public List<PracticeRecord> getRecentRecords(Long userId, Integer limit) {
        return practiceRecordMapper.selectRecentRecords(userId, limit);
    }
    
    @Override
    public List<Map<String, Object>> getDailyCorrectRate(Long userId, Integer days) {
        return practiceRecordMapper.selectDailyCorrectRate(userId, days);
    }
    
    @Override
    public void createRecord(PracticeRecord record) {
        practiceRecordMapper.insert(record);
    }
}
