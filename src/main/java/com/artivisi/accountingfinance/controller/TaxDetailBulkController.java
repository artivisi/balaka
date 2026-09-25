package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.security.LogSanitizer;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.TaxTransactionDetailService;
import com.artivisi.accountingfinance.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.artivisi.accountingfinance.controller.ViewConstants.ATTR_CURRENT_PAGE;

@Controller
@RequestMapping("/transactions/tax-details/bulk")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.TAX_EXPORT + "')")
@Slf4j
public class TaxDetailBulkController {

    private static final String PAGE_TAX_DETAIL_BULK = "tax-detail-bulk";
    private static final String PREFIX_TAX_TYPE = "taxType_";

    private final TransactionService transactionService;
    private final TaxTransactionDetailService taxDetailService;

    @GetMapping
    public String list(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Model model) {

        // Default to current month
        if (startDate == null) {
            startDate = LocalDate.now().withDayOfMonth(1);
        }
        if (endDate == null) {
            endDate = startDate.plusMonths(1).minusDays(1);
        }

        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TAX_DETAIL_BULK);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        // Get POSTED transactions in date range
        Page<Transaction> transactionPage = transactionService.findByFilters(
                com.artivisi.accountingfinance.enums.TransactionStatus.POSTED,
                null, null, null, startDate, endDate,
                PageRequest.of(page, size));

        List<Transaction> transactions = transactionPage.getContent();

        // Find which ones already have tax details
        List<UUID> ids = transactions.stream().map(Transaction::getId).toList();
        Set<UUID> withDetails = taxDetailService.findTransactionIdsWithDetails(ids);

        // Filter to only those missing tax details and having PPN/PPh templates
        List<Transaction> missingDetails = transactions.stream()
                .filter(t -> !withDetails.contains(t.getId()))
                .filter(t -> {
                    String templateName = t.getJournalTemplate() != null
                            ? t.getJournalTemplate().getTemplateName().toUpperCase() : "";
                    return templateName.contains("PPN") || templateName.contains("PPH");
                })
                .toList();

        // Generate suggestions for each
        Map<UUID, List<TaxTransactionDetailService.TaxDetailSuggestion>> suggestionsMap = missingDetails.stream()
                .collect(Collectors.toMap(Transaction::getId,
                        t -> taxDetailService.suggestFromTransaction(t)));

        model.addAttribute("transactions", missingDetails);
        model.addAttribute("suggestionsMap", suggestionsMap);
        model.addAttribute("page", transactionPage);
        model.addAttribute("taxTypes", TaxType.values());

        return "transactions/tax-details-bulk";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permission.TAX_EXPORT + "') and hasAuthority('" + Permission.TRANSACTION_EDIT + "')")
    public String bulkSave(
            @RequestParam Map<String, String> params,
            RedirectAttributes redirectAttributes) {

        int savedCount = 0;

        Set<String> transactionIds = params.keySet().stream()
                .filter(k -> k.startsWith(PREFIX_TAX_TYPE))
                .map(k -> k.substring(PREFIX_TAX_TYPE.length()))
                .collect(Collectors.toSet());

        for (String txIdStr : transactionIds) {
            String taxTypeStr = params.get(PREFIX_TAX_TYPE + txIdStr);
            if (taxTypeStr == null || taxTypeStr.isBlank()) {
                continue;
            }

            try {
                UUID txId = UUID.fromString(txIdStr);
                TaxTransactionDetail detail = buildDetailFromParams(params, txIdStr, taxTypeStr);
                taxDetailService.save(txId, detail);
                savedCount++;
            } catch (Exception e) {
                log.warn("Failed to save tax detail for transaction {}: {}",
                        LogSanitizer.sanitize(txIdStr), LogSanitizer.sanitize(e.getMessage()));
            }
        }

        redirectAttributes.addFlashAttribute("successMessage",
                savedCount + " detail pajak berhasil disimpan");
        return "redirect:/transactions/tax-details/bulk";
    }

    private TaxTransactionDetail buildDetailFromParams(Map<String, String> params,
                                                        String txIdStr, String taxTypeStr) {
        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTaxType(TaxType.valueOf(taxTypeStr));
        detail.setFakturNumber(params.getOrDefault("fakturNumber_" + txIdStr, null));
        detail.setBupotNumber(params.getOrDefault("bupotNumber_" + txIdStr, null));
        detail.setCounterpartyName(params.getOrDefault("counterpartyName_" + txIdStr, ""));

        setDecimalFieldFromParam(params, "dpp_" + txIdStr, detail::setDpp);
        setDecimalFieldFromParam(params, "ppn_" + txIdStr, detail::setPpn);
        setDecimalFieldFromParam(params, "grossAmount_" + txIdStr, detail::setGrossAmount);
        setDecimalFieldFromParam(params, "taxRate_" + txIdStr, detail::setTaxRate);
        setDecimalFieldFromParam(params, "taxAmount_" + txIdStr, detail::setTaxAmount);

        String npwp = params.get("counterpartyNpwp_" + txIdStr);
        if (npwp != null && !npwp.isBlank()) {
            detail.setCounterpartyNpwp(npwp);
        }
        detail.setCounterpartyIdType("TIN");
        detail.setPpnbm(java.math.BigDecimal.ZERO);

        return detail;
    }

    private void setDecimalFieldFromParam(Map<String, String> params, String key,
                                           java.util.function.Consumer<java.math.BigDecimal> setter) {
        String value = params.get(key);
        if (value != null && !value.isBlank()) {
            setter.accept(new java.math.BigDecimal(value));
        }
    }
}
