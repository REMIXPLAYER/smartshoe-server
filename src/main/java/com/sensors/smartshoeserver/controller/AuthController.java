package com.sensors.smartshoeserver.controller;

import com.sensors.smartshoeserver.dto.AuthResponse;
import com.sensors.smartshoeserver.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 * 处理登录、注册、资料修改等HTTP请求
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*") // 允许跨域，实际项目中应该限制具体域名
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户登录
     * @param params 包含email和password的请求参数
     * @return 登录结果
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestParam Map<String, String> params) {
        String email = params.get("email");
        String password = params.get("password");

        logger.info("[AUTH] 登录请求 - Email: {}", email);

        // 参数校验
        if (email == null || email.isEmpty()) {
            logger.warn("[AUTH] 登录失败 - 邮箱为空");
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "邮箱不能为空"));
        }
        if (password == null || password.isEmpty()) {
            logger.warn("[AUTH] 登录失败 - 密码为空");
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "密码不能为空"));
        }

        AuthResponse response = authService.login(email, password);

        if (response.isSuccess()) {
            logger.info("[AUTH] 登录成功 - Email: {}", email);
            return ResponseEntity.ok(response);
        } else {
            logger.warn("[AUTH] 登录失败 - Email: {}, 原因: {}", email, response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 用户注册
     * @param params 包含username、email和password的请求参数
     * @return 注册结果
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestParam Map<String, String> params) {
        String username = params.get("username");
        String email = params.get("email");
        String password = params.get("password");

        logger.info("[AUTH] 注册请求 - Username: {}, Email: {}", username, email);

        // 参数校验
        if (username == null || username.isEmpty()) {
            logger.warn("[AUTH] 注册失败 - 用户名为空");
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "用户名不能为空"));
        }
        if (email == null || email.isEmpty()) {
            logger.warn("[AUTH] 注册失败 - 邮箱为空");
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "邮箱不能为空"));
        }
        if (password == null || password.isEmpty()) {
            logger.warn("[AUTH] 注册失败 - 密码为空");
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "密码不能为空"));
        }

        AuthResponse response = authService.register(username, email, password);

        if (response.isSuccess()) {
            logger.info("[AUTH] 注册成功 - Username: {}, Email: {}", username, email);
            return ResponseEntity.ok(response);
        } else {
            logger.warn("[AUTH] 注册失败 - Email: {}, 原因: {}", email, response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 修改用户资料
     * @param params 包含userId、currentPassword、newUsername、newEmail和newPassword的请求参数
     * @return 修改结果
     */
    @PostMapping("/update-profile")
    public ResponseEntity<AuthResponse> updateProfile(@RequestParam Map<String, String> params) {
        String userId = params.get("userId");
        String currentPassword = params.get("currentPassword");
        String newUsername = params.get("newUsername");
        String newEmail = params.get("newEmail");
        String newPassword = params.get("newPassword");

        // 参数校验
        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "用户ID不能为空"));
        }
        if (currentPassword == null || currentPassword.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "当前密码不能为空"));
        }
        if (newUsername == null || newUsername.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "用户名不能为空"));
        }
        if (newEmail == null || newEmail.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "邮箱不能为空"));
        }

        AuthResponse response = authService.updateProfile(
                userId, 
                currentPassword, 
                newUsername, 
                newEmail, 
                newPassword != null ? newPassword : ""
        );
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 验证Token
     * @param params 包含token的请求参数
     * @return 验证结果
     */
    @PostMapping("/verify-token")
    public ResponseEntity<AuthResponse> verifyToken(@RequestParam Map<String, String> params) {
        String token = params.get("token");

        // 参数校验
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "Token不能为空"));
        }

        AuthResponse response = authService.verifyToken(token);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 健康检查接口
     * @return 服务状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "auth-service"));
    }
}