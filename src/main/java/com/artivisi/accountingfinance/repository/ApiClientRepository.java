package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiClientRepository extends JpaRepository<ApiClient, UUID> {

    @Query("SELECT c FROM ApiClient c LEFT JOIN FETCH c.user WHERE c.clientId = :clientId AND c.active = true")
    Optional<ApiClient> findActiveByClientId(@Param("clientId") String clientId);

    boolean existsByClientId(String clientId);

    @Query("SELECT c FROM ApiClient c LEFT JOIN FETCH c.user ORDER BY c.createdAt DESC")
    List<ApiClient> findAllWithUser();
}
