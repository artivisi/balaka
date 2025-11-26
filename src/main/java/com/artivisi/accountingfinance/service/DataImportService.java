package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.dto.dataimport.COAImportDto;
import com.artivisi.accountingfinance.dto.dataimport.COAImportFileDto;
import com.artivisi.accountingfinance.dto.dataimport.ImportError;
import com.artivisi.accountingfinance.dto.dataimport.ImportPreview;
import com.artivisi.accountingfinance.dto.dataimport.ImportResult;
import com.artivisi.accountingfinance.dto.dataimport.TemplateImportDto;
import com.artivisi.accountingfinance.dto.dataimport.TemplateImportFileDto;
import com.artivisi.accountingfinance.dto.dataimport.TemplateLineImportDto;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.CashFlowCategory;
import com.artivisi.accountingfinance.enums.JournalPosition;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TemplateType;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DataImportService {

    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalTemplateRepository journalTemplateRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final TransactionRepository transactionRepository;
    private final FormulaEvaluator formulaEvaluator;
    private final jakarta.persistence.EntityManager entityManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========================= COA Import =========================

    public COAImportFileDto parseCOAJsonFile(MultipartFile file) throws IOException {
        return objectMapper.readValue(file.getInputStream(), COAImportFileDto.class);
    }

    public COAImportFileDto parseCOAExcelFile(MultipartFile file) throws IOException {
        List<COAImportDto> accounts = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            int rowNum = 0;

            for (Row row : sheet) {
                if (rowNum == 0) {
                    rowNum++;
                    continue; // Skip header row
                }

                String code = getCellStringValue(row.getCell(0));
                if (code == null || code.isBlank()) {
                    continue; // Skip empty rows
                }

                String name = getCellStringValue(row.getCell(1));
                String type = getCellStringValue(row.getCell(2));
                String normalBalance = getCellStringValue(row.getCell(3));
                String parentCode = getCellStringValue(row.getCell(4));
                Boolean isHeader = getCellBooleanValue(row.getCell(5));
                Boolean isPermanent = getCellBooleanValue(row.getCell(6));
                String description = getCellStringValue(row.getCell(7));

                accounts.add(new COAImportDto(
                        code, name, type, normalBalance, parentCode,
                        isHeader, isPermanent, description
                ));
                rowNum++;
            }
        }

        return new COAImportFileDto(
                file.getOriginalFilename(),
                "1.0",
                accounts
        );
    }

    public ImportPreview previewCOA(COAImportFileDto importFile) {
        List<ImportError> errors = validateCOAStructure(importFile.accounts());

        int existingCount = 0;
        int newCount = 0;
        List<String> sampleRecords = new ArrayList<>();

        for (int i = 0; i < importFile.accounts().size(); i++) {
            COAImportDto account = importFile.accounts().get(i);
            boolean exists = chartOfAccountRepository.existsByAccountCode(account.code());

            if (exists) {
                existingCount++;
            } else {
                newCount++;
            }

            if (i < 5) {
                sampleRecords.add(account.code() + " - " + account.name());
            }
        }

        return ImportPreview.forCOA(
                importFile.name(),
                importFile.version(),
                importFile.accounts().size(),
                newCount,
                existingCount,
                sampleRecords,
                errors
        );
    }

    @Transactional
    public ImportResult importCOA(COAImportFileDto importFile, boolean clearExisting) {
        // Validate structure first
        List<ImportError> errors = validateCOAStructure(importFile.accounts());
        if (!errors.isEmpty()) {
            return ImportResult.failed(errors, importFile.accounts().size());
        }

        // Clear existing if requested (this also clears templates)
        if (clearExisting) {
            clearAllData();
        }

        // Import accounts in order (parents first)
        List<COAImportDto> sortedAccounts = sortByHierarchy(importFile.accounts());

        int successCount = 0;
        List<ImportError> importErrors = new ArrayList<>();
        Map<String, ChartOfAccount> importedAccounts = new HashMap<>();

        for (int i = 0; i < sortedAccounts.size(); i++) {
            COAImportDto dto = sortedAccounts.get(i);

            try {
                ChartOfAccount account = convertCOAToEntity(dto, importedAccounts);
                ChartOfAccount saved = chartOfAccountRepository.save(account);
                importedAccounts.put(dto.code(), saved);
                successCount++;
            } catch (Exception e) {
                log.error("Error importing account {}: {}", dto.code(), e.getMessage());
                importErrors.add(new ImportError(i + 1, dto.code(), e.getMessage()));
            }
        }

        if (importErrors.isEmpty()) {
            return ImportResult.success(importFile.accounts().size(), successCount);
        } else if (successCount > 0) {
            return ImportResult.partial(importFile.accounts().size(), successCount, importErrors);
        } else {
            return ImportResult.failed(importErrors, importFile.accounts().size());
        }
    }

    // ========================= Template Import =========================

    public TemplateImportFileDto parseTemplateJsonFile(MultipartFile file) throws IOException {
        return objectMapper.readValue(file.getInputStream(), TemplateImportFileDto.class);
    }

    public ImportPreview previewTemplate(TemplateImportFileDto importFile) {
        List<ImportError> errors = validateTemplateStructure(importFile.templates());

        int existingCount = 0;
        int newCount = 0;
        List<String> sampleRecords = new ArrayList<>();

        for (int i = 0; i < importFile.templates().size(); i++) {
            TemplateImportDto template = importFile.templates().get(i);
            boolean exists = journalTemplateRepository.existsByTemplateName(template.name());

            if (exists) {
                existingCount++;
            } else {
                newCount++;
            }

            if (i < 5) {
                sampleRecords.add(template.name() + " (" + template.category() + ")");
            }
        }

        return ImportPreview.forTemplate(
                importFile.name(),
                importFile.version(),
                importFile.templates().size(),
                newCount,
                existingCount,
                sampleRecords,
                errors
        );
    }

    @Transactional
    public ImportResult importTemplate(TemplateImportFileDto importFile, boolean clearExisting) {
        // Validate structure first
        List<ImportError> errors = validateTemplateStructure(importFile.templates());
        if (!errors.isEmpty()) {
            return ImportResult.failed(errors, importFile.templates().size());
        }

        // Clear existing if requested
        if (clearExisting) {
            clearAllTemplates();
        }

        int successCount = 0;
        List<ImportError> importErrors = new ArrayList<>();

        for (int i = 0; i < importFile.templates().size(); i++) {
            TemplateImportDto dto = importFile.templates().get(i);

            try {
                JournalTemplate template = convertTemplateToEntity(dto);
                journalTemplateRepository.save(template);
                successCount++;
            } catch (Exception e) {
                log.error("Error importing template {}: {}", dto.name(), e.getMessage());
                importErrors.add(new ImportError(i + 1, dto.name(), e.getMessage()));
            }
        }

        if (importErrors.isEmpty()) {
            log.info("Successfully imported {} templates", successCount);
            return ImportResult.success(importFile.templates().size(), successCount);
        } else if (successCount > 0) {
            log.info("Partially imported {} of {} templates", successCount, importFile.templates().size());
            return ImportResult.partial(importFile.templates().size(), successCount, importErrors);
        } else {
            log.error("Failed to import templates: {} errors", importErrors.size());
            return ImportResult.failed(importErrors, importFile.templates().size());
        }
    }

    // ========================= Clear Operations =========================

    @Transactional
    public void clearAllData() {
        // Check if any journal entries exist
        long journalEntryCount = journalEntryRepository.count();
        if (journalEntryCount > 0) {
            throw new IllegalStateException(
                    "Tidak dapat menghapus data: terdapat " + journalEntryCount + " jurnal yang sudah dibuat");
        }

        // Clear templates first (they reference accounts)
        clearAllTemplates();

        // Then clear accounts
        chartOfAccountRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();
        log.info("Cleared all chart of accounts");
    }

    @Transactional
    public void clearAllTemplates() {
        // Check if any transactions reference templates
        List<JournalTemplate> templates = journalTemplateRepository.findAll();
        for (JournalTemplate template : templates) {
            if (transactionRepository.existsByJournalTemplateId(template.getId())) {
                throw new IllegalStateException(
                        "Tidak dapat menghapus template: template '" + template.getTemplateName() +
                        "' digunakan oleh transaksi");
            }
        }

        // Delete all templates (cascade will handle lines and tags)
        journalTemplateRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();
        log.info("Cleared all journal templates");
    }

    public boolean canClearData() {
        return journalEntryRepository.count() == 0 && canClearTemplates();
    }

    public boolean canClearTemplates() {
        List<JournalTemplate> templates = journalTemplateRepository.findAll();
        for (JournalTemplate template : templates) {
            if (transactionRepository.existsByJournalTemplateId(template.getId())) {
                return false;
            }
        }
        return true;
    }

    // ========================= COA Validation =========================

    private List<ImportError> validateCOAStructure(List<COAImportDto> accounts) {
        List<ImportError> errors = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        Set<String> parentCodes = new HashSet<>();

        // Collect all codes and parent codes
        for (COAImportDto account : accounts) {
            codes.add(account.code());
            if (account.parentCode() != null && !account.parentCode().isBlank()) {
                parentCodes.add(account.parentCode());
            }
        }

        for (int i = 0; i < accounts.size(); i++) {
            COAImportDto account = accounts.get(i);
            int lineNum = i + 1;

            // Validate required fields
            if (account.code() == null || account.code().isBlank()) {
                errors.add(new ImportError(lineNum, "code", "Kode akun harus diisi"));
            }
            if (account.name() == null || account.name().isBlank()) {
                errors.add(new ImportError(lineNum, "name", "Nama akun harus diisi"));
            }

            // Validate account type
            if (account.type() != null) {
                try {
                    AccountType.valueOf(account.type().toUpperCase());
                } catch (IllegalArgumentException e) {
                    errors.add(new ImportError(lineNum, "type", account.type(),
                            "Tipe akun tidak valid: " + account.type() +
                            ". Harus salah satu dari: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE"));
                }
            }

            // Validate normal balance
            if (account.normalBalance() != null) {
                try {
                    NormalBalance.valueOf(account.normalBalance().toUpperCase());
                } catch (IllegalArgumentException e) {
                    errors.add(new ImportError(lineNum, "normalBalance", account.normalBalance(),
                            "Saldo normal tidak valid: " + account.normalBalance() +
                            ". Harus salah satu dari: DEBIT, CREDIT"));
                }
            }

            // Validate parent reference - only check within file for clean import
            if (account.parentCode() != null && !account.parentCode().isBlank()) {
                if (!codes.contains(account.parentCode())) {
                    errors.add(new ImportError(lineNum, "parentCode", account.parentCode(),
                            "Parent akun tidak ditemukan dalam file: " + account.parentCode()));
                }
            }

            // Check for duplicate codes within file
            long duplicateCount = accounts.stream()
                    .filter(a -> a.code() != null && a.code().equals(account.code()))
                    .count();
            if (duplicateCount > 1) {
                errors.add(new ImportError(lineNum, "code", account.code(),
                        "Kode akun duplikat dalam file: " + account.code()));
            }
        }

        return errors;
    }

    // ========================= Template Validation =========================

    private List<ImportError> validateTemplateStructure(List<TemplateImportDto> templates) {
        List<ImportError> errors = new ArrayList<>();
        Set<String> templateNames = new HashSet<>();

        for (int i = 0; i < templates.size(); i++) {
            TemplateImportDto template = templates.get(i);
            int lineNum = i + 1;

            // Validate required fields
            if (template.name() == null || template.name().isBlank()) {
                errors.add(new ImportError(lineNum, "name", "Nama template harus diisi"));
            }

            // Validate template category
            if (template.category() != null) {
                try {
                    TemplateCategory.valueOf(template.category().toUpperCase());
                } catch (IllegalArgumentException e) {
                    errors.add(new ImportError(lineNum, "category", template.category(),
                            "Kategori template tidak valid: " + template.category() +
                            ". Harus salah satu dari: INCOME, EXPENSE, PAYMENT, RECEIPT, TRANSFER"));
                }
            }

            // Validate cash flow category
            if (template.cashFlowCategory() != null) {
                try {
                    CashFlowCategory.valueOf(template.cashFlowCategory().toUpperCase());
                } catch (IllegalArgumentException e) {
                    errors.add(new ImportError(lineNum, "cashFlowCategory", template.cashFlowCategory(),
                            "Kategori arus kas tidak valid: " + template.cashFlowCategory() +
                            ". Harus salah satu dari: OPERATING, INVESTING, FINANCING"));
                }
            }

            // Validate template type if present
            if (template.templateType() != null) {
                try {
                    TemplateType.valueOf(template.templateType().toUpperCase());
                } catch (IllegalArgumentException e) {
                    errors.add(new ImportError(lineNum, "templateType", template.templateType(),
                            "Tipe template tidak valid: " + template.templateType() +
                            ". Harus salah satu dari: SIMPLE, DETAILED"));
                }
            }

            // Check for duplicate names within file
            if (template.name() != null && !template.name().isBlank()) {
                if (templateNames.contains(template.name())) {
                    errors.add(new ImportError(lineNum, "name", template.name(),
                            "Nama template duplikat dalam file: " + template.name()));
                }
                templateNames.add(template.name());
            }

            // Validate lines
            if (template.lines() == null || template.lines().size() < 2) {
                errors.add(new ImportError(lineNum, "lines", "Template harus memiliki minimal 2 baris"));
            } else {
                boolean hasDebit = false;
                boolean hasCredit = false;

                for (int j = 0; j < template.lines().size(); j++) {
                    TemplateLineImportDto line = template.lines().get(j);

                    // Validate account code exists
                    if (line.accountCode() == null || line.accountCode().isBlank()) {
                        errors.add(new ImportError(lineNum, "lines[" + j + "].accountCode",
                                "Kode akun baris " + (j + 1) + " harus diisi"));
                    } else if (!chartOfAccountRepository.existsByAccountCode(line.accountCode())) {
                        errors.add(new ImportError(lineNum, "lines[" + j + "].accountCode", line.accountCode(),
                                "Kode akun tidak ditemukan: " + line.accountCode()));
                    }

                    // Validate position
                    if (line.position() != null) {
                        try {
                            JournalPosition position = JournalPosition.valueOf(line.position().toUpperCase());
                            if (position == JournalPosition.DEBIT) {
                                hasDebit = true;
                            } else {
                                hasCredit = true;
                            }
                        } catch (IllegalArgumentException e) {
                            errors.add(new ImportError(lineNum, "lines[" + j + "].position", line.position(),
                                    "Posisi tidak valid: " + line.position() +
                                    ". Harus salah satu dari: DEBIT, CREDIT"));
                        }
                    }

                    // Validate formula
                    if (line.formula() != null && !line.formula().isBlank()) {
                        List<String> formulaErrors = formulaEvaluator.validate(line.formula());
                        if (!formulaErrors.isEmpty()) {
                            errors.add(new ImportError(lineNum, "lines[" + j + "].formula", line.formula(),
                                    "Formula tidak valid: " + String.join(", ", formulaErrors)));
                        }
                    }
                }

                if (!hasDebit || !hasCredit) {
                    errors.add(new ImportError(lineNum, "lines",
                            "Template harus memiliki minimal satu baris debit dan satu baris kredit"));
                }
            }
        }

        return errors;
    }

    // ========================= Conversion Helpers =========================

    private List<COAImportDto> sortByHierarchy(List<COAImportDto> accounts) {
        List<COAImportDto> sorted = new ArrayList<>();
        Set<String> processed = new HashSet<>();

        // First, add all accounts without parents (root accounts)
        for (COAImportDto account : accounts) {
            if (account.parentCode() == null || account.parentCode().isBlank()) {
                sorted.add(account);
                processed.add(account.code());
            }
        }

        // Then, iteratively add accounts whose parents are already processed
        int maxIterations = accounts.size();
        int iteration = 0;
        while (sorted.size() < accounts.size() && iteration < maxIterations) {
            for (COAImportDto account : accounts) {
                if (!processed.contains(account.code())) {
                    if (account.parentCode() != null && processed.contains(account.parentCode())) {
                        sorted.add(account);
                        processed.add(account.code());
                    }
                }
            }
            iteration++;
        }

        // Add any remaining (orphaned) accounts
        for (COAImportDto account : accounts) {
            if (!processed.contains(account.code())) {
                sorted.add(account);
            }
        }

        return sorted;
    }

    private ChartOfAccount convertCOAToEntity(COAImportDto dto, Map<String, ChartOfAccount> importedAccounts) {
        ChartOfAccount account = new ChartOfAccount();
        account.setAccountCode(dto.code());
        account.setAccountName(dto.name());
        account.setAccountType(AccountType.valueOf(dto.type().toUpperCase()));
        account.setNormalBalance(NormalBalance.valueOf(dto.normalBalance().toUpperCase()));
        account.setIsHeader(dto.isHeader() != null ? dto.isHeader() : false);
        account.setPermanent(dto.isPermanent() != null ? dto.isPermanent() : true);
        account.setDescription(dto.description());
        account.setActive(true);

        // Set parent
        if (dto.parentCode() != null && !dto.parentCode().isBlank()) {
            ChartOfAccount parent = importedAccounts.get(dto.parentCode());
            if (parent != null) {
                account.setParent(parent);
                account.setLevel(parent.getLevel() + 1);
                // Inherit account type from parent
                account.setAccountType(parent.getAccountType());
                account.setNormalBalance(parent.getNormalBalance());
            } else {
                account.setLevel(1);
            }
        } else {
            account.setLevel(1);
        }

        return account;
    }

    private JournalTemplate convertTemplateToEntity(TemplateImportDto dto) {
        JournalTemplate template = new JournalTemplate();
        template.setTemplateName(dto.name());
        template.setCategory(TemplateCategory.valueOf(dto.category().toUpperCase()));
        template.setCashFlowCategory(CashFlowCategory.valueOf(dto.cashFlowCategory().toUpperCase()));
        template.setTemplateType(dto.templateType() != null ?
                TemplateType.valueOf(dto.templateType().toUpperCase()) : TemplateType.SIMPLE);
        template.setDescription(dto.description());
        template.setIsFavorite(false);
        template.setIsSystem(false);
        template.setActive(true);
        template.setVersion(1);
        template.setUsageCount(0);

        // Add lines
        int lineOrder = 1;
        for (TemplateLineImportDto lineDto : dto.lines()) {
            JournalTemplateLine line = new JournalTemplateLine();

            ChartOfAccount account = chartOfAccountRepository.findByAccountCode(lineDto.accountCode())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Akun tidak ditemukan: " + lineDto.accountCode()));
            line.setAccount(account);
            line.setPosition(JournalPosition.valueOf(lineDto.position().toUpperCase()));
            line.setFormula(lineDto.formula() != null ? lineDto.formula() : "amount");
            line.setDescription(lineDto.description());
            line.setLineOrder(lineOrder++);

            template.addLine(line);
        }

        // Add tags
        if (dto.tags() != null) {
            for (String tag : dto.tags()) {
                template.addTag(tag);
            }
        }

        return template;
    }

    // ========================= Excel Helpers =========================

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return null;
    }

    private Boolean getCellBooleanValue(Cell cell) {
        if (cell == null) {
            return false;
        }
        if (cell.getCellType() == CellType.BOOLEAN) {
            return cell.getBooleanCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            String value = cell.getStringCellValue().trim().toLowerCase();
            return "true".equals(value) || "yes".equals(value) || "1".equals(value) || "ya".equals(value);
        } else if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue() == 1;
        }
        return false;
    }
}
