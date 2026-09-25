package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    @Query("SELECT p FROM Product p WHERE p.active = true ORDER BY p.name")
    List<Product> findAllActive();

    @Query("SELECT p FROM Product p WHERE p.active = true ORDER BY p.code")
    List<Product> findAllActiveOrderByCode();

    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId ORDER BY p.name")
    List<Product> findByCategoryId(@Param("categoryId") UUID categoryId);

    @Query(value = "SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE " +
           "(COALESCE(:search, '') = '' OR LOWER(p.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:active IS NULL OR p.active = :active) " +
           "ORDER BY p.code",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE " +
           "(COALESCE(:search, '') = '' OR LOWER(p.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:active IS NULL OR p.active = :active)")
    Page<Product> findByFilters(@Param("search") String search,
                                @Param("categoryId") UUID categoryId,
                                @Param("active") Boolean active,
                                Pageable pageable);

    @Query("SELECT COUNT(b) FROM BillOfMaterial b WHERE b.product.id = :productId")
    long countBillOfMaterialsByProductId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(l) FROM BillOfMaterialLine l WHERE l.component.id = :productId")
    long countBillOfMaterialLinesByComponentId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(l) FROM InvoiceLine l WHERE l.product.id = :productId")
    long countInvoiceLinesByProductId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(l) FROM BillLine l WHERE l.product.id = :productId")
    long countBillLinesByProductId(@Param("productId") UUID productId);

    @Query("SELECT p FROM Product p WHERE p.trackInventory = true AND p.active = true ORDER BY p.name")
    List<Product> findTrackableProducts();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.inventoryAccount " +
           "LEFT JOIN FETCH p.cogsAccount LEFT JOIN FETCH p.salesAccount WHERE p.id = :id")
    Optional<Product> findByIdWithDetails(@Param("id") UUID id);
}
