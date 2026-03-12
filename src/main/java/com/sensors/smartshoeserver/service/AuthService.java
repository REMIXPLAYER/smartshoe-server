package com.sensors.smartshoeserver.service;

import com.sensors.smartshoeserver.dto.AuthResponse;
import com.sensors.smartshoeserver.entity.User;
import com.sensors.smartshoeserver.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * 认证服务层
 * 处理用户登录、注册、资料修改等业务逻辑
 */
@Service
public class AuthService {

    private final UserRepository userRepository;

    // JWT密钥，实际项目应该从配置文件或环境变量读取
    private final SecretKey jwtSecretKey;

    // Token有效期（毫秒），默认7天
    @Value("${jwt.expiration:604800000}")
    private long jwtExpiration;

    @Autowired
    public AuthService(UserRepository userRepository, @Value("${jwt.secret:SmartShoeDefaultSecretKeyForJWTGeneration2024}") String jwtSecret) {
        this.userRepository = userRepository;
        // 使用提供的密钥生成安全的JWT签名密钥
        this.jwtSecretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 用户登录
     * @param email 邮箱
     * @param password 密码（明文，实际应该加密）
     * @return 认证响应
     */
    @Transactional
    public AuthResponse login(String email, String password) {
        // 查找用户
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return new AuthResponse(false, "邮箱或密码错误");
        }

        User user = userOpt.get();

        // 检查用户状态
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            return new AuthResponse(false, "账户已被禁用");
        }

        // 验证密码（实际项目中应该使用加密比较）
        if (!user.getPassword().equals(password)) {
            return new AuthResponse(false, "邮箱或密码错误");
        }

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // 生成JWT Token
        String token = generateToken(user);

        // 构建响应数据
        AuthResponse.UserData userData = new AuthResponse.UserData(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                token
        );

        return new AuthResponse(true, "登录成功", userData);
    }

    /**
     * 用户注册
     * @param username 用户名
     * @param email 邮箱
     * @param password 密码
     * @return 认证响应
     */
    @Transactional
    public AuthResponse register(String username, String email, String password) {
        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(email)) {
            return new AuthResponse(false, "该邮箱已被注册");
        }

        // 生成唯一用户ID
        String userId = generateUserId();

        // 创建新用户
        User newUser = new User();
        newUser.setUserId(userId);
        newUser.setUsername(username);
        newUser.setEmail(email);
        newUser.setPassword(password); // 实际项目中应该加密存储
        newUser.setStatus(User.UserStatus.ACTIVE);

        // 保存用户
        userRepository.save(newUser);

        // 生成JWT Token
        String token = generateToken(newUser);

        // 构建响应数据
        AuthResponse.UserData userData = new AuthResponse.UserData(
                newUser.getUserId(),
                newUser.getUsername(),
                newUser.getEmail(),
                token
        );

        return new AuthResponse(true, "注册成功", userData);
    }

    /**
     * 修改用户资料
     * @param userId 用户ID
     * @param currentPassword 当前密码
     * @param newUsername 新用户名
     * @param newEmail 新邮箱
     * @param newPassword 新密码（可选）
     * @return 认证响应
     */
    @Transactional
    public AuthResponse updateProfile(String userId, String currentPassword, 
                                      String newUsername, String newEmail, String newPassword) {
        // 查找用户
        Optional<User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            return new AuthResponse(false, "用户不存在");
        }

        User user = userOpt.get();

        // 验证当前密码
        if (!user.getPassword().equals(currentPassword)) {
            return new AuthResponse(false, "当前密码错误");
        }

        // 检查新邮箱是否已被其他用户使用
        if (!newEmail.equals(user.getEmail())) {
            Optional<User> existingUser = userRepository.findByEmail(newEmail);
            if (existingUser.isPresent() && !existingUser.get().getUserId().equals(userId)) {
                return new AuthResponse(false, "该邮箱已被其他用户使用");
            }
        }

        // 更新用户信息
        user.setUsername(newUsername);
        user.setEmail(newEmail);
        
        // 如果提供了新密码，则更新
        if (newPassword != null && !newPassword.isEmpty()) {
            user.setPassword(newPassword); // 实际项目中应该加密存储
        }

        // 保存更新
        userRepository.save(user);

        // 生成新JWT Token
        String token = generateToken(user);

        // 构建响应数据
        AuthResponse.UserData userData = new AuthResponse.UserData(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                token
        );

        return new AuthResponse(true, "资料更新成功", userData);
    }

    /**
     * 验证Token
     * @param token 登录令牌
     * @return 认证响应
     */
    @Transactional(readOnly = true)
    public AuthResponse verifyToken(String token) {
        // 先尝试解析JWT Token
        String userId = parseJwtToken(token);
        
        // 如果JWT解析失败，尝试兼容旧版简单Token
        if (userId == null) {
            userId = parseLegacyToken(token);
        }

        if (userId == null) {
            return new AuthResponse(false, "无效的Token");
        }

        // 查找用户
        Optional<User> userOpt = userRepository.findByUserId(userId);
        
        if (userOpt.isEmpty()) {
            return new AuthResponse(false, "用户不存在");
        }

        User user = userOpt.get();

        // 检查用户状态
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            return new AuthResponse(false, "账户已被禁用");
        }

        // 构建响应数据
        AuthResponse.UserData userData = new AuthResponse.UserData(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                token
        );

        return new AuthResponse(true, "Token有效", userData);
    }

    /**
     * 生成用户ID
     * @return 唯一用户ID
     */
    private String generateUserId() {
        return "user_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 生成JWT Token
     * @param user 用户对象
     * @return JWT Token字符串
     */
    private String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(user.getUserId())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(jwtSecretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析JWT Token
     * @param token JWT Token字符串
     * @return 用户ID，如果解析失败返回null
     */
    private String parseJwtToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwtSecretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            return claims.getSubject();
        } catch (Exception e) {
            // JWT解析失败，返回null让调用方尝试其他方式
            return null;
        }
    }

    /**
     * 解析旧版简单Token（兼容旧版本）
     * @param token 旧版Token字符串
     * @return 用户ID
     */
    private String parseLegacyToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String[] parts = token.split("_");
        // Token格式: user_时间戳_UUID_时间戳_随机数
        // userId应该是前3部分: user_时间戳_UUID
        if (parts.length >= 3) {
            return parts[0] + "_" + parts[1] + "_" + parts[2];
        }
        return null;
    }
}
