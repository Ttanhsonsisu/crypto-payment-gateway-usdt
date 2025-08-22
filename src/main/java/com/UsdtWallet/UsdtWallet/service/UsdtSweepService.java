package com.UsdtWallet.UsdtWallet.service;

import com.UsdtWallet.UsdtWallet.model.dto.SweepResultDto;
import com.UsdtWallet.UsdtWallet.model.entity.WalletTransaction;
import com.UsdtWallet.UsdtWallet.model.entity.HdMasterWallet;
import com.UsdtWallet.UsdtWallet.repository.WalletTransactionRepository;
import com.UsdtWallet.UsdtWallet.util.TronAddressUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsdtSweepService {

    private final TronApiService tronApiService;
    private final WalletTransactionRepository walletTransactionRepository;
    private final HdWalletService hdWalletService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${sweep.min.amount:5}")
    private BigDecimal minimumSweepAmount;

    @Value("${sweep.gas.limit:15}")
    private BigDecimal gasLimitTrx;

    @Value("${sweep.batch.size:10}")
    private Integer sweepBatchSize;

    @Value("${sweep.enabled:true}")
    private Boolean sweepEnabled;

    @Value("${tron.usdt.contract:TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t}")
    private String usdtContractAddress;

    private static final String SWEEP_LOCK_KEY = "sweep:lock";
    private static final String SWEEP_STATS_KEY = "sweep:stats";

    /**
     * Scheduled sweep every 5 minutes
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void scheduledSweep() {
        if (!sweepEnabled) {
            log.debug("USDT sweep is disabled");
            return;
        }

        try {
            // Acquire lock to prevent multiple sweep processes
            Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(SWEEP_LOCK_KEY, "locked", 600, TimeUnit.SECONDS); // 10 min lock

            if (!Boolean.TRUE.equals(lockAcquired)) {
                log.debug("Sweep already in progress, skipping...");
                return;
            }

            log.info("🧹 Starting scheduled USDT sweep...");

            SweepResultDto result = sweepUnsweptDeposits();

            log.info("✅ Scheduled sweep completed: {} transactions, {} USDT total",
                result.getTotalTransactions(), result.getTotalAmount());

            // Store sweep stats
            redisTemplate.opsForValue().set(SWEEP_STATS_KEY, result, 24, TimeUnit.HOURS);

        } catch (Exception e) {
            log.error("❌ Error during scheduled sweep", e);
        } finally {
            // Release lock
            redisTemplate.delete(SWEEP_LOCK_KEY);
        }
    }

    /**
     * Sweep all unswept deposits
     */
    @Transactional
    public SweepResultDto sweepUnsweptDeposits() {
        // Get master wallet info first
        HdMasterWallet masterWallet = hdWalletService.getMasterWallet();
        String masterAddress = masterWallet.getMasterAddress();

        SweepResultDto.SweepResultDtoBuilder resultBuilder = SweepResultDto.builder()
            .masterWalletAddress(masterAddress)
            .successfulSweeps(new ArrayList<>())
            .failedSweeps(new ArrayList<>())
            .totalAmount(BigDecimal.ZERO)
            .totalGasUsed(BigDecimal.ZERO)
            .totalTransactions(0);

        try {
            // Get unswept deposits
            List<WalletTransaction> unsweptDeposits = walletTransactionRepository.findUnsweptDeposits();

            if (unsweptDeposits.isEmpty()) {
                log.info("No unswept deposits found");
                return resultBuilder.status("SUCCESS").message("No deposits to sweep").build();
            }

            log.info("Found {} unswept deposits to process", unsweptDeposits.size());

            // Check master wallet TRX balance for gas
            BigDecimal masterTrxBalance = tronApiService.getTrxBalance(masterAddress);
            BigDecimal requiredGas = gasLimitTrx.multiply(new BigDecimal(Math.min(unsweptDeposits.size(), sweepBatchSize)));

            if (masterTrxBalance.compareTo(requiredGas) < 0) {
                String message = String.format("Insufficient TRX for gas. Required: %s, Available: %s",
                    requiredGas, masterTrxBalance);
                log.error("❌ " + message);
                return resultBuilder.status("FAILED").message(message).build();
            }

            // Process deposits in batches
            List<WalletTransaction> depositsToSweep = unsweptDeposits.stream()
                .filter(tx -> tx.getAmount().compareTo(minimumSweepAmount) >= 0)
                .limit(sweepBatchSize)
                .toList();

            int successCount = 0;
            BigDecimal totalSwept = BigDecimal.ZERO;
            BigDecimal totalGas = BigDecimal.ZERO;
            List<SweepResultDto.SweepTransactionDto> successfulSweeps = new ArrayList<>();
            List<SweepResultDto.SweepTransactionDto> failedSweeps = new ArrayList<>();

            for (WalletTransaction deposit : depositsToSweep) {
                try {
                    SweepResultDto.SweepTransactionDto sweepResult = sweepSingleDeposit(deposit);

                    if ("SUCCESS".equals(sweepResult.getStatus())) {
                        successfulSweeps.add(sweepResult);
                        successCount++;
                        totalSwept = totalSwept.add(sweepResult.getAmount());
                        totalGas = totalGas.add(sweepResult.getGasUsed());
                    } else {
                        failedSweeps.add(sweepResult);
                    }

                } catch (Exception e) {
                    log.error("Error sweeping deposit {}: {}", deposit.getTxHash(), e.getMessage());

                    SweepResultDto.SweepTransactionDto failedSweep = SweepResultDto.SweepTransactionDto.builder()
                        .fromAddress(deposit.getToAddress())
                        .amount(deposit.getAmount())
                        .status("FAILED")
                        .errorMessage(e.getMessage())
                        .build();

                    failedSweeps.add(failedSweep);
                }
            }

            String status = successCount > 0 ? "SUCCESS" : "FAILED";
            String message = String.format("Swept %d/%d deposits, Total: %s USDT",
                successCount, depositsToSweep.size(), totalSwept);

            return resultBuilder
                .status(status)
                .message(message)
                .totalTransactions(successCount)
                .totalAmount(totalSwept)
                .totalGasUsed(totalGas)
                .successfulSweeps(successfulSweeps)
                .failedSweeps(failedSweeps)
                .build();

        } catch (Exception e) {
            log.error("Error during sweep process", e);
            return resultBuilder
                .status("FAILED")
                .message("Sweep failed: " + e.getMessage())
                .build();
        }
    }

    /**
     * Sweep a single deposit
     */
    @Transactional
    public SweepResultDto.SweepTransactionDto sweepSingleDeposit(WalletTransaction deposit) {
        try {
            String fromAddress = deposit.getToAddress(); // Child wallet
            HdMasterWallet masterWallet = hdWalletService.getMasterWallet();
            String masterAddress = masterWallet.getMasterAddress();
            BigDecimal amount = deposit.getAmount();

            log.info("🧹 Sweeping {} USDT from {} to master wallet", amount, fromAddress);

            // Check current USDT balance of child wallet
            BigDecimal currentBalance = tronApiService.getUsdtBalance(fromAddress);
            if (currentBalance.compareTo(amount) < 0) {
                String error = String.format("Insufficient balance. Expected: %s, Current: %s", amount, currentBalance);
                log.warn(error);

                return SweepResultDto.SweepTransactionDto.builder()
                    .fromAddress(fromAddress)
                    .amount(amount)
                    .status("FAILED")
                    .errorMessage(error)
                    .build();
            }

            // Get private key for child wallet - simplified for now
            // TronAddressUtil.WalletInfo walletInfo = hdWalletService.getChildWalletInfo(fromAddress);
            // For now, we'll skip the actual private key operations
            log.warn("Sweep transaction creation is placeholder - needs TronWeb integration");

            // Check TRX balance for gas
            BigDecimal trxBalance = tronApiService.getTrxBalance(fromAddress);
            if (trxBalance.compareTo(gasLimitTrx) < 0) {
                // Need to send TRX for gas first
                boolean gasSent = sendGasToChildWallet(fromAddress, gasLimitTrx);
                if (!gasSent) {
                    throw new RuntimeException("Failed to send gas to child wallet");
                }

                // Wait a bit for gas transaction to confirm
                Thread.sleep(3000);
            }

            // Create and sign USDT transfer transaction (placeholder)
            String rawTransaction = createUsdtTransferTransaction(
                "placeholder_private_key",
                fromAddress,
                masterAddress,
                amount
            );

            // Broadcast transaction
            String txHash = tronApiService.broadcastTransaction(rawTransaction);
            if (txHash == null) {
                throw new RuntimeException("Failed to broadcast sweep transaction");
            }

            // Update deposit as swept
            deposit.setIsSwept(true);
            deposit.setSweepTxHash(txHash);
            deposit.setSweptAt(LocalDateTime.now());
            walletTransactionRepository.save(deposit);

            // Create sweep transaction record
            WalletTransaction sweepTx = WalletTransaction.builder()
                .txHash(txHash)
                .fromAddress(fromAddress)
                .toAddress(masterAddress)
                .amount(amount)
                .tokenAddress(usdtContractAddress)
                .transactionType(WalletTransaction.TransactionType.SWEEP)
                .status(WalletTransaction.TransactionStatus.PENDING)
                .userId(deposit.getUserId())
                .gasUsed(gasLimitTrx)
                .build();

            walletTransactionRepository.save(sweepTx);

            log.info("✅ Sweep transaction broadcasted: {} for {} USDT", txHash, amount);

            return SweepResultDto.SweepTransactionDto.builder()
                .fromAddress(fromAddress)
                .txHash(txHash)
                .amount(amount)
                .status("SUCCESS")
                .gasUsed(gasLimitTrx)
                .build();

        } catch (Exception e) {
            log.error("Error sweeping deposit {}: {}", deposit.getTxHash(), e.getMessage());

            return SweepResultDto.SweepTransactionDto.builder()
                .fromAddress(deposit.getToAddress())
                .amount(deposit.getAmount())
                .status("FAILED")
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Send TRX for gas to child wallet
     */
    private boolean sendGasToChildWallet(String childAddress, BigDecimal gasAmount) {
        try {
            log.info("⛽ Sending {} TRX gas to {}", gasAmount, childAddress);

            // Get master address properly
            HdMasterWallet masterWallet = hdWalletService.getMasterWallet();
            String masterAddress = masterWallet.getMasterAddress();

            // Create TRX transfer transaction (placeholder)
            String rawTransaction = createTrxTransferTransaction(
                "placeholder_master_key",
                masterAddress,
                childAddress,
                gasAmount
            );

            // Broadcast transaction
            String txHash = tronApiService.broadcastTransaction(rawTransaction);
            if (txHash != null) {
                log.info("✅ Gas sent: {} TRX to {}, TX: {}", gasAmount, childAddress, txHash);
                return true;
            }

        } catch (Exception e) {
            log.error("Error sending gas to child wallet {}: {}", childAddress, e.getMessage());
        }

        return false;
    }

    /**
     * Create USDT transfer transaction (simplified)
     */
    private String createUsdtTransferTransaction(String privateKey, String from, String to, BigDecimal amount) {
        // This is a simplified implementation
        // In reality, you would use TronWeb or similar library to create the transaction
        log.info("Creating USDT transfer: {} USDT from {} to {}", amount, from, to);

        // Placeholder for actual transaction creation
        // You would need to:
        // 1. Create TRC20 transfer call data
        // 2. Create transaction with proper gas limit
        // 3. Sign with private key
        // 4. Return raw transaction hex

        return "placeholder_raw_transaction_hex";
    }

    /**
     * Create TRX transfer transaction (simplified)
     */
    private String createTrxTransferTransaction(String privateKey, String from, String to, BigDecimal amount) {
        // This is a simplified implementation
        log.info("Creating TRX transfer: {} TRX from {} to {}", amount, from, to);

        // Placeholder for actual transaction creation
        return "placeholder_raw_transaction_hex";
    }

    /**
     * Manual sweep for specific address
     */
    public SweepResultDto sweepAddress(String address) {
        log.info("🧹 Manual sweep for address: {}", address);

        List<WalletTransaction> unsweptDeposits = walletTransactionRepository
            .findUnsweptDepositsByAddress(address);

        if (unsweptDeposits.isEmpty()) {
            return SweepResultDto.builder()
                .status("SUCCESS")
                .message("No unswept deposits found for address")
                .totalTransactions(0)
                .totalAmount(BigDecimal.ZERO)
                .build();
        }

        // Process each deposit
        List<SweepResultDto.SweepTransactionDto> successful = new ArrayList<>();
        List<SweepResultDto.SweepTransactionDto> failed = new ArrayList<>();

        for (WalletTransaction deposit : unsweptDeposits) {
            SweepResultDto.SweepTransactionDto result = sweepSingleDeposit(deposit);
            if ("SUCCESS".equals(result.getStatus())) {
                successful.add(result);
            } else {
                failed.add(result);
            }
        }

        BigDecimal totalSwept = successful.stream()
            .map(SweepResultDto.SweepTransactionDto::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Fix the last remaining getMasterWalletAddress call
        HdMasterWallet masterWallet = hdWalletService.getMasterWallet();
        String masterAddress = masterWallet.getMasterAddress();

        return SweepResultDto.builder()
            .masterWalletAddress(masterAddress)
            .totalTransactions(successful.size())
            .totalAmount(totalSwept)
            .successfulSweeps(successful)
            .failedSweeps(failed)
            .status(successful.size() > 0 ? "SUCCESS" : "FAILED")
            .message(String.format("Swept %d/%d deposits", successful.size(), unsweptDeposits.size()))
            .build();
    }

    /**
     * Get sweep statistics
     */
    public Map<String, Object> getSweepStats() {
        Object cachedStats = redisTemplate.opsForValue().get(SWEEP_STATS_KEY);

        // Get current unswept count
        List<WalletTransaction> unswept = walletTransactionRepository.findUnsweptDeposits();
        BigDecimal unsweptAmount = unswept.stream()
            .map(WalletTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
            "lastSweepResult", cachedStats != null ? cachedStats : "No recent sweep",
            "unsweptDeposits", unswept.size(),
            "unsweptAmount", unsweptAmount,
            "isSweeping", redisTemplate.hasKey(SWEEP_LOCK_KEY),
            "sweepEnabled", sweepEnabled
        );
    }
}
