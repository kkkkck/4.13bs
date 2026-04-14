package com.example.刷题.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.刷题.entity.UserActivitySession;

import java.util.List;
import java.util.Map;

public interface UserActivityService extends IService<UserActivitySession> {

    void recordHeartbeat(Long userId, String sessionId, String path, Integer activeSeconds);

    Integer getAverageDurationSecondsPerUser();

    Integer getTrackedUserCount();

    List<Map<String, Object>> getDailyDuration(Integer days);

    List<Map<String, Object>> getDurationDistribution();
}
