package com.UsdtWallet.UsdtWallet.repository;

import com.UsdtWallet.UsdtWallet.model.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByTxHashAndDirectionAndLogIndex(
            String txHash, WalletTransaction.Direction direction, Integer logIndex
    );

    List<WalletTransaction> findByStatusAndDirection(
            WalletTransaction.TransactionStatus status, WalletTransaction.Direction direction
    );

    @Query("SELECT w FROM WalletTransaction w WHERE w.status = :status AND w.direction = 'IN' AND w.confirmations >= :confirmations")
    List<WalletTransaction> findConfirmedDeposits(
            @Param("status") WalletTransaction.TransactionStatus status,
            @Param("confirmations") Integer confirmations
    );
}
