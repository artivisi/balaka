package com.artivisi.accountingfinance.functional.campus;

import com.artivisi.accountingfinance.functional.page.CashFlowPage;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Campus payment collection: students from campus/students.csv are billed and pay through the payment templates,
 * which debit the bank chosen in the form and credit the student receivable. Postings are dated in {@link #YEAR},
 * a year no other campus class uses.
 */
@DisplayName("Campus Industry - Payment Collection")
@Import(CampusTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CampusPaymentTest extends CampusTestBase {

    private static final int YEAR = 2082;

    private final Map<String, UUID> postings = new LinkedHashMap<>();

    @Test
    @Order(1)
    @DisplayName("Should define each payment template as debit the bank chosen at entry, credit the student receivable")
    void shouldDefinePaymentTemplates() {
        loginAsAdmin();
        assertTemplateLines("Pembayaran SPP", "Penerimaan",
                "?|BANK|Debit|amount", "1.1.10|Piutang SPP Mahasiswa|Kredit|amount");
        takeManualScreenshot("campus/payment-templates");
        assertTemplateLines("Pembayaran Uang Pangkal", "Penerimaan",
                "?|BANK|Debit|amount", "1.1.11|Piutang Uang Pangkal|Kredit|amount");
        assertTemplateLines("Pembayaran Biaya Praktikum", "Penerimaan",
                "?|BANK|Debit|amount", "1.1.12|Piutang Biaya Praktikum|Kredit|amount");
        assertTemplateLines("Pembayaran Wisuda", "Penerimaan",
                "?|BANK|Debit|amount", "1.1.13|Piutang Wisuda|Kredit|amount");
    }

    @Test
    @Order(2)
    @DisplayName("Should settle an SPP bill paid in two installments to two banks")
    void shouldSettleSppInInstallments() {
        Student s = student("2201010001");
        long tuition = s.program().tuition();
        assertEquals(7_500_000L, tuition);
        loginAsAdmin();

        postings.put("SPP bill", post("Tagihan SPP Mahasiswa", LocalDate.of(YEAR, 8, 1), tuition,
                "Tagihan SPP " + s.name(), ref("SPP", s), List.of(
                        journal("DEBIT", "1.1.10", tuition), journal("CREDIT", "4.1.01", tuition))));
        postings.put("SPP installment 1", postToBank("Pembayaran SPP", LocalDate.of(YEAR, 8, 10), 3_000_000L,
                "Cicilan 1 SPP " + s.name(), ref("PAY1", s), "1.1.02", List.of(
                        journal("DEBIT", "1.1.02", 3_000_000L), journal("CREDIT", "1.1.10", 3_000_000L))));
        takeManualScreenshot("campus/payment-spp-installment");
        postings.put("SPP installment 2", postToBank("Pembayaran SPP", LocalDate.of(YEAR, 9, 10), 4_500_000L,
                "Cicilan 2 SPP " + s.name(), ref("PAY2", s), "1.1.03", List.of(
                        journal("DEBIT", "1.1.03", 4_500_000L), journal("CREDIT", "1.1.10", 4_500_000L))));

        List<UUID> ids = List.copyOf(postings.values());
        assertEquals(0L, balance("1.1.10", ids), "SPP receivable after both installments");
        assertEquals(3_000_000L, balance("1.1.02", ids));
        assertEquals(4_500_000L, balance("1.1.03", ids));
    }

    @Test
    @Order(3)
    @DisplayName("Should settle enrollment, lab and graduation fees paid in full")
    void shouldSettleOtherFees() {
        Student newStudent = student("2401010001");
        Student finalYear = student("2301010002");
        long enrollment = newStudent.program().enrollmentFee();
        long lab = finalYear.program().labFee();
        long graduation = finalYear.program().graduationFee();
        loginAsAdmin();

        List<UUID> ids = new ArrayList<>();
        ids.add(post("Tagihan Uang Pangkal", LocalDate.of(YEAR, 7, 15), enrollment, "Tagihan uang pangkal " + newStudent.name(),
                ref("UP", newStudent), List.of(journal("DEBIT", "1.1.11", enrollment), journal("CREDIT", "4.1.02", enrollment))));
        ids.add(postToBank("Pembayaran Uang Pangkal", LocalDate.of(YEAR, 7, 20), enrollment, "Pelunasan uang pangkal " + newStudent.name(),
                ref("PAYUP", newStudent), "1.1.02", List.of(journal("DEBIT", "1.1.02", enrollment), journal("CREDIT", "1.1.11", enrollment))));
        ids.add(post("Tagihan Biaya Praktikum", LocalDate.of(YEAR, 8, 5), lab, "Tagihan praktikum " + finalYear.name(),
                ref("PRAK", finalYear), List.of(journal("DEBIT", "1.1.12", lab), journal("CREDIT", "4.1.03", lab))));
        ids.add(postToBank("Pembayaran Biaya Praktikum", LocalDate.of(YEAR, 8, 12), lab, "Pelunasan praktikum " + finalYear.name(),
                ref("PAYPRAK", finalYear), "1.1.02", List.of(journal("DEBIT", "1.1.02", lab), journal("CREDIT", "1.1.12", lab))));
        ids.add(post("Tagihan Wisuda", LocalDate.of(YEAR, 9, 1), graduation, "Tagihan wisuda " + finalYear.name(),
                ref("WIS", finalYear), List.of(journal("DEBIT", "1.1.13", graduation), journal("CREDIT", "4.1.04", graduation))));
        ids.add(postToBank("Pembayaran Wisuda", LocalDate.of(YEAR, 9, 15), graduation, "Pelunasan wisuda " + finalYear.name(),
                ref("PAYWIS", finalYear), "1.1.02", List.of(journal("DEBIT", "1.1.02", graduation), journal("CREDIT", "1.1.13", graduation))));

        assertEquals(0L, balance("1.1.11", ids));
        assertEquals(0L, balance("1.1.12", ids));
        assertEquals(0L, balance("1.1.13", ids));
        assertEquals(enrollment + lab + graduation, balance("1.1.02", ids));
        assertEquals(8_500_000L, enrollment + lab + graduation);
        ids.forEach(id -> postings.put(id.toString(), id));
    }

    @Test
    @Order(4)
    @DisplayName("Should list the year's bills and payments, and only the payments under the Penerimaan filter")
    void shouldListBillsAndPayments() {
        assertEquals(9, postings.size(), "postings from orders 2 and 3");
        loginAsAdmin();
        navigateTo("/transactions?startDate=" + YEAR + "-01-01&endDate=" + YEAR + "-12-31&size=50");
        waitForPageLoad();
        takeManualScreenshot("campus/transaction-list");
        assertThat(page.locator("[id^='trx-row-']")).hasCount(9);
        for (UUID id : postings.values()) {
            Map<String, Object> tx = jdbcTemplate.queryForMap(
                    "SELECT transaction_number, amount FROM transactions WHERE id = ?", id);
            Locator row = page.locator("#trx-row-" + id);
            assertThat(row).containsText((String) tx.get("transaction_number"));
            assertThat(row).containsText("POSTED");
            assertThat(row).containsText(rupiah(((BigDecimal) tx.get("amount")).longValueExact()));
        }

        navigateTo("/transactions?startDate=" + YEAR + "-01-01&endDate=" + YEAR + "-12-31&category=RECEIPT&size=50");
        waitForPageLoad();
        assertThat(page.locator("[id^='trx-row-']")).hasCount(5);
        assertThat(page.locator("#trx-row-" + postings.get("SPP installment 1"))).containsText("Penerimaan");
        assertThat(page.locator("#trx-row-" + postings.get("SPP bill"))).hasCount(0);
    }

    @Test
    @Order(5)
    @DisplayName("Should report each payment template as operating cash inflow; bills move no cash")
    void shouldReportPaymentsAsOperatingCash() {
        loginAsAdmin();
        new CashFlowPage(page, baseUrl()).navigate(YEAR + "-01-01", YEAR + "-12-31")
                .verifyPageTitle();

        assertThat(cashFlowItem("Pembayaran SPP")).hasText("7.500.000");
        assertThat(cashFlowItem("Pembayaran Uang Pangkal")).hasText("5.000.000");
        assertThat(cashFlowItem("Pembayaran Biaya Praktikum")).hasText("1.000.000");
        assertThat(cashFlowItem("Pembayaran Wisuda")).hasText("2.500.000");
        assertThat(cashFlowItem("Tagihan SPP Mahasiswa")).hasCount(0);
        assertThat(page.locator("#operating-total")).hasText("16.000.000");
        assertThat(page.locator("#investing-total")).hasText("0");
        assertThat(page.locator("#financing-total")).hasText("0");
        assertThat(page.locator("#net-cash-change")).hasText("16.000.000");
        assertEquals(amount(page.locator("#beginning-balance")) + 16_000_000L, amount(page.locator("#ending-balance")));

        takeManualScreenshot("campus/payment-cash-flow");
    }

    private Locator cashFlowItem(String templateName) {
        return page.locator("span.text-gray-700:text-is('" + templateName + "') + span");
    }

    private String ref(String prefix, Student s) {
        return prefix + "-" + YEAR + "-" + s.nim() + "-" + runId;
    }
}
