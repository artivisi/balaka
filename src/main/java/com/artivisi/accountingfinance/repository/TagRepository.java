package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByTagTypeIdOrderByName(UUID tagTypeId);

    java.util.Optional<Tag> findByTagTypeIdAndCode(UUID tagTypeId, String code);

    @Query("SELECT t FROM Tag t WHERE t.tagType.id = :tagTypeId " +
           "AND (COALESCE(:search, '') = '' OR LOWER(t.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "ORDER BY t.code")
    Page<Tag> findByTagTypeIdAndSearch(@Param("tagTypeId") UUID tagTypeId, @Param("search") String search, Pageable pageable);

    @Query("SELECT t FROM Tag t WHERE t.active = true ORDER BY t.tagType.name, t.name")
    List<Tag> findAllActiveOrdered();

    boolean existsByTagTypeIdAndCode(UUID tagTypeId, String code);

    boolean existsByTagTypeIdAndCodeAndIdNot(UUID tagTypeId, String code, UUID id);

    long countByTagTypeId(UUID tagTypeId);
}
