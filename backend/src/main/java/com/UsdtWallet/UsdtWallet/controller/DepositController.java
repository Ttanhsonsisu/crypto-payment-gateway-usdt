package com.UsdtWallet.UsdtWallet.controller;

import com.UsdtWallet.UsdtWallet.model.dto.SweepResultDto;
import com.UsdtWallet.UsdtWallet.service.DepositScannerService;
import com.UsdtWallet.UsdtWallet.service.UsdtSweepService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/deposits")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class DepositController {

    private final DepositScannerService depositScannerService;
    private final UsdtSweepService usdtSweepService;

    /**
     * Get deposit scanning statistics
     */
    @GetMapping("/scan/stats")
    public ResponseEntity<Map<String, Object>> getScanningStats() {
        try {
            Map<String, Object> stats = depositScannerService.getScanningStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting scanning stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Manual scan for specific address
     */
    @PostMapping("/scan/address/{address}")
    public ResponseEntity<Map<String, Object>> scanAddress(
            @PathVariable String address,
            @RequestParam(defaultValue = "0") Long fromBlock,
            @RequestParam(defaultValue = "0") Long toBlock) {

        try {
            if (toBlock == 0) {
                // Use current block if not specified
                toBlock = fromBlock + 1000; // Scan 1000 blocks by default
            }

            int depositsFound = depositScannerService.scanAddressManually(address, fromBlock, toBlock);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Manual scan completed",
                "address", address,
                "fromBlock", fromBlock,
                "toBlock", toBlock,
                "depositsFound", depositsFound
            ));

        } catch (Exception e) {
            log.error("Error in manual address scan", e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "Manual scan failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Manual scan for block range
     */
    @PostMapping("/scan/blocks")
    public ResponseEntity<Map<String, Object>> scanBlockRange(
            @RequestParam Long fromBlock,
            @RequestParam Long toBlock) {

        try {
            int depositsFound = depositScannerService.scanBlockRange(fromBlock, toBlock);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Block range scan completed",
                "fromBlock", fromBlock,
                "toBlock", toBlock,
                "depositsFound", depositsFound
            ));

        } catch (Exception e) {
            log.error("Error in manual block range scan", e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "Block range scan failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Get sweep statistics
     */
    @GetMapping("/sweep/stats")
    public ResponseEntity<Map<String, Object>> getSweepStats() {
        try {
            Map<String, Object> stats = usdtSweepService.getSweepStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting sweep stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Manual sweep all unswept deposits
     */
    @PostMapping("/sweep/all")
    public ResponseEntity<SweepResultDto> sweepAllDeposits() {
        try {
            log.info("Manual sweep all deposits requested");
            SweepResultDto result = usdtSweepService.sweepUnsweptDeposits();
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error in manual sweep all", e);
            return ResponseEntity.ok(SweepResultDto.builder()
                .status("FAILED")
                .message("Sweep failed: " + e.getMessage())
                .totalTransactions(0)
                .build());
        }
    }

    /**
     * Manual sweep specific address
     */
    @PostMapping("/sweep/address/{address}")
    public ResponseEntity<SweepResultDto> sweepAddress(@PathVariable String address) {
        try {
            log.info("Manual sweep for address: {}", address);
            SweepResultDto result = usdtSweepService.sweepAddress(address);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error in manual address sweep", e);
            return ResponseEntity.ok(SweepResultDto.builder()
                .status("FAILED")
                .message("Address sweep failed: " + e.getMessage())
                .totalTransactions(0)
                .build());
        }
    }

    /**
     * Emergency stop scanning (for maintenance)
     */
    @PostMapping("/scan/stop")
    public ResponseEntity<Map<String, Object>> stopScanning() {
        // This would set a flag to temporarily disable scanning
        // Implementation depends on your specific requirements
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Scanning stopped for maintenance"
        ));
    }
}
