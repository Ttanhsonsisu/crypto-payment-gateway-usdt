package com.UsdtWallet.UsdtWallet.service.Interface;


import com.UsdtWallet.UsdtWallet.model.dto.request.UserCreateRequest;
import com.UsdtWallet.UsdtWallet.model.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    Page<UserResponse> getAllUsers(Pageable pageable);
    UserResponse getUserById(Long id);
    UserResponse createUser(UserCreateRequest request);
    UserResponse updateUser(Long id, UserCreateRequest request);
    void deleteUser(Long id);
    Page<UserResponse> searchUsers(String keyword, Pageable pageable);
    UserResponse getUserByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
