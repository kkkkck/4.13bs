package com.example.刷题.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.刷题.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface UserService extends IService<User> {

    User findByEmail(String email);

    User findByNickname(String nickname);

    User register(String email, String password, String nickname);

    User login(String account, String password);

    User getActiveUserById(Long userId);

    User updateProfile(Long userId, String nickname, String email, String verificationCode);

    User updateAvatar(Long userId, MultipartFile file);

    User removeAvatar(Long userId);

    void changePassword(Long userId, String currentPassword, String newPassword);

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
