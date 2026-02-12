package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.DeviceCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceCodeRepository extends JpaRepository<DeviceCode, UUID> {

    Optional<DeviceCode> findByDeviceCode(String deviceCode);

    Optional<DeviceCode> findByUserCode(String userCode);

    @Modifying
    @Query("UPDATE DeviceCode d SET d.status = 'EXPIRED' WHERE d.status = 'PENDING' AND d.expiresAt < :now")
    int expireOldCodes(LocalDateTime now);

    @Modifying
    @Query("DELETE FROM DeviceCode d WHERE d.createdAt < :before")
    int deleteOldCodes(LocalDateTime before);
}
