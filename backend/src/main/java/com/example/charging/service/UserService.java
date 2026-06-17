package com.example.charging.service;

import com.example.charging.dto.LoginResponse;
import com.example.charging.dto.RegisterRequest;
import com.example.charging.dto.UserProfileDTO;
import com.example.charging.entity.User;
import com.example.charging.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserProfileDTO register(RegisterRequest request) {
        // 检查用户名是否已存在
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("用户名已存在");
        }
        // 检查手机号是否已存在（可选）
        if (request.getPhone() != null && userRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new IllegalArgumentException("手机号已被绑定");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());  // 第一版明文存储，后续需加密
        user.setPhone(request.getPhone());
        user.setRole("USER");
        User saved = userRepository.save(user);
        return new UserProfileDTO(saved.getId(), saved.getUsername(), saved.getPhone(), saved.getRole());
    }

    public LoginResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!user.getPassword().equals(password)) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = "mock:" + user.getRole() + ":" + user.getId();
        return new LoginResponse(user.getId(), user.getUsername(), user.getRole(), token);
    }

    public User requireAdmin(String authorization) {
        User user = requireAuthenticatedUser(authorization);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("当前账号没有管理权限");
        }
        return user;
    }

    public User requireAuthenticatedUser(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new IllegalArgumentException("请先登录");
        }

        String token = authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : authorization.trim();
        String[] parts = token.split(":");
        if (parts.length != 3 || !"mock".equals(parts[0])) {
            throw new IllegalArgumentException("登录态无效，请重新登录");
        }

        Long userId;
        try {
            userId = Long.valueOf(parts[2]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("登录态无效，请重新登录");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (!user.getRole().equalsIgnoreCase(parts[1])) {
            throw new IllegalArgumentException("登录态无效，请重新登录");
        }
        return user;
    }
}
