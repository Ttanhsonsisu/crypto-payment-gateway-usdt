package com.UsdtWallet.UsdtWallet.controller;

import com.UsdtWallet.UsdtWallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/new-address")
    public String getNewAddress() throws Exception {
        return walletService.getFreeAddressFromPool();
    }
}
