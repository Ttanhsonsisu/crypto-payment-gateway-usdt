package com.UsdtWallet.UsdtWallet.repository;

import com.UsdtWallet.UsdtWallet.model.entity.ChildWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ChildWalletRepository extends JpaRepository<ChildWallet, UUID> {
    ChildWallet findFirstByUsedFalse();
    @Query("SELECT MAX(c.index) FROM ChildWallet c")
    Optional<Integer> findMaxIndex();
}
