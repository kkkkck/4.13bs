package com.example.刷题.controller;

import com.example.support.SecurityUtils;
import com.example.刷题.dto.ChangePasswordRequest;
import com.example.刷题.common.Result;
import com.example.刷题.dto.LoginRequest;
import com.example.刷题.dto.RegisterRequest;
import com.example.刷题.dto.SendCodeRequest;
import com.example.刷题.dto.SendCodeResponse;
import com.example.刷题.dto.UpdateProfileRequest;
import com.example.刷题.entity.User;
import com.example.刷题.exception.BusinessException;
import com.example.刷题.service.EmailService;
import com.example.刷题.service.UserService;
import com.example.刷题.service.VerificationCodeService;
import com.example.刷题.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

        return sendVerificationCode(
                email,
                "验证码已发送，请注意查收邮箱",
                "验证码已生成。当前环境启用调试模式，已直接返回开发验证码。"
        );
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

    @PostMapping("/profile/email-code")
    public Result<SendCodeResponse> sendProfileEmailCode() {
        Long userId = securityUtils.getCurrentUserId();
        User user = userService.getActiveUserById(userId);

        return sendVerificationCode(
                user.getEmail(),
                "验证码已发送到当前邮箱，请查收后再修改邮箱。",
                "验证码已生成。当前环境启用调试模式，已直接返回开发验证码。"
        );
    }

    @PutMapping("/profile")
    public Result<User> updateProfile(@Validated @RequestBody UpdateProfileRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        User user = userService.updateProfile(userId, request.getNickname(), request.getEmail(), request.getVerificationCode());
        return Result.success(user);
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<User> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = securityUtils.getCurrentUserId();
        return Result.success(userService.updateAvatar(userId, file));
    }

    @DeleteMapping("/avatar")
    public Result<User> deleteAvatar() {
        Long userId = securityUtils.getCurrentUserId();
        return Result.success(userService.removeAvatar(userId));
    }

    @GetMapping("/avatar/{filename}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable String filename) throws IOException {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        Path avatarPath = Paths.get("uploads", "avatars", filename).toAbsolutePath().normalize();
        if (!Files.exists(avatarPath) || !Files.isRegularFile(avatarPath)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(resolveMediaType(filename))
                .body(Files.readAllBytes(avatarPath));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Validated @RequestBody ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("两次输入的新密码不一致");
        }

        Long userId = securityUtils.getCurrentUserId();
        userService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        return Result.success();
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

    private Result<SendCodeResponse> sendVerificationCode(String email, String mailMessage, String debugMessage) {
        String code;
        try {
            code = verificationCodeService.generateCode(email);
        } catch (IllegalStateException ex) {
            throw new BusinessException(429, ex.getMessage());
        }

        try {
            emailService.sendVerificationCode(email, code);
        } catch (RuntimeException ex) {
            verificationCodeService.invalidateCode(email);
            throw ex;
        }

        int expiresInSeconds = verificationCodeService.getCodeExpireSeconds();
        boolean mailEnabled = emailService.canSendMail();
        String debugCode = emailService.canReturnDebugCode() ? code : null;
        String message = mailEnabled ? mailMessage : debugMessage;
        return Result.success(new SendCodeResponse(message, debugCode, expiresInSeconds, mailEnabled));
    }

    private MediaType resolveMediaType(String filename) {
        String lowerName = filename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lowerName.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
