package com.artivisi.accountingfinance.functional.demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

@Slf4j
@DisplayName("Verify: Online Seller")
@DirtiesContext @Tag("demo") @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OnlineSellerVerificationTest extends DemoVerificationTest {
    @Override protected String industryName() { return "Online Seller"; }
    @Override protected String seedDataPath() { return "industry-seed/online-seller/seed-data"; }
    @Override protected String demoDataPath() { return "src/test/resources/demo-data/online-seller"; }
    @Override protected long baseSalary() { return 5500000; }
    @Override protected boolean ppnEnabled() { return false; }
    @Override protected List<FiscalAdj> fiscalAdjustments() {
        return List.of(
            new FiscalAdj("Beban iklan marketplace non-deductible", "PERMANENT", "POSITIVE",
                    2000000, "", "Pasal 9 — iklan tanpa bukti")
        );
    }
}
