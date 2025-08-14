package com.UsdtWallet.UsdtWallet.service;

import com.UsdtWallet.UsdtWallet.model.dto.request.UserCreateRequest;
import com.UsdtWallet.UsdtWallet.model.dto.response.UserResponse;
import com.UsdtWallet.UsdtWallet.repository.UserRepository;
import com.UsdtWallet.UsdtWallet.service.Interface.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j // auto config logger
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return null;
    }

    @Override
    public UserResponse getUserById(Long id) {
        return null;
    }

    @Override
    public UserResponse createUser(UserCreateRequest request) {
        return null;
    }

    @Override
    public UserResponse updateUser(Long id, UserCreateRequest request) {
        return null;
    }

    @Override
    public void deleteUser(Long id) {

    }

    @Override
    public Page<UserResponse> searchUsers(String keyword, Pageable pageable) {
        return null;
    }

    @Override
    public UserResponse getUserByUsername(String username) {
        return null;
    }

    @Override
    public boolean existsByUsername(String username) {
        return false;
    }

    @Override
    public boolean existsByEmail(String email) {
        return false;
    }
}
