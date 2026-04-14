package com.example.刷题.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.刷题.entity.User;

import java.util.List;
import java.util.Map;

public interface UserService extends IService<User> {

    User findByEmail(String email);

    User findByNickname(String nickname);

    User register(String email, String password, String nickname);

    User login(String account, String password);

    User getActiveUserById(Long userId);

    Map<String, Object> getUserList(
            Integer page,
            Integer size,
            String keyword,
            Integer role,
            Integer status,
            String activityStatus,
            String sortField,
            String sortOrder
    );

    User adminUpdateUser(Long id, String nickname, Integer role, Integer status, Long operatorId);

    int adminBatchUpdateStatus(List<Long> userIds, Integer status, Long operatorId);

    int adminBatchUpdateRole(List<Long> userIds, Integer role, Long operatorId);
}
