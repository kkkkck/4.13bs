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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    private static final String DEFAULT_AVATAR_PRESET = "sunrise-reader";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern AVATAR_PRESET_PATTERN = Pattern.compile("^[a-z0-9-]{2,40}$");
    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final String AVATAR_URL_PREFIX = "/api/auth/avatar/";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserActivitySessionMapper userActivitySessionMapper;

    @Autowired
    private VerificationCodeService verificationCodeService;

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
        user.setAvatarPreset(DEFAULT_AVATAR_PRESET);
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
        if (!StringUtils.hasText(user.getAvatarPreset())) {
            user.setAvatarPreset(DEFAULT_AVATAR_PRESET);
        }
        return user;
    }

    @Override
    public User updateProfile(Long userId, String nickname, String email, String verificationCode) {
        User user = getActiveUserById(userId);

        String normalizedNickname = normalizeNickname(nickname);
        String normalizedEmail = normalizeEmail(email);

        if (!StringUtils.hasText(normalizedNickname)) {
            throw new BusinessException("昵称不能为空");
        }
        if (!StringUtils.hasText(normalizedEmail) || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }

        User existingNickname = findByNickname(normalizedNickname);
        if (existingNickname != null && !existingNickname.getId().equals(userId)) {
            throw new BusinessException("该昵称已被占用");
        }

        User existingEmail = findByEmail(normalizedEmail);
        if (existingEmail != null && !existingEmail.getId().equals(userId)) {
            throw new BusinessException("该邮箱已被占用");
        }

        if (!normalizedEmail.equalsIgnoreCase(user.getEmail())) {
            if (!StringUtils.hasText(verificationCode) || !verificationCodeService.verifyCode(user.getEmail(), verificationCode)) {
                throw new BusinessException("原邮箱验证码错误或已过期");
            }
        }

        user.setNickname(normalizedNickname);
        user.setEmail(normalizedEmail);
        updateById(user);
        return getActiveUserById(userId);
    }

    @Override
    public User updateAvatar(Long userId, MultipartFile file) {
        User user = getActiveUserById(userId);
        validateAvatarFile(file);

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new BusinessException("仅支持 JPG、PNG、WEBP 图片");
        };

        Path avatarDir = resolveAvatarDir();
        try {
            Files.createDirectories(avatarDir);
            String filename = userId + "-" + UUID.randomUUID().toString().replace("-", "") + extension;
            Path targetPath = avatarDir.resolve(filename).normalize();
            file.transferTo(targetPath);
            deleteAvatarFileIfManaged(user.getAvatarUrl());
            user.setAvatarUrl(AVATAR_URL_PREFIX + filename);
            updateById(user);
            return getActiveUserById(userId);
        } catch (IOException ex) {
            throw new BusinessException("头像上传失败");
        }
    }

    @Override
    public User removeAvatar(Long userId) {
        User user = getActiveUserById(userId);
        deleteAvatarFileIfManaged(user.getAvatarUrl());
        lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getAvatarUrl, null)
                .update();
        return getActiveUserById(userId);
    }

    @Override
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = getActiveUserById(userId);
        if (!StringUtils.hasText(currentPassword) || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException(401, "当前密码不正确");
        }

        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6 || newPassword.length() > 32) {
            throw new BusinessException("新密码长度需在 6 到 32 位之间");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        updateById(user);
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
            item.put("avatarPreset", user.getAvatarPreset());
            item.put("avatarUrl", user.getAvatarUrl());
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

    private String normalizeAvatarPreset(String avatarPreset) {
        String normalized = avatarPreset == null ? "" : avatarPreset.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            return DEFAULT_AVATAR_PRESET;
        }
        if (!AVATAR_PRESET_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException("头像方案不合法");
        }
        return normalized;
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择头像图片");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BusinessException("头像图片不能超过 2MB");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_AVATAR_TYPES.contains(contentType)) {
            throw new BusinessException("仅支持 JPG、PNG、WEBP 图片");
        }
    }

    private Path resolveAvatarDir() {
        return Paths.get("uploads", "avatars").toAbsolutePath().normalize();
    }

    private void deleteAvatarFileIfManaged(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl) || !avatarUrl.startsWith(AVATAR_URL_PREFIX)) {
            return;
        }

        String filename = avatarUrl.substring(AVATAR_URL_PREFIX.length());
        if (!StringUtils.hasText(filename) || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return;
        }

        try {
            Files.deleteIfExists(resolveAvatarDir().resolve(filename).normalize());
        } catch (IOException ignored) {
        }
    }
}
