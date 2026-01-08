package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductService {

    private static final String ERR_PRODUCT_NOT_FOUND = "Produk tidak ditemukan: ";

    private final ProductRepository productRepository;

    public Product create(Product product) {
        validateUniqueCode(product.getCode(), null);
        return productRepository.save(product);
    }

    public Product update(UUID id, Product updated) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ERR_PRODUCT_NOT_FOUND + id));

        validateUniqueCode(updated.getCode(), id);

        existing.setCode(updated.getCode());
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setUnit(updated.getUnit());
        existing.setCategory(updated.getCategory());
        existing.setCostingMethod(updated.getCostingMethod());
        existing.setTrackInventory(updated.isTrackInventory());
        existing.setMinimumStock(updated.getMinimumStock());
        existing.setSellingPrice(updated.getSellingPrice());
        existing.setInventoryAccount(updated.getInventoryAccount());
        existing.setCogsAccount(updated.getCogsAccount());
        existing.setSalesAccount(updated.getSalesAccount());
        existing.setActive(updated.isActive());

        return productRepository.save(existing);
    }

    public void delete(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ERR_PRODUCT_NOT_FOUND + id));

        // TODO: Check for inventory transactions before deleting

        productRepository.delete(product);
        log.info("Deleted product: {}", product.getCode());
    }

    public void activate(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ERR_PRODUCT_NOT_FOUND + id));
        product.setActive(true);
        productRepository.save(product);
        log.info("Activated product: {}", product.getCode());
    }

    public void deactivate(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ERR_PRODUCT_NOT_FOUND + id));
        product.setActive(false);
        productRepository.save(product);
        log.info("Deactivated product: {}", product.getCode());
    }

    @Transactional(readOnly = true)
    public Optional<Product> findById(UUID id) {
        return productRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Product> findByIdWithDetails(UUID id) {
        return productRepository.findByIdWithDetails(id);
    }

    @Transactional(readOnly = true)
    public Optional<Product> findByCode(String code) {
        return productRepository.findByCode(code);
    }

    @Transactional(readOnly = true)
    public List<Product> findAllActive() {
        return productRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public List<Product> findByCategoryId(UUID categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }

    @Transactional(readOnly = true)
    public Page<Product> findByFilters(String search, UUID categoryId, Boolean active, Pageable pageable) {
        return productRepository.findByFilters(search, categoryId, active, pageable);
    }

    @Transactional(readOnly = true)
    public List<Product> findTrackableProducts() {
        return productRepository.findTrackableProducts();
    }

    private void validateUniqueCode(String code, UUID excludeId) {
        if (excludeId == null) {
            if (productRepository.existsByCode(code)) {
                throw new IllegalArgumentException("Kode produk sudah digunakan: " + code);
            }
        } else {
            if (productRepository.existsByCodeAndIdNot(code, excludeId)) {
                throw new IllegalArgumentException("Kode produk sudah digunakan: " + code);
            }
        }
    }
}
