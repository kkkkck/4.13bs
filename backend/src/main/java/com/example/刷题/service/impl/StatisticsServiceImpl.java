package com.example.刷题.service.impl;

import com.example.刷题.entity.PracticeRecord;
import com.example.刷题.mapper.PracticeRecordMapper;
import com.example.刷题.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
        int safeDays = days == null || days < 1 ? 7 : Math.min(days, 30);
        Map<LocalDate, Map<String, Object>> existingRows = practiceRecordMapper
                .selectDailyCorrectRate(userId, safeDays)
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        item -> parseDate(item.get("date")),
                        item -> item,
                        (left, right) -> right
                ));

        LocalDate startDate = LocalDate.now().minusDays(safeDays - 1L);
        return java.util.stream.IntStream.range(0, safeDays)
                .mapToObj(startDate::plusDays)
                .map(date -> normalizeDailyRow(date, existingRows.get(date)))
                .toList();
    }
    
    @Override
    public void createRecord(PracticeRecord record) {
        practiceRecordMapper.insert(record);
    }

    private Map<String, Object> normalizeDailyRow(LocalDate date, Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("date", date.toString());
        normalized.put("correctCount", row == null ? 0 : numberValue(row.get("correctCount")));
        normalized.put("totalCount", row == null ? 0 : numberValue(row.get("totalCount")));
        normalized.put("correctRate", row == null ? 0 : numberValue(row.get("correctRate")));
        return normalized;
    }

    private LocalDate parseDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private Number numberValue(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
