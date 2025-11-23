package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JournalTemplateRepository extends JpaRepository<JournalTemplate, UUID> {

    List<JournalTemplate> findByActiveOrderByTemplateNameAsc(Boolean active);

    List<JournalTemplate> findByCategoryAndActiveOrderByTemplateNameAsc(TemplateCategory category, Boolean active);

    List<JournalTemplate> findByIsFavoriteAndActiveOrderByTemplateNameAsc(Boolean isFavorite, Boolean active);

    List<JournalTemplate> findByActiveOrderByUsageCountDesc(Boolean active);

    List<JournalTemplate> findByActiveOrderByLastUsedAtDesc(Boolean active);

    @Query("SELECT t FROM JournalTemplate t WHERE t.active = :active AND " +
           "(LOWER(t.templateName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<JournalTemplate> searchTemplates(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);

    @Query("SELECT t FROM JournalTemplate t WHERE t.active = true AND " +
           "(:category IS NULL OR t.category = :category) " +
           "ORDER BY t.templateName")
    List<JournalTemplate> findByOptionalCategory(@Param("category") TemplateCategory category);

    @Query("SELECT t FROM JournalTemplate t LEFT JOIN FETCH t.lines WHERE t.id = :id")
    java.util.Optional<JournalTemplate> findByIdWithLines(@Param("id") UUID id);
}
