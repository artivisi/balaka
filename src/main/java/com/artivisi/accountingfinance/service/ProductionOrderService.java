package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.BillOfMaterial;
import com.artivisi.accountingfinance.entity.BillOfMaterialLine;
import com.artivisi.accountingfinance.entity.ProductionOrder;
import com.artivisi.accountingfinance.entity.ProductionOrderStatus;
import com.artivisi.accountingfinance.repository.BillOfMaterialRepository;
import com.artivisi.accountingfinance.repository.ProductionOrderRepository;
import com.artivisi.accountingfinance.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionOrderService {

    private static final String MSG_ORDER_NOT_FOUND = "Production order tidak ditemukan: ";

    private final ProductionOrderRepository orderRepository;
    private final BillOfMaterialRepository bomRepository;
    private final InventoryService inventoryService;
    private final DocumentPostingService documentPostingService;

    @Transactional(readOnly = true)
    public List<ProductionOrder> findAll() {
        return orderRepository.findAllWithBom();
    }

    @Transactional(readOnly = true)
    public List<ProductionOrder> findByStatus(ProductionOrderStatus status) {
        return orderRepository.findByStatusWithBom(status);
    }

    @Transactional(readOnly = true)
    public Optional<ProductionOrder> findById(UUID id) {
        return orderRepository.findByIdWithBom(id);
    }

    @Transactional(readOnly = true)
    public Optional<ProductionOrder> findByIdWithLines(UUID id) {
        return orderRepository.findByIdWithBomAndLines(id);
    }

    @Transactional
    public ProductionOrder create(ProductionOrder order) {
        validateOrder(order);

        // Generate order number
        String orderNumber = generateOrderNumber();
        order.setOrderNumber(orderNumber);

        // Load BOM with lines
        BillOfMaterial bom = bomRepository.findByIdWithLines(order.getBillOfMaterial().getId())
                .orElseThrow(() -> new IllegalArgumentException("BOM tidak ditemukan"));
        order.setBillOfMaterial(bom);

        order.setStatus(ProductionOrderStatus.DRAFT);
        order.setCreatedBy(getCurrentUsername());

        log.info("Creating production order {} for BOM {}", orderNumber, bom.getCode());
        return orderRepository.save(order);
    }

    @Transactional
    public ProductionOrder update(UUID id, ProductionOrder updated) {
        ProductionOrder existing = orderRepository.findByIdWithBom(id)
                .orElseThrow(() -> new IllegalArgumentException(MSG_ORDER_NOT_FOUND + id));

        if (!existing.isDraft()) {
            throw new IllegalStateException("Hanya order dengan status DRAFT yang dapat diubah");
        }

        validateOrder(updated);

        // Load BOM
        BillOfMaterial bom = bomRepository.findByIdWithLines(updated.getBillOfMaterial().getId())
                .orElseThrow(() -> new IllegalArgumentException("BOM tidak ditemukan"));

        existing.setBillOfMaterial(bom);
        existing.setQuantity(updated.getQuantity());
        existing.setOrderDate(updated.getOrderDate());
        existing.setPlannedCompletionDate(updated.getPlannedCompletionDate());
        existing.setNotes(updated.getNotes());

        log.info("Updating production order {}", existing.getOrderNumber());
        return orderRepository.save(existing);
    }

    /**
     * Start production - changes status from DRAFT to IN_PROGRESS.
     * This reserves the raw materials but doesn't consume them yet.
     */
    @Transactional
    public ProductionOrder start(UUID id) {
        ProductionOrder order = orderRepository.findByIdWithBomAndLines(id)
                .orElseThrow(() -> new IllegalArgumentException(MSG_ORDER_NOT_FOUND + id));

        if (!order.canStart()) {
            throw new IllegalStateException("Order tidak dapat dimulai dari status " + order.getStatus());
        }

        // Validate that all components have sufficient stock
        BillOfMaterial bom = order.getBillOfMaterial();
        BigDecimal multiplier = order.getQuantity().divide(bom.getOutputQuantity(), 4, RoundingMode.HALF_UP);

        for (BillOfMaterialLine line : bom.getLines()) {
            BigDecimal requiredQty = line.getQuantity().multiply(multiplier);
            BigDecimal availableQty = inventoryService.getAvailableQuantity(line.getComponent().getId());

            if (availableQty.compareTo(requiredQty) < 0) {
                throw new IllegalStateException(String.format(
                        "Stok komponen %s tidak mencukupi. Dibutuhkan: %s, Tersedia: %s",
                        line.getComponent().getCode(),
                        requiredQty.setScale(2, RoundingMode.HALF_UP),
                        availableQty.setScale(2, RoundingMode.HALF_UP)
                ));
            }
        }

        order.setStatus(ProductionOrderStatus.IN_PROGRESS);
        log.info("Starting production order {}", order.getOrderNumber());
        return orderRepository.save(order);
    }

    /**
     * Complete production - consumes raw materials and adds finished goods.
     * Creates inventory transactions for material consumption and production output.
     */
    @Transactional
    public ProductionOrder complete(UUID id) {
        ProductionOrder order = orderRepository.findByIdWithBomAndLines(id)
                .orElseThrow(() -> new IllegalArgumentException(MSG_ORDER_NOT_FOUND + id));

        if (!order.canComplete()) {
            throw new IllegalStateException("Order tidak dapat diselesaikan dari status " + order.getStatus());
        }

        BillOfMaterial bom = order.getBillOfMaterial();
        BigDecimal multiplier = order.getQuantity().divide(bom.getOutputQuantity(), 4, RoundingMode.HALF_UP);

        // Calculate total component cost and consume materials
        BigDecimal totalCost = BigDecimal.ZERO;
        String referenceNumber = order.getOrderNumber();

        // Finished good's inventory account is the debit side for every produced-DRAFT.
        var finishedGoodAccount = bom.getProduct().getInventoryAccount();
        if (finishedGoodAccount == null) {
            throw new IllegalStateException("Produk jadi '" + bom.getProduct().getName()
                    + "' belum memiliki Akun Persediaan");
        }

        for (BillOfMaterialLine line : bom.getLines()) {
            BigDecimal requiredQty = line.getQuantity().multiply(multiplier);

            // Record production out (consume raw materials)
            var outTx = inventoryService.recordProductionOut(
                    line.getComponent().getId(),
                    order.getOrderDate(),
                    requiredQty,
                    referenceNumber,
                    "Konsumsi untuk produksi " + bom.getProduct().getCode()
            );

            totalCost = totalCost.add(outTx.getTotalCost());

            // Compose a DRAFT journal per component: Dr finished-goods / Cr raw-material
            // (skips a transient WIP for simplicity; one DRAFT per BOM line — R2 pattern).
            var rawMaterialAccount = line.getComponent().getInventoryAccount();
            if (rawMaterialAccount == null) {
                throw new IllegalStateException("Bahan baku '" + line.getComponent().getName()
                        + "' belum memiliki Akun Persediaan");
            }
            java.util.Map<String, java.util.UUID> hints = new java.util.HashMap<>();
            hints.put("PERSEDIAAN_JADI", finishedGoodAccount.getId());
            hints.put("PERSEDIAAN_BAHAN", rawMaterialAccount.getId());
            java.util.Map<String, BigDecimal> vars = new java.util.HashMap<>();
            vars.put("componentCost", outTx.getTotalCost());
            documentPostingService.createDraftFromTemplate(DocumentPostingService.DraftRequest.builder()
                    .templateName("Penyelesaian Produksi")
                    .date(order.getOrderDate())
                    .description("Produksi " + order.getOrderNumber() + " - " + line.getComponent().getName())
                    .amount(outTx.getTotalCost())
                    .hintToAccount(hints)
                    .variables(vars)
                    .createdBy(getCurrentUsername())
                    .sourceDocumentType("PRODUCTION_ORDER")
                    .sourceDocumentId(order.getId())
                    .build());
        }

        // Calculate unit cost for finished goods
        BigDecimal unitCost = totalCost.divide(order.getQuantity(), 4, RoundingMode.HALF_UP);

        // Record production in (add finished goods)
        inventoryService.recordProductionIn(
                bom.getProduct().getId(),
                order.getOrderDate(),
                order.getQuantity(),
                unitCost,
                referenceNumber,
                "Hasil produksi dari BOM " + bom.getCode()
        );

        order.setTotalComponentCost(totalCost);
        order.setUnitCost(unitCost);
        order.setStatus(ProductionOrderStatus.COMPLETED);
        order.setActualCompletionDate(LocalDate.now());
        order.setCompletedBy(getCurrentUsername());

        log.info("Completed production order {}. Total cost: {}, Unit cost: {}",
                order.getOrderNumber(), totalCost, unitCost);
        return orderRepository.save(order);
    }

    /**
     * Cancel production order.
     * Can only cancel if DRAFT or IN_PROGRESS (if IN_PROGRESS, no materials have been consumed yet).
     */
    @Transactional
    public ProductionOrder cancel(UUID id) {
        ProductionOrder order = orderRepository.findByIdWithBom(id)
                .orElseThrow(() -> new IllegalArgumentException(MSG_ORDER_NOT_FOUND + id));

        if (!order.canCancel()) {
            throw new IllegalStateException("Order tidak dapat dibatalkan dari status " + order.getStatus());
        }

        order.setStatus(ProductionOrderStatus.CANCELLED);
        log.info("Cancelled production order {}", order.getOrderNumber());
        return orderRepository.save(order);
    }

    @Transactional
    public void delete(UUID id) {
        ProductionOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(MSG_ORDER_NOT_FOUND + id));

        if (!order.isDraft()) {
            throw new IllegalStateException("Hanya order dengan status DRAFT yang dapat dihapus");
        }

        log.info("Deleting production order {}", order.getOrderNumber());
        orderRepository.delete(order);
    }

    private void validateOrder(ProductionOrder order) {
        if (order.getBillOfMaterial() == null || order.getBillOfMaterial().getId() == null) {
            throw new IllegalArgumentException("BOM wajib dipilih");
        }
        if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Jumlah produksi harus lebih dari 0");
        }
        if (order.getOrderDate() == null) {
            throw new IllegalArgumentException("Tanggal order wajib diisi");
        }
    }

    private String generateOrderNumber() {
        String year = String.valueOf(Year.now().getValue());
        Integer maxNumber = orderRepository.findMaxOrderNumberForYear(year);
        int nextNumber = (maxNumber == null ? 0 : maxNumber) + 1;
        return String.format("PO-%s-%04d", year, nextNumber);
    }

    private String getCurrentUsername() {
        return CurrentUser.nameOrSystem();
    }
}
