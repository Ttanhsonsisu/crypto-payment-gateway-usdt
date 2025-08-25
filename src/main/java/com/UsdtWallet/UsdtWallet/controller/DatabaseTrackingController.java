package com.UsdtWallet.UsdtWallet.controller;

import com.UsdtWallet.UsdtWallet.model.entity.TokenSweep;
import com.UsdtWallet.UsdtWallet.model.entity.GasTopup;
import com.UsdtWallet.UsdtWallet.repository.TokenSweepRepository;
import com.UsdtWallet.UsdtWallet.repository.GasTopupRepository;
import com.UsdtWallet.UsdtWallet.service.UsdtSweepService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/tracking")
@RequiredArgsConstructor
@Slf4j
public class DatabaseTrackingController {

    private final TokenSweepRepository tokenSweepRepository;
    private final GasTopupRepository gasTopupRepository;
    private final UsdtSweepService usdtSweepService;

    /**
     * Xem tất cả TokenSweep records - GIẢI QUYẾT VẤN ĐỀ DATABASE KHÔNG CẬP NHẬT
     */
    @GetMapping("/token-sweeps")
    public ResponseEntity<Map<String, Object>> getTokenSweeps(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "20") int limit) {

        try {
            TokenSweep.SweepStatus sweepStatus = TokenSweep.SweepStatus.valueOf(status.toUpperCase());
            List<TokenSweep> sweeps = tokenSweepRepository.findByStatus(sweepStatus);

            // Limit results
            List<TokenSweep> limitedSweeps = sweeps.stream().limit(limit).toList();

            Map<String, Object> response = new HashMap<>();
            response.put("total_count", sweeps.size());
            response.put("showing_count", limitedSweeps.size());
            response.put("status_filter", status);
            response.put("sweeps", limitedSweeps);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Lỗi lấy token sweeps: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage(),
                "available_statuses", List.of("PENDING", "CONFIRMED", "FAILED")
            ));
        }
    }

    /**
     * Xem tất cả GasTopup records
     */
    @GetMapping("/gas-topups")
    public ResponseEntity<Map<String, Object>> getGasTopups(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "20") int limit) {

        try {
            GasTopup.TopupStatus topupStatus = GasTopup.TopupStatus.valueOf(status.toUpperCase());
            List<GasTopup> topups = gasTopupRepository.findByStatus(topupStatus);

            // Limit results
            List<GasTopup> limitedTopups = topups.stream().limit(limit).toList();

            Map<String, Object> response = new HashMap<>();
            response.put("total_count", topups.size());
            response.put("showing_count", limitedTopups.size());
            response.put("status_filter", status);
            response.put("topups", limitedTopups);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Lỗi lấy gas topups: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage(),
                "available_statuses", List.of("PENDING", "SENT", "CONFIRMED", "FAILED")
            ));
        }
    }

    /**
     * Dashboard tổng quan database tracking
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getTrackingDashboard() {
        try {
            Map<String, Object> dashboard = new HashMap<>();

            // Token Sweep Statistics
            long pendingSweeps = tokenSweepRepository.findByStatus(TokenSweep.SweepStatus.PENDING).size();
            long confirmedSweeps = tokenSweepRepository.findByStatus(TokenSweep.SweepStatus.CONFIRMED).size();
            long failedSweeps = tokenSweepRepository.findByStatus(TokenSweep.SweepStatus.FAILED).size();

            dashboard.put("token_sweeps", Map.of(
                "pending", pendingSweeps,
                "confirmed", confirmedSweeps,
                "failed", failedSweeps,
                "total", pendingSweeps + confirmedSweeps + failedSweeps
            ));

            // Gas Topup Statistics
            long pendingTopups = gasTopupRepository.findByStatus(GasTopup.TopupStatus.PENDING).size();
            long sentTopups = gasTopupRepository.findByStatus(GasTopup.TopupStatus.SENT).size();
            long confirmedTopups = gasTopupRepository.findByStatus(GasTopup.TopupStatus.CONFIRMED).size();
            long failedTopups = gasTopupRepository.findByStatus(GasTopup.TopupStatus.FAILED).size();

            dashboard.put("gas_topups", Map.of(
                "pending", pendingTopups,
                "sent", sentTopups,
                "confirmed", confirmedTopups,
                "failed", failedTopups,
                "total", pendingTopups + sentTopups + confirmedTopups + failedTopups
            ));

            // System Status
            dashboard.put("system_status", Map.of(
                "database_active", true,
                "tracking_enabled", true,
                "last_updated", LocalDateTime.now()
            ));

            return ResponseEntity.ok(dashboard);

        } catch (Exception e) {
            log.error("❌ Lỗi lấy tracking dashboard: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Trigger manual confirmation check cho pending sweeps
     */
    @PostMapping("/token-sweeps/check-confirmations")
    public ResponseEntity<Map<String, Object>> checkSweepConfirmations() {
        try {
            log.info("🔄 Manual trigger: Checking token sweep confirmations");

            // Gọi method confirmation từ UsdtSweepService
            usdtSweepService.confirmPendingTokenSweeps();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Đã trigger check confirmations cho token sweeps");
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Lỗi trigger check confirmations: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lấy TokenSweep theo child index
     */
    @GetMapping("/token-sweeps/child/{childIndex}")
    public ResponseEntity<List<TokenSweep>> getSweepsByChildIndex(@PathVariable Integer childIndex) {
        try {
            List<TokenSweep> sweeps = tokenSweepRepository.findByChildIndexAndStatus(
                childIndex, TokenSweep.SweepStatus.PENDING);

            return ResponseEntity.ok(sweeps);

        } catch (Exception e) {
            log.error("❌ Lỗi lấy sweeps cho child {}: {}", childIndex, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Lấy GasTopup theo child index
     */
    @GetMapping("/gas-topups/child/{childIndex}")
    public ResponseEntity<List<GasTopup>> getTopupsByChildIndex(@PathVariable Integer childIndex) {
        try {
            List<GasTopup> topups = gasTopupRepository.findByChildIndexOrderByCreatedAtDesc(childIndex);

            return ResponseEntity.ok(topups);

        } catch (Exception e) {
            log.error("❌ Lỗi lấy topups cho child {}: {}", childIndex, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
}
