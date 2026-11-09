package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import lombok.Builder;
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
     * Parameters for composing a DRAFT transaction from a journal template.
     *
     * @param fixedTemplateId    optional well-known template UUID (matched first; may be null)
     * @param templateName       template name used as the resolution fallback (required)
     * @param date               transaction date
     * @param description        transaction description
     * @param amount             template formula input (e.g. subtotal; PPN/AR computed by formulas)
     * @param hintToAccount      maps each template line's account_hint to the resolved account id
     * @param variables          named amounts consumed by the template formulas
     * @param createdBy          audit actor
     * @param sourceDocumentType document type backlink (e.g. INVOICE, BILL, ASSET)
     * @param sourceDocumentId   document id backlink
     */
    @Builder
    public record DraftRequest(UUID fixedTemplateId, String templateName,
                               LocalDate date, String description, BigDecimal amount,
                               Map<String, UUID> hintToAccount,
                               Map<String, BigDecimal> variables, String createdBy,
                               String sourceDocumentType, UUID sourceDocumentId) {}

    /**
     * Compose a DRAFT transaction from a journal template, binding the template's
     * account-hint lines to concrete accounts.
     *
     * @return the persisted DRAFT transaction (no journal entries until posted)
     */
    public Transaction createDraftFromTemplate(DraftRequest request) {
        JournalTemplate template = resolveTemplate(request.fixedTemplateId(), request.templateName());

        // A template without lines would post as an empty, trivially balanced journal.
        if (template.getLines().isEmpty()) {
            throw new IllegalStateException("Journal template has no lines: " + template.getTemplateName());
        }

        Map<UUID, UUID> accountMappings = new HashMap<>();
        for (JournalTemplateLine line : template.getLines()) {
            if (line.getAccount() == null && line.getAccountHint() != null) {
                UUID accountId = request.hintToAccount().get(line.getAccountHint());
                if (accountId == null) {
                    throw new IllegalStateException("No account mapped for template hint '"
                            + line.getAccountHint() + "' on template '" + request.templateName() + "'");
                }
                accountMappings.put(line.getId(), accountId);
            }
        }

        Transaction transaction = new Transaction();
        transaction.setJournalTemplate(template);
        transaction.setTransactionDate(request.date());
        transaction.setAmount(request.amount());
        transaction.setDescription(request.description());
        transaction.setCreatedBy(request.createdBy());
        transaction.setSourceDocumentType(request.sourceDocumentType());
        transaction.setSourceDocumentId(request.sourceDocumentId());

        // create() sets DRAFT and stores the account mappings + variables; journal is computed at post.
        return transactionService.create(transaction, accountMappings, request.variables());
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
