package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.TaxTransactionDetailService;
import com.artivisi.accountingfinance.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/transactions/{transactionId}/tax-details")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.TAX_EXPORT + "')")
public class TaxDetailController {

    private static final String FRAGMENT_PREFIX = "fragments/tax-detail-section :: ";
    private static final String ATTR_TAX_DETAILS = "taxDetails";
    private static final String ATTR_TRANSACTION_ID = "transactionId";
    private static final String ATTR_TAX_DETAIL_SECTION = "taxDetailSection";
    private static final String ATTR_TAX_TYPES = "taxTypes";
    private static final String ATTR_DETAIL = "detail";
    private static final String ATTR_TAX_DETAIL_FORM = "taxDetailForm";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";

    private final TaxTransactionDetailService taxDetailService;
    private final TransactionService transactionService;

    @GetMapping
    public String list(@PathVariable UUID transactionId, Model model) {
        List<TaxTransactionDetail> details = taxDetailService.findByTransactionId(transactionId);
        model.addAttribute(ATTR_TAX_DETAILS, details);
        model.addAttribute(ATTR_TRANSACTION_ID, transactionId);
        return FRAGMENT_PREFIX + ATTR_TAX_DETAIL_SECTION;
    }

    @GetMapping("/form")
    @PreAuthorize("hasAuthority('" + Permission.TAX_EXPORT + "') and hasAuthority('" + Permission.TRANSACTION_EDIT + "')")
    public String showForm(@PathVariable UUID transactionId, Model model) {
        Transaction transaction = transactionService.findByIdWithJournalEntries(transactionId);
        List<TaxTransactionDetailService.TaxDetailSuggestion> suggestions =
                taxDetailService.suggestFromTransaction(transaction);

        model.addAttribute(ATTR_TRANSACTION_ID, transactionId);
        model.addAttribute("suggestions", suggestions);
        model.addAttribute(ATTR_TAX_TYPES, TaxType.values());
        model.addAttribute(ATTR_DETAIL, new TaxTransactionDetail());
        return FRAGMENT_PREFIX + ATTR_TAX_DETAIL_FORM;
    }

    @GetMapping("/{detailId}/edit")
    @PreAuthorize("hasAuthority('" + Permission.TAX_EXPORT + "') and hasAuthority('" + Permission.TRANSACTION_EDIT + "')")
    public String showEditForm(@PathVariable UUID transactionId,
                               @PathVariable UUID detailId,
                               Model model) {
        TaxTransactionDetail detail = taxDetailService.findById(detailId);
        model.addAttribute(ATTR_TRANSACTION_ID, transactionId);
        model.addAttribute(ATTR_DETAIL, detail);
        model.addAttribute(ATTR_TAX_TYPES, TaxType.values());
        model.addAttribute("isEdit", true);
        return FRAGMENT_PREFIX + ATTR_TAX_DETAIL_FORM;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permission.TAX_EXPORT + "') and hasAuthority('" + Permission.TRANSACTION_EDIT + "')")
    public String create(@PathVariable UUID transactionId,
                         TaxDetailFormData form,
                         Model model) {
        TaxTransactionDetail detail = form.toEntity();

        try {
            taxDetailService.save(transactionId, detail);
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute(ATTR_TRANSACTION_ID, transactionId);
            model.addAttribute(ATTR_TAX_TYPES, TaxType.values());
            model.addAttribute(ATTR_DETAIL, detail);
            return FRAGMENT_PREFIX + ATTR_TAX_DETAIL_FORM;
        }

        List<TaxTransactionDetail> details = taxDetailService.findByTransactionId(transactionId);
        model.addAttribute(ATTR_TAX_DETAILS, details);
        model.addAttribute(ATTR_TRANSACTION_ID, transactionId);
        model.addAttribute(ATTR_SUCCESS_MESSAGE, "Detail pajak berhasil disimpan");
        return FRAGMENT_PREFIX + ATTR_TAX_DETAIL_SECTION;
    }

    @PostMapping("/{detailId}")
    @PreAuthorize("hasAuthority('" + Permission.TAX_EXPORT + "') and hasAuthority('" + Permission.TRANSACTION_EDIT + "')")
    public String update(@PathVariable UUID transactionId,
                         @PathVariable UUID detailId,
                         TaxDetailFormData form,
                         Model model) {
        TaxTransactionDetail detail = form.toEntity();

        try {
            taxDetailService.update(detailId, detail);
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute(ATTR_TRANSACTION_ID, transactionId);
            model.addAttribute(ATTR_TAX_TYPES, TaxType.values());
            model.addAttribute(ATTR_DETAIL, detail);
            model.addAttribute("isEdit", true);
            return FRAGMENT_PREFIX + ATTR_TAX_DETAIL_FORM;
        }

        List<TaxTransactionDetail> details = taxDetailService.findByTransactionId(transactionId);
        model.addAttribute(ATTR_TAX_DETAILS, details);
        model.addAttribute(ATTR_TRANSACTION_ID, transactionId);
        model.addAttribute(ATTR_SUCCESS_MESSAGE, "Detail pajak berhasil diperbarui");
        return FRAGMENT_PREFIX + ATTR_TAX_DETAIL_SECTION;
    }

    @DeleteMapping("/{detailId}")
    @PreAuthorize("hasAuthority('" + Permission.TAX_EXPORT + "') and hasAuthority('" + Permission.TRANSACTION_EDIT + "')")
    public String delete(@PathVariable UUID transactionId,
                         @PathVariable UUID detailId,
                         Model model) {
        taxDetailService.delete(detailId);

        List<TaxTransactionDetail> details = taxDetailService.findByTransactionId(transactionId);
        model.addAttribute(ATTR_TAX_DETAILS, details);
        model.addAttribute(ATTR_TRANSACTION_ID, transactionId);
        model.addAttribute(ATTR_SUCCESS_MESSAGE, "Detail pajak berhasil dihapus");
        return FRAGMENT_PREFIX + ATTR_TAX_DETAIL_SECTION;
    }

    /**
     * Form-binding object for tax detail create/update to reduce controller parameter count.
     * Spring MVC binds request parameters to fields automatically.
     */
    public record TaxDetailFormData(
            TaxType taxType,
            String fakturNumber,
            LocalDate fakturDate,
            String transactionCode,
            BigDecimal dpp,
            BigDecimal ppn,
            BigDecimal ppnbm,
            String bupotNumber,
            String taxObjectCode,
            BigDecimal grossAmount,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            String counterpartyIdType,
            String counterpartyNpwp,
            String counterpartyNik,
            String counterpartyNitku,
            String counterpartyName,
            String counterpartyAddress
    ) {
        public TaxTransactionDetail toEntity() {
            TaxTransactionDetail detail = new TaxTransactionDetail();
            detail.setTaxType(taxType);
            detail.setFakturNumber(fakturNumber);
            detail.setFakturDate(fakturDate);
            detail.setTransactionCode(transactionCode);
            detail.setDpp(dpp);
            detail.setPpn(ppn);
            detail.setPpnbm(ppnbm != null ? ppnbm : BigDecimal.ZERO);
            detail.setBupotNumber(bupotNumber);
            detail.setTaxObjectCode(taxObjectCode);
            detail.setGrossAmount(grossAmount);
            detail.setTaxRate(taxRate);
            detail.setTaxAmount(taxAmount);
            detail.setCounterpartyIdType(counterpartyIdType != null ? counterpartyIdType : "TIN");
            detail.setCounterpartyNpwp(counterpartyNpwp);
            detail.setCounterpartyNik(counterpartyNik);
            detail.setCounterpartyNitku(counterpartyNitku);
            detail.setCounterpartyName(counterpartyName);
            detail.setCounterpartyAddress(counterpartyAddress);
            return detail;
        }
    }
}
