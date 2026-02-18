package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.JournalTemplateTag;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateLineRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateTagRepository;
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

    private static final String MSG_ACCOUNT_NOT_FOUND = "Account not found";

    private final JournalTemplateRepository journalTemplateRepository;
    private final JournalTemplateLineRepository journalTemplateLineRepository;
    private final JournalTemplateTagRepository journalTemplateTagRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final FormulaEvaluator formulaEvaluator;

    public List<JournalTemplate> findAll() {
        return journalTemplateRepository.findByActiveAndIsCurrentVersionTrueOrderByTemplateNameAsc(true);
    }

    public List<JournalTemplate> findAllWithLines() {
        return journalTemplateRepository.findAllWithLines();
    }

    public List<JournalTemplate> findByCategory(TemplateCategory category) {
        if (category == null) {
            return findAll();
        }
        return journalTemplateRepository.findByCategoryAndActiveAndIsCurrentVersionTrueOrderByTemplateNameAsc(category, true);
    }

    public List<JournalTemplate> findMostUsed() {
        return journalTemplateRepository.findByActiveAndIsCurrentVersionTrueOrderByUsageCountDesc(true);
    }

    public List<JournalTemplate> findRecentlyUsed() {
        return journalTemplateRepository.findByActiveAndIsCurrentVersionTrueOrderByLastUsedAtDesc(true);
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
        JournalTemplate existing = findByIdWithLines(id);

        if (existing.getIsSystem()) {
            throw new IllegalStateException("Cannot modify system template");
        }

        // Check if template is in use by any transaction
        boolean isInUse = journalTemplateRepository.isTemplateInUse(id);

        if (isInUse) {
            // Template is in use - create new version
            return createNewVersion(existing, templateData);
        } else {
            // Template not in use - update in place
            return updateInPlace(existing, templateData);
        }
    }

    private JournalTemplate updateInPlace(JournalTemplate existing, JournalTemplate templateData) {
        existing.setTemplateName(templateData.getTemplateName());
        existing.setCategory(templateData.getCategory());
        existing.setCashFlowCategory(templateData.getCashFlowCategory());
        existing.setTemplateType(templateData.getTemplateType());
        existing.setDescription(templateData.getDescription());
        existing.setVersion(existing.getVersion() + 1);

        // AI semantic metadata
        existing.setSemanticDescription(templateData.getSemanticDescription());
        existing.setKeywords(templateData.getKeywords());
        existing.setExampleMerchants(templateData.getExampleMerchants());
        existing.setTypicalAmountMin(templateData.getTypicalAmountMin());
        existing.setTypicalAmountMax(templateData.getTypicalAmountMax());
        existing.setMerchantPatterns(templateData.getMerchantPatterns());

        // Clear and recreate lines (safe since not in use)
        existing.clearLines();
        for (JournalTemplateLine line : templateData.getLines()) {
            ChartOfAccount account = null;
            if (line.getAccount() != null && line.getAccount().getId() != null) {
                account = chartOfAccountRepository.findById(line.getAccount().getId())
                        .orElseThrow(() -> new EntityNotFoundException(MSG_ACCOUNT_NOT_FOUND));
            }
            JournalTemplateLine newLine = new JournalTemplateLine();
            newLine.setAccount(account);
            newLine.setPosition(line.getPosition());
            newLine.setFormula(line.getFormula());
            newLine.setLineOrder(line.getLineOrder());
            newLine.setDescription(line.getDescription());
            newLine.setAccountHint(line.getAccountHint());
            existing.addLine(newLine);
        }

        validateTemplateLines(existing);
        return journalTemplateRepository.save(existing);
    }

    private JournalTemplate createNewVersion(JournalTemplate existing, JournalTemplate templateData) {
        // Determine the root template for version tracking
        JournalTemplate rootTemplate = existing.getOriginalTemplate() != null
                ? existing.getOriginalTemplate()
                : existing;

        // Get the next version number
        Integer maxVersion = journalTemplateRepository.findMaxVersion(rootTemplate.getId());
        int nextVersion = (maxVersion != null ? maxVersion : 1) + 1;

        // Mark existing as not current
        existing.setIsCurrentVersion(false);
        journalTemplateRepository.save(existing);

        // Create new version
        JournalTemplate newVersion = new JournalTemplate();
        newVersion.setTemplateName(templateData.getTemplateName());
        newVersion.setCategory(templateData.getCategory());
        newVersion.setCashFlowCategory(templateData.getCashFlowCategory());
        newVersion.setTemplateType(templateData.getTemplateType());
        newVersion.setDescription(templateData.getDescription());
        newVersion.setIsSystem(false);
        newVersion.setActive(true);
        newVersion.setVersion(nextVersion);
        newVersion.setOriginalTemplate(rootTemplate);
        newVersion.setIsCurrentVersion(true);
        newVersion.setUsageCount(existing.getUsageCount());
        newVersion.setLastUsedAt(existing.getLastUsedAt());

        // AI semantic metadata
        newVersion.setSemanticDescription(templateData.getSemanticDescription());
        newVersion.setKeywords(templateData.getKeywords());
        newVersion.setExampleMerchants(templateData.getExampleMerchants());
        newVersion.setTypicalAmountMin(templateData.getTypicalAmountMin());
        newVersion.setTypicalAmountMax(templateData.getTypicalAmountMax());
        newVersion.setMerchantPatterns(templateData.getMerchantPatterns());

        // Copy lines to new version
        for (JournalTemplateLine line : templateData.getLines()) {
            ChartOfAccount account = null;
            if (line.getAccount() != null && line.getAccount().getId() != null) {
                account = chartOfAccountRepository.findById(line.getAccount().getId())
                        .orElseThrow(() -> new EntityNotFoundException(MSG_ACCOUNT_NOT_FOUND));
            }
            JournalTemplateLine newLine = new JournalTemplateLine();
            newLine.setAccount(account);
            newLine.setPosition(line.getPosition());
            newLine.setFormula(line.getFormula());
            newLine.setLineOrder(line.getLineOrder());
            newLine.setDescription(line.getDescription());
            newLine.setAccountHint(line.getAccountHint());
            newVersion.addLine(newLine);
        }

        // Copy tags to new version
        for (String tag : existing.getTagNames()) {
            newVersion.addTag(tag);
        }

        validateTemplateLines(newVersion);
        return journalTemplateRepository.save(newVersion);
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
        duplicate.setIsSystem(false);
        duplicate.setActive(true);

        for (JournalTemplateLine sourceLine : source.getLines()) {
            JournalTemplateLine newLine = new JournalTemplateLine();
            // Reload account from database to avoid detached entity with null version
            if (sourceLine.getAccount() != null && sourceLine.getAccount().getId() != null) {
                ChartOfAccount managedAccount = chartOfAccountRepository.findById(sourceLine.getAccount().getId())
                        .orElseThrow(() -> new EntityNotFoundException(MSG_ACCOUNT_NOT_FOUND));
                newLine.setAccount(managedAccount);
            }
            newLine.setPosition(sourceLine.getPosition());
            newLine.setFormula(sourceLine.getFormula());
            newLine.setLineOrder(sourceLine.getLineOrder());
            newLine.setDescription(sourceLine.getDescription());
            newLine.setAccountHint(sourceLine.getAccountHint());
            duplicate.addLine(newLine);
        }

        return journalTemplateRepository.save(duplicate);
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

    // Tag-related methods

    public List<String> getDistinctTags() {
        return journalTemplateTagRepository.findDistinctTags();
    }

    public List<String> searchTags(String query) {
        return journalTemplateTagRepository.searchTags(query);
    }

    public List<JournalTemplate> findByTag(String tag) {
        List<UUID> templateIds = journalTemplateTagRepository.findTemplateIdsByTag(tag.toLowerCase().trim());
        if (templateIds.isEmpty()) {
            return List.of();
        }
        return journalTemplateRepository.findAllById(templateIds).stream()
                .filter(t -> t.getActive())
                .sorted((a, b) -> a.getTemplateName().compareToIgnoreCase(b.getTemplateName()))
                .toList();
    }

    @Transactional
    public void addTag(UUID templateId, String tag) {
        JournalTemplate template = findById(templateId);
        String normalizedTag = tag.toLowerCase().trim();
        if (journalTemplateTagRepository.existsByJournalTemplateIdAndTag(templateId, normalizedTag)) {
            throw new IllegalArgumentException("Tag already exists on this template");
        }
        template.addTag(normalizedTag);
        journalTemplateRepository.save(template);
    }

    @Transactional
    public void removeTag(UUID templateId, String tag) {
        JournalTemplate template = findById(templateId);
        template.removeTag(tag);
        journalTemplateRepository.save(template);
    }

    public List<String> getTagsForTemplate(UUID templateId) {
        return journalTemplateTagRepository.findByJournalTemplateId(templateId).stream()
                .map(JournalTemplateTag::getTag)
                .sorted()
                .toList();
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

        // Validate formulas for each line
        for (int i = 0; i < template.getLines().size(); i++) {
            JournalTemplateLine line = template.getLines().get(i);
            List<String> errors = formulaEvaluator.validate(line.getFormula());
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("Invalid formula on line %d: %s", i + 1, String.join(", ", errors)));
            }
        }
    }
}
