package com.UsdtWallet.UsdtWallet.utils;

import java.security.MessageDigest;

public class StaticFunctionCommon {
    public static byte[] doubleDigest(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] first = digest.digest(input);
            return digest.digest(first);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
