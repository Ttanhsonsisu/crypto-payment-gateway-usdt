package com.UsdtWallet.UsdtWallet.controller;

import com.UsdtWallet.UsdtWallet.model.dto.request.LoginRequest;
import com.UsdtWallet.UsdtWallet.model.dto.request.UserCreateRequest;
import com.UsdtWallet.UsdtWallet.model.dto.response.ApiResponse;
import com.UsdtWallet.UsdtWallet.model.dto.response.AuthenResponse;
import com.UsdtWallet.UsdtWallet.service.Interface.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor  // Constructor-based DI
@Tag(name = "Authentication", description = "APIs for user authentication")
public class AuthController {
    @Autowired
    private  AuthService authService;  // Interface injection

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and return JWT token")
    public ResponseEntity<ApiResponse<AuthenResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthenResponse authResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
    }

    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Register a new user")
    public ResponseEntity<ApiResponse<AuthenResponse>> register(
            @Valid @RequestBody UserCreateRequest request) {

        AuthenResponse authResponse = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", authResponse));
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Logout current user")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        authService.logout(token);

        return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Refresh JWT token")
    public ResponseEntity<ApiResponse<AuthenResponse>> refreshToken(
            @RequestParam String refreshToken) {

        AuthenResponse authResponse = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", authResponse));
    }

    // Private helper method
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
