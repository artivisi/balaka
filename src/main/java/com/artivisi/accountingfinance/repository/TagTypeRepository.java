package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.TagType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TagTypeRepository extends JpaRepository<TagType, UUID> {

    boolean existsByCode(String code);

    java.util.Optional<TagType> findByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    @Query("SELECT t FROM TagType t WHERE t.active = true ORDER BY t.name")
    List<TagType> findAllActive();

    @Query("SELECT t FROM TagType t WHERE " +
           "(COALESCE(:search, '') = '' OR LOWER(t.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "ORDER BY t.code")
    Page<TagType> findBySearch(@Param("search") String search, Pageable pageable);
}
