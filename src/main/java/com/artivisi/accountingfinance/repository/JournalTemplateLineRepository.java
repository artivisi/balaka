package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JournalTemplateLineRepository extends JpaRepository<JournalTemplateLine, UUID> {

    List<JournalTemplateLine> findByJournalTemplateIdOrderByLineOrderAsc(UUID journalTemplateId);

    void deleteByJournalTemplateId(UUID journalTemplateId);
}
