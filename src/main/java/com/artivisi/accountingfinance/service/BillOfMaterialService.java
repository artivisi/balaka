package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.BillOfMaterial;
import com.artivisi.accountingfinance.entity.BillOfMaterialLine;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.repository.BillOfMaterialRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import com.artivisi.accountingfinance.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillOfMaterialService {

    private final BillOfMaterialRepository bomRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<BillOfMaterial> findAll() {
        return bomRepository.findAllActiveWithProduct();
    }

    @Transactional(readOnly = true)
    public List<BillOfMaterial> search(String search) {
        if (search == null || search.isBlank()) {
            return bomRepository.findAllActiveWithProduct();
        }
        return bomRepository.searchActive(search);
    }

    @Transactional(readOnly = true)
    public Optional<BillOfMaterial> findById(UUID id) {
        return bomRepository.findByIdWithProduct(id);
    }

    @Transactional(readOnly = true)
    public Optional<BillOfMaterial> findByIdWithLines(UUID id) {
        return bomRepository.findByIdWithLines(id);
    }

    @Transactional(readOnly = true)
    public Optional<BillOfMaterial> findByCode(String code) {
        return bomRepository.findByCode(code);
    }

    @Transactional
    public BillOfMaterial create(BillOfMaterial bom) {
        validateBom(bom);

        if (bomRepository.existsByCode(bom.getCode())) {
            throw new IllegalArgumentException("Kode BOM '" + bom.getCode() + "' sudah digunakan");
        }

        // Validate that product is selected
        if (bom.getProduct() == null || bom.getProduct().getId() == null) {
            throw new IllegalArgumentException("Produk jadi wajib dipilih");
        }

        // Load the product entity
        Product product = productRepository.findById(bom.getProduct().getId())
                .orElseThrow(() -> new IllegalArgumentException("Produk tidak ditemukan"));
        bom.setProduct(product);

        // Validate and load component products
        for (BillOfMaterialLine line : bom.getLines()) {
            if (line.getComponent() == null || line.getComponent().getId() == null) {
                throw new IllegalArgumentException("Komponen pada baris wajib dipilih");
            }
            Product component = productRepository.findById(line.getComponent().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Komponen tidak ditemukan: " + line.getComponent().getId()));

            // Cannot use the finished product as a component of itself
            if (component.getId().equals(product.getId())) {
                throw new IllegalArgumentException("Produk jadi tidak boleh menjadi komponen dari dirinya sendiri");
            }

            line.setComponent(component);
            line.setBillOfMaterial(bom);
        }

        log.info("Creating BOM {} for product {}", LogSanitizer.sanitize(bom.getCode()), LogSanitizer.sanitize(product.getCode()));
        return bomRepository.save(bom);
    }

    @Transactional
    public BillOfMaterial update(UUID id, BillOfMaterial updated) {
        BillOfMaterial existing = bomRepository.findByIdWithLines(id)
                .orElseThrow(() -> new IllegalArgumentException("BOM tidak ditemukan: " + id));

        validateBom(updated);

        if (bomRepository.existsByCodeAndIdNot(updated.getCode(), id)) {
            throw new IllegalArgumentException("Kode BOM '" + updated.getCode() + "' sudah digunakan");
        }

        // Load the product entity
        if (updated.getProduct() == null || updated.getProduct().getId() == null) {
            throw new IllegalArgumentException("Produk jadi wajib dipilih");
        }
        Product product = productRepository.findById(updated.getProduct().getId())
                .orElseThrow(() -> new IllegalArgumentException("Produk tidak ditemukan"));

        existing.setCode(updated.getCode());
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setProduct(product);
        existing.setOutputQuantity(updated.getOutputQuantity());
        existing.setActive(updated.isActive());

        // Update lines
        existing.clearLines();
        for (BillOfMaterialLine line : updated.getLines()) {
            if (line.getComponent() == null || line.getComponent().getId() == null) {
                throw new IllegalArgumentException("Komponen pada baris wajib dipilih");
            }
            Product component = productRepository.findById(line.getComponent().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Komponen tidak ditemukan: " + line.getComponent().getId()));

            if (component.getId().equals(product.getId())) {
                throw new IllegalArgumentException("Produk jadi tidak boleh menjadi komponen dari dirinya sendiri");
            }

            BillOfMaterialLine newLine = new BillOfMaterialLine();
            newLine.setComponent(component);
            newLine.setQuantity(line.getQuantity());
            newLine.setNotes(line.getNotes());
            newLine.setLineOrder(line.getLineOrder());
            existing.addLine(newLine);
        }

        log.info("Updating BOM {} for product {}", LogSanitizer.sanitize(existing.getCode()), LogSanitizer.sanitize(product.getCode()));
        return bomRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        BillOfMaterial bom = bomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BOM tidak ditemukan: " + id));

        // Soft delete by deactivating
        bom.setActive(false);
        log.info("Deactivating BOM {}", bom.getCode());
        bomRepository.save(bom);
    }

    private void validateBom(BillOfMaterial bom) {
        if (bom.getCode() == null || bom.getCode().isBlank()) {
            throw new IllegalArgumentException("Kode BOM wajib diisi");
        }
        if (bom.getName() == null || bom.getName().isBlank()) {
            throw new IllegalArgumentException("Nama BOM wajib diisi");
        }
        if (bom.getOutputQuantity() == null || bom.getOutputQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Jumlah output harus lebih dari 0");
        }
        if (bom.getLines() == null || bom.getLines().isEmpty()) {
            throw new IllegalArgumentException("BOM harus memiliki minimal 1 komponen");
        }
        for (BillOfMaterialLine line : bom.getLines()) {
            if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Jumlah komponen harus lebih dari 0");
            }
        }
    }
}
