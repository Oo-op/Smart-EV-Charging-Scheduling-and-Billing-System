package com.example.charging.service;

import com.example.charging.dto.LoginResponse;
import com.example.charging.dto.RegisterRequest;
import com.example.charging.entity.User;
import com.example.charging.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User register(RegisterRequest request) {
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
        return userRepository.save(user);
    }

    public LoginResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!user.getPassword().equals(password)) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        // 第一版返回 mock token
        return new LoginResponse(user.getId(), user.getUsername(), user.getRole(), "mock-token-" + user.getId());
    }
}