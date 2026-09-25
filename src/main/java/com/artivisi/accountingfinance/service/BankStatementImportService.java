package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.BankStatement;
import com.artivisi.accountingfinance.entity.BankStatementItem;
import com.artivisi.accountingfinance.entity.BankStatementParserConfig;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.enums.StatementItemMatchStatus;
import com.artivisi.accountingfinance.repository.BankStatementItemRepository;
import com.artivisi.accountingfinance.repository.BankStatementRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BankStatementImportService {

    private final BankStatementRepository statementRepository;
    private final BankStatementItemRepository itemRepository;
    private final BankStatementParserConfigService parserConfigService;
    private final CompanyBankAccountService bankAccountService;

    public List<BankStatement> findAllStatements() {
        return statementRepository.findAllWithRelations();
    }

    public BankStatement findStatementById(UUID id) {
        return statementRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new EntityNotFoundException("Bank statement not found with id: " + id));
    }

    @Transactional
    public BankStatement importStatement(BankStatementImportParams params) {

        CompanyBankAccount bankAccount = bankAccountService.findById(params.bankAccountId());
        BankStatementParserConfig config = parserConfigService.findById(params.parserConfigId());

        // Parse CSV
        List<BankStatementItem> items = parseCSV(params.file(), config);

        // Create statement
        BankStatement statement = new BankStatement();
        statement.setBankAccount(bankAccount);
        statement.setParserConfig(config);
        statement.setStatementPeriodStart(params.periodStart());
        statement.setStatementPeriodEnd(params.periodEnd());
        statement.setOpeningBalance(params.openingBalance());
        statement.setClosingBalance(params.closingBalance());
        statement.setOriginalFilename(params.file().getOriginalFilename());
        statement.setTotalItems(items.size());
        statement.setImportedAt(LocalDateTime.now());
        statement.setImportedBy(params.username());

        // Compute totals
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (BankStatementItem item : items) {
            if (item.getDebitAmount() != null) {
                totalDebit = totalDebit.add(item.getDebitAmount());
            }
            if (item.getCreditAmount() != null) {
                totalCredit = totalCredit.add(item.getCreditAmount());
            }
        }
        statement.setTotalDebit(totalDebit);
        statement.setTotalCredit(totalCredit);

        statement = statementRepository.save(statement);

        // Save items
        for (BankStatementItem item : items) {
            item.setBankStatement(statement);
            itemRepository.save(item);
        }

        log.info("Imported bank statement: {} items from {}", items.size(),
                LogSanitizer.sanitize(params.file().getOriginalFilename()));
        return statement;
    }

    private List<BankStatementItem> parseCSV(MultipartFile file, BankStatementParserConfig config) {
        List<BankStatementItem> items = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(config.getDateFormat());

        char delimiterChar = config.getDelimiter().charAt(0);
        if ("\t".equals(config.getDelimiter())) {
            delimiterChar = '\t';
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiterChar)
                .setQuote('"')
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();

        Charset charset = Charset.forName(config.getEncoding());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), charset));
             CSVParser parser = format.parse(reader)) {

            int lineNumber = 0;
            int skipRows = config.getSkipHeaderRows();

            for (CSVRecord csvRecord : parser) {
                lineNumber++;
                if (lineNumber <= skipRows || csvRecord.size() == 0 || isEmptyRecord(csvRecord)) {
                    continue;
                }

                BankStatementItem item = parseSingleRecord(csvRecord, config, dateFormatter, lineNumber);
                items.add(item);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Gagal membaca file CSV: " + e.getMessage());
        }

        if (items.isEmpty()) {
            throw new IllegalArgumentException("File CSV tidak mengandung data transaksi");
        }

        return items;
    }

    /**
     * Parse a single CSV record into a BankStatementItem.
     * Extracted from the loop in parseCSV to satisfy S1141 (no nested try) and S135 (single continue).
     */
    private BankStatementItem parseSingleRecord(CSVRecord csvRecord, BankStatementParserConfig config,
                                                 DateTimeFormatter dateFormatter, int lineNumber) {
        try {
            return parseRecord(csvRecord, config, dateFormatter, lineNumber);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Gagal parsing baris " + lineNumber + ": " + e.getMessage()
                            + " (data: " + csvRecord.toString() + ")");
        }
    }

    private BankStatementItem parseRecord(CSVRecord csvRecord, BankStatementParserConfig config,
                                          DateTimeFormatter dateFormatter, int lineNumber) {
        BankStatementItem item = new BankStatementItem();
        item.setLineNumber(lineNumber);
        item.setMatchStatus(StatementItemMatchStatus.UNMATCHED);
        item.setRawLine(csvRecord.toString());

        // Parse date
        String dateStr = getColumn(csvRecord, config.getDateColumn()).trim();
        try {
            item.setTransactionDate(LocalDate.parse(dateStr, dateFormatter));
        } catch (DateTimeParseException _) {
            throw new IllegalArgumentException("Format tanggal tidak valid: '" + dateStr
                    + "' (expected: " + config.getDateFormat() + ")");
        }

        // Parse description
        item.setDescription(getColumn(csvRecord, config.getDescriptionColumn()).trim());

        // Parse amounts
        if (config.getDebitColumn() != null) {
            String debitStr = getColumn(csvRecord, config.getDebitColumn()).trim();
            if (!debitStr.isEmpty()) {
                item.setDebitAmount(parseAmount(debitStr, config));
            }
        }

        if (config.getCreditColumn() != null) {
            String creditStr = getColumn(csvRecord, config.getCreditColumn()).trim();
            if (!creditStr.isEmpty()) {
                item.setCreditAmount(parseAmount(creditStr, config));
            }
        }

        // Parse balance
        if (config.getBalanceColumn() != null) {
            String balanceStr = getColumn(csvRecord, config.getBalanceColumn()).trim();
            if (!balanceStr.isEmpty()) {
                item.setBalance(parseAmount(balanceStr, config));
            }
        }

        return item;
    }

    private String getColumn(CSVRecord csvRecord, int column) {
        if (column >= csvRecord.size()) {
            throw new IllegalArgumentException("Kolom " + column + " tidak ditemukan (total kolom: " + csvRecord.size() + ")");
        }
        return csvRecord.get(column);
    }

    private BigDecimal parseAmount(String amountStr, BankStatementParserConfig config) {
        if (amountStr == null || amountStr.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Remove thousand separators
        String thousandSep = config.getThousandSeparator();
        if (thousandSep != null && !thousandSep.isEmpty()) {
            amountStr = amountStr.replace(thousandSep, "");
        }

        // Replace decimal separator with dot
        String decimalSep = config.getDecimalSeparator();
        if (decimalSep != null && !".".equals(decimalSep)) {
            amountStr = amountStr.replace(decimalSep, ".");
        }

        // Remove currency symbols and whitespace
        amountStr = amountStr.replaceAll("[^\\d.\\-]", "");

        if (amountStr.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(amountStr).abs();
    }

    private boolean isEmptyRecord(CSVRecord csvRecord) {
        for (int i = 0; i < csvRecord.size(); i++) {
            if (csvRecord.get(i) != null && !csvRecord.get(i).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parameter object for bank statement import to reduce method parameter count.
     */
    public record BankStatementImportParams(
            UUID bankAccountId,
            UUID parserConfigId,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            MultipartFile file,
            String username
    ) {}
}
