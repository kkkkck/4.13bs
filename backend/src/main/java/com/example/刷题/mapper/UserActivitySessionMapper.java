package com.example.刷题.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.刷题.entity.UserActivitySession;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface UserActivitySessionMapper extends BaseMapper<UserActivitySession> {

    Integer selectAverageDurationSecondsPerUser();

    Integer selectTrackedUserCount();

    List<Map<String, Object>> selectDailyDuration(@Param("days") Integer days);

    List<Map<String, Object>> selectDurationDistribution();

    List<Map<String, Object>> selectUserActivitySummaryByUserIds(@Param("userIds") List<Long> userIds);
}
