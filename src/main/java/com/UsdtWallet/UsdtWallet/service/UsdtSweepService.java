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

    @Value("${tron.usdt.contract:TXYZopYRdj2D9XRtbG411XZZ3kM5VkAeBf}")
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

            // 1. Check current USDT balance of child wallet
            BigDecimal currentUsdtBalance = tronApiService.getUsdtBalance(fromAddress);
            if (currentUsdtBalance.compareTo(amount) < 0) {
                String error = String.format("Insufficient USDT balance. Expected: %s, Current: %s", amount, currentUsdtBalance);
                log.warn(error);

                return SweepResultDto.SweepTransactionDto.builder()
                    .fromAddress(fromAddress)
                    .amount(amount)
                    .status("FAILED")
                    .errorMessage(error)
                    .build();
            }

            // 2. Check TRX balance for gas fees
            BigDecimal trxBalance = tronApiService.getTrxBalance(fromAddress);
            log.debug("Child wallet TRX balance: {} TRX, Required gas: {} TRX", trxBalance, gasLimitTrx);

            if (trxBalance.compareTo(gasLimitTrx) < 0) {
                log.info("⛽ Child wallet needs TRX for gas. Current: {} TRX, Required: {} TRX",
                    trxBalance, gasLimitTrx);

                // Check master wallet TRX balance
                BigDecimal masterTrxBalance = tronApiService.getTrxBalance(masterAddress);
                if (masterTrxBalance.compareTo(gasLimitTrx) < 0) {
                    String error = String.format("Master wallet insufficient TRX for gas. Available: %s, Required: %s",
                        masterTrxBalance, gasLimitTrx);
                    log.error("❌ " + error);

                    return SweepResultDto.SweepTransactionDto.builder()
                        .fromAddress(fromAddress)
                        .amount(amount)
                        .status("FAILED")
                        .errorMessage(error)
                        .build();
                }

                // Send TRX for gas first
                boolean gasSent = sendGasToChildWallet(fromAddress, gasLimitTrx);
                if (!gasSent) {
                    String error = "Failed to send TRX gas to child wallet";
                    log.error("❌ " + error);

                    return SweepResultDto.SweepTransactionDto.builder()
                        .fromAddress(fromAddress)
                        .amount(amount)
                        .status("FAILED")
                        .errorMessage(error)
                        .build();
                }

                // Wait for gas transaction to confirm with retry logic
                log.info("⏳ Waiting for gas transaction to confirm...");

                if (!waitForGasConfirmation(fromAddress, gasLimitTrx, 60)) {
                    String error = String.format("Gas transaction not confirmed after 60 seconds. Current balance: %s TRX",
                        tronApiService.getTrxBalance(fromAddress));
                    log.warn("⚠️ " + error);

                    return SweepResultDto.SweepTransactionDto.builder()
                        .fromAddress(fromAddress)
                        .amount(amount)
                        .status("FAILED")
                        .errorMessage(error)
                        .build();
                }

                log.info("✅ Gas successfully sent and confirmed. New balance: {} TRX", trxBalance);
            }

            // 3. Create and sign USDT transfer transaction (using TronGrid API)
            log.info("🚀 Creating USDT transfer transaction on TronGrid");

            // Get private key for child wallet
            String childPrivateKey = hdWalletService.getPrivateKeyForAddress(fromAddress);

            String rawTransaction = tronApiService.createUsdtTransferTransaction(fromAddress, masterAddress, amount);
            if (rawTransaction == null) {
                throw new RuntimeException("Failed to create USDT transaction");
            }

            // Sign the transaction with real private key
            String signedTransaction = tronApiService.signTransaction(rawTransaction, childPrivateKey);
            if (signedTransaction == null) {
                throw new RuntimeException("Failed to sign USDT transaction");
            }

            // 4. Broadcast transaction
            log.info("📡 Broadcasting USDT transaction");
            String txHash = tronApiService.broadcastTransaction(signedTransaction);
            if (txHash == null) {
                throw new RuntimeException("Failed to broadcast USDT transaction");
            }

            // 5. Update deposit as swept
            deposit.setIsSwept(true);
            deposit.setSweepTxHash(txHash);
            deposit.setSweptAt(LocalDateTime.now());
            walletTransactionRepository.save(deposit);

            // 6. Create sweep transaction record
            WalletTransaction sweepTx = WalletTransaction.builder()
                .txHash(txHash)
                .fromAddress(fromAddress)
                .toAddress(masterAddress)
                .amount(amount)
                .tokenAddress(usdtContractAddress)
                .transactionType(WalletTransaction.TransactionType.SWEEP)
                .direction(WalletTransaction.TransactionDirection.OUT) // ADD THIS LINE
                .status(WalletTransaction.TransactionStatus.PENDING)
                .userId(deposit.getUserId())
                .gasUsed(gasLimitTrx)
                .build();

            walletTransactionRepository.save(sweepTx);

            log.info("✅ Sweep process completed: {} USDT from {}", amount, fromAddress);

            return SweepResultDto.SweepTransactionDto.builder()
                .fromAddress(fromAddress)
                .txHash(txHash)
                .amount(amount)
                .status("SUCCESS")
                .gasUsed(gasLimitTrx)
                .build();

        } catch (Exception e) {
            log.error("❌ Error sweeping deposit {}: {}", deposit.getTxHash(), e.getMessage(), e);

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

            // Get master address and private key
            HdMasterWallet masterWallet = hdWalletService.getMasterWallet();
            String masterAddress = masterWallet.getMasterAddress();
            String masterPrivateKey = hdWalletService.getMasterPrivateKey();

            // Create TRX transfer transaction (using TronGrid API)
            String rawTransaction = tronApiService.createTrxTransferTransaction(masterAddress, childAddress, gasAmount);
            if (rawTransaction == null) {
                throw new RuntimeException("Failed to create TRX transaction");
            }

            // Sign the transaction with master private key
            String signedTransaction = tronApiService.signTransaction(rawTransaction, masterPrivateKey);
            if (signedTransaction == null) {
                throw new RuntimeException("Failed to sign TRX transaction");
            }

            // Broadcast transaction
            String txHash = tronApiService.broadcastTransaction(signedTransaction);
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
     * Create USDT transfer transaction (using TronGrid API)
     */
    private String createUsdtTransferTransaction(String privateKey, String from, String to, BigDecimal amount) {
        try {
            // 1. Create unsigned transaction
            String rawTransaction = tronApiService.createUsdtTransferTransaction(from, to, amount);
            if (rawTransaction == null) {
                throw new RuntimeException("Failed to create USDT transaction");
            }

            // 2. Sign transaction (currently placeholder)
            String signedTransaction = tronApiService.signTransaction(rawTransaction, privateKey);
            if (signedTransaction == null) {
                throw new RuntimeException("Failed to sign USDT transaction");
            }

            log.info("✅ USDT transaction created and signed: {} USDT from {} to {}", amount, from, to);
            return signedTransaction;

        } catch (Exception e) {
            log.error("❌ Error creating USDT transfer transaction", e);
            throw new RuntimeException("Failed to create USDT transaction: " + e.getMessage());
        }
    }

    /**
     * Create TRX transfer transaction (using TronGrid API)
     */
    private String createTrxTransferTransaction(String privateKey, String from, String to, BigDecimal amount) {
        try {
            // 1. Create unsigned transaction
            String rawTransaction = tronApiService.createTrxTransferTransaction(from, to, amount);
            if (rawTransaction == null) {
                throw new RuntimeException("Failed to create TRX transaction");
            }

            // 2. Sign transaction (currently placeholder)
            String signedTransaction = tronApiService.signTransaction(rawTransaction, privateKey);
            if (signedTransaction == null) {
                throw new RuntimeException("Failed to sign TRX transaction");
            }

            log.info("✅ TRX transaction created and signed: {} TRX from {} to {}", amount, from, to);
            return signedTransaction;

        } catch (Exception e) {
            log.error("❌ Error creating TRX transfer transaction", e);
            throw new RuntimeException("Failed to create TRX transaction: " + e.getMessage());
        }
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

    /**
     * Wait for gas confirmation
     */
    private boolean waitForGasConfirmation(String address, BigDecimal requiredGas, int maxRetries) {
        try {
            int retries = 0;
            BigDecimal trxBalance;

            do {
                // Check TRX balance
                trxBalance = tronApiService.getTrxBalance(address);
                log.info("⏳ Waiting for gas confirmation... Attempt {}: TRX balance is {} TRX", retries + 1, trxBalance);

                // If balance is sufficient, exit loop
                if (trxBalance.compareTo(requiredGas) >= 0) {
                    return true;
                }

                // Wait before next check
                Thread.sleep(5000);
                retries++;

            } while (retries < maxRetries);

        } catch (Exception e) {
            log.error("Error waiting for gas confirmation: {}", e.getMessage());
        }

        return false;
    }
}
