package com.aichat.controller;

import com.aichat.domain.dto.auth.AuthResponse;
import com.aichat.domain.dto.auth.LoginRequest;
import com.aichat.domain.dto.auth.RegisterRequest;
import com.aichat.domain.dto.common.ApiResponse;
import com.aichat.domain.entity.User;
import com.aichat.exception.BusinessException;
import com.aichat.repository.UserRepository;
import com.aichat.security.UserPrincipal;
import com.aichat.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    private final UserRepository userRepository;
    
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ApiResponse.success("注册成功", response);
    }
    
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ApiResponse.success("登录成功", response);
    }
    
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refreshToken(@RequestParam String refreshToken) {
        AuthResponse response = authService.refreshToken(refreshToken);
        return ApiResponse.success("刷新成功", response);
    }
    
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        authService.logout(userPrincipal.getId());
        return ApiResponse.success("登出成功", null);
    }
    
    @GetMapping("/me")
    public ApiResponse<AuthResponse.UserInfo> getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        
        AuthResponse.UserInfo userInfo = AuthResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole().name())
                .build();
        return ApiResponse.success(userInfo);
    }
}

