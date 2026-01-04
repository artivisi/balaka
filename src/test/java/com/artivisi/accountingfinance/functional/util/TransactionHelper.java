package com.artivisi.accountingfinance.functional.util;

import com.artivisi.accountingfinance.entity.*;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Helper class to insert test transactions directly from CSV data.
 * Used in report tests to quickly set up test data without UI interaction.
 * 
 * For functional UI tests, use Playwright to execute transactions via the actual UI.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionHelper {

    private final TransactionRepository transactionRepository;
    private final JournalTemplateRepository templateRepository;
    private final ProjectRepository projectRepository;
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Execute transactions from CSV file.
     * CSV format: sequence,date,template,inputs,description,reference,project,notes
     */
    @Transactional
    public void executeTransactionsFromCsv(String csvPath) {
        List<TransactionRow> transactions = CsvLoader.loadTransactions(csvPath);
        
        log.info("Executing {} transactions from {}", transactions.size(), csvPath);
        
        for (TransactionRow tx : transactions) {
            executeTransaction(tx);
        }
        
        log.info("Completed executing {} transactions", transactions.size());
    }

    private void executeTransaction(TransactionRow tx) {
        // Find template
        JournalTemplate template = templateRepository.findByTemplateNameAndIsCurrentVersionTrue(tx.templateName())
            .orElseThrow(() -> new RuntimeException("Template not found: " + tx.templateName()));

        // Parse inputs
        Map<String, String> inputs = parseInputs(tx.inputs());
        BigDecimal amount = new BigDecimal(inputs.getOrDefault("amount", "0"));

        // Create transaction
        Transaction transaction = new Transaction();
        transaction.setTransactionNumber(generateTransactionNumber());
        transaction.setTransactionDate(LocalDate.parse(tx.date(), DATE_FORMAT));
        transaction.setJournalTemplate(template);
        transaction.setDescription(tx.description());
        transaction.setReferenceNumber(tx.reference());
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.POSTED);
        transaction.setPostedAt(java.time.LocalDateTime.now());
        transaction.setPostedBy("system");

        // Set project if specified
        if (tx.project() != null && !tx.project().isEmpty()) {
            projectRepository.findByCode(tx.project())
                .ifPresent(transaction::setProject);
        }

        Transaction savedTx = transactionRepository.save(transaction);

        // Create journal entries based on template
        createJournalEntries(savedTx, template, amount, inputs);
        
        log.debug("Executed transaction: {} - {}", savedTx.getTransactionNumber(), tx.description());
    }

    private void createJournalEntries(Transaction transaction, JournalTemplate template, 
                                     BigDecimal amount, Map<String, String> inputs) {
        // This is simplified - real implementation would parse template lines
        // and create appropriate journal entries based on template type (SIMPLE/DETAILED)
        
        // For now, just log - full implementation needed based on template structure
        log.debug("Creating journal entries for template: {}", template.getTemplateName());
    }

    private Map<String, String> parseInputs(String inputs) {
        Map<String, String> result = new java.util.HashMap<>();
        if (inputs == null || inputs.isEmpty()) {
            return result;
        }
        
        String[] pairs = inputs.split("\\|");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                result.put(kv[0].trim(), kv[1].trim());
            }
        }
        return result;
    }

    private String generateTransactionNumber() {
        // Simple sequential number - in real app this uses TransactionSequence
        long count = transactionRepository.count() + 1;
        return String.format("TRX-TEST-%05d", count);
    }
}
