package com.example.刷题.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.刷题.entity.PracticeRecord;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/**
 * 练习记录Mapper接口
 */
public interface PracticeRecordMapper extends BaseMapper<PracticeRecord> {
    
    /**
     * 统计用户总刷题量
     */
    Integer selectTotalQuestions(@Param("userId") Long userId);
    
    /**
     * 统计用户总答对题数
     */
    Integer selectTotalCorrect(@Param("userId") Long userId);
    
    /**
     * 统计用户连续刷题天数
     */
    Integer selectContinuousDays(@Param("userId") Long userId);
    
    /**
     * 按分类统计正确率
     */
    List<Map<String, Object>> selectCorrectRateByCategory(@Param("userId") Long userId);
    
    /**
     * 获取近期练习记录（按时间倒序）
     */
    List<PracticeRecord> selectRecentRecords(@Param("userId") Long userId, @Param("limit") Integer limit);
    
    /**
     * 获取近期正确率趋势（按日期分组）
     */
    List<Map<String, Object>> selectDailyCorrectRate(@Param("userId") Long userId, @Param("days") Integer days);
}
