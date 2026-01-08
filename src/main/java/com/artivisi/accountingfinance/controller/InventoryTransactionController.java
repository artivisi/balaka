package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.InventoryBalance;
import com.artivisi.accountingfinance.entity.InventoryFifoLayer;
import com.artivisi.accountingfinance.entity.InventoryTransaction;
import com.artivisi.accountingfinance.entity.InventoryTransactionType;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.InventoryService;
import com.artivisi.accountingfinance.service.ProductCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryTransactionController {

    private static final String ATTR_PRODUCTS = "products";
    private static final String ATTR_TRANSACTIONS = "transactions";
    private static final String ATTR_TRANSACTION = "transaction";
    private static final String REDIRECT_INVENTORY_TRANSACTIONS = "redirect:/inventory/transactions/";
    private static final String VIEW_PURCHASE_FORM = "inventory/purchase-form";
    private static final String VIEW_SALE_FORM = "inventory/sale-form";
    private static final String VIEW_ADJUSTMENT_FORM = "inventory/adjustment-form";

    private final InventoryService inventoryService;
    private final ProductRepository productRepository;
    private final ProductCategoryService categoryService;

    // ============================================
    // Stock Balance List
    // ============================================

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_VIEW + "')")
    public String listStock(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        Pageable pageable = PageRequest.of(page, size);
        Page<InventoryBalance> balances = inventoryService.findBalances(search, categoryId, pageable);

        model.addAttribute("balances", balances);
        model.addAttribute("search", search);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("categories", categoryService.findAllActive());
        model.addAttribute("lowStockProducts", inventoryService.findLowStockProducts());
        model.addAttribute("totalInventoryValue", inventoryService.getTotalInventoryValue());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_STOCK);

        return "inventory/stock-list";
    }

    @GetMapping("/stock/table")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_VIEW + "')")
    public String stockTable(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        Pageable pageable = PageRequest.of(page, size);
        Page<InventoryBalance> balances = inventoryService.findBalances(search, categoryId, pageable);

        model.addAttribute("balances", balances);
        model.addAttribute("search", search);
        model.addAttribute("categoryId", categoryId);

        return "inventory/fragments/stock-table :: stockTable";
    }

    // ============================================
    // Transaction List
    // ============================================

    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_VIEW + "')")
    public String listTransactions(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) InventoryTransactionType transactionType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        Pageable pageable = PageRequest.of(page, size);
        Page<InventoryTransaction> transactions = inventoryService.findTransactions(
                productId, transactionType, startDate, endDate, pageable);

        model.addAttribute(ATTR_TRANSACTIONS, transactions);
        model.addAttribute("productId", productId);
        model.addAttribute("transactionType", transactionType);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("transactionTypes", InventoryTransactionType.values());
        model.addAttribute(ATTR_PRODUCTS, productRepository.findAllActiveOrderByCode());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_TRANSACTIONS);

        return "inventory/transaction-list";
    }

    @GetMapping("/transactions/table")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_VIEW + "')")
    public String transactionTable(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) InventoryTransactionType transactionType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        Pageable pageable = PageRequest.of(page, size);
        Page<InventoryTransaction> transactions = inventoryService.findTransactions(
                productId, transactionType, startDate, endDate, pageable);

        model.addAttribute(ATTR_TRANSACTIONS, transactions);
        model.addAttribute("productId", productId);
        model.addAttribute("transactionType", transactionType);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "inventory/fragments/transaction-table :: transactionTable";
    }

    // ============================================
    // Transaction Detail
    // ============================================

    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_VIEW + "')")
    public String viewTransaction(@PathVariable UUID id, Model model) {
        InventoryTransaction transaction = inventoryService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaksi tidak ditemukan: " + id));

        model.addAttribute(ATTR_TRANSACTION, transaction);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_TRANSACTIONS);

        return "inventory/transaction-detail";
    }

    // ============================================
    // Purchase Form
    // ============================================

    @GetMapping("/purchase")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_PURCHASE + "')")
    public String showPurchaseForm(Model model) {
        model.addAttribute("form", new InventoryTransactionForm());
        model.addAttribute(ATTR_PRODUCTS, productRepository.findAllActiveOrderByCode());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_PURCHASE);

        return VIEW_PURCHASE_FORM;
    }

    @PostMapping("/purchase")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_PURCHASE + "')")
    public String recordPurchase(
            @Valid @ModelAttribute("form") InventoryTransactionForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_PRODUCTS, productRepository.findAllActiveOrderByCode());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_PURCHASE);
            return VIEW_PURCHASE_FORM;
        }

        try {
            InventoryTransaction transaction = inventoryService.recordPurchase(
                    form.getProductId(),
                    form.getTransactionDate(),
                    form.getQuantity(),
                    form.getUnitCost(),
                    form.getReferenceNumber(),
                    form.getNotes()
            );

            redirectAttributes.addFlashAttribute("success", "Pembelian berhasil dicatat");
            return REDIRECT_INVENTORY_TRANSACTIONS + transaction.getId();
        } catch (Exception e) {
            log.error("Error recording purchase", e);
            model.addAttribute("error", e.getMessage());
            model.addAttribute(ATTR_PRODUCTS, productRepository.findAllActiveOrderByCode());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_PURCHASE);
            return VIEW_PURCHASE_FORM;
        }
    }

    // ============================================
    // Sale Form
    // ============================================

    @GetMapping("/sale")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_SALE + "')")
    public String showSaleForm(Model model) {
        model.addAttribute("form", new InventoryTransactionForm());
        model.addAttribute(ATTR_PRODUCTS, productRepository.findAllActiveOrderByCode());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_SALE);

        return VIEW_SALE_FORM;
    }

    @PostMapping("/sale")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_SALE + "')")
    public String recordSale(
            @Valid @ModelAttribute("form") InventoryTransactionForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_PRODUCTS, productRepository.findAllActiveOrderByCode());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_SALE);
            return VIEW_SALE_FORM;
        }

        try {
            InventoryTransaction transaction = inventoryService.recordSale(
                    form.getProductId(),
                    form.getTransactionDate(),
                    form.getQuantity(),
                    form.getUnitPrice(),
                    form.getReferenceNumber(),
                    form.getNotes()
            );

            redirectAttributes.addFlashAttribute("success", "Penjualan berhasil dicatat");
            return REDIRECT_INVENTORY_TRANSACTIONS + transaction.getId();
        } catch (Exception e) {
            log.error("Error recording sale", e);
            model.addAttribute("error", e.getMessage());
            model.addAttribute(ATTR_PRODUCTS, productRepository.findAllActiveOrderByCode());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_SALE);
            return VIEW_SALE_FORM;
        }
    }

    // ============================================
    // Adjustment Form
    // ============================================

    @GetMapping("/adjustment")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_ADJUST + "')")
    public String showAdjustmentForm(Model model) {
        model.addAttribute("form", new InventoryTransactionForm());
        model.addAttribute(ATTR_PRODUCTS, productRepository.findAllActiveOrderByCode());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_ADJUSTMENT);

        return VIEW_ADJUSTMENT_FORM;
    }

    @PostMapping("/adjustment")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_ADJUST + "')")
    public String recordAdjustment(
            @Valid @ModelAttribute("form") InventoryTransactionForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_PRODUCTS, productRepository.findAllActiveOrderByCode());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_ADJUSTMENT);
            return VIEW_ADJUSTMENT_FORM;
        }

        try {
            InventoryTransaction transaction;
            if (form.isInbound()) {
                transaction = inventoryService.recordAdjustmentIn(
                        form.getProductId(),
                        form.getTransactionDate(),
                        form.getQuantity(),
                        form.getUnitCost(),
                        form.getReferenceNumber(),
                        form.getNotes()
                );
            } else {
                transaction = inventoryService.recordAdjustmentOut(
                        form.getProductId(),
                        form.getTransactionDate(),
                        form.getQuantity(),
                        form.getReferenceNumber(),
                        form.getNotes()
                );
            }

            redirectAttributes.addFlashAttribute("success", "Penyesuaian berhasil dicatat");
            return REDIRECT_INVENTORY_TRANSACTIONS + transaction.getId();
        } catch (Exception e) {
            log.error("Error recording adjustment", e);
            model.addAttribute("error", e.getMessage());
            model.addAttribute(ATTR_PRODUCTS, productRepository.findAllActiveOrderByCode());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_ADJUSTMENT);
            return VIEW_ADJUSTMENT_FORM;
        }
    }

    // ============================================
    // Product Stock Card
    // ============================================

    @GetMapping("/stock/{productId}")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_VIEW + "')")
    public String viewStockCard(@PathVariable UUID productId, Model model) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produk tidak ditemukan: " + productId));

        InventoryBalance balance = inventoryService.findBalanceByProductId(productId)
                .orElse(null);

        List<InventoryTransaction> transactions = inventoryService.findByProductId(productId);
        List<InventoryFifoLayer> fifoLayers = inventoryService.getFifoLayers(productId);

        model.addAttribute("product", product);
        model.addAttribute("balance", balance);
        model.addAttribute(ATTR_TRANSACTIONS, transactions);
        model.addAttribute("fifoLayers", fifoLayers);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVENTORY_STOCK);

        return "inventory/stock-card";
    }

    // ============================================
    // Form DTO
    // ============================================

    public static class InventoryTransactionForm {
        private UUID productId;
        private LocalDate transactionDate = LocalDate.now();
        private BigDecimal quantity;
        private BigDecimal unitCost;
        private BigDecimal unitPrice;
        private String referenceNumber;
        private String notes;
        private boolean inbound = true; // for adjustment

        // Getters and setters
        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }

        public LocalDate getTransactionDate() { return transactionDate; }
        public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

        public BigDecimal getUnitCost() { return unitCost; }
        public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

        public String getReferenceNumber() { return referenceNumber; }
        public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }

        public boolean isInbound() { return inbound; }
        public void setInbound(boolean inbound) { this.inbound = inbound; }
    }
}
