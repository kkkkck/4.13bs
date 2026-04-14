package com.example.刷题.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.刷题.entity.User;
import com.example.刷题.exception.BusinessException;
import com.example.刷题.mapper.UserActivitySessionMapper;
import com.example.刷题.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserActivitySessionMapper userActivitySessionMapper;

    @Override
    public User findByEmail(String email) {
        return userMapper.findByEmail(normalizeEmail(email));
    }

    @Override
    public User findByNickname(String nickname) {
        return userMapper.findByNickname(normalizeNickname(nickname));
    }

    @Override
    public User register(String email, String password, String nickname) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedNickname = normalizeNickname(nickname);

        if (findByEmail(normalizedEmail) != null) {
            throw new BusinessException("该邮箱已注册");
        }

        if (!StringUtils.hasText(normalizedNickname)) {
            throw new BusinessException("昵称不能为空");
        }

        if (findByNickname(normalizedNickname) != null) {
            throw new BusinessException("该昵称已被占用");
        }
        String resolvedNickname = normalizedNickname;

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(resolvedNickname);
        user.setRole(0);
        user.setStatus(1);
        user.setEmailVerified(true);
        save(user);
        return getById(user.getId());
    }

    @Override
    public User login(String account, String password) {
        User user = findByAccount(account);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(401, "用户不存在或已被禁用");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(401, "邮箱/昵称或密码错误");
        }

        return user;
    }

    @Override
    public User getActiveUserById(Long userId) {
        User user = getById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(404, "User not found");
        }
        return user;
    }

    @Override
    public Map<String, Object> getUserList(
            Integer page,
            Integer size,
            String keyword,
            Integer role,
            Integer status,
            String activityStatus,
            String sortField,
            String sortOrder
    ) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(User::getEmail, keyword)
                        .or()
                        .like(User::getNickname, keyword))
                .eq(role != null, User::getRole, role)
                .eq(status != null, User::getStatus, status)
                .orderByDesc(User::getCreatedAt);

        List<User> matchedUsers = userMapper.selectList(queryWrapper);

        List<Long> userIds = matchedUsers.stream().map(User::getId).toList();
        Map<Long, Map<String, Object>> activitySummaryMap = userIds.isEmpty()
                ? Map.of()
                : userActivitySessionMapper.selectUserActivitySummaryByUserIds(userIds).stream()
                .collect(Collectors.toMap(
                        item -> Long.parseLong(String.valueOf(item.get("userId"))),
                        item -> item
                ));

        List<Map<String, Object>> records = matchedUsers.stream().map(user -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("email", user.getEmail());
            item.put("nickname", user.getNickname());
            item.put("role", user.getRole());
            item.put("status", user.getStatus());
            item.put("emailVerified", user.getEmailVerified());
            item.put("createdAt", user.getCreatedAt());
            item.put("updatedAt", user.getUpdatedAt());

            Map<String, Object> summary = activitySummaryMap.get(user.getId());
            item.put("lastSeenAt", summary == null ? null : summary.get("lastSeenAt"));
            return item;
        }).filter(item -> matchesActivityStatus(extractDateTime(item.get("lastSeenAt")), activityStatus))
                .sorted(resolveSortComparator(sortField, sortOrder))
                .toList();

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : size;
        int fromIndex = Math.min((safePage - 1) * safeSize, records.size());
        int toIndex = Math.min(fromIndex + safeSize, records.size());

        Map<String, Object> result = new HashMap<>();
        result.put("records", records.subList(fromIndex, toIndex));
        result.put("total", records.size());
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    @Override
    public User adminUpdateUser(Long id, String nickname, Integer role, Integer status, Long operatorId) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException(404, "User not found");
        }

        if (!StringUtils.hasText(nickname)) {
            throw new BusinessException("昵称不能为空");
        }

        if (operatorId != null && operatorId.equals(id)) {
            if (status != null && status != 1) {
                throw new BusinessException("Current admin cannot disable self");
            }
            if (role != null && role != 1) {
                throw new BusinessException("Current admin cannot remove own admin role");
            }
        }

        String normalizedNickname = normalizeNickname(nickname);
        User existing = findByNickname(normalizedNickname);
        if (existing != null && !existing.getId().equals(id)) {
            throw new BusinessException("该昵称已被占用");
        }
        user.setNickname(normalizedNickname);

        if (role != null) {
            user.setRole(role);
        }
        if (status != null) {
            user.setStatus(status);
        }

        updateById(user);
        return getById(id);
    }

    @Override
    public int adminBatchUpdateStatus(List<Long> userIds, Integer status, Long operatorId) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException("请选择要批量处理的用户");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态参数不合法");
        }

        List<Long> validIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();

        if (validIds.isEmpty()) {
            throw new BusinessException("请选择有效的用户");
        }

        int updated = 0;
        for (Long userId : validIds) {
            User target = getById(userId);
            if (target == null) {
                continue;
            }
            if (operatorId != null && operatorId.equals(userId) && status == 0) {
                continue;
            }
            adminUpdateUser(userId, target.getNickname(), target.getRole(), status, operatorId);
            updated += 1;
        }
        return updated;
    }

    @Override
    public int adminBatchUpdateRole(List<Long> userIds, Integer role, Long operatorId) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException("请选择要批量处理的用户");
        }
        if (role == null || (role != 0 && role != 1)) {
            throw new BusinessException("角色参数不合法");
        }

        List<Long> validIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();

        if (validIds.isEmpty()) {
            throw new BusinessException("请选择有效的用户");
        }

        int updated = 0;
        for (Long userId : validIds) {
            User target = getById(userId);
            if (target == null) {
                continue;
            }
            if (operatorId != null && operatorId.equals(userId) && role == 0) {
                continue;
            }
            adminUpdateUser(userId, target.getNickname(), role, target.getStatus(), operatorId);
            updated += 1;
        }
        return updated;
    }

    private User findByAccount(String account) {
        String trimmedAccount = account == null ? "" : account.trim();
        if (!StringUtils.hasText(trimmedAccount)) {
            return null;
        }

        if (trimmedAccount.contains("@")) {
            return findByEmail(trimmedAccount);
        }

        LambdaQueryWrapper<User> nicknameQuery = new LambdaQueryWrapper<User>()
                .eq(User::getNickname, normalizeNickname(trimmedAccount))
                .eq(User::getStatus, 1);

        List<User> nicknameMatches = userMapper.selectList(nicknameQuery);

        if (nicknameMatches.isEmpty()) {
            return null;
        }

        if (nicknameMatches.size() > 1) {
            throw new BusinessException(401, "该昵称存在重复账号，请使用邮箱登录");
        }

        return nicknameMatches.get(0);
    }

    private boolean matchesActivityStatus(LocalDateTime lastSeenAt, String activityStatus) {
        if (!StringUtils.hasText(activityStatus) || "all".equalsIgnoreCase(activityStatus)) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        return switch (activityStatus) {
            case "active24h" -> lastSeenAt != null && !lastSeenAt.isBefore(now.minusHours(24));
            case "active7d" -> lastSeenAt != null && !lastSeenAt.isBefore(now.minusDays(7));
            case "inactive7d" -> lastSeenAt == null || lastSeenAt.isBefore(now.minusDays(7));
            default -> true;
        };
    }

    private Comparator<Map<String, Object>> resolveSortComparator(String sortField, String sortOrder) {
        Comparator<Map<String, Object>> byCreatedAtAsc = Comparator.comparing(
                item -> extractDateTime(item.get("createdAt")),
                Comparator.nullsLast(LocalDateTime::compareTo)
        );
        Comparator<Map<String, Object>> byCreatedAtDesc = Comparator.comparing(
                item -> extractDateTime(item.get("createdAt")),
                Comparator.nullsLast(Comparator.reverseOrder())
        );
        Comparator<Map<String, Object>> byLastSeenAtAsc = Comparator.comparing(
                item -> extractDateTime(item.get("lastSeenAt")),
                Comparator.nullsLast(LocalDateTime::compareTo)
        );
        Comparator<Map<String, Object>> byLastSeenAtDesc = Comparator.comparing(
                item -> extractDateTime(item.get("lastSeenAt")),
                Comparator.nullsLast(Comparator.reverseOrder())
        );
        Comparator<Map<String, Object>> byNicknameAsc = Comparator.comparing(
                item -> String.valueOf(item.get("nickname")),
                Comparator.nullsLast(String::compareToIgnoreCase)
        );
        Comparator<Map<String, Object>> byNicknameDesc = Comparator.comparing(
                item -> String.valueOf(item.get("nickname")),
                Comparator.nullsLast((left, right) -> right.compareToIgnoreCase(left))
        );
        Comparator<Map<String, Object>> byRoleAsc = Comparator.comparing(
                item -> parseInteger(item.get("role")),
                Comparator.nullsLast(Integer::compareTo)
        );
        Comparator<Map<String, Object>> byRoleDesc = Comparator.comparing(
                item -> parseInteger(item.get("role")),
                Comparator.nullsLast(Comparator.reverseOrder())
        );
        Comparator<Map<String, Object>> byStatusAsc = Comparator.comparing(
                item -> parseInteger(item.get("status")),
                Comparator.nullsLast(Integer::compareTo)
        );
        Comparator<Map<String, Object>> byStatusDesc = Comparator.comparing(
                item -> parseInteger(item.get("status")),
                Comparator.nullsLast(Comparator.reverseOrder())
        );

        String normalizedField = normalizeSortField(sortField);
        String normalizedOrder = "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";

        return switch (normalizedField) {
            case "createdAt" -> "asc".equals(normalizedOrder) ? byCreatedAtAsc : byCreatedAtDesc;
            case "lastSeenAt" -> "asc".equals(normalizedOrder) ? byLastSeenAtAsc : byLastSeenAtDesc;
            case "nickname" -> "asc".equals(normalizedOrder) ? byNicknameAsc : byNicknameDesc;
            case "role" -> "asc".equals(normalizedOrder) ? byRoleAsc : byRoleDesc;
            case "status" -> "asc".equals(normalizedOrder) ? byStatusAsc : byStatusDesc;
            default -> byCreatedAtDesc;
        };
    }

    private String normalizeSortField(String sortField) {
        if (!StringUtils.hasText(sortField)) {
            return "createdAt";
        }
        return switch (sortField.trim()) {
            case "created", "createdAt" -> "createdAt";
            case "lastSeen", "lastSeenAt" -> "lastSeenAt";
            case "nickname" -> "nickname";
            case "role" -> "role";
            case "status" -> "status";
            default -> "createdAt";
        };
    }

    private LocalDateTime extractDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        }
        if (value instanceof String text) {
            try {
                return LocalDateTime.parse(text);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNickname(String nickname) {
        return nickname == null ? "" : nickname.trim();
    }
}
