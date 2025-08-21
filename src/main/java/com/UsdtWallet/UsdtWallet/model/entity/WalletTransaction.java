package com.UsdtWallet.UsdtWallet.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tx_hash", "direction", "log_index"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tx_hash", length = 128, nullable = false)
    private String txHash;

    @Column(name = "log_index")
    private Integer logIndex = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private Direction direction;

    @Column(name = "child_index")
    private Integer childIndex;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "token_contract", length = 128)
    private String tokenContract;

    @Column(name = "amount", precision = 36, scale = 6, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "confirmations")
    private Integer confirmations = 0;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum Direction {
        IN, OUT
    }

    public enum TransactionStatus {
        PENDING, CONFIRMED, FAILED
    }
}