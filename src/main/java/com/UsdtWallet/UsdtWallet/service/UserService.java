package com.UsdtWallet.UsdtWallet.service;

import com.UsdtWallet.UsdtWallet.model.dto.request.UserRegistrationRequest;
import com.UsdtWallet.UsdtWallet.model.dto.response.UserRegistrationResponse;
import com.UsdtWallet.UsdtWallet.model.entity.ChildWalletPool;
import com.UsdtWallet.UsdtWallet.model.entity.User;
import com.UsdtWallet.UsdtWallet.repository.UserRepository;
import com.UsdtWallet.UsdtWallet.repository.ChildWalletPoolRepository;
import com.UsdtWallet.UsdtWallet.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final HdWalletService hdWalletService;
    private final PasswordEncoder passwordEncoder;
    private final ChildWalletPoolRepository childWalletPoolRepository;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Register new user with auto wallet assignment
     */
    @Transactional
    public UserRegistrationResponse registerUser(UserRegistrationRequest request) {
        // Validate input
        validateRegistrationRequest(request);

        // Check if user already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // Create new user
        User user = createUser(request);
        User savedUser = userRepository.save(user);

        log.info("User created successfully: {}", savedUser.getUsername());

        // Auto assign wallet to user
        ChildWalletPool assignedWallet = hdWalletService.assignWalletToUser(savedUser.getId());

        // Update user's address field with wallet address
        savedUser.setAddress(assignedWallet.getAddress());
        savedUser = userRepository.save(savedUser);

        log.info("Wallet {} assigned to user {} and saved to user.address field",
            assignedWallet.getAddress(), savedUser.getUsername());

        return UserRegistrationResponse.builder()
                .userId(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .walletAddress(assignedWallet.getAddress())
                .registeredAt(savedUser.getDateCreated())
                .message("User registered successfully with auto-assigned wallet")
                .build();
    }

    /**
     * Validate registration request
     */
    private void validateRegistrationRequest(UserRegistrationRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Password and confirm password do not match");
        }
    }

    /**
     * Create user entity from request
     */
    private User createUser(UserRegistrationRequest request) {
        return User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(User.Role.USER)
                .status(1) // Active status
                .isActive(true) // Set user as active
                .isUser(true) // Set as user type
                .userCreated("SYSTEM") // Set who created the user
                .build();
    }

    /**
     * Get user by username
     */
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    /**
     * Get user by email
     */
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    /**
     * Get user wallet address
     */
    public String getUserWalletAddress(UUID userId) {
        ChildWalletPool wallet = hdWalletService.getWalletByUserId(userId.getMostSignificantBits());
        return wallet != null ? wallet.getAddress() : null;
    }

    /**
     * Check if user exists by username
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Check if user exists by email
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * User login
     */
    public Map<String, Object> login(String username, String password) {
        // Find user by username
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        // Check password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }

        // Check if user is active using the isActive field
        if (!user.isActive()) {
            throw new RuntimeException("User account is disabled");
        }

        // Create UserPrincipal with correct constructor parameters
        com.UsdtWallet.UsdtWallet.security.UserPrincipal userPrincipal =
            new com.UsdtWallet.UsdtWallet.security.UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                java.util.Collections.singletonList(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                )
            );

        // Create Authentication object for JWT generation
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken authToken =
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userPrincipal,
                null,
                userPrincipal.getAuthorities()
            );

        // Generate JWT token
        String token = jwtTokenProvider.generateToken(authToken);

        // Get user wallet address
        String walletAddress = getUserWalletAddress(user.getId());

        log.info("User {} logged in successfully", username);

        return Map.of(
            "token", token,
            "tokenType", "Bearer",
            "userId", user.getId().toString(),
            "username", user.getUsername(),
            "email", user.getEmail(),
            "fullName", user.getFullName() != null ? user.getFullName() : "",
            "walletAddress", walletAddress != null ? walletAddress : "No wallet assigned",
            "role", user.getRole().toString()
        );
    }

    /**
     * Get user info by ID
     */
    public Map<String, Object> getUserInfo(String userId) {
        UUID userUuid = UUID.fromString(userId);
        User user = userRepository.findById(userUuid)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String walletAddress = getUserWalletAddress(user.getId());

        return Map.of(
            "userId", user.getId().toString(),
            "username", user.getUsername(),
            "email", user.getEmail(),
            "fullName", user.getFullName() != null ? user.getFullName() : "",
            "phone", user.getPhone() != null ? user.getPhone() : "",
            "walletAddress", walletAddress != null ? walletAddress : "No wallet assigned",
            "role", user.getRole().toString(),
            "status", user.getStatus(),
            "isActive", user.isActive(), // Use the isActive() method directly
            "createdAt", user.getDateCreated()
        );
    }
}
