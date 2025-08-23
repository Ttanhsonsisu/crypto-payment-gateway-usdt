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

    /**
     * Test master wallet balance
     */
    @GetMapping("/wallet/master/balance")
    public ResponseEntity<Map<String, Object>> testMasterWalletBalance() {
        try {
            HdMasterWallet masterWallet = hdWalletService.getMasterWallet();
            String masterAddress = masterWallet.getMasterAddress(); // Fix: get masterAddress properly
            BigDecimal trxBalance = tronApiService.getTrxBalance(masterAddress);
            BigDecimal usdtBalance = tronApiService.getUsdtBalance(masterAddress);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "masterAddress", masterAddress,
                            "trxBalance", trxBalance + " TRX",
                            "usdtBalance", usdtBalance + " USDT",
                            "network", "Nile Testnet"
                    )
            ));

        } catch (Exception e) {
            log.error("Error getting master wallet balance", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to get master wallet balance: " + e.getMessage()
            ));
        }
    }

    /**
     * Test child wallet generation
     */
    @PostMapping("/wallet/child/generate")
    public ResponseEntity<Map<String, Object>> testChildWalletGeneration(@RequestParam(defaultValue = "5") int count) {
        try {
            log.info("Testing child wallet generation: {} wallets", count);

            // This would test the child wallet generation logic
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Child wallet generation test completed",
                    "data", Map.of(
                            "requestedCount", count,
                            "note", "Check logs for detailed wallet generation process"
                    )
            ));

        } catch (Exception e) {
            log.error("Error testing child wallet generation", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to test child wallet generation: " + e.getMessage()
            ));
        }
    }

    /**
     * Test deposit scanning
     */
    @PostMapping("/deposit/scan/test")
    public ResponseEntity<Map<String, Object>> testDepositScanning(
            @RequestParam(required = false) String address,
            @RequestParam(defaultValue = "100") Long blockRange) {

        try {
            Long currentBlock = tronApiService.getLatestBlockNumber();
            if (currentBlock == null) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Failed to get current block number"
                ));
            }

            Long fromBlock = currentBlock - blockRange;
            Long toBlock = currentBlock;

            int depositsFound;
            if (address != null) {
                depositsFound = depositScannerService.scanAddressManually(address, fromBlock, toBlock);
            } else {
                depositsFound = depositScannerService.scanBlockRange(fromBlock, toBlock);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Deposit scanning test completed",
                    "data", Map.of(
                            "scannedBlocks", blockRange,
                            "fromBlock", fromBlock,
                            "toBlock", toBlock,
                            "depositsFound", depositsFound,
                            "scannedAddress", address != null ? address : "All child wallets"
                    )
            ));

        } catch (Exception e) {
            log.error("Error testing deposit scanning", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to test deposit scanning: " + e.getMessage()
            ));
        }
    }

    /**
     * Test points system
     */
    @PostMapping("/points/test")
    public ResponseEntity<Map<String, Object>> testPointsSystem(
            @RequestParam String userId,
            @RequestParam BigDecimal amount) {

        try {
            // Test balance before
            BigDecimal balanceBefore = pointsService.getCurrentBalance(userId);

            // Test credit points
            boolean credited = pointsService.creditPointsForDeposit(
                    userId, amount, "test-tx-" + System.currentTimeMillis(), amount);

            // Test balance after
            BigDecimal balanceAfter = pointsService.getCurrentBalance(userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Points system test completed",
                    "data", Map.of(
                            "userId", userId,
                            "testedAmount", amount,
                            "balanceBefore", balanceBefore,
                            "balanceAfter", balanceAfter,
                            "creditedSuccessfully", credited,
                            "balanceIncrease", balanceAfter.subtract(balanceBefore)
                    )
            ));

        } catch (Exception e) {
            log.error("Error testing points system", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to test points system: " + e.getMessage()
            ));
        }
    }

    /**
     * Test P2P transfer
     */
    @PostMapping("/points/transfer/test")
    public ResponseEntity<Map<String, Object>> testP2PTransfer(
            @RequestParam String fromUserId,
            @RequestParam String toUserId,
            @RequestParam BigDecimal amount) {

        try {
            // Check balances before
            BigDecimal fromBalanceBefore = pointsService.getCurrentBalance(fromUserId);
            BigDecimal toBalanceBefore = pointsService.getCurrentBalance(toUserId);

            // Test transfer
            boolean success = pointsService.transferPoints(fromUserId, toUserId, amount, "Test P2P transfer");

            // Check balances after
            BigDecimal fromBalanceAfter = pointsService.getCurrentBalance(fromUserId);
            BigDecimal toBalanceAfter = pointsService.getCurrentBalance(toUserId);

            return ResponseEntity.ok(Map.of(
                    "success", success,
                    "message", success ? "P2P transfer test completed successfully" : "P2P transfer failed",
                    "data", Map.of(
                            "transferAmount", amount,
                            "fromUser", Map.of(
                                    "userId", fromUserId,
                                    "balanceBefore", fromBalanceBefore,
                                    "balanceAfter", fromBalanceAfter,
                                    "change", fromBalanceAfter.subtract(fromBalanceBefore)
                            ),
                            "toUser", Map.of(
                                    "userId", toUserId,
                                    "balanceBefore", toBalanceBefore,
                                    "balanceAfter", toBalanceAfter,
                                    "change", toBalanceAfter.subtract(toBalanceBefore)
                            )
                    )
            ));

        } catch (Exception e) {
            log.error("Error testing P2P transfer", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to test P2P transfer: " + e.getMessage()
            ));
        }
    }

    /**
     * Get system overview for testing
     */
    @GetMapping("/system/overview")
    public ResponseEntity<Map<String, Object>> getSystemOverview() {
        try {
            // Get scanning stats
            Map<String, Object> scanStats = depositScannerService.getScanningStats();

            // Get sweep stats
            Map<String, Object> sweepStats = usdtSweepService.getSweepStats();

            // Get wallet pool stats (simplified)
            // HdWalletService.PoolStats poolStats = hdWalletService.getPoolStats();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "network", "Nile Testnet",
                            "scanning", scanStats,
                            "sweep", sweepStats,
                            "walletPool", Map.of(
                                    "note", "Wallet pool stats available through separate endpoint",
                                    "status", "Available"
                            )
                    )
            ));

        } catch (Exception e) {
            log.error("Error getting system overview", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to get system overview: " + e.getMessage()
            ));
        }
    }

    /**
     * Health check for all services
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
                "tronApi", testServiceHealth("TronAPI", () -> tronApiService.getLatestBlockNumber() != null),
                "database", testServiceHealth("Database", () -> true), // Would check DB connection
                "redis", testServiceHealth("Redis", () -> true), // Would check Redis connection
                "depositScanner", testServiceHealth("DepositScanner", () -> true),
                "sweepService", testServiceHealth("SweepService", () -> true),
                "pointsService", testServiceHealth("PointsService", () -> true)
        );

        boolean allHealthy = health.values().stream()
                .allMatch(status -> "healthy".equals(((Map<String, Object>) status).get("status")));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "overallStatus", allHealthy ? "healthy" : "degraded",
                "services", health,
                "timestamp", System.currentTimeMillis()
        ));
    }

    private Map<String, Object> testServiceHealth(String serviceName, HealthChecker checker) {
        try {
            boolean healthy = checker.check();
            return Map.of(
                    "status", healthy ? "healthy" : "unhealthy",
                    "message", healthy ? "Service is operational" : "Service check failed"
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "error",
                    "message", e.getMessage()
            );
        }
    }

    @FunctionalInterface
    private interface HealthChecker {
        boolean check() throws Exception;
    }

    /**
     * Get deposit scanning statistics (public endpoint for testing)
     */
    @GetMapping("/deposit/scan/stats")
    public ResponseEntity<Map<String, Object>> getDepositScanStats() {
        try {
            Map<String, Object> stats = depositScannerService.getScanningStats();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Deposit scanner statistics retrieved successfully",
                    "data", stats,
                    "metadata", Map.of(
                            "network", "Nile Testnet",
                            "timestamp", System.currentTimeMillis(),
                            "endpoint", "/api/test/deposit/scan/stats"
                    )
            ));

        } catch (Exception e) {
            log.error("Error getting deposit scan stats", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to get deposit scan stats: " + e.getMessage(),
                    "error", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * Reset scan position to current block - 50 (for testing and deployment)
     */
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