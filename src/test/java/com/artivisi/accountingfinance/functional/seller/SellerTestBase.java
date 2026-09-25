package com.artivisi.accountingfinance.functional.seller;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CostingMethod;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.ProductCategory;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ProductCategoryRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared helpers for the seller classes that share the seller Spring context: own products, inventory movements
 * recorded through the inventory forms, template transactions posted through the transaction form, and report
 * locators.
 */
abstract class SellerTestBase extends PlaywrightTestBase {

    private static final Pattern INVENTORY_DETAIL_URL = Pattern.compile(".*/inventory/transactions/([0-9a-f-]{36})$");
    private static final Pattern TRANSACTION_DETAIL_URL = Pattern.compile(".*/transactions/([0-9a-f-]{36})$");

    protected final String runId = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    protected SellerTestDataHelper testDataHelper;

    /** Creates a tracked product of this run in the helper's category, on the seller inventory/COGS/sales accounts. */
    protected Product createProduct(String code, String name, CostingMethod costingMethod, String categoryCode,
                                    long sellingPrice) {
        testDataHelper.setupProductsAndCategories();
        Product product = new Product();
        product.setCode(code);
        product.setName(name);
        product.setUnit("pcs");
        product.setCostingMethod(costingMethod);
        product.setCategory(categoryRepository.findByCode(categoryCode)
                .orElseThrow(() -> new IllegalStateException("Seller category missing: " + categoryCode)));
        product.setSellingPrice(BigDecimal.valueOf(sellingPrice));
        product.setInventoryAccount(account("1.1.20"));
        product.setCogsAccount(account("5.1.01"));
        product.setSalesAccount(account("4.1.01"));
        product.setTrackInventory(true);
        product.setActive(true);
        return productRepository.save(product);
    }

    /** Records a purchase through /inventory/purchase and returns the movement id. */
    protected UUID recordPurchase(Product product, LocalDate date, int quantity, long unitCost, String reference) {
        navigateTo("/inventory/purchase");
        waitForPageLoad();
        fillMovement(product, date, quantity, reference, "Pembelian " + reference);
        page.locator("#unitCost").fill(String.valueOf(unitCost));
        return submitMovement("Pembelian berhasil dicatat");
    }

    /** Records a sale through /inventory/sale and returns the movement id. */
    protected UUID recordSale(Product product, LocalDate date, int quantity, long unitPrice, String reference) {
        navigateTo("/inventory/sale");
        waitForPageLoad();
        fillMovement(product, date, quantity, reference, "Penjualan " + reference);
        page.locator("#unitPrice").fill(String.valueOf(unitPrice));
        return submitMovement("Penjualan berhasil dicatat");
    }

    /** Records an outbound adjustment (write-off) through /inventory/adjustment and returns the movement id. */
    protected UUID recordAdjustmentOut(Product product, LocalDate date, int quantity, String reference, String reason) {
        navigateTo("/inventory/adjustment");
        waitForPageLoad();
        page.locator("input[name='inbound'][value='false']").check();
        fillMovement(product, date, quantity, reference, reason);
        return submitMovement("Penyesuaian berhasil dicatat");
    }

    private void fillMovement(Product product, LocalDate date, int quantity, String reference, String notes) {
        page.locator("#productId").selectOption(product.getId().toString());
        page.locator("#transactionDate").fill(date.toString());
        page.locator("#quantity").fill(String.valueOf(quantity));
        page.locator("#referenceNumber").fill(reference);
        page.locator("#notes").fill(notes);
    }

    private UUID submitMovement(String flash) {
        page.locator("#btn-submit").click();
        page.waitForURL(INVENTORY_DETAIL_URL);
        waitForPageLoad();
        assertThat(page.getByTestId("flash-success")).hasText(flash);
        Matcher matcher = INVENTORY_DETAIL_URL.matcher(page.url());
        assertTrue(matcher.matches(), page.url());
        return UUID.fromString(matcher.group(1));
    }

    /** Opens the template's transaction form, fills date, description and reference; callers fill the amounts. */
    protected void openTransactionForm(String templateName, LocalDate date, String description, String reference) {
        navigateTo("/transactions/new?templateId=" + templateId(templateName));
        waitForPageLoad();
        page.locator("#transactionDate").fill(date.toString());
        page.locator("#description").fill(description);
        page.locator("#referenceNumber").fill(reference);
    }

    protected void pickBank(String templateName, String accountCode) {
        UUID lineId = jdbcTemplate.queryForObject("""
                SELECT id FROM journal_template_lines WHERE id_journal_template = ? AND account_hint = 'BANK'""",
                UUID.class, templateId(templateName));
        Locator input = page.locator("#accountMapping_" + lineId);
        input.click();
        input.fill(accountCode);
        input.locator("xpath=..").locator("[data-testid='account-picker-result']")
                .filter(new Locator.FilterOptions().setHasText(accountCode + " — ")).click();
        assertThat(page.locator("input[name='accountMapping[" + lineId + "]']")).hasValue(account(accountCode).getId().toString());
    }

    /** Clicks "Simpan & Posting", then asserts the POSTED transaction and its journal in the database and on the page. */
    protected UUID postAndVerify(String templateName, LocalDate date, String reference, long total,
                                 List<String> expectedJournal) {
        page.locator("#btn-simpan-posting").click();
        page.waitForURL(TRANSACTION_DETAIL_URL);
        waitForPageLoad();
        Matcher matcher = TRANSACTION_DETAIL_URL.matcher(page.url());
        assertTrue(matcher.matches(), page.url());
        UUID id = UUID.fromString(matcher.group(1));

        Map<String, Object> tx = jdbcTemplate.queryForMap("""
                SELECT t.status, t.transaction_number, t.transaction_date, t.reference_number, jt.template_name
                FROM transactions t JOIN journal_templates jt ON jt.id = t.id_journal_template WHERE t.id = ?""", id);
        assertEquals(String.join("|", "POSTED", templateName, date.toString(), reference),
                String.join("|", (String) tx.get("status"), (String) tx.get("template_name"),
                        ((java.sql.Date) tx.get("transaction_date")).toLocalDate().toString(),
                        (String) tx.get("reference_number")));
        assertEquals(expectedJournal, journalLines(id), templateName + " journal");

        assertThat(page.getByTestId("status-posted")).hasText("Transaksi Berhasil Diposting");
        assertThat(page.locator("#transaction-number")).hasText((String) tx.get("transaction_number"));
        assertThat(page.getByTestId("template-link")).hasText(templateName);
        List<String> expectedRows = expectedJournal.stream().map(line -> {
            String[] p = line.split(":");
            String money = rupiah(new BigDecimal(p[2]).longValueExact());
            return p[1] + "|" + (p[0].equals("DEBIT") ? money + "|-" : "-|" + money);
        }).sorted().toList();
        assertEquals(expectedRows, journalRows(), templateName + " journal rows on detail page");
        assertThat(page.locator("#total-debit")).hasText(rupiah(total));
        assertThat(page.locator("#total-credit")).hasText(rupiah(total));
        return id;
    }

    protected UUID templateId(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM journal_templates WHERE template_name = ? AND is_current_version = true", UUID.class, name);
    }

    protected List<String> journalLines(UUID transactionId) {
        return jdbcTemplate.queryForList("""
                SELECT CASE WHEN je.debit_amount > 0 THEN 'DEBIT:' || coa.account_code || ':' || je.debit_amount
                            ELSE 'CREDIT:' || coa.account_code || ':' || je.credit_amount END
                FROM journal_entries je JOIN chart_of_accounts coa ON coa.id = je.id_account
                WHERE je.id_transaction = ? ORDER BY je.debit_amount = 0, coa.account_code""", String.class, transactionId);
    }

    private List<String> journalRows() {
        Locator rows = page.locator(".journal-entry-row");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < rows.count(); i++) {
            Locator cells = rows.nth(i).locator("> div");
            result.add(page.locator("#entry-account-code-" + i).textContent().trim() + "|"
                    + cells.nth(1).textContent().trim() + "|" + cells.nth(2).textContent().trim());
        }
        return result.stream().sorted().toList();
    }

    protected ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seller account missing: " + code));
    }

    protected static String journal(String position, String accountCode, long amount) {
        return position + ":" + accountCode + ":" + amount + ".00";
    }

    protected static String rupiah(long amount) {
        return String.format(Locale.of("id", "ID"), "Rp %,d", amount);
    }

    /** Parses a rendered amount ("850.820.000", "8,880,000", "Rp 1.000", "(8.880.000)", "-"). */
    protected static long amount(Locator locator) {
        String text = locator.innerText().trim();
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            if (text.equals("-")) {
                return 0L;
            }
            throw new IllegalStateException("Not an amount: '" + text + "'");
        }
        long value = Long.parseLong(digits);
        return text.startsWith("(") || text.startsWith("-") ? -value : value;
    }

    protected Locator reportRow(String rowClass, String accountName) {
        return page.locator("." + rowClass).filter(new Locator.FilterOptions()
                .setHas(page.locator(".account-name").getByText(accountName, new Locator.GetByTextOptions().setExact(true))));
    }

    protected Locator cashFlowItem(String templateName) {
        return page.locator("span.text-gray-700:text-is('" + templateName + "') + span");
    }
}
