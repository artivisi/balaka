package com.artivisi.accountingfinance.functional.demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;
import java.util.List;

/**
 * Demo data loader for Coffee Shop industry (Kedai Kopi Nusantara).
 *
 * Features exercised:
 * - Cash sales with COGS (Penjualan Tunai + COGS — DETAILED)
 * - Raw material purchases (tunai + kredit)
 * - Online platform sales (GrabFood/GoFood — DETAILED with adminFee)
 * - Payroll (6 employees, lower salary)
 * - PPh Final UMKM (0.5%)
 * - Fixed assets (coffee equipment) + depreciation
 * - BOM + production orders (from seed data)
 *
 * Usage:
 *   ./mvnw test -Dtest=DemoCoffeeShopDataLoader
 */
@Slf4j
@DisplayName("Demo: Coffee Shop Data Loader")
@DirtiesContext @Tag("demo") @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoCoffeeShopDataLoader extends DemoDataLoaderBase {

    @Override protected String industryName() { return "Coffee Shop"; }
    @Override protected String seedDataPath() { return "industry-seed/coffee-shop/seed-data"; }
    @Override protected String demoDataPath() { return "src/test/resources/demo-data/coffee-shop"; }
    @Override protected int jkkRiskClass() { return 2; }
    @Override protected long baseSalary() { return 5000000; }
    @Override protected boolean ppnEnabled() { return false; }

    @Override
    protected List<FiscalAdj> fiscalAdjustments() {
        return List.of(
                new FiscalAdj("Beban konsumsi karyawan tanpa daftar nominatif", "PERMANENT", "POSITIVE",
                        3000000, "", "Pasal 9 ayat 1 — jamuan karyawan")
        );
    }

    @Test @Order(1) void importSeed() throws Exception { importSeedData(); }
    @Test @Order(2) void importMaster() throws Exception { importMasterData(); }
    @Test @Order(3) void createUsers() { createDemoUsers(); }
    @Test @Order(4) void executeTransactions() {
        executeDemoTransactions("demo-data/coffee-shop/demo-transactions.csv");
    }
    @Test @Order(5) void validateBalance() {
        validateTrialBalance(LocalDate.of(2025, 12, 31));
    }
    @Test @Order(6) void validateDashboardLoads() { validateDashboard(); }
}
