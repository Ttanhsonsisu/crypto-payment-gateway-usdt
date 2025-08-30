package com.UsdtWallet.UsdtWallet.controller;

import com.UsdtWallet.UsdtWallet.model.entity.HdMasterWallet;
import com.UsdtWallet.UsdtWallet.service.TronApiService;
import com.UsdtWallet.UsdtWallet.service.DepositScannerService;
import com.UsdtWallet.UsdtWallet.service.UsdtSweepService;
import com.UsdtWallet.UsdtWallet.service.PointsService;
import com.UsdtWallet.UsdtWallet.service.HdWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
// @PreAuthorize("hasRole('ADMIN')") // Temporarily disabled for testing
public class TestController {

    private final TronApiService tronApiService;
    private final DepositScannerService depositScannerService;
    private final UsdtSweepService usdtSweepService;
    private final PointsService pointsService;
    private final HdWalletService hdWalletService;

    /**
     * Test Nile testnet connectivity
     */
    @GetMapping("/nile/connection")
    public ResponseEntity<Map<String, Object>> testNileConnection() {
        try {
            Long currentBlock = tronApiService.getLatestBlockNumber();
            Map<String, Object> networkInfo = tronApiService.getNetworkInfo();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Connected to Nile testnet successfully",
                    "data", Map.of(
                            "currentBlock", currentBlock != null ? currentBlock : "Failed to get",
                            "networkInfo", networkInfo,
                            "apiUrl", "https://nile.trongrid.io"
                    )
            ));

        } catch (Exception e) {
            log.error("Error testing Nile connection", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to connect to Nile testnet: " + e.getMessage()
            ));
        }
    }
    @PostMapping("/deposit/scan/reset")
    public ResponseEntity<Map<String, Object>> resetScanPosition() {
        try {
            Long currentBlock = tronApiService.getLatestBlockNumber();
            if (currentBlock == null) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Failed to get current block number"
                ));
            }

            // Reset to current block - 50 for fresh scanning
            Long newScanPosition = currentBlock - 50;
            depositScannerService.resetScanPosition(newScanPosition);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Scan position reset successfully",
                    "data", Map.of(
                            "currentBlock", currentBlock,
                            "newScanPosition", newScanPosition,
                            "blocksToScan", 50,
                            "reason", "Reset for fresh scanning from recent blocks"
                    )
            ));

        } catch (Exception e) {
            log.error("Error resetting scan position", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to reset scan position: " + e.getMessage()
            ));
        }
    }
}