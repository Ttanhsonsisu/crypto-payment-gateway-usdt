package com.UsdtWallet.UsdtWallet.service;


import com.UsdtWallet.UsdtWallet.model.dto.request.UserCreateRequest;
import com.UsdtWallet.UsdtWallet.model.dto.response.UserResponse;
import com.UsdtWallet.UsdtWallet.model.entity.User;
import com.UsdtWallet.UsdtWallet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {


    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        log.debug("Getting all users with pagination: {}", pageable);
        return null;
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        log.debug("Getting user by id: {}", id);
//        User user = userRepository.findById(id)

        return UserResponse.from(new User());
    }

    public UserResponse createUser(UserCreateRequest request) throws BadRequestException {
        log.debug("Creating new user with username: {}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken!");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already in use!");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
//                .phoneNumber(request.getPhoneNumber())
                .role(User.Role.USER)
//                .active(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User created successfully with id: {}", savedUser.getId());

        return UserResponse.from(savedUser);
    }

    public UserResponse updateUser(Long id, UserCreateRequest request) {
        log.debug("Updating user with id: {}", id);

//        User user = userRepository.findById(id)
//                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Check if username/email is already taken by another user
//        if (!user.getUsername().equals(request.getUsername()) &&
//                userRepository.existsByUsername(request.getUsername())) {
//            throw new BadRequestException("Username is already taken!");
//        }
//
//        if (!user.getEmail().equals(request.getEmail()) &&
//                userRepository.existsByEmail(request.getEmail())) {
//            throw new BadRequestException("Email is already in use!");
//        }
//
//        user.setUsername(request.getUsername());
//        user.setEmail(request.getEmail());
//        user.setFullName(request.getFullName());
//        user.setPhoneNumber(request.getPhoneNumber());
//
//        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
//            user.setPassword(passwordEncoder.encode(request.getPassword()));
//        }
//
//        User updatedUser = userRepository.save(user);
//        log.info("User updated successfully with id: {}", updatedUser.getId());

        return UserResponse.from(new User());
    }

    public void deleteUser(Long id) {
        log.debug("Deleting user with id: {}", id);

//        User user = userRepository.findById(id)
//                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Soft delete
//        user.setActive(false);
//        userRepository.save(user);

        log.info("User deleted successfully with id: {}", id);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(String keyword, Pageable pageable) {
        log.debug("Searching users with keyword: {}", keyword);
//        return userRepository.searchActiveUsers(keyword, pageable)
//                .map(UserResponse::from);
        return null;
    }
}
