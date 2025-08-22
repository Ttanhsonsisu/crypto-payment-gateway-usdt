package com.UsdtWallet.UsdtWallet.service;

import com.UsdtWallet.UsdtWallet.model.entity.ChildWalletPool;
import com.UsdtWallet.UsdtWallet.model.entity.WalletTransaction;
import com.UsdtWallet.UsdtWallet.repository.ChildWalletPoolRepository;
import com.UsdtWallet.UsdtWallet.repository.WalletTransactionRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepositScannerService {

    private final TronApiService tronApiService;
    private final WalletTransactionRepository walletTransactionRepository;
    private final ChildWalletPoolRepository childWalletPoolRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PointsService pointsService;

    @Value("${deposit.scanner.confirmations.required:3}")
    private Integer requiredConfirmations;

    @Value("${deposit.scanner.block.batch.size:50}")
    private Integer blockBatchSize;

    @Value("${tron.usdt.contract:TXYZopYRdj2D9XRtbG411XZZ3kM5VkAeBf}")
    private String usdtContractAddress;

    @Value("${deposit.scanner.min.amount:0.1}")
    private BigDecimal minimumDepositAmount; // Lower minimum for testnet

    private static final String LAST_SCANNED_BLOCK_KEY = "deposit:scanner:last_block";
    private static final String SCANNING_LOCK_KEY = "deposit:scanner:lock";

    /**
     * Scheduled task to scan for new deposits every 30 seconds
     */
    @Scheduled(fixedDelay = 30000) // 30 seconds
    public void scanForDeposits() {
        try {
            // Acquire lock to prevent multiple scanning processes
            Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(SCANNING_LOCK_KEY, "locked", 60, TimeUnit.SECONDS);

            if (!Boolean.TRUE.equals(lockAcquired)) {
                log.debug("Deposit scanning already in progress, skipping...");
                return;
            }

            log.info("🔍 Starting deposit scan...");

            Long currentBlock = tronApiService.getLatestBlockNumber();
            if (currentBlock == null) {
                log.error("❌ Failed to get latest block number");
                return;
            }

            Long lastScannedBlock = getLastScannedBlock();
            Long fromBlock = lastScannedBlock + 1;
            Long toBlock = Math.min(fromBlock + blockBatchSize - 1, currentBlock - requiredConfirmations);

            if (fromBlock > toBlock) {
                log.debug("No new blocks to scan. Current: {}, Last scanned: {}", currentBlock, lastScannedBlock);
                return;
            }

            log.info("📊 Scanning blocks {} to {} (current: {})", fromBlock, toBlock, currentBlock);

            int depositsFound = scanBlockRange(fromBlock, toBlock);

            // Update last scanned block
            setLastScannedBlock(toBlock);

            log.info("✅ Scan completed. Blocks: {} to {}, Deposits found: {}", fromBlock, toBlock, depositsFound);

        } catch (Exception e) {
            log.error("❌ Error during deposit scanning", e);
        } finally {
            // Release lock
            redisTemplate.delete(SCANNING_LOCK_KEY);
        }
    }

    /**
     * Scan a range of blocks for deposits
     */
    @Transactional
    public int scanBlockRange(Long fromBlock, Long toBlock) {
        int totalDeposits = 0;

        try {
            // Get all active child wallet addresses
            List<String> childAddresses = redisTemplate.opsForSet()
                .members("child_wallet_addresses")
                .stream()
                .map(Object::toString)
                .toList();

            if (childAddresses.isEmpty()) {
                log.warn("No child wallet addresses found in cache");
                return 0;
            }

            log.debug("Scanning {} child wallets for deposits", childAddresses.size());

            // Scan each child wallet for transactions
            for (String address : childAddresses) {
                try {
                    List<Map<String, Object>> transactions = tronApiService
                        .getTransactionsInRange(address, fromBlock, toBlock);

                    for (Map<String, Object> txData : transactions) {
                        if (processTransaction(address, txData)) {
                            totalDeposits++;
                        }
                    }
                } catch (Exception e) {
                    log.error("Error scanning address {}: {}", address, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Error scanning block range {} to {}", fromBlock, toBlock, e);
        }

        return totalDeposits;
    }

    /**
     * Process a single transaction
     */
    @Transactional
    public boolean processTransaction(String toAddress, Map<String, Object> txData) {
        try {
            String txHash = (String) txData.get("transaction_id");

            // Skip if we already processed this transaction
            if (walletTransactionRepository.existsByTxHash(txHash)) {
                return false;
            }

            // Extract transaction details
            String fromAddress = (String) txData.get("from");
            Object contractAddressObj = txData.get("token_info");
            String contractAddress = "";

            // Handle contract address extraction properly
            if (contractAddressObj instanceof Map) {
                Map<String, Object> tokenInfo = (Map<String, Object>) contractAddressObj;
                Object addressObj = tokenInfo.get("address");
                contractAddress = addressObj != null ? addressObj.toString() : "";
            } else if (contractAddressObj != null) {
                contractAddress = contractAddressObj.toString();
            }

            // Only process USDT transactions
            if (!usdtContractAddress.equalsIgnoreCase(contractAddress)) {
                return false;
            }

            Object valueObj = txData.get("value");
            if (valueObj == null) {
                return false;
            }

            // Convert USDT amount (6 decimals)
            BigDecimal amount = new BigDecimal(valueObj.toString())
                .divide(new BigDecimal("1000000"));

            // Skip small amounts
            if (amount.compareTo(minimumDepositAmount) < 0) {
                log.debug("Skipping small deposit: {} USDT to {}", amount, toAddress);
                return false;
            }

            // Get user ID from child wallet
            Optional<ChildWalletPool> childWallet = childWalletPoolRepository.findByAddress(toAddress);
            if (childWallet.isEmpty() || childWallet.get().getUserId() == null) {
                log.warn("Deposit to unassigned wallet: {} amount: {} USDT", toAddress, amount);
                return false;
            }

            String userId = String.valueOf(childWallet.get().getUserId()); // Convert Long to String
            Long blockNumber = ((Number) txData.get("block_number")).longValue();
            Long blockTimestamp = ((Number) txData.get("block_timestamp")).longValue();

            // Convert timestamp to LocalDateTime
            LocalDateTime transactionTime = java.time.Instant.ofEpochMilli(blockTimestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();

            // Create transaction record
            WalletTransaction transaction = WalletTransaction.builder()
                .txHash(txHash)
                .fromAddress(fromAddress)
                .toAddress(toAddress)
                .amount(amount)
                .tokenAddress(contractAddress)
                .blockNumber(blockNumber)
                .blockTimestamp(transactionTime)
                .transactionType(WalletTransaction.TransactionType.DEPOSIT)
                .status(WalletTransaction.TransactionStatus.CONFIRMED)
                .userId(userId)
                .confirmationCount(requiredConfirmations)
                .build();

            walletTransactionRepository.save(transaction);

            // Update child wallet first deposit time if needed
            if (childWallet.get().getFirstDepositAt() == null) {
                childWallet.get().setFirstDepositAt(LocalDateTime.now());
                childWalletPoolRepository.save(childWallet.get());
            }

            log.info("💰 New deposit detected: {} USDT from {} to {} (User: {})",
                amount, fromAddress, toAddress, userId);

            // Async credit points to user
            creditPointsForDeposit(transaction);

            return true;

        } catch (Exception e) {
            log.error("Error processing transaction: {}", txData, e);
            return false;
        }
    }

    /**
     * Credit points to user for deposit (async)
     */
    @Async
    public void creditPointsForDeposit(WalletTransaction transaction) {
        try {
            // Credit points to user (1 USDT = 1 Point by default)
            BigDecimal pointsToCredit = transaction.getAmount();

            boolean success = pointsService.creditPointsForDeposit(
                transaction.getUserId(),
                pointsToCredit,
                transaction.getId(),
                transaction.getAmount()
            );

            if (success) {
                // Update transaction with points credited
                transaction.setPointsCredited(pointsToCredit);
                transaction.setPointsCreditedAt(LocalDateTime.now());
                walletTransactionRepository.save(transaction);

                log.info("✅ Credited {} points to user {} for deposit {}",
                    pointsToCredit, transaction.getUserId(), transaction.getTxHash());
            } else {
                log.error("❌ Failed to credit points for deposit {}", transaction.getTxHash());
            }

        } catch (Exception e) {
            log.error("Error crediting points for deposit {}", transaction.getTxHash(), e);
        }
    }

    /**
     * Manual scan for specific address
     */
    public int scanAddressManually(String address, Long fromBlock, Long toBlock) {
        log.info("🔍 Manual scan for address: {} from block {} to {}", address, fromBlock, toBlock);

        try {
            List<Map<String, Object>> transactions = tronApiService
                .getTransactionsInRange(address, fromBlock, toBlock);

            int processed = 0;
            for (Map<String, Object> txData : transactions) {
                if (processTransaction(address, txData)) {
                    processed++;
                }
            }

            log.info("✅ Manual scan completed. Address: {}, Processed: {}", address, processed);
            return processed;

        } catch (Exception e) {
            log.error("Error in manual scan for address: " + address, e);
            return 0;
        }
    }

    /**
     * Get last scanned block from Redis
     */
    private Long getLastScannedBlock() {
        Object value = redisTemplate.opsForValue().get(LAST_SCANNED_BLOCK_KEY);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        // If no last block, start from current block minus some blocks
        Long currentBlock = tronApiService.getLatestBlockNumber();
        return currentBlock != null ? currentBlock - 1000 : 0L;
    }

    /**
     * Set last scanned block in Redis
     */
    private void setLastScannedBlock(Long blockNumber) {
        redisTemplate.opsForValue().set(LAST_SCANNED_BLOCK_KEY, blockNumber);
    }

    /**
     * Get scanning statistics
     */
    public Map<String, Object> getScanningStats() {
        Long lastScannedBlock = getLastScannedBlock();
        Long currentBlock = tronApiService.getLatestBlockNumber();

        return Map.of(
            "lastScannedBlock", lastScannedBlock,
            "currentBlock", currentBlock != null ? currentBlock : 0,
            "blocksBehind", currentBlock != null ? currentBlock - lastScannedBlock : 0,
            "isScanning", redisTemplate.hasKey(SCANNING_LOCK_KEY)
        );
    }
}
