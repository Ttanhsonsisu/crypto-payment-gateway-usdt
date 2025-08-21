package com.UsdtWallet.UsdtWallet.service;


import com.UsdtWallet.UsdtWallet.model.entity.ChildWallet;
import com.UsdtWallet.UsdtWallet.repository.ChildWalletRepository;
import jakarta.annotation.PostConstruct;
import org.bitcoinj.base.Base58;
import org.bitcoinj.crypto.*;
import org.bitcoinj.protobuf.wallet.Protos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

import static com.UsdtWallet.UsdtWallet.utils.StaticFunctionCommon.doubleDigest;

@Service
public class WalletService {
    @Value("${app.encryption.key}")
    private String encryptionKey;

    @Autowired
    private ChildWalletRepository childWalletRepository;

    private static final String MNEMONIC_FILE = "mnemonic.encrypted";

    public String generateAndStoreMnemonic() throws Exception {
        // 128-bit entropy -> 12 words
        byte[] entropy = new byte[16];
        new SecureRandom().nextBytes(entropy);
        List<String> words = MnemonicCode.INSTANCE.toMnemonic(entropy);
        String mnemonic = String.join(" ", words);

        // Encrypt with AES
        SecretKeySpec keySpec = buildAesKey(encryptionKey);
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(mnemonic.getBytes(StandardCharsets.UTF_8));

        // Store to file
        try (FileOutputStream fos = new FileOutputStream(new File(MNEMONIC_FILE))) {
            fos.write(encrypted);
        }

        // Trả mnemonic để setup ban đầu; đừng log/return trong production
        return mnemonic;
    }

    public String loadMnemonic() throws Exception {
        byte[] encrypted = Files.readAllBytes(new File(MNEMONIC_FILE).toPath());
        SecretKeySpec keySpec = buildAesKey(encryptionKey);
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] plain = cipher.doFinal(encrypted);
        return new String(plain, StandardCharsets.UTF_8);
    }

    public String deriveChildAddress(int index) throws Exception {
        String mnemonic = loadMnemonic();

        // Seed từ mnemonic (BIP39)
        List<String> words = Arrays.asList(mnemonic.trim().split("\\s+"));
        byte[] seedBytes = MnemonicCode.toSeed(words, ""); // passphrase rỗng

        // Master key (BIP32)
        DeterministicKey masterPrivKey = HDKeyDerivation.createMasterPrivateKey(seedBytes);

        // Path m/44'/195'/0'/0/index (Tron coin type 195)
        List<ChildNumber> path = Arrays.asList(
                new ChildNumber(44, true),
                new ChildNumber(195, true),
                new ChildNumber(0, true),
                ChildNumber.ZERO,
                new ChildNumber(index, false)
        );

        DeterministicHierarchy hierarchy = new DeterministicHierarchy(masterPrivKey);
        DeterministicKey childKey = hierarchy.get(path, true, true);

        // Lấy public key uncompressed
        ECKey ecKey = ECKey.fromPrivate(childKey.getPrivKeyBytes(), false);
        byte[] pubBytes = ecKey.getPubKey();           // 65 bytes, bắt đầu 0x04
        byte[] pubNoPrefix = Arrays.copyOfRange(pubBytes, 1, pubBytes.length); // 64 bytes

        // Keccak-256 (web3j)
        byte[] hashed = Hash.sha3(pubNoPrefix);        // 32 bytes

        // Tron address: 0x41 + last 20 bytes (bytes 12..31)
        byte[] addressBytes = new byte[21];
        addressBytes[0] = 0x41;
        System.arraycopy(hashed, 12, addressBytes, 1, 20);

        // Base58Check
        return base58CheckEncode(addressBytes);
    }

    // ---- Helpers ----

    private static String base58CheckEncode(byte[] payload) {
        byte[] checksumFull = doubleDigest(payload);
        byte[] out = Arrays.copyOf(payload, payload.length + 4);
        System.arraycopy(checksumFull, 0, out, payload.length, 4);
        return Base58.encode(out);
    }

    private static SecretKeySpec buildAesKey(String keyStr) {
        byte[] k = keyStr.getBytes(StandardCharsets.UTF_8);
        if (!(k.length == 16 || k.length == 24 || k.length == 32)) {
            throw new IllegalArgumentException("app.encryption.key must be 16/24/32 bytes (AES-128/192/256). Current length=" + k.length);
        }
        return new SecretKeySpec(k, "AES");
    }

    public String getFreeAddressFromPool() throws Exception {
        ChildWallet freeWallet = childWalletRepository.findFirstByUsedFalse();
        if (freeWallet == null) {
            // Generate more if pool empty
            preGenerateChildWallets(10); // Example
            freeWallet = childWalletRepository.findFirstByUsedFalse();
        }
        freeWallet.setUsed(true);
        childWalletRepository.save(freeWallet);
        return freeWallet.getAddress();
    }

    @PostConstruct
    public void initPool() throws Exception {

        File file = new File(MNEMONIC_FILE);
        if (!file.exists()) {
            // Lần đầu chạy -> sinh mnemonic mới và lưu
            String mnemonic = generateAndStoreMnemonic();
            System.out.println("Generated new mnemonic (chỉ dùng để backup, không log trong production): " + mnemonic);
        }

        if (childWalletRepository.count() == 0) {
            preGenerateChildWallets(100); // Initial pool
        }
    }

    private void preGenerateChildWallets(int count) throws Exception {
        int maxIndex = childWalletRepository.findMaxIndex().orElse(0);
        for (int i = 1; i <= count; i++) {
            int index = maxIndex + i;
            String address = deriveChildAddress(index);
            ChildWallet wallet = new ChildWallet();
            wallet.setIndex(index);
            wallet.setAddress(address);
            wallet.setUsed(false);
            childWalletRepository.save(wallet);
        }
    }

}
