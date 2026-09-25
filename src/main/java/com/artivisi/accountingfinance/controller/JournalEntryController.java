package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.JournalEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;
import static java.util.Objects.requireNonNullElse;

/**
 * Controller for General Ledger and Account Ledger views.
 * All transaction operations (create, edit, post, void) are handled by TransactionController.
 */
@io.swagger.v3.oas.annotations.Hidden
@Controller
@RequestMapping("/journals")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.JOURNAL_VIEW + "')")
public class JournalEntryController {

    private final JournalEntryService journalEntryService;
    private final ChartOfAccountService chartOfAccountService;

    /**
     * General Ledger view - shows journal entries grouped by account.
     */
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

        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_JOURNALS);
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

    // REST API Endpoints for ledger data

    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<Page<JournalEntry>> apiList(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            Pageable pageable) {
        return ResponseEntity.ok(journalEntryService.findAllByDateRange(startDate, endDate, pageable));
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
}
