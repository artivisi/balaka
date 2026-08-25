package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.dto.FormulaContext;
import com.artivisi.accountingfinance.entity.InventoryBalance;
import com.artivisi.accountingfinance.entity.InventoryFifoLayer;
import com.artivisi.accountingfinance.entity.InventoryTransaction;
import com.artivisi.accountingfinance.entity.InventoryTransactionType;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.repository.InventoryBalanceRepository;
import com.artivisi.accountingfinance.repository.InventoryFifoLayerRepository;
import com.artivisi.accountingfinance.repository.InventoryTransactionRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryTransactionRepository transactionRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryFifoLayerRepository fifoLayerRepository;
    private final ProductRepository productRepository;
    private final JournalTemplateRepository journalTemplateRepository;
    private final TransactionService transactionService;

    // Template names for inventory journal entries (lookup by name, not hardcoded UUID)
    private static final String PURCHASE_TEMPLATE_NAME = "Pembelian Persediaan";
    private static final String SALE_TEMPLATE_NAME = "Penjualan Persediaan";
    private static final String ADJUSTMENT_IN_TEMPLATE_NAME = "Penyesuaian Persediaan Masuk";
    private static final String ADJUSTMENT_OUT_TEMPLATE_NAME = "Penyesuaian Persediaan Keluar";

    /**
     * Record an inventory purchase.
     * Creates a new FIFO layer and updates balance.
     */
    @Transactional
    public InventoryTransaction recordPurchase(UUID productId, LocalDate date,
                                                BigDecimal quantity, BigDecimal unitCost,
                                                String referenceNumber, String notes) {
        Product product = getProduct(productId);
        return recordInboundTransaction(product, InventoryTransactionType.PURCHASE, date,
                quantity, unitCost, referenceNumber, notes);
    }

    /**
     * Record an inventory sale.
     * Calculates COGS based on product's costing method.
     */
    @Transactional
    public InventoryTransaction recordSale(UUID productId, LocalDate date,
                                           BigDecimal quantity, BigDecimal unitPrice,
                                           String referenceNumber, String notes) {
        Product product = getProduct(productId);
        return recordOutboundTransaction(product, InventoryTransactionType.SALE, date,
                quantity, unitPrice, referenceNumber, notes);
    }

    /**
     * Record an inventory adjustment (increase).
     */
    @Transactional
    public InventoryTransaction recordAdjustmentIn(UUID productId, LocalDate date,
                                                    BigDecimal quantity, BigDecimal unitCost,
                                                    String referenceNumber, String notes) {
        Product product = getProduct(productId);
        return recordInboundTransaction(product, InventoryTransactionType.ADJUSTMENT_IN, date,
                quantity, unitCost, referenceNumber, notes);
    }

    /**
     * Record an inventory adjustment (decrease).
     */
    @Transactional
    public InventoryTransaction recordAdjustmentOut(UUID productId, LocalDate date,
                                                     BigDecimal quantity,
                                                     String referenceNumber, String notes) {
        Product product = getProduct(productId);
        return recordOutboundTransaction(product, InventoryTransactionType.ADJUSTMENT_OUT, date,
                quantity, null, referenceNumber, notes);
    }

    /**
     * Record production output (finished goods).
     */
    @Transactional
    public InventoryTransaction recordProductionIn(UUID productId, LocalDate date,
                                                    BigDecimal quantity, BigDecimal unitCost,
                                                    String referenceNumber, String notes) {
        Product product = getProduct(productId);
        return recordInboundTransaction(product, InventoryTransactionType.PRODUCTION_IN, date,
                quantity, unitCost, referenceNumber, notes);
    }

    /**
     * Record production consumption (raw materials).
     */
    @Transactional
    public InventoryTransaction recordProductionOut(UUID productId, LocalDate date,
                                                     BigDecimal quantity,
                                                     String referenceNumber, String notes) {
        Product product = getProduct(productId);
        return recordOutboundTransaction(product, InventoryTransactionType.PRODUCTION_OUT, date,
                quantity, null, referenceNumber, notes);
    }

    /**
     * Get or create inventory balance for a product.
     * Note: Always called from @Transactional methods, no separate transaction needed.
     */
    public InventoryBalance getOrCreateBalance(Product product) {
        return balanceRepository.findByProduct(product)
                .orElseGet(() -> {
                    InventoryBalance balance = new InventoryBalance();
                    balance.setProduct(product);
                    balance.setQuantity(BigDecimal.ZERO);
                    balance.setTotalCost(BigDecimal.ZERO);
                    balance.setAverageCost(BigDecimal.ZERO);
                    return balanceRepository.save(balance);
                });
    }

    /**
     * Get current stock quantity for a product.
     */
    public BigDecimal getCurrentStock(UUID productId) {
        return balanceRepository.findByProductId(productId)
                .map(InventoryBalance::getQuantity)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Get current average cost for a product.
     */
    public BigDecimal getCurrentAverageCost(UUID productId) {
        return balanceRepository.findByProductId(productId)
                .map(InventoryBalance::getAverageCost)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Calculate COGS for a quantity based on product's costing method.
     */
    public BigDecimal calculateCogs(UUID productId, BigDecimal quantity) {
        Product product = getProduct(productId);
        if (product.getCostingMethod() == CostingMethod.FIFO) {
            return calculateFifoCogs(productId, quantity);
        } else {
            return calculateWeightedAverageCogs(productId, quantity);
        }
    }

    /**
     * Find transactions with filters.
     */
    public Page<InventoryTransaction> findTransactions(UUID productId,
                                                       InventoryTransactionType transactionType,
                                                       LocalDate startDate,
                                                       LocalDate endDate,
                                                       Pageable pageable) {
        String transactionTypeStr = transactionType != null ? transactionType.name() : null;
        return transactionRepository.findByFilters(productId, transactionTypeStr, startDate, endDate, pageable);
    }

    /**
     * Get transaction by ID with product loaded.
     */
    public Optional<InventoryTransaction> findById(UUID id) {
        return Optional.ofNullable(transactionRepository.findByIdWithProduct(id));
    }

    /**
     * Get transactions for a product.
     */
    public List<InventoryTransaction> findByProductId(UUID productId) {
        return transactionRepository.findByProductId(productId);
    }

    /**
     * Find inventory balances with filters.
     */
    public Page<InventoryBalance> findBalances(String search, UUID categoryId, Pageable pageable) {
        return balanceRepository.findByFilters(search, categoryId, pageable);
    }

    /**
     * Get balance for a product.
     */
    public Optional<InventoryBalance> findBalanceByProductId(UUID productId) {
        return balanceRepository.findByProductId(productId);
    }

    /**
     * Get products below minimum stock.
     */
    public List<InventoryBalance> findLowStockProducts() {
        return balanceRepository.findLowStockProducts();
    }

    /**
     * Get FIFO layers for a product.
     */
    public List<InventoryFifoLayer> getFifoLayers(UUID productId) {
        return fifoLayerRepository.findByProductId(productId);
    }

    /**
     * Get total inventory value.
     */
    public BigDecimal getTotalInventoryValue() {
        return balanceRepository.getTotalInventoryValue();
    }

    /**
     * Get available quantity for a product.
     */
    public BigDecimal getAvailableQuantity(UUID productId) {
        return balanceRepository.findByProductId(productId)
                .map(InventoryBalance::getQuantity)
                .orElse(BigDecimal.ZERO);
    }

    // Private helper methods

    private Product getProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produk tidak ditemukan: " + productId));
    }

    private InventoryTransaction recordInboundTransaction(Product product,
                                                          InventoryTransactionType type,
                                                          LocalDate date,
                                                          BigDecimal quantity,
                                                          BigDecimal unitCost,
                                                          String referenceNumber,
                                                          String notes) {
        log.info("Recording inbound {} for product {} qty {} @ {}", type, product.getCode(), quantity, unitCost);

        InventoryBalance balance = getOrCreateBalance(product);

        // Update balance
        balance.addInventory(quantity, unitCost);
        balanceRepository.save(balance);

        // Create transaction
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setProduct(product);
        transaction.setTransactionType(type);
        transaction.setTransactionDate(date);
        transaction.setQuantity(quantity);
        transaction.setUnitCost(unitCost);
        transaction.setTotalCost(quantity.multiply(unitCost));
        transaction.setReferenceNumber(referenceNumber);
        transaction.setNotes(notes);
        transaction.setBalanceAfter(balance.getQuantity());
        transaction.setTotalCostAfter(balance.getTotalCost());
        transaction.setCreatedBy(getCurrentUsername());

        transaction = transactionRepository.save(transaction);

        // Create FIFO layer if product uses FIFO
        if (product.getCostingMethod() == CostingMethod.FIFO) {
            InventoryFifoLayer layer = new InventoryFifoLayer();
            layer.setProduct(product);
            layer.setInventoryTransaction(transaction);
            layer.setLayerDate(date);
            layer.setOriginalQuantity(quantity);
            layer.setRemainingQuantity(quantity);
            layer.setUnitCost(unitCost);
            fifoLayerRepository.save(layer);
        }

        // Create journal entry if product has inventory account configured
        if (product.getInventoryAccount() != null) {
            Transaction journalTransaction = createJournalEntry(transaction, product);
            if (journalTransaction != null) {
                transaction.setTransaction(journalTransaction);
                transaction = transactionRepository.save(transaction);
            }
        }

        log.info("Inbound transaction recorded. New balance: {} @ avg cost {}",
                balance.getQuantity(), balance.getAverageCost());

        return transaction;
    }

    private InventoryTransaction recordOutboundTransaction(Product product,
                                                           InventoryTransactionType type,
                                                           LocalDate date,
                                                           BigDecimal quantity,
                                                           BigDecimal unitPrice,
                                                           String referenceNumber,
                                                           String notes) {
        log.info("Recording outbound {} for product {} qty {}", type, product.getCode(), quantity);

        InventoryBalance balance = getOrCreateBalance(product);

        // Check sufficient stock
        if (balance.getQuantity().compareTo(quantity) < 0) {
            throw new IllegalArgumentException(
                    String.format("Stok tidak mencukupi untuk %s. Stok saat ini: %s, diminta: %s",
                            product.getCode(), balance.getQuantity(), quantity));
        }

        // Calculate cost based on costing method
        BigDecimal unitCost;
        BigDecimal totalCost;

        if (product.getCostingMethod() == CostingMethod.FIFO) {
            totalCost = consumeFifoLayers(product, quantity);
            unitCost = totalCost.divide(quantity, 4, RoundingMode.HALF_UP);
        } else {
            unitCost = balance.getAverageCost();
            totalCost = balance.removeInventory(quantity);
        }

        // Update balance for FIFO (already done in consumeFifoLayers for weighted average)
        if (product.getCostingMethod() == CostingMethod.FIFO) {
            balance.setQuantity(balance.getQuantity().subtract(quantity));
            balance.setTotalCost(balance.getTotalCost().subtract(totalCost));
            if (balance.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                balance.setAverageCost(balance.getTotalCost().divide(balance.getQuantity(), 4, RoundingMode.HALF_UP));
            } else {
                balance.setAverageCost(BigDecimal.ZERO);
                balance.setTotalCost(BigDecimal.ZERO);
            }
            balance.setLastTransactionDate(LocalDateTime.now());
        }
        balanceRepository.save(balance);

        // Create transaction
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setProduct(product);
        transaction.setTransactionType(type);
        transaction.setTransactionDate(date);
        transaction.setQuantity(quantity);
        transaction.setUnitCost(unitCost);
        transaction.setTotalCost(totalCost);
        transaction.setUnitPrice(unitPrice);
        transaction.setReferenceNumber(referenceNumber);
        transaction.setNotes(notes);
        transaction.setBalanceAfter(balance.getQuantity());
        transaction.setTotalCostAfter(balance.getTotalCost());
        transaction.setCreatedBy(getCurrentUsername());

        transaction = transactionRepository.save(transaction);

        // Create journal entry if product has inventory account configured
        if (product.getInventoryAccount() != null) {
            Transaction journalTransaction = createJournalEntry(transaction, product);
            if (journalTransaction != null) {
                transaction.setTransaction(journalTransaction);
                transaction = transactionRepository.save(transaction);
            }
        }

        log.info("Outbound transaction recorded. COGS: {}, New balance: {}",
                totalCost, balance.getQuantity());

        return transaction;
    }

    private BigDecimal consumeFifoLayers(Product product, BigDecimal quantity) {
        List<InventoryFifoLayer> layers = fifoLayerRepository.findAvailableLayers(product.getId());

        BigDecimal remainingQty = quantity;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (InventoryFifoLayer layer : layers) {
            if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal consumed = layer.consume(remainingQty);
            totalCost = totalCost.add(layer.getCostForQuantity(consumed));
            remainingQty = remainingQty.subtract(consumed);

            fifoLayerRepository.save(layer);
        }

        if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                    String.format("FIFO layers insufficient for %s. Missing: %s", product.getCode(), remainingQty));
        }

        return totalCost;
    }

    private BigDecimal calculateFifoCogs(UUID productId, BigDecimal quantity) {
        List<InventoryFifoLayer> layers = fifoLayerRepository.findAvailableLayers(productId);

        BigDecimal remainingQty = quantity;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (InventoryFifoLayer layer : layers) {
            if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal available = layer.getRemainingQuantity();
            BigDecimal toConsume = available.min(remainingQty);
            totalCost = totalCost.add(layer.getCostForQuantity(toConsume));
            remainingQty = remainingQty.subtract(toConsume);
        }

        return totalCost;
    }

    private BigDecimal calculateWeightedAverageCogs(UUID productId, BigDecimal quantity) {
        BigDecimal avgCost = getCurrentAverageCost(productId);
        return quantity.multiply(avgCost);
    }

    private String getCurrentUsername() {
        return CurrentUser.nameOrSystem();
    }

    /**
     * Create and post a journal entry for an inventory transaction.
     * Uses the appropriate template based on transaction type.
     */
    private Transaction createJournalEntry(InventoryTransaction invTransaction, Product product) {
        String templateName = getTemplateNameForType(invTransaction.getTransactionType());
        if (templateName == null) {
            log.debug("No journal template for transaction type: {}", invTransaction.getTransactionType());
            return null;
        }

        Optional<JournalTemplate> templateOpt = journalTemplateRepository.findByTemplateNameWithLines(templateName);
        if (templateOpt.isEmpty()) {
            log.warn("Journal template not found: {}", templateName);
            return null;
        }

        JournalTemplate template = templateOpt.get();
        FormulaContext context = buildFormulaContext(invTransaction);
        String description = buildJournalDescription(invTransaction, product);
        Map<UUID, UUID> accountMappings = buildAccountMappings(template, product);

        Transaction transaction = new Transaction();
        transaction.setJournalTemplate(template);
        transaction.setTransactionDate(invTransaction.getTransactionDate());
        transaction.setAmount(invTransaction.getTotalCost());
        transaction.setDescription(description);
        transaction.setReferenceNumber(invTransaction.getReferenceNumber());

        // DRAFT-by-default: persist context variables so post can compute the journal,
        // then leave the transaction as DRAFT for accounting approval. The InventoryTransaction
        // record itself is the operational truth; accounting approves the resulting journal.
        Transaction savedTransaction = transactionService.create(transaction, accountMappings, context.variables());

        log.info("Created DRAFT journal voucher for inventory transaction {}", invTransaction.getId());

        return savedTransaction;
    }

    private FormulaContext buildFormulaContext(InventoryTransaction invTransaction) {
        Map<String, BigDecimal> variables = new HashMap<>();
        variables.put("amount", invTransaction.getTotalCost());
        variables.put("cogsAmount", invTransaction.getTotalCost());

        if (invTransaction.getUnitPrice() != null) {
            BigDecimal revenueAmount = invTransaction.getQuantity().multiply(invTransaction.getUnitPrice());
            variables.put("revenueAmount", revenueAmount);
        }

        return FormulaContext.of(invTransaction.getTotalCost(), variables);
    }

    private Map<UUID, UUID> buildAccountMappings(JournalTemplate template, Product product) {
        Map<UUID, UUID> accountMappings = new HashMap<>();
        for (JournalTemplateLine line : template.getLines()) {
            if (line.getAccount() != null || line.getAccountHint() == null) {
                continue;
            }
            UUID accountId = resolveAccountFromHint(line.getAccountHint(), product);
            if (accountId != null) {
                accountMappings.put(line.getId(), accountId);
            }
        }
        return accountMappings;
    }

    private UUID resolveAccountFromHint(String hint, Product product) {
        return switch (hint) {
            case "PERSEDIAAN" -> product.getInventoryAccount() != null ? product.getInventoryAccount().getId() : null;
            case "HPP" -> product.getCogsAccount() != null ? product.getCogsAccount().getId() : null;
            case "PENJUALAN" -> product.getSalesAccount() != null ? product.getSalesAccount().getId() : null;
            default -> null;
        };
    }

    private String getTemplateNameForType(InventoryTransactionType type) {
        return switch (type) {
            case PURCHASE -> PURCHASE_TEMPLATE_NAME;
            case SALE -> SALE_TEMPLATE_NAME;
            case ADJUSTMENT_IN -> ADJUSTMENT_IN_TEMPLATE_NAME;
            case ADJUSTMENT_OUT -> ADJUSTMENT_OUT_TEMPLATE_NAME;
            // Production and transfer don't auto-generate journals yet
            default -> null;
        };
    }

    private String buildJournalDescription(InventoryTransaction invTransaction, Product product) {
        String typeDesc = switch (invTransaction.getTransactionType()) {
            case PURCHASE -> "Pembelian";
            case SALE -> "Penjualan";
            case ADJUSTMENT_IN -> "Penyesuaian Masuk";
            case ADJUSTMENT_OUT -> "Penyesuaian Keluar";
            case PRODUCTION_IN -> "Produksi Masuk";
            case PRODUCTION_OUT -> "Produksi Keluar";
            case TRANSFER_IN -> "Transfer Masuk";
            case TRANSFER_OUT -> "Transfer Keluar";
        };
        return String.format("%s %s - %s x %s",
                typeDesc, product.getCode(), invTransaction.getQuantity(), product.getUnit());
    }
}
