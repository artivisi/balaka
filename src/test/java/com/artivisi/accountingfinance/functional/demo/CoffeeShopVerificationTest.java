package com.artivisi.accountingfinance.functional.demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.List;

@Slf4j
@DisplayName("Verify: Coffee Shop")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CoffeeShopVerificationTest extends DemoVerificationTest {
    @Override protected String industryName() { return "Coffee Shop"; }
    @Override protected String seedDataPath() { return "industry-seed/coffee-shop/seed-data"; }
    @Override protected String demoDataPath() { return "src/test/resources/demo-data/coffee-shop"; }
    @Override protected int jkkRiskClass() { return 2; }
    @Override protected long baseSalary() { return 5000000; }
    @Override protected boolean ppnEnabled() { return false; }
    @Override protected List<FiscalAdj> fiscalAdjustments() {
        return List.of(
            new FiscalAdj("Beban konsumsi karyawan tanpa daftar nominatif", "PERMANENT", "POSITIVE",
                    3000000, "", "Pasal 9 ayat 1 — jamuan karyawan")
        );
    }
}
