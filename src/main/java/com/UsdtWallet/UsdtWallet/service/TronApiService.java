package com.UsdtWallet.UsdtWallet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TronApiService {

    private final RestTemplate restTemplate;

    @Value("${tron.api.url:https://nile.trongrid.io}")
    private String tronApiUrl;

    @Value("${tron.usdt.contract:TXYZopYRdj2D9XRtbG411XZZ3kM5VkAeBf}")
    private String usdtContractAddress;

    @Value("${tron.api.key:}")
    private String apiKey;

    private static final String API_KEY_HEADER = "TRON-PRO-API-KEY";

    /**
     * Get latest block number from Nile testnet
     */
    public Long getLatestBlockNumber() {
        try {
            String url = tronApiUrl + "/wallet/getnowblock";

            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> blockHeader = (Map<String, Object>) response.getBody().get("block_header");
                if (blockHeader != null) {
                    Map<String, Object> rawData = (Map<String, Object>) blockHeader.get("raw_data");
                    if (rawData != null) {
                        Object numberObj = rawData.get("number");
                        if (numberObj instanceof Number) {
                            Long blockNumber = ((Number) numberObj).longValue();
                            log.debug("Latest Nile testnet block: {}", blockNumber);
                            return blockNumber;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting latest block number from Nile testnet", e);
        }
        return null;
    }

    /**
     * Get USDT balance using Nile testnet TRC20 API
     */
    public BigDecimal getUsdtBalance(String address) {
        try {
            // Use TronGrid's account API for TRC20 balance
            String url = String.format("%s/v1/accounts/%s/transactions/trc20?limit=1&contract_address=%s",
                tronApiUrl, address, usdtContractAddress);

            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // For getting actual balance, we need to use a different endpoint
                return getAccountTrc20Balance(address, usdtContractAddress);
            }
        } catch (Exception e) {
            log.error("Error getting USDT balance for address: {} on Nile testnet", address, e);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Get TRC20 token balance using account info
     */
    private BigDecimal getAccountTrc20Balance(String address, String contractAddress) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("address", address);
            request.put("visible", true);

            String url = tronApiUrl + "/wallet/getaccount";

            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // Check for TRC20 token balances
                Object trc20Obj = response.getBody().get("trc20");
                if (trc20Obj instanceof List) {
                    List<Map<String, Object>> trc20List = (List<Map<String, Object>>) trc20Obj;
                    for (Map<String, Object> token : trc20List) {
                        String tokenAddress = (String) token.get("contract_address");
                        if (contractAddress.equalsIgnoreCase(tokenAddress)) {
                            Object balanceObj = token.get("balance");
                            if (balanceObj instanceof String) {
                                // USDT has 6 decimals on Tron
                                BigInteger balanceWei = new BigInteger((String) balanceObj);
                                return new BigDecimal(balanceWei).divide(new BigDecimal("1000000"));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting TRC20 balance for {}", address, e);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Get TRX balance from Nile testnet
     */
    public BigDecimal getTrxBalance(String address) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("address", address);
            request.put("visible", true);

            String url = tronApiUrl + "/wallet/getaccount";

            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object balanceObj = response.getBody().get("balance");
                if (balanceObj instanceof Number) {
                    // Convert from sun to TRX (1 TRX = 1,000,000 sun)
                    BigInteger balanceInSun = BigInteger.valueOf(((Number) balanceObj).longValue());
                    BigDecimal trxBalance = new BigDecimal(balanceInSun).divide(new BigDecimal("1000000"));
                    log.debug("TRX balance for {}: {} TRX", address, trxBalance);
                    return trxBalance;
                }
            }
        } catch (Exception e) {
            log.error("Error getting TRX balance for address: {} on Nile testnet", address, e);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Get TRC20 transactions for address using Nile testnet API
     */
    public List<Map<String, Object>> getTransactionsInRange(String address, Long fromBlock, Long toBlock) {
        try {
            // Convert block numbers to timestamps (approximate)
            long fromTimestamp = fromBlock * 3000; // 3 seconds per block
            long toTimestamp = toBlock * 3000;

            String url = String.format(
                "%s/v1/accounts/%s/transactions/trc20?limit=200&min_timestamp=%d&max_timestamp=%d&contract_address=%s",
                tronApiUrl, address, fromTimestamp, toTimestamp, usdtContractAddress);

            log.debug("Scanning Nile testnet transactions: {} (blocks {}-{})", address, fromBlock, toBlock);

            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object dataObj = response.getBody().get("data");
                if (dataObj instanceof List) {
                    List<Map<String, Object>> transactions = (List<Map<String, Object>>) dataObj;
                    log.debug("Found {} TRC20 transactions for address {}", transactions.size(), address);
                    return transactions;
                }
            }
        } catch (Exception e) {
            log.error("Error getting transactions for address: {} on Nile testnet", address, e);
        }
        return List.of();
    }

    /**
     * Broadcast transaction to Nile testnet
     */
    public String broadcastTransaction(String rawTransaction) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("raw_data_hex", rawTransaction);

            String url = tronApiUrl + "/wallet/broadcasttransaction";

            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Boolean result = (Boolean) response.getBody().get("result");
                if (Boolean.TRUE.equals(result)) {
                    String txHash = (String) response.getBody().get("txid");
                    log.info("✅ Transaction broadcasted to Nile testnet: {}", txHash);
                    return txHash;
                } else {
                    log.error("❌ Failed to broadcast transaction to Nile testnet: {}", response.getBody());
                }
            }
        } catch (Exception e) {
            log.error("Error broadcasting transaction to Nile testnet", e);
        }
        return null;
    }

    /**
     * Get transaction by hash from Nile testnet
     */
    public Map<String, Object> getTransactionByHash(String txHash) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("value", txHash);

            String url = tronApiUrl + "/wallet/gettransactionbyid";

            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.debug("Retrieved transaction {} from Nile testnet", txHash);
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Error getting transaction by hash: {} from Nile testnet", txHash, e);
        }
        return null;
    }

    /**
     * Get Nile testnet network info
     */
    public Map<String, Object> getNetworkInfo() {
        try {
            String url = tronApiUrl + "/wallet/getnodeinfo";

            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Error getting Nile testnet info", e);
        }
        return Map.of("network", "nile", "status", "unknown");
    }

    /**
     * Create HTTP headers with API key if available
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (!apiKey.isEmpty()) {
            headers.set(API_KEY_HEADER, apiKey);
            log.debug("Using TronGrid API key for Nile testnet");
        }
        return headers;
    }

    /**
     * Check if address is valid Tron address
     */
    public boolean isValidTronAddress(String address) {
        return address != null && address.length() == 34 && address.startsWith("T");
    }

    /**
     * Get faucet TRX for testing (Nile testnet only)
     */
    public boolean requestFaucetTrx(String address) {
        try {
            // Note: This is a hypothetical faucet endpoint
            // You would need to implement actual faucet logic or use external faucet
            log.info("🚰 Requesting faucet TRX for address: {} on Nile testnet", address);

            // For now, just log the request
            log.warn("Faucet functionality not implemented. Please use Nile testnet faucet manually.");
            return false;

        } catch (Exception e) {
            log.error("Error requesting faucet TRX", e);
            return false;
        }
    }
}
