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

    // Current version queries (for user-facing operations)
    List<JournalTemplate> findByActiveAndIsCurrentVersionTrueOrderByTemplateNameAsc(Boolean active);

    List<JournalTemplate> findByCategoryAndActiveAndIsCurrentVersionTrueOrderByTemplateNameAsc(TemplateCategory category, Boolean active);

    List<JournalTemplate> findByActiveAndIsCurrentVersionTrueOrderByUsageCountDesc(Boolean active);

    List<JournalTemplate> findByActiveAndIsCurrentVersionTrueOrderByLastUsedAtDesc(Boolean active);

    @Query("SELECT t FROM JournalTemplate t WHERE t.active = :active AND t.isCurrentVersion = true AND " +
           "(LOWER(t.templateName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<JournalTemplate> searchTemplates(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);

    @Query("SELECT t FROM JournalTemplate t WHERE t.active = true AND t.isCurrentVersion = true AND " +
           "(:category IS NULL OR t.category = :category) " +
           "ORDER BY t.templateName")
    List<JournalTemplate> findByOptionalCategory(@Param("category") TemplateCategory category);

    @Query("SELECT t FROM JournalTemplate t LEFT JOIN FETCH t.lines l LEFT JOIN FETCH l.account WHERE t.id = :id")
    java.util.Optional<JournalTemplate> findByIdWithLines(@Param("id") UUID id);

    @Query("SELECT DISTINCT t FROM JournalTemplate t LEFT JOIN FETCH t.lines l LEFT JOIN FETCH l.account WHERE t.active = true AND t.isCurrentVersion = true ORDER BY t.templateName ASC")
    List<JournalTemplate> findAllWithLines();

    @Query("SELECT t FROM JournalTemplate t LEFT JOIN FETCH t.lines l LEFT JOIN FETCH l.account WHERE t.templateName = :name AND t.isCurrentVersion = true")
    java.util.Optional<JournalTemplate> findByTemplateNameWithLines(@Param("name") String templateName);

    boolean existsByTemplateNameAndIsCurrentVersionTrue(String templateName);

    java.util.Optional<JournalTemplate> findByTemplateNameAndIsCurrentVersionTrue(String templateName);

    // Version history queries
    @Query("SELECT t FROM JournalTemplate t WHERE (t.originalTemplate.id = :rootId OR t.id = :rootId) ORDER BY t.version DESC")
    List<JournalTemplate> findAllVersions(@Param("rootId") UUID rootTemplateId);

    @Query("SELECT MAX(t.version) FROM JournalTemplate t WHERE t.originalTemplate.id = :rootId OR t.id = :rootId")
    Integer findMaxVersion(@Param("rootId") UUID rootTemplateId);

    // Check if template is in use by any transaction
    @Query("SELECT COUNT(t) > 0 FROM Transaction t WHERE t.journalTemplate.id = :templateId")
    boolean isTemplateInUse(@Param("templateId") UUID templateId);

}
