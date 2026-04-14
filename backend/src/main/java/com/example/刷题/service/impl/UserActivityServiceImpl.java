package com.example.刷题.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.刷题.entity.UserActivitySession;
import com.example.刷题.mapper.UserActivitySessionMapper;
import com.example.刷题.service.UserActivityService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class UserActivityServiceImpl extends ServiceImpl<UserActivitySessionMapper, UserActivitySession> implements UserActivityService {

    @Override
    public void recordHeartbeat(Long userId, String sessionId, String path, Integer activeSeconds) {
        if (userId == null || !StringUtils.hasText(sessionId) || !StringUtils.hasText(path) || activeSeconds == null) {
            return;
        }

        int safeDuration = Math.max(1, Math.min(activeSeconds, 120));
        UserActivitySession session = new UserActivitySession();
        session.setUserId(userId);
        session.setSessionId(sessionId.trim());
        session.setLastPath(path.trim());
        session.setLastSeenAt(LocalDateTime.now());
        session.setTotalDurationSeconds(safeDuration);
        save(session);
    }

    @Override
    public Integer getAverageDurationSecondsPerUser() {
        return getBaseMapper().selectAverageDurationSecondsPerUser();
    }

    @Override
    public Integer getTrackedUserCount() {
        return getBaseMapper().selectTrackedUserCount();
    }

    @Override
    public List<Map<String, Object>> getDailyDuration(Integer days) {
        return getBaseMapper().selectDailyDuration(days);
    }

    @Override
    public List<Map<String, Object>> getDurationDistribution() {
        return getBaseMapper().selectDurationDistribution();
    }
}
