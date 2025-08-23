package com.UsdtWallet.UsdtWallet.controller;

import com.UsdtWallet.UsdtWallet.model.dto.request.UserRegistrationRequest;
import com.UsdtWallet.UsdtWallet.model.dto.request.LoginRequest;
import com.UsdtWallet.UsdtWallet.model.dto.response.ApiResponse;
import com.UsdtWallet.UsdtWallet.model.dto.response.UserRegistrationResponse;
import com.UsdtWallet.UsdtWallet.model.entity.User;
import com.UsdtWallet.UsdtWallet.security.UserPrincipal;
import com.UsdtWallet.UsdtWallet.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * User registration with auto wallet assignment
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserRegistrationResponse>> registerUser(
            @Valid @RequestBody UserRegistrationRequest request) {
        try {
            log.info("=== STARTING USER REGISTRATION ===");
            log.info("Request received for username: {}, email: {}", request.getUsername(), request.getEmail());

            // Validate request
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                log.error("Username is null or empty");
                return ResponseEntity.badRequest()
                    .body(ApiResponse.<UserRegistrationResponse>builder()
                        .success(false)
                        .message("Username is required")
                        .build());
            }

            UserRegistrationResponse response = userService.registerUser(request);

            log.info("=== USER REGISTRATION SUCCESSFUL ===");
            log.info("User {} registered successfully with wallet {}",
                response.getUsername(), response.getWalletAddress());

            ApiResponse<UserRegistrationResponse> apiResponse = ApiResponse.success(
                "User registered successfully with auto-assigned wallet", response);

            log.info("Returning response: {}", apiResponse);
            return ResponseEntity.ok(apiResponse);

        } catch (Exception e) {
            log.error("=== USER REGISTRATION FAILED ===");
            log.error("Error details: ", e);

            ApiResponse<UserRegistrationResponse> errorResponse = ApiResponse.<UserRegistrationResponse>builder()
                .success(false)
                .message("Registration failed: " + e.getMessage())
                .data(null)
                .build();

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Check username availability
     */
    @GetMapping("/check-username")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkUsername(
            @RequestParam String username) {
        try {
            boolean exists = userService.existsByUsername(username);

            Map<String, Object> result = Map.of(
                "username", username,
                "available", !exists,
                "exists", exists
            );

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            log.error("Error checking username: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message("Failed to check username: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Check email availability
     */
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkEmail(
            @RequestParam String email) {
        try {
            boolean exists = userService.existsByEmail(email);

            Map<String, Object> result = Map.of(
                "email", email,
                "available", !exists,
                "exists", exists
            );

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            log.error("Error checking email: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message("Failed to check email: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Get user wallet address
     */
    @GetMapping("/wallet/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserWallet(
            @PathVariable String userId) {
        try {
            java.util.UUID userUuid = java.util.UUID.fromString(userId);
            String walletAddress = userService.getUserWalletAddress(userUuid);

            if (walletAddress != null) {
                Map<String, Object> result = Map.of(
                    "userId", userId,
                    "walletAddress", walletAddress
                );
                return ResponseEntity.ok(ApiResponse.success(result));
            } else {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.<Map<String, Object>>builder()
                        .success(false)
                        .message("No wallet assigned to user")
                        .build());
            }

        } catch (Exception e) {
            log.error("Error getting user wallet: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message("Failed to get user wallet: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Get user info with wallet address
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserInfo(
            @PathVariable String username) {
        try {
            log.info("Getting user info for username: {}", username);

            User user = userService.getUserByUsername(username);
            String walletAddress = userService.getUserWalletAddress(user.getId());

            Map<String, Object> result = Map.of(
                "userId", user.getId().toString(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "fullName", user.getFullName() != null ? user.getFullName() : "",
                "walletAddress", walletAddress != null ? walletAddress : "No wallet assigned",
                "status", user.getStatus(),
                "createdAt", user.getDateCreated()
            );

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            log.error("Error getting user info: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message("Failed to get user info: " + e.getMessage())
                    .build());
        }
    }

    /**
     * User login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @Valid @RequestBody LoginRequest request) {
        try {
            log.info("Login attempt for username: {}", request.getUsername());

            Map<String, Object> response = userService.login(request.getUsername(), request.getPassword());

            log.info("Login successful for user: {}", request.getUsername());
            return ResponseEntity.ok(ApiResponse.success("Login successful", response));

        } catch (Exception e) {
            log.error("Login failed for username: {}", request.getUsername(), e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message("Login failed: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Get current user profile
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            Map<String, Object> userInfo = userService.getUserInfo(userPrincipal.getId().toString());
            return ResponseEntity.ok(ApiResponse.success(userInfo));
        } catch (Exception e) {
            log.error("Error getting user profile", e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message("Failed to get profile: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Simple test endpoint
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testEndpoint() {
        log.info("=== TEST ENDPOINT CALLED ===");
        Map<String, Object> response = Map.of(
            "status", "OK",
            "message", "API is working",
            "timestamp", System.currentTimeMillis()
        );
        log.info("Test response: {}", response);
        return ResponseEntity.ok(response);
    }
}
