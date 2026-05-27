package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shared bridge between high-level operational documents (invoices, bills, etc.)
 * and the general ledger. The journal template is the bridge: a document composes
 * a DRAFT transaction by selecting a template and binding its account-hint lines
 * to concrete accounts. The journal entries are computed by the template engine at
 * post time, so accounting must approve the DRAFT before it hits the ledger.
 *
 * Mirrors the resolution pattern in FixedAssetService: try a fixed system-template
 * UUID, fall back to lookup by name (production imports assign random UUIDs).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DocumentPostingService {

    private final JournalTemplateRepository journalTemplateRepository;
    private final TransactionService transactionService;

    /**
     * Compose a DRAFT transaction from a journal template, binding the template's
     * account-hint lines to concrete accounts.
     *
     * @param fixedTemplateId optional well-known template UUID (matched first; may be null)
     * @param templateName    template name used as the resolution fallback (required)
     * @param date            transaction date
     * @param description     transaction description
     * @param amount          template formula input (e.g. subtotal; PPN/AR computed by formulas)
     * @param hintToAccount   maps each template line's account_hint to the resolved account id
     * @param createdBy       audit actor
     * @return the persisted DRAFT transaction (no journal entries until posted)
     */
    public Transaction createDraftFromTemplate(UUID fixedTemplateId, String templateName,
                                               LocalDate date, String description, BigDecimal amount,
                                               Map<String, UUID> hintToAccount,
                                               Map<String, BigDecimal> variables, String createdBy,
                                               String sourceDocumentType, UUID sourceDocumentId) {
        JournalTemplate template = resolveTemplate(fixedTemplateId, templateName);

        Map<UUID, UUID> accountMappings = new HashMap<>();
        for (JournalTemplateLine line : template.getLines()) {
            if (line.getAccount() == null && line.getAccountHint() != null) {
                UUID accountId = hintToAccount.get(line.getAccountHint());
                if (accountId == null) {
                    throw new IllegalStateException("No account mapped for template hint '"
                            + line.getAccountHint() + "' on template '" + templateName + "'");
                }
                accountMappings.put(line.getId(), accountId);
            }
        }

        Transaction transaction = new Transaction();
        transaction.setJournalTemplate(template);
        transaction.setTransactionDate(date);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setCreatedBy(createdBy);
        transaction.setSourceDocumentType(sourceDocumentType);
        transaction.setSourceDocumentId(sourceDocumentId);

        // create() sets DRAFT and stores the account mappings + variables; journal is computed at post.
        return transactionService.create(transaction, accountMappings, variables);
    }

    private JournalTemplate resolveTemplate(UUID fixedTemplateId, String templateName) {
        if (fixedTemplateId != null) {
            var byId = journalTemplateRepository.findByIdWithLines(fixedTemplateId);
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        return journalTemplateRepository.findByTemplateNameWithLines(templateName)
                .orElseThrow(() -> new IllegalStateException(
                        "Journal template not found: " + templateName));
    }
}
