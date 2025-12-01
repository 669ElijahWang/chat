package com.aichat.controller;

import com.aichat.domain.dto.common.ApiResponse;
import com.aichat.domain.dto.user.UpdatePasswordRequest;
import com.aichat.domain.dto.user.UpdateProfileRequest;
import com.aichat.domain.entity.User;
import com.aichat.exception.BusinessException;
import com.aichat.repository.UserRepository;
import com.aichat.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    /**
     * 更新用户基本信息
     */
    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UpdateProfileRequest request) {
        
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        
        // 检查用户名是否已被占用
        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new BusinessException("用户名已被占用");
            }
            user.setUsername(request.getUsername());
        }
        
        // 检查邮箱是否已被占用
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException("邮箱已被占用");
            }
            user.setEmail(request.getEmail());
        }
        
        // 更新昵称
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        
        user = userRepository.save(user);
        
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("role", user.getRole().name());
        
        return ApiResponse.success("更新成功", userInfo);
    }
    
    /**
     * 修改密码
     */
    @PutMapping("/password")
    public ApiResponse<Void> updatePassword(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UpdatePasswordRequest request) {
        
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        
        // 验证原密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        
        // 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        
        return ApiResponse.success("密码修改成功", null);
    }
}

