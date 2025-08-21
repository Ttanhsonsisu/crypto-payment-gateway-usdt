package com.UsdtWallet.UsdtWallet.repository;

import com.UsdtWallet.UsdtWallet.model.entity.GasTopup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GasTopupRepository extends JpaRepository<GasTopup, Long> {

    List<GasTopup> findByStatus(GasTopup.TopupStatus status);
}