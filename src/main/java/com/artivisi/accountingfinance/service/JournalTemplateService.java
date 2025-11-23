package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateLineRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Validated
@Transactional(readOnly = true)
public class JournalTemplateService {

    private final JournalTemplateRepository journalTemplateRepository;
    private final JournalTemplateLineRepository journalTemplateLineRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;

    public List<JournalTemplate> findAll() {
        return journalTemplateRepository.findByActiveOrderByTemplateNameAsc(true);
    }

    public List<JournalTemplate> findByCategory(TemplateCategory category) {
        if (category == null) {
            return findAll();
        }
        return journalTemplateRepository.findByCategoryAndActiveOrderByTemplateNameAsc(category, true);
    }

    public List<JournalTemplate> findFavorites() {
        return journalTemplateRepository.findByIsFavoriteAndActiveOrderByTemplateNameAsc(true, true);
    }

    public List<JournalTemplate> findMostUsed() {
        return journalTemplateRepository.findByActiveOrderByUsageCountDesc(true);
    }

    public List<JournalTemplate> findRecentlyUsed() {
        return journalTemplateRepository.findByActiveOrderByLastUsedAtDesc(true);
    }

    public Page<JournalTemplate> search(String search, Pageable pageable) {
        return journalTemplateRepository.searchTemplates(search, true, pageable);
    }

    public JournalTemplate findById(UUID id) {
        return journalTemplateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Template not found with id: " + id));
    }

    public JournalTemplate findByIdWithLines(UUID id) {
        return journalTemplateRepository.findByIdWithLines(id)
                .orElseThrow(() -> new EntityNotFoundException("Template not found with id: " + id));
    }

    @Transactional
    public JournalTemplate create(@Valid JournalTemplate template) {
        validateTemplateLines(template);
        return journalTemplateRepository.save(template);
    }

    @Transactional
    public JournalTemplate update(UUID id, @Valid JournalTemplate templateData) {
        JournalTemplate existing = findById(id);

        if (existing.getIsSystem()) {
            throw new IllegalStateException("Cannot modify system template");
        }

        existing.setTemplateName(templateData.getTemplateName());
        existing.setCategory(templateData.getCategory());
        existing.setCashFlowCategory(templateData.getCashFlowCategory());
        existing.setTemplateType(templateData.getTemplateType());
        existing.setDescription(templateData.getDescription());
        existing.setIsFavorite(templateData.getIsFavorite());
        existing.setVersion(existing.getVersion() + 1);

        existing.getLines().clear();
        for (JournalTemplateLine line : templateData.getLines()) {
            ChartOfAccount account = chartOfAccountRepository.findById(line.getAccount().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Account not found"));
            line.setAccount(account);
            existing.addLine(line);
        }

        validateTemplateLines(existing);
        return journalTemplateRepository.save(existing);
    }

    @Transactional
    public JournalTemplate duplicate(UUID sourceId, String newName) {
        JournalTemplate source = findByIdWithLines(sourceId);

        JournalTemplate duplicate = new JournalTemplate();
        duplicate.setTemplateName(newName);
        duplicate.setCategory(source.getCategory());
        duplicate.setCashFlowCategory(source.getCashFlowCategory());
        duplicate.setTemplateType(source.getTemplateType());
        duplicate.setDescription(source.getDescription());
        duplicate.setIsFavorite(false);
        duplicate.setIsSystem(false);
        duplicate.setActive(true);

        for (JournalTemplateLine sourceLine : source.getLines()) {
            JournalTemplateLine newLine = new JournalTemplateLine();
            newLine.setAccount(sourceLine.getAccount());
            newLine.setPosition(sourceLine.getPosition());
            newLine.setFormula(sourceLine.getFormula());
            newLine.setLineOrder(sourceLine.getLineOrder());
            newLine.setDescription(sourceLine.getDescription());
            duplicate.addLine(newLine);
        }

        return journalTemplateRepository.save(duplicate);
    }

    @Transactional
    public void toggleFavorite(UUID id) {
        JournalTemplate template = findById(id);
        template.setIsFavorite(!template.getIsFavorite());
        journalTemplateRepository.save(template);
    }

    @Transactional
    public void recordUsage(UUID id) {
        JournalTemplate template = findById(id);
        template.setUsageCount(template.getUsageCount() + 1);
        template.setLastUsedAt(LocalDateTime.now());
        journalTemplateRepository.save(template);
    }

    @Transactional
    public void activate(UUID id) {
        JournalTemplate template = findById(id);
        template.setActive(true);
        journalTemplateRepository.save(template);
    }

    @Transactional
    public void deactivate(UUID id) {
        JournalTemplate template = findById(id);
        if (template.getIsSystem()) {
            throw new IllegalStateException("Cannot deactivate system template");
        }
        template.setActive(false);
        journalTemplateRepository.save(template);
    }

    @Transactional
    public void delete(UUID id) {
        JournalTemplate template = findById(id);
        if (template.getIsSystem()) {
            throw new IllegalStateException("Cannot delete system template");
        }
        journalTemplateRepository.delete(template);
    }

    private void validateTemplateLines(JournalTemplate template) {
        if (template.getLines() == null || template.getLines().size() < 2) {
            throw new IllegalArgumentException("Template must have at least 2 lines");
        }

        boolean hasDebit = template.getLines().stream()
                .anyMatch(l -> l.getPosition().name().equals("DEBIT"));
        boolean hasCredit = template.getLines().stream()
                .anyMatch(l -> l.getPosition().name().equals("CREDIT"));

        if (!hasDebit || !hasCredit) {
            throw new IllegalArgumentException("Template must have at least one debit and one credit line");
        }
    }
}
