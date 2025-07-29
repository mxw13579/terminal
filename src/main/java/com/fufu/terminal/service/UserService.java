package com.fufu.terminal.service;

import com.fufu.terminal.entity.User;
import com.fufu.terminal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import com.fufu.terminal.config.PasswordConfig.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User authenticate(String username, String password) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }
        return null;
    }

    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User save(User user) {
        if (user.getId() == null && user.getPassword() != null) {
            // 新用户，加密密码
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        return userRepository.save(user);
    }

    public void deleteById(Long id) {
        userRepository.deleteById(id);
    }

    public User updatePassword(Long id, String newPassword) {
        User user = findById(id);
        if (user != null) {
            user.setPassword(passwordEncoder.encode(newPassword));
            return userRepository.save(user);
        }
        return null;
    }
}
