package com.UsdtWallet.UsdtWallet.service;

import com.UsdtWallet.UsdtWallet.model.dto.request.LoginRequest;
import com.UsdtWallet.UsdtWallet.model.dto.request.UserCreateRequest;
import com.UsdtWallet.UsdtWallet.model.dto.response.AuthenResponse;
import com.UsdtWallet.UsdtWallet.repository.UserRepository;
import com.UsdtWallet.UsdtWallet.security.JwtTokenProvider;
import com.UsdtWallet.UsdtWallet.service.Interface.AuthService;
import com.UsdtWallet.UsdtWallet.service.Interface.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    @Override
    public AuthenResponse login(LoginRequest loginRequest) {
        return null;
    }

    @Override
    public AuthenResponse register(UserCreateRequest userCreateRequest) {
        return null;
    }

    @Override
    public void logout(String token) {

    }

    @Override
    public AuthenResponse refreshToken(String refreshToken) {
        return null;
    }

    @Override
    public boolean validateToken(String token) {
        return false;
    }
}
