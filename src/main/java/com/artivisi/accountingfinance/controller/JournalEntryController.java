package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.dto.AccountOptionDto;
import com.artivisi.accountingfinance.dto.JournalEntryDto;
import com.artivisi.accountingfinance.dto.JournalEntryEditDto;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.JournalEntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNullElse;

@Controller
@RequestMapping("/journals")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.JOURNAL_VIEW + "')")
public class JournalEntryController {

    private final JournalEntryService journalEntryService;
    private final ChartOfAccountService chartOfAccountService;

    private static final int DEFAULT_PAGE_SIZE = 20;

    @GetMapping
    public String list(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {

        LocalDate start = requireNonNullElse(startDate, LocalDate.now().withDayOfMonth(1));
        LocalDate end = requireNonNullElse(endDate, LocalDate.now());

        model.addAttribute("currentPage", "journals");
        model.addAttribute("selectedAccount", accountId);
        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);
        model.addAttribute("searchQuery", search);
        model.addAttribute("accounts", chartOfAccountService.findTransactableAccounts());
        model.addAttribute("pageNumber", page);
        model.addAttribute("pageSize", size);

        if (accountId != null) {
            Pageable pageable = PageRequest.of(page, size);
            model.addAttribute("ledgerData",
                    journalEntryService.getGeneralLedgerPaged(accountId, start, end, search, pageable));
        }

        // Return fragment for HTMX requests, full page otherwise
        if ("true".equals(hxRequest)) {
            return "fragments/journal-ledger :: ledger";
        }
        return "journals/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("currentPage", "journals");
        model.addAttribute("isEdit", false);
        model.addAttribute("accounts", toAccountOptions(chartOfAccountService.findTransactableAccounts()));
        model.addAttribute("journalEntry", null);
        return "journals/form";
    }

    private List<AccountOptionDto> toAccountOptions(List<ChartOfAccount> accounts) {
        return accounts.stream()
                .map(a -> new AccountOptionDto(a.getId().toString(), a.getAccountCode(), a.getAccountName()))
                .toList();
    }

    @GetMapping("/{journalNumber}")
    public String detail(@PathVariable String journalNumber, Model model) {
        List<JournalEntry> entries = journalEntryService.findAllByJournalNumberWithAccount(journalNumber);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Journal not found: " + journalNumber);
        }
        JournalEntry entry = entries.get(0);

        // Calculate totals
        BigDecimal totalDebit = entries.stream()
                .map(JournalEntry::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = entries.stream()
                .map(JournalEntry::getCreditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate account impact
        List<JournalEntryService.AccountImpact> accountImpacts = journalEntryService.calculateAccountImpact(entries);

        model.addAttribute("currentPage", "journals");
        model.addAttribute("journalEntry", entry);
        model.addAttribute("journalEntries", entries);
        model.addAttribute("totalDebit", totalDebit);
        model.addAttribute("totalCredit", totalCredit);
        model.addAttribute("isBalanced", totalDebit.compareTo(totalCredit) == 0);
        model.addAttribute("accountImpacts", accountImpacts);
        return "journals/detail";
    }

    @GetMapping("/{journalNumber}/edit")
    public String edit(@PathVariable String journalNumber, Model model) {
        List<JournalEntry> entries = journalEntryService.findAllByJournalNumberWithAccount(journalNumber);
        if (entries.isEmpty()) {
            return "redirect:/journals";
        }

        JournalEntry firstEntry = entries.get(0);
        if (!firstEntry.isDraft()) {
            return "redirect:/journals/" + firstEntry.getId();
        }

        List<JournalEntryEditDto> editDtos = entries.stream()
                .map(JournalEntryEditDto::fromEntity)
                .toList();

        model.addAttribute("currentPage", "journals");
        model.addAttribute("isEdit", true);
        model.addAttribute("accounts", toAccountOptions(chartOfAccountService.findTransactableAccounts()));
        model.addAttribute("journalNumber", journalNumber);
        model.addAttribute("journalEntries", editDtos);
        return "journals/form";
    }

    @GetMapping("/ledger/{accountId}")
    public String accountLedger(
            @PathVariable UUID accountId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Model model) {
        model.addAttribute("currentPage", "journals");
        model.addAttribute("account", chartOfAccountService.findById(accountId));

        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);
        model.addAttribute("ledgerData", journalEntryService.getGeneralLedger(accountId, start, end));

        return "journals/ledger";
    }

    // REST API Endpoints

    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<Page<JournalEntry>> apiList(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            Pageable pageable) {
        return ResponseEntity.ok(journalEntryService.findAllByDateRange(startDate, endDate, pageable));
    }

    @GetMapping("/api/{journalNumber}")
    @ResponseBody
    public ResponseEntity<JournalEntry> apiGet(@PathVariable String journalNumber) {
        JournalEntry entry = journalEntryService.findByJournalNumber(journalNumber);
        return ResponseEntity.ok(entry);
    }

    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<?> apiCreate(@Valid @RequestBody JournalEntryDto dto) {
        List<JournalEntry> entries = new ArrayList<>();

        for (JournalEntryDto.JournalEntryLineDto line : dto.lines()) {
            if (line.accountId() == null || (line.debit().compareTo(BigDecimal.ZERO) == 0 && line.credit().compareTo(BigDecimal.ZERO) == 0)) {
                continue;
            }

            ChartOfAccount account = chartOfAccountService.findById(line.accountId());

            JournalEntry entry = new JournalEntry();
            entry.setJournalDate(dto.journalDate());
            entry.setReferenceNumber(dto.referenceNumber());
            entry.setDescription(line.lineDescription() != null && !line.lineDescription().isBlank()
                    ? line.lineDescription()
                    : dto.description());
            entry.setAccount(account);
            entry.setDebitAmount(line.debit() != null ? line.debit() : BigDecimal.ZERO);
            entry.setCreditAmount(line.credit() != null ? line.credit() : BigDecimal.ZERO);

            entries.add(entry);
        }

        if (entries.size() < 2) {
            return ResponseEntity.badRequest().body(Map.of("message", "Minimal harus ada 2 baris jurnal dengan akun dan nilai"));
        }

        List<JournalEntry> saved = journalEntryService.create(entries);

        if (dto.postImmediately()) {
            saved = journalEntryService.post(saved.get(0).getJournalNumber());
        }

        return ResponseEntity.ok(Map.of(
                "id", saved.get(0).getId().toString(),
                "journalNumber", saved.get(0).getJournalNumber(),
                "status", saved.get(0).getStatus().name()
        ));
    }

    @PutMapping("/api/{journalNumber}")
    @ResponseBody
    public ResponseEntity<?> apiUpdate(@PathVariable String journalNumber, @Valid @RequestBody JournalEntryDto dto) {
        List<JournalEntry> entries = new ArrayList<>();

        for (JournalEntryDto.JournalEntryLineDto line : dto.lines()) {
            if (line.accountId() == null || (line.debit().compareTo(BigDecimal.ZERO) == 0 && line.credit().compareTo(BigDecimal.ZERO) == 0)) {
                continue;
            }

            ChartOfAccount account = chartOfAccountService.findById(line.accountId());

            JournalEntry entry = new JournalEntry();
            entry.setJournalDate(dto.journalDate());
            entry.setReferenceNumber(dto.referenceNumber());
            entry.setDescription(line.lineDescription() != null && !line.lineDescription().isBlank()
                    ? line.lineDescription()
                    : dto.description());
            entry.setAccount(account);
            entry.setDebitAmount(line.debit() != null ? line.debit() : BigDecimal.ZERO);
            entry.setCreditAmount(line.credit() != null ? line.credit() : BigDecimal.ZERO);

            entries.add(entry);
        }

        if (entries.size() < 2) {
            return ResponseEntity.badRequest().body(Map.of("message", "Minimal harus ada 2 baris jurnal dengan akun dan nilai"));
        }

        List<JournalEntry> saved = journalEntryService.update(journalNumber, entries);

        if (dto.postImmediately()) {
            saved = journalEntryService.post(journalNumber);
        }

        return ResponseEntity.ok(Map.of(
                "id", saved.get(0).getId().toString(),
                "journalNumber", saved.get(0).getJournalNumber(),
                "status", saved.get(0).getStatus().name()
        ));
    }

    @GetMapping("/api/by-transaction/{transactionId}")
    @ResponseBody
    public ResponseEntity<List<JournalEntry>> apiByTransaction(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(journalEntryService.findByTransactionId(transactionId));
    }

    @GetMapping("/api/ledger/{accountId}")
    @ResponseBody
    public ResponseEntity<JournalEntryService.GeneralLedgerData> apiLedger(
            @PathVariable UUID accountId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(journalEntryService.getGeneralLedger(accountId, startDate, endDate));
    }

    @PostMapping("/api/{journalNumber}/post")
    @ResponseBody
    public ResponseEntity<?> apiPost(@PathVariable String journalNumber) {
        try {
            List<JournalEntry> posted = journalEntryService.post(journalNumber);
            return ResponseEntity.ok(Map.of(
                    "id", posted.get(0).getId().toString(),
                    "journalNumber", posted.get(0).getJournalNumber(),
                    "status", posted.get(0).getStatus().name()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/api/{journalNumber}/void")
    @ResponseBody
    public ResponseEntity<?> apiVoid(@PathVariable String journalNumber, @RequestBody Map<String, String> request) {
        String reason = request.get("reason");
        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Alasan void harus diisi"));
        }

        try {
            List<JournalEntry> voided = journalEntryService.voidEntry(journalNumber, reason);
            return ResponseEntity.ok(Map.of(
                    "id", voided.get(0).getId().toString(),
                    "journalNumber", voided.get(0).getJournalNumber(),
                    "status", voided.get(0).getStatus().name()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
