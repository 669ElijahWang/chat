package com.aichat.service;

import com.aichat.domain.dto.auth.AuthResponse;
import com.aichat.domain.dto.auth.LoginRequest;
import com.aichat.domain.dto.auth.RegisterRequest;
import com.aichat.domain.entity.User;
import com.aichat.exception.BusinessException;
import com.aichat.repository.UserRepository;
import com.aichat.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${jwt.expiration}")
    private long jwtExpirationMs;
    
    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;
    
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        
        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }
        
        // 创建新用户
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname() != null ? request.getNickname() : request.getUsername())
                .role(User.UserRole.USER)
                .status(User.UserStatus.ACTIVE)
                .build();
        
        user = userRepository.save(user);
        log.info("新用户注册成功: {}", user.getUsername());
        
        // 生成token
        String accessToken = tokenProvider.generateAccessToken(user.getId());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());
        
        // 将token存入Redis
        storeTokenInRedis(user.getId(), accessToken, refreshToken);
        
        return buildAuthResponse(user, accessToken, refreshToken);
    }
    
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 校验验证码（先于用户名密码校验）
        String captchaKey = "captcha:" + request.getCaptchaId();
        String expectCode = redisTemplate.opsForValue().get(captchaKey);
        if (expectCode == null) {
            throw new BusinessException("验证码已过期");
        }
        if (!expectCode.equalsIgnoreCase(request.getCaptchaCode())) {
            throw new BusinessException("验证码错误");
        }
        // 验证码一次性使用
        redisTemplate.delete(captchaKey);

        // 认证用户
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        
        // 获取用户信息
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        
        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        // 生成token
        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());
        
        // 将token存入Redis
        storeTokenInRedis(user.getId(), accessToken, refreshToken);
        
        log.info("用户登录成功: {}", user.getUsername());
        
        return buildAuthResponse(user, accessToken, refreshToken);
    }
    
    public AuthResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("刷新令牌无效或已过期");
        }
        
        Long userId = tokenProvider.getUserIdFromToken(refreshToken);
        
        // 验证刷新令牌是否在Redis中
        String redisRefreshToken = redisTemplate.opsForValue().get("refresh_token:" + userId);
        if (redisRefreshToken == null || !redisRefreshToken.equals(refreshToken)) {
            throw new BusinessException("刷新令牌无效");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        
        // 生成新的access token
        String newAccessToken = tokenProvider.generateAccessToken(userId);
        String newRefreshToken = tokenProvider.generateRefreshToken(userId);
        
        // 更新Redis中的token
        storeTokenInRedis(userId, newAccessToken, newRefreshToken);
        
        return buildAuthResponse(user, newAccessToken, newRefreshToken);
    }
    
    public void logout(Long userId) {
        // 从Redis中删除token
        redisTemplate.delete("access_token:" + userId);
        redisTemplate.delete("refresh_token:" + userId);
        
        log.info("用户登出成功: {}", userId);
    }
    
    private void storeTokenInRedis(Long userId, String accessToken, String refreshToken) {
        // 存储access token
        redisTemplate.opsForValue().set(
                "access_token:" + userId,
                accessToken,
                jwtExpirationMs,
                TimeUnit.MILLISECONDS
        );
        
        // 存储refresh token
        redisTemplate.opsForValue().set(
                "refresh_token:" + userId,
                refreshToken,
                refreshExpirationMs,
                TimeUnit.MILLISECONDS
        );
    }
    
    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        AuthResponse.UserInfo userInfo = AuthResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole().name())
                .build();
        
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs / 1000)
                .userInfo(userInfo)
                .build();
    }
}

