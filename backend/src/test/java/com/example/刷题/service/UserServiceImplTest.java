package com.example.刷题.service;

import com.example.刷题.entity.User;
import com.example.刷题.exception.BusinessException;
import com.example.刷题.mapper.UserActivitySessionMapper;
import com.example.刷题.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserActivitySessionMapper userActivitySessionMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new UserServiceImpl());
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "userActivitySessionMapper", userActivitySessionMapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
    }

    @Test
    void registerRejectsBlankNickname() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.register("user@example.com", "secret123", " ")
        );

        assertEquals(400, exception.getCode());
        assertEquals("昵称不能为空", exception.getMessage());
    }

    @Test
    void registerRejectsDuplicateNickname() {
        when(userMapper.findByEmail("user@example.com")).thenReturn(null);
        User existing = new User();
        existing.setId(2L);
        existing.setNickname("nick");
        when(userMapper.findByNickname("nick")).thenReturn(existing);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.register("user@example.com", "secret123", "nick")
        );

        assertEquals(400, exception.getCode());
        assertEquals("该昵称已被占用", exception.getMessage());
    }

    @Test
    void registerStoresEncodedPasswordAndNormalizedEmail() {
        when(userMapper.findByEmail("user@example.com")).thenReturn(null);
        when(userMapper.findByNickname("NickName")).thenReturn(null);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");

        AtomicReference<User> savedUser = new AtomicReference<>();
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(99L);
            savedUser.set(user);
            return true;
        }).when(service).save(any(User.class));
        doAnswer(invocation -> savedUser.get()).when(service).getById(99L);

        User created = service.register("User@Example.com", "secret123", "NickName");

        assertNotNull(created);
        assertEquals(99L, created.getId());
        assertEquals("user@example.com", savedUser.get().getEmail());
        assertEquals("NickName", savedUser.get().getNickname());
        assertEquals("encoded-password", savedUser.get().getPassword());
    }

    @Test
    void loginSupportsNicknameWhenExactlyOneActiveUserMatches() {
        User user = new User();
        user.setId(1L);
        user.setNickname("Nick");
        user.setEmail("nick@example.com");
        user.setPassword("encoded-password");
        user.setStatus(1);

        when(userMapper.selectList(any())).thenReturn(List.of(user));
        when(passwordEncoder.matches("secret123", "encoded-password")).thenReturn(true);

        User loggedIn = service.login("Nick", "secret123");

        assertEquals(1L, loggedIn.getId());
        assertEquals("nick@example.com", loggedIn.getEmail());
    }

    @Test
    void loginRejectsDuplicateNicknameMatches() {
        User first = new User();
        first.setId(1L);
        first.setNickname("SameNick");
        first.setStatus(1);

        User second = new User();
        second.setId(2L);
        second.setNickname("SameNick");
        second.setStatus(1);

        when(userMapper.selectList(any())).thenReturn(List.of(first, second));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.login("SameNick", "secret123")
        );

        assertEquals(401, exception.getCode());
        assertEquals("该昵称存在重复账号，请使用邮箱登录", exception.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getUserListFiltersInactiveUsersAndSortsNullLastForLastSeenDesc() {
        User active = buildUser(1L, "active@example.com", "ActiveNick", LocalDateTime.now().minusDays(2));
        User stale = buildUser(2L, "stale@example.com", "StaleNick", LocalDateTime.now().minusDays(1));
        User neverSeen = buildUser(3L, "never@example.com", "NeverNick", LocalDateTime.now());

        when(userMapper.selectList(any())).thenReturn(List.of(active, stale, neverSeen));
        when(userActivitySessionMapper.selectUserActivitySummaryByUserIds(any())).thenReturn(List.of(
                Map.of("userId", 1L, "lastSeenAt", LocalDateTime.now().minusHours(3)),
                Map.of("userId", 2L, "lastSeenAt", LocalDateTime.now().minusDays(10))
        ));

        Map<String, Object> result = service.getUserList(1, 20, null, null, null, "inactive7d", "lastSeenAt", "desc");
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");

        assertEquals(2, result.get("total"));
        assertEquals(2L, records.get(0).get("id"));
        assertEquals(3L, records.get(1).get("id"));
        assertEquals(null, records.get(1).get("lastSeenAt"));
    }

    private User buildUser(Long id, String email, String nickname, LocalDateTime createdAt) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setNickname(nickname);
        user.setRole(0);
        user.setStatus(1);
        user.setCreatedAt(createdAt);
        return user;
    }
}
