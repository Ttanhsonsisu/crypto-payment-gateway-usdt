package com.UsdtWallet.UsdtWallet.service.Interface;


import com.UsdtWallet.UsdtWallet.model.dto.request.LoginRequest;
import com.UsdtWallet.UsdtWallet.model.dto.request.UserCreateRequest;
import com.UsdtWallet.UsdtWallet.model.dto.response.AuthenResponse;

public interface AuthService {
    AuthenResponse login(LoginRequest loginRequest);
    AuthenResponse register(UserCreateRequest userCreateRequest);
    void logout(String token);
    AuthenResponse refreshToken(String refreshToken);
    boolean validateToken(String token);
}
