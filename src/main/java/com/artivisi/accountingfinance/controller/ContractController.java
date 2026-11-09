package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Contract;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.VendorRepository;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.ContractService;
import com.artivisi.accountingfinance.service.EvidenceQueryService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.ATTR_CURRENT_PAGE;
import static com.artivisi.accountingfinance.controller.ViewConstants.PAGE_CONTRACTS;

/** Contract register pages. */
@Controller
@RequestMapping("/contracts")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.EVIDENCE_VIEW + "')")
public class ContractController {

    private static final String FLASH_SUCCESS = "successMessage";
    private static final String FLASH_ERROR = "errorMessage";
    private static final String REDIRECT_DETAIL = "redirect:/contracts/";
    private static final String MANAGE = "hasAuthority('" + Permission.EVIDENCE_MANAGE + "')";

    private final ContractService contractService;
    private final EvidenceQueryService evidenceQueryService;
    private final ClientRepository clientRepository;
    private final VendorRepository vendorRepository;

    @GetMapping
    public String list(@RequestParam(required = false) UUID clientId,
                       @RequestParam(required = false) UUID vendorId,
                       Model model) {
        model.addAttribute("contracts", evidenceQueryService.contracts(clientId, vendorId));
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CONTRACTS);
        return "contracts/list";
    }

    @GetMapping("/new")
    @PreAuthorize(MANAGE)
    public String newForm(Model model) {
        formModel(model, null);
        return "contracts/form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize(MANAGE)
    public String editForm(@PathVariable UUID id, Model model) {
        formModel(model, contractService.findById(id));
        return "contracts/form";
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public String create(@RequestParam(required = false) String contractNumber,
                         @RequestParam(required = false) String title,
                         @RequestParam(required = false) UUID clientId,
                         @RequestParam(required = false) UUID vendorId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                         @RequestParam(required = false) BigDecimal contractValue,
                         @RequestParam(required = false) String notes,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            Contract saved = contractService.create(new ContractService.ContractData(contractNumber, title, clientId,
                    vendorId, startDate, endDate, contractValue, notes), authentication.getName());
            redirectAttributes.addFlashAttribute(FLASH_SUCCESS, "Kontrak tersimpan");
            return REDIRECT_DETAIL + saved.getId();
        } catch (IllegalArgumentException | IllegalStateException | EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return "redirect:/contracts/new";
        }
    }

    @PostMapping("/{id}")
    @PreAuthorize(MANAGE)
    public String update(@PathVariable UUID id,
                         @RequestParam(required = false) String contractNumber,
                         @RequestParam(required = false) String title,
                         @RequestParam(required = false) UUID clientId,
                         @RequestParam(required = false) UUID vendorId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                         @RequestParam(required = false) BigDecimal contractValue,
                         @RequestParam(required = false) String notes,
                         RedirectAttributes redirectAttributes) {
        try {
            contractService.update(id, new ContractService.ContractData(contractNumber, title, clientId, vendorId,
                    startDate, endDate, contractValue, notes));
            redirectAttributes.addFlashAttribute(FLASH_SUCCESS, "Kontrak tersimpan");
            return REDIRECT_DETAIL + id;
        } catch (IllegalArgumentException | IllegalStateException | EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return REDIRECT_DETAIL + id + "/edit";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        Contract contract = contractService.findById(id);
        model.addAttribute("contract", contract);
        model.addAttribute("invoices", contractService.linkedInvoices(id));
        model.addAttribute("transactions", contractService.linkedTransactions(id));
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CONTRACTS);
        return "contracts/detail";
    }

    @PostMapping("/{id}/file")
    @PreAuthorize(MANAGE)
    public String attachFile(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                             Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            contractService.attachFile(id, file, authentication.getName());
            redirectAttributes.addFlashAttribute(FLASH_SUCCESS, "Dokumen kontrak tersimpan");
        } catch (IllegalArgumentException | IOException e) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, "Gagal mengunggah dokumen: " + e.getMessage());
        }
        return REDIRECT_DETAIL + id;
    }

    @PostMapping("/{id}/invoices")
    @PreAuthorize(MANAGE)
    public String linkInvoice(@PathVariable UUID id, @RequestParam(required = false) String invoiceNumber,
                              RedirectAttributes redirectAttributes) {
        return link(id, redirectAttributes, () -> contractService.linkInvoice(id, invoiceNumber), "Invoice ditautkan");
    }

    @PostMapping("/{id}/invoices/{invoiceId}/unlink")
    @PreAuthorize(MANAGE)
    public String unlinkInvoice(@PathVariable UUID id, @PathVariable UUID invoiceId, RedirectAttributes redirectAttributes) {
        return link(id, redirectAttributes, () -> contractService.unlinkInvoice(id, invoiceId), "Tautan invoice dilepas");
    }

    @PostMapping("/{id}/transactions")
    @PreAuthorize(MANAGE)
    public String linkTransaction(@PathVariable UUID id, @RequestParam(required = false) String transactionNumber,
                                  RedirectAttributes redirectAttributes) {
        return link(id, redirectAttributes, () -> contractService.linkTransaction(id, transactionNumber), "Transaksi ditautkan");
    }

    @PostMapping("/{id}/transactions/{transactionId}/unlink")
    @PreAuthorize(MANAGE)
    public String unlinkTransaction(@PathVariable UUID id, @PathVariable UUID transactionId,
                                    RedirectAttributes redirectAttributes) {
        return link(id, redirectAttributes, () -> contractService.unlinkTransaction(id, transactionId),
                "Tautan transaksi dilepas");
    }

    private String link(UUID id, RedirectAttributes redirectAttributes, Runnable action, String success) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute(FLASH_SUCCESS, success);
        } catch (IllegalArgumentException | IllegalStateException | EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return REDIRECT_DETAIL + id;
    }

    private void formModel(Model model, Contract contract) {
        model.addAttribute("contract", contract);
        model.addAttribute("clients", clientRepository.findByActiveTrue());
        model.addAttribute("vendors", vendorRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CONTRACTS);
    }
}
