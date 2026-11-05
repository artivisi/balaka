package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.BillService;
import com.artivisi.accountingfinance.service.ClientService;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.service.VendorService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Sales tax chain API on the IT service seed pack. There is no invoice API, so invoices come from the services.
 * Reserved test year 2153.
 */
@DisplayName("Sales tax chain API")
@Import(ServiceTestDataInitializer.class)
class SalesTaxChainApiTest extends PlaywrightTestBase {

    private static final int YEAR = 2153;

    @Autowired private ClientService clientService;
    @Autowired private VendorService vendorService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private BillService billService;
    @Autowired private ProductRepository productRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionService transactionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private APIRequestContext api;
    private String token;
    private ArApFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        api = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(baseUrl()));
        token = deviceToken();
        fixture = new ArApFixture(clientService, vendorService, invoiceService, billService, productRepository, accountRepository);
    }

    @AfterEach
    void tearDown() {
        api.dispose();
    }

    @Test
    @DisplayName("Faktur and bupot through the API: chain, exceptions and reconciliation report exact figures")
    void fullChain() throws Exception {
        Client client = fixture.client("Pelanggan API");
        Invoice invoice = fixture.sentInvoice(client, LocalDate.of(YEAR, 3, 5), LocalDate.of(YEAR, 4, 5), "10000000", "11");
        Invoice unrelated = fixture.sentInvoice(client, LocalDate.of(YEAR, 3, 6), LocalDate.of(YEAR, 4, 6), "1000000", "11");
        transactionRepository.findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc("INVOICE", invoice.getId())
                .forEach(t -> transactionService.post(t.getId(), "admin"));
        var payment = new com.artivisi.accountingfinance.entity.InvoicePayment();
        payment.setPaymentDate(LocalDate.of(YEAR, 3, 20));
        payment.setAmount(new BigDecimal("11100000"));
        payment.setPph23Withheld(new BigDecimal("200000"));
        payment.setPaymentMethod(com.artivisi.accountingfinance.enums.PaymentMethod.TRANSFER);
        invoiceService.recordPayment(invoice.getId(), payment);

        String chainPath = "/api/sales-tax-chain/invoices/" + invoice.getId();
        JsonNode exceptions = json(api.get("/api/sales-tax-chain/exceptions?from=" + YEAR + "-01-01&to=" + YEAR + "-12-31", auth()), 200);
        assertThat(types(exceptions, invoice.getInvoiceNumber())).containsExactlyInAnyOrder("PAID_NO_FAKTUR", "MISSING_BUPOT");

        Map<String, Object> faktur = new HashMap<>(Map.of("fakturNumber", number("F"), "fakturDate", YEAR + "-03-07",
                "transactionCode", "01", "dpp", 10000000, "ppn", 1100000));
        JsonNode recorded = json(api.post(chainPath + "/faktur", body(faktur)), 201);
        assertThat(recorded.get("transactionStatus").asText()).isEqualTo("POSTED");
        faktur.put("fakturNumber", number("F"));
        assertThat(api.post(chainPath + "/faktur", body(faktur)).status()).isEqualTo(409);

        Map<String, Object> bupot = new HashMap<>(Map.of("paymentId", payment.getId().toString(), "bupotNumber", number("B"),
                "bupotDate", YEAR + "-03-25", "taxObjectCode", "24-104-01", "grossAmount", 10000000, "taxRate", 2, "taxAmount", 200000));
        JsonNode bupotRow = json(api.post(chainPath + "/bupot", body(bupot)), 201);
        assertThat(bupotRow.get("transactionStatus").asText()).isEqualTo("DRAFT");

        JsonNode chain = json(api.get(chainPath, auth()), 200);
        assertAmount(chain, "bookedRevenue", "10000000");
        assertThat(chain.get("fakturs")).hasSize(1);
        assertThat(chain.get("fakturs").get(0).get("fakturDate").asText()).isEqualTo(YEAR + "-03-07");
        assertAmount(chain.get("receipts").get(0), "cashAmount", "10900000");
        assertAmount(chain.get("receipts").get(0), "bupotOutstanding", "0");
        assertThat(chain.get("bupots").get(0).get("paymentId").asText()).isEqualTo(payment.getId().toString());
        assertThat(chain.get("exceptions")).isEmpty();

        JsonNode posted = json(api.post("/api/transactions/" + bupotRow.get("transactionId").asText() + "/post", auth()), 200);
        assertThat(posted.get("status").asText()).isEqualTo("POSTED");

        JsonNode recon = json(api.get("/api/sales-tax-chain/reconciliation?year=" + YEAR + "&month=3", auth()), 200);
        assertAmount(recon, "bookedRevenue", "10000000");
        assertAmount(recon, "sptDpp", "10000000");
        assertAmount(recon, "unreconciled", "0");
        assertAmount(recon.get("taxEffects"), "ppnPerFaktur", "1100000");
        assertAmount(recon.get("taxEffects"), "ppnPerLedger", "1100000");
        assertAmount(recon.get("taxEffects"), "pph23PerBupot", "200000");
        assertAmount(recon.get("taxEffects"), "pph23PerLedger", "200000");
        assertAmount(recon.get("taxEffects"), "netOfPph23", "10900000");

        JsonNode after = json(api.get("/api/sales-tax-chain/exceptions?to=" + YEAR + "-12-31", auth()), 200);
        assertThat(types(after, invoice.getInvoiceNumber())).isEmpty();
        assertThat(types(after, unrelated.getInvoiceNumber())).isEmpty();
    }

    @Test
    @DisplayName("Validation and lookups: a missing field is 400, an unknown invoice is 404, an invalid type filter is rejected")
    void validation() throws Exception {
        assertThat(api.post("/api/sales-tax-chain/invoices/" + UUID.randomUUID() + "/faktur",
                body(Map.of("fakturDate", YEAR + "-01-01"))).status()).isEqualTo(400);
        assertThat(api.get("/api/sales-tax-chain/invoices/" + UUID.randomUUID(), auth()).status()).isEqualTo(404);
        assertThat(api.get("/api/sales-tax-chain/exceptions", auth()).status()).isEqualTo(400);
        assertThat(api.get("/api/sales-tax-chain/exceptions?to=" + YEAR + "-01-31&type=NOPE", auth()).status()).isEqualTo(400);
    }

    private java.util.List<String> types(JsonNode exceptions, String invoiceNumber) {
        java.util.List<String> result = new java.util.ArrayList<>();
        exceptions.forEach(e -> {
            if (invoiceNumber.equals(e.get("invoiceNumber").asText())) {
                result.add(e.get("type").asText());
            }
        });
        return result;
    }

    private String number(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private void assertAmount(JsonNode node, String field, String expected) {
        assertThat(node.get(field).decimalValue()).as(field).isEqualByComparingTo(expected);
    }

    private RequestOptions auth() {
        return RequestOptions.create().setHeader("Authorization", "Bearer " + token);
    }

    private RequestOptions body(Object body) {
        return auth().setHeader("Content-Type", "application/json").setData(body);
    }

    private JsonNode json(APIResponse response, int status) throws Exception {
        assertThat(response.status()).as(response.text()).isEqualTo(status);
        return objectMapper.readTree(response.text());
    }

    private String deviceToken() throws Exception {
        APIResponse codeResponse = api.post("/api/device/code", RequestOptions.create()
                .setHeader("Content-Type", "application/json").setData(Map.of("clientId", "sales-tax-chain-api-test")));
        JsonNode code = json(codeResponse, 200);

        loginAsAdmin();
        navigateTo("/device?code=" + code.get("userCode").asText());
        waitForPageLoad();
        page.locator("input[name='deviceName']").fill("Sales Tax Chain API Test");
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        AtomicReference<String> ref = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(2)).until(() -> {
            APIResponse tokenResponse = api.post("/api/device/token", RequestOptions.create()
                    .setHeader("Content-Type", "application/json").setData(Map.of("deviceCode", code.get("deviceCode").asText())));
            if (tokenResponse.ok()) {
                ref.set(objectMapper.readTree(tokenResponse.text()).get("accessToken").asText());
                return true;
            }
            return false;
        });
        return ref.get();
    }
}
