package com.example.刷题.controller;

import com.example.support.SecurityUtils;
import com.example.刷题.common.Result;
import com.example.刷题.dto.LoginRequest;
import com.example.刷题.dto.RegisterRequest;
import com.example.刷题.dto.SendCodeRequest;
import com.example.刷题.dto.SendCodeResponse;
import com.example.刷题.entity.User;
import com.example.刷题.exception.BusinessException;
import com.example.刷题.service.EmailService;
import com.example.刷题.service.UserService;
import com.example.刷题.service.VerificationCodeService;
import com.example.刷题.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/send-code")
    public Result<SendCodeResponse> sendCode(@Validated @RequestBody SendCodeRequest request) {
        String email = normalizeEmail(request.getEmail());
        User existingUser = userService.findByEmail(email);
        if (existingUser != null) {
            return Result.fail("该邮箱已注册，请直接登录");
        }

        String code;
        try {
            code = verificationCodeService.generateCode(email);
        } catch (IllegalStateException ex) {
            throw new BusinessException(429, ex.getMessage());
        }

        emailService.sendVerificationCode(email, code);
        int expiresInSeconds = verificationCodeService.getCodeExpireSeconds();

        if (emailService.canSendMail()) {
            return Result.success(new SendCodeResponse("验证码已发送，请注意查收邮箱", null, expiresInSeconds, true));
        }

        return Result.success(new SendCodeResponse(
                "验证码已生成。当前环境未启用真实邮箱发送，已直接返回开发验证码。",
                code,
                expiresInSeconds,
                false
        ));
    }

    @PostMapping("/register")
    public Result<User> register(@Validated @RequestBody RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        String code = request.getCode() == null ? "" : request.getCode().trim();
        String nickname = request.getNickname() == null ? "" : request.getNickname().trim();

        if (!verificationCodeService.verifyCode(email, code)) {
            return Result.fail("验证码错误或已过期");
        }
        User user = userService.register(email, request.getPassword(), nickname);
        return Result.success(user);
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Validated @RequestBody LoginRequest request) {
        String account = normalizeAccount(request.getAccount());
        User user = userService.login(account, request.getPassword());

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("nickname", user.getNickname());
        claims.put("role", user.getRole());

        String token = jwtUtil.generateToken(claims);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);
        return Result.success(result);
    }

    @GetMapping("/me")
    public Result<User> me() {
        Long userId = securityUtils.getCurrentUserId();
        return Result.success(userService.getActiveUserById(userId));
    }

    private String normalizeAccount(String account) {
        String trimmed = account == null ? "" : account.trim();
        if (trimmed.contains("@")) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
