package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.Vendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    /** Serializes withholding on one payee: tax on a payment depends on earlier payments in the masa. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vendor v WHERE v.id = :id")
    Optional<Vendor> findByIdForUpdate(@Param("id") UUID id);

    List<Vendor> findByPayeeTypeIsNotNullOrderByNameAsc();

    Optional<Vendor> findByCode(String code);

    boolean existsByCode(String code);

    Page<Vendor> findByActiveTrue(Pageable pageable);

    List<Vendor> findByActiveTrueOrderByNameAsc();

    Page<Vendor> findAllByOrderByNameAsc(Pageable pageable);

    @Query("SELECT v FROM Vendor v WHERE " +
            "(:active IS NULL OR v.active = :active) " +
            "ORDER BY v.name ASC")
    Page<Vendor> findByFilters(@Param("active") Boolean active, Pageable pageable);

    @Query("SELECT v FROM Vendor v WHERE " +
            "(:active IS NULL OR v.active = :active) AND " +
            "(LOWER(v.code) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            " LOWER(v.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            " LOWER(v.contactPerson) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY v.name ASC")
    Page<Vendor> findByFiltersAndSearch(
            @Param("active") Boolean active,
            @Param("search") String search,
            Pageable pageable);

    long countByActiveTrue();

    Optional<Vendor> findByNameIgnoreCase(String name);
}
