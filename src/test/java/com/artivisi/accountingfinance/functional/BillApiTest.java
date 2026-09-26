package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.enums.BillStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.BillRepository;
import com.artivisi.accountingfinance.repository.VendorRepository;
import com.artivisi.accountingfinance.service.BillService;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("Bill API - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class BillApiTest extends PlaywrightTestBase {

    private static final int YEAR = 2086;

    @Autowired private BillService billService;
    @Autowired private BillRepository billRepository;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private APIRequestContext apiContext;
    private ObjectMapper objectMapper;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        apiContext = playwright.request().newContext(new APIRequest.NewContextOptions()
                .setBaseURL(baseUrl()));

        accessToken = authenticateViaDeviceFlow();
    }

    @AfterEach
    void tearDown() {
        if (apiContext != null) {
            apiContext.dispose();
        }
    }

    @Test
    @DisplayName("POST /api/bills - auto-creates an unknown vendor and returns the persisted bill with PPN")
    void testCreateBillWithNewVendor() throws Exception {
        String vendorName = "PT Vendor Baru API " + shortId();
        JsonNode created = createBill(vendorName, YEAR + "-02-10", YEAR + "-03-10", null, "Catatan API", List.of(
                Map.of("description", "Pembelian ATK", "unitPrice", 250000, "quantity", 10, "taxRate", 11,
                        "expenseAccountCode", "5.1.09")));

        Vendor vendor = vendorRepository.findByNameIgnoreCase(vendorName)
                .orElseThrow(() -> new AssertionError("Vendor not auto-created: " + vendorName));
        assertThat(vendor.getCode()).matches("VND-\\d{4,}");
        assertThat(vendor.getActive()).isTrue();

        assertThat(created.get("vendorName").asText()).isEqualTo(vendorName);
        assertThat(created.get("vendorCode").asText()).isEqualTo(vendor.getCode());
        assertThat(created.get("vendorId").asText()).isEqualTo(vendor.getId().toString());
        assertThat(created.get("billNumber").asText()).matches(
                "BILL-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-\\d{4}");
        assertThat(created.get("billDate").asText()).isEqualTo(YEAR + "-02-10");
        assertThat(created.get("dueDate").asText()).isEqualTo(YEAR + "-03-10");
        assertThat(created.get("notes").asText()).isEqualTo("Catatan API");
        assertThat(created.get("vendorInvoiceNumber").isNull()).isTrue();
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        assertThat(created.get("amount").decimalValue()).isEqualByComparingTo("2500000");
        assertThat(created.get("taxAmount").decimalValue()).isEqualByComparingTo("275000");
        assertThat(created.get("approvedAt").isNull()).isTrue();

        JsonNode lines = created.get("lines");
        assertThat(lines.size()).isEqualTo(1);
        assertThat(lines.get(0).get("description").asText()).isEqualTo("Pembelian ATK");
        assertThat(lines.get(0).get("quantity").decimalValue()).isEqualByComparingTo("10");
        assertThat(lines.get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("250000");
        assertThat(lines.get(0).get("taxRate").decimalValue()).isEqualByComparingTo("11");
        assertThat(lines.get(0).get("taxAmount").decimalValue()).isEqualByComparingTo("275000");
        assertThat(lines.get(0).get("amount").decimalValue()).isEqualByComparingTo("2500000");
        assertThat(lines.get(0).get("expenseAccountCode").asText()).isEqualTo("5.1.09");
        assertThat(lines.get(0).get("expenseAccountName").asText()).isEqualTo("Beban ATK");

        Bill saved = billService.findByBillNumber(created.get("billNumber").asText());
        assertThat(saved.getId().toString()).isEqualTo(created.get("id").asText());
        assertThat(saved.getVendor().getId()).isEqualTo(vendor.getId());
        assertThat(saved.getAmount()).isEqualByComparingTo("2500000");
        assertThat(saved.getTaxAmount()).isEqualByComparingTo("275000");
        assertThat(saved.getStatus()).isEqualTo(BillStatus.DRAFT);
    }

    @Test
    @DisplayName("POST /api/bills - reuses the existing vendor whose name matches case-insensitively")
    void testCreateBillWithExistingVendor() throws Exception {
        String vendorName = "PT Vendor Pertama " + shortId();
        JsonNode first = createBill(vendorName, YEAR + "-02-01", YEAR + "-03-01", "INV-001", null, List.of(
                Map.of("description", "Jasa Konsultasi", "unitPrice", 5000000, "quantity", 1)));
        long vendorsBefore = vendorRepository.count();

        JsonNode second = createBill(vendorName.toUpperCase(), YEAR + "-02-15", YEAR + "-03-15", "INV-002", null, List.of(
                Map.of("description", "Jasa Maintenance", "unitPrice", 3000000, "quantity", 2)));

        assertThat(vendorRepository.count()).isEqualTo(vendorsBefore);
        assertThat(second.get("vendorId").asText()).isEqualTo(first.get("vendorId").asText());
        assertThat(second.get("vendorName").asText()).isEqualTo(vendorName);
        assertThat(second.get("vendorInvoiceNumber").asText()).isEqualTo("INV-002");
        assertThat(second.get("amount").decimalValue()).isEqualByComparingTo("6000000");
        assertThat(second.get("taxAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(second.get("billNumber").asText()).isNotEqualTo(first.get("billNumber").asText());
    }

    @Test
    @DisplayName("POST /api/bills - sums multiple lines; PPN only on the taxed line; default quantity 1")
    void testCreateBillWithMultipleLines() throws Exception {
        JsonNode created = createBill("PT Multi Line Vendor " + shortId(), YEAR + "-02-05", YEAR + "-03-05",
                "ML-001", null, List.of(
                        Map.of("description", "Item A", "unitPrice", 1000000),
                        Map.of("description", "Item B", "unitPrice", 500000, "quantity", 3),
                        Map.of("description", "Item C", "unitPrice", 200000, "quantity", 5, "taxRate", 11)));

        JsonNode lines = created.get("lines");
        assertThat(lines.size()).isEqualTo(3);
        assertThat(lines.get(0).get("description").asText()).isEqualTo("Item A");
        assertThat(lines.get(0).get("quantity").decimalValue()).isEqualByComparingTo("1");
        assertThat(lines.get(0).get("amount").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(lines.get(1).get("description").asText()).isEqualTo("Item B");
        assertThat(lines.get(1).get("amount").decimalValue()).isEqualByComparingTo("1500000");
        assertThat(lines.get(1).get("taxAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(lines.get(2).get("description").asText()).isEqualTo("Item C");
        assertThat(lines.get(2).get("amount").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(lines.get(2).get("taxAmount").decimalValue()).isEqualByComparingTo("110000");
        assertThat(created.get("amount").decimalValue()).isEqualByComparingTo("3500000");
        assertThat(created.get("taxAmount").decimalValue()).isEqualByComparingTo("110000");
    }

    @Test
    @DisplayName("POST /api/bills - rejects missing lines and unknown expense accounts without creating anything")
    void testCreateBillRejected() throws Exception {
        String vendorName = "PT Ditolak " + shortId();
        long billsBefore = billRepository.count();

        Map<String, Object> noLines = new HashMap<>();
        noLines.put("vendorName", vendorName);
        noLines.put("billDate", YEAR + "-02-01");
        noLines.put("dueDate", YEAR + "-03-01");
        noLines.put("lines", List.of());
        APIResponse response = post("/api/bills", noLines);
        assertApiError(response, 400, "VALIDATION_ERROR", "Minimal satu baris tagihan wajib diisi");
        JsonNode error = parse(response);
        assertThat(error.get("fieldErrors").get("lines").asText()).isEqualTo("Minimal satu baris tagihan wajib diisi");

        Map<String, Object> badAccount = new HashMap<>(noLines);
        badAccount.put("lines", List.of(Map.of("description", "X", "unitPrice", 1000, "expenseAccountCode", "9.9.99")));
        response = post("/api/bills", badAccount);
        assertApiError(response, 404, "NOT_FOUND", "Akun beban tidak ditemukan: 9.9.99");

        assertThat(billRepository.count()).isEqualTo(billsBefore);
        assertThat(vendorRepository.findByNameIgnoreCase(vendorName)).isEmpty();
    }

    @Test
    @DisplayName("GET /api/bills - filters by vendor, status and date range, and paginates")
    void testListBills() throws Exception {
        String vendorName = "PT List Vendor " + shortId();
        JsonNode january = createBill(vendorName, YEAR + "-01-15", YEAR + "-02-15", null, null, List.of(
                Map.of("description", "Januari", "unitPrice", 100000, "expenseAccountCode", "5.1.20")));
        JsonNode february = createBill(vendorName, YEAR + "-02-15", YEAR + "-03-15", null, null, List.of(
                Map.of("description", "Februari", "unitPrice", 200000, "expenseAccountCode", "5.1.20")));
        JsonNode march = createBill(vendorName, YEAR + "-03-15", YEAR + "-04-15", null, null, List.of(
                Map.of("description", "Maret", "unitPrice", 300000, "expenseAccountCode", "5.1.20")));
        assertThat(post("/api/bills/" + february.get("id").asText() + "/approve", Map.of()).status()).isEqualTo(200);
        String vendorId = january.get("vendorId").asText();

        JsonNode all = getPage("/api/bills?vendorId=" + vendorId);
        assertThat(all.get("totalElements").asLong()).isEqualTo(3);
        assertThat(all.get("totalPages").asInt()).isEqualTo(1);
        assertThat(all.get("currentPage").asInt()).isZero();
        assertThat(all.get("pageSize").asInt()).isEqualTo(20);
        assertThat(billNumbers(all)).containsExactly(march.get("billNumber").asText(),
                february.get("billNumber").asText(), january.get("billNumber").asText());

        JsonNode drafts = getPage("/api/bills?vendorId=" + vendorId + "&status=draft");
        assertThat(billNumbers(drafts)).containsExactly(march.get("billNumber").asText(), january.get("billNumber").asText());
        JsonNode approved = getPage("/api/bills?vendorId=" + vendorId + "&status=APPROVED");
        assertThat(billNumbers(approved)).containsExactly(february.get("billNumber").asText());
        assertThat(approved.get("items").get(0).get("status").asText()).isEqualTo("APPROVED");

        JsonNode range = getPage("/api/bills?vendorId=" + vendorId + "&dateFrom=" + YEAR + "-02-01&dateTo=" + YEAR + "-03-15");
        assertThat(billNumbers(range)).containsExactly(march.get("billNumber").asText(), february.get("billNumber").asText());

        JsonNode page1 = getPage("/api/bills?vendorId=" + vendorId + "&page=1&size=2");
        assertThat(page1.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page1.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page1.get("currentPage").asInt()).isEqualTo(1);
        assertThat(page1.get("pageSize").asInt()).isEqualTo(2);
        assertThat(billNumbers(page1)).containsExactly(january.get("billNumber").asText());
    }

    @Test
    @DisplayName("GET /api/bills/{id} - returns bill detail; unknown id is 404")
    void testGetBillDetail() throws Exception {
        String vendorName = "PT Detail Vendor " + shortId();
        JsonNode created = createBill(vendorName, YEAR + "-02-01", YEAR + "-03-01", "DET-001", null, List.of(
                Map.of("description", "Detail item", "unitPrice", 750000, "quantity", 2, "expenseAccountCode", "5.1.20")));
        String billId = created.get("id").asText();

        APIResponse response = get("/api/bills/" + billId);
        assertThat(response.status()).isEqualTo(200);
        JsonNode bill = parse(response);
        assertThat(bill.get("id").asText()).isEqualTo(billId);
        assertThat(bill.get("billNumber").asText()).isEqualTo(created.get("billNumber").asText());
        assertThat(bill.get("vendorName").asText()).isEqualTo(vendorName);
        assertThat(bill.get("vendorInvoiceNumber").asText()).isEqualTo("DET-001");
        assertThat(bill.get("billDate").asText()).isEqualTo(YEAR + "-02-01");
        assertThat(bill.get("amount").decimalValue()).isEqualByComparingTo("1500000");
        assertThat(bill.get("status").asText()).isEqualTo("DRAFT");
        assertThat(bill.get("lines").size()).isEqualTo(1);
        assertThat(bill.get("lines").get(0).get("description").asText()).isEqualTo("Detail item");
        assertThat(bill.get("lines").get(0).get("expenseAccountName").asText()).isEqualTo("Beban Cloud & Server");

        UUID missingId = UUID.randomUUID();
        assertApiError(get("/api/bills/" + missingId), 404, "NOT_FOUND", "Tagihan tidak ditemukan: " + missingId);
    }

    @Test
    @DisplayName("POST /api/bills/{id}/approve - approves a draft, records the approver, creates the expense draft; second approval is 409")
    void testApproveBill() throws Exception {
        JsonNode created = createBill("PT Approve Vendor " + shortId(), YEAR + "-04-01", YEAR + "-05-01", null, null, List.of(
                Map.of("description", "Approve item", "unitPrice", 500000, "quantity", 1, "taxRate", 11,
                        "expenseAccountCode", "5.1.20")));
        String billId = created.get("id").asText();

        APIResponse response = post("/api/bills/" + billId + "/approve", Map.of());
        assertThat(response.status()).isEqualTo(200);
        JsonNode approved = parse(response);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("approvedBy").asText()).isEqualTo("admin");
        assertThat(LocalDateTime.parse(approved.get("approvedAt").asText())).isAfter(LocalDateTime.now().minusMinutes(5));

        Bill bill = billService.findById(UUID.fromString(billId));
        assertThat(bill.getStatus()).isEqualTo(BillStatus.APPROVED);
        assertThat(bill.getApprovedBy()).isEqualTo("admin");
        assertThat(jdbcTemplate.queryForList("""
                SELECT t.status || '|' || t.transaction_date || '|' || t.amount || '|' || jt.template_name
                FROM transactions t JOIN journal_templates jt ON jt.id = t.id_journal_template
                WHERE t.source_document_type = 'BILL' AND t.source_document_id = ?""", String.class, bill.getId()))
                .containsExactly("DRAFT|" + YEAR + "-04-01|555000.00|Tagihan Vendor");

        APIResponse again = post("/api/bills/" + billId + "/approve", Map.of());
        assertApiError(again, 409, "CONFLICT", "Hanya tagihan draf yang dapat disetujui");
        assertThat(billTransactionCount(bill)).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/bills/{id}/mark-paid - refuses a draft (409), marks an approved bill paid")
    void testMarkBillPaid() throws Exception {
        JsonNode created = createBill("PT Paid Vendor " + shortId(), YEAR + "-05-01", YEAR + "-06-01", null, null, List.of(
                Map.of("description", "Paid item", "unitPrice", 1000000, "quantity", 1, "expenseAccountCode", "5.1.20")));
        String billId = created.get("id").asText();

        APIResponse draftPaid = post("/api/bills/" + billId + "/mark-paid", Map.of());
        assertApiError(draftPaid, 409, "CONFLICT",
                "Hanya tagihan yang disetujui, jatuh tempo, atau sebagian yang dapat ditandai lunas");
        assertThat(billService.findById(UUID.fromString(billId)).getStatus()).isEqualTo(BillStatus.DRAFT);

        assertThat(post("/api/bills/" + billId + "/approve", Map.of()).status()).isEqualTo(200);
        APIResponse response = post("/api/bills/" + billId + "/mark-paid", Map.of());
        assertThat(response.status()).isEqualTo(200);
        JsonNode paid = parse(response);
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(LocalDateTime.parse(paid.get("paidAt").asText())).isAfter(LocalDateTime.now().minusMinutes(5));

        Bill bill = billService.findById(UUID.fromString(billId));
        assertThat(bill.getStatus()).isEqualTo(BillStatus.PAID);
        assertThat(bill.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("API endpoints reject unauthenticated requests with 401 and create nothing")
    void testUnauthenticated() {
        long billsBefore = billRepository.count();
        APIResponse listResponse = apiContext.get("/api/bills");
        assertThat(listResponse.status()).isEqualTo(401);

        APIResponse createResponse = apiContext.post("/api/bills",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("vendorName", "test", "billDate", YEAR + "-01-01", "dueDate", YEAR + "-02-01",
                                "lines", List.of(Map.of("description", "x", "unitPrice", 1)))));
        assertThat(createResponse.status()).isEqualTo(401);
        assertThat(billRepository.count()).isEqualTo(billsBefore);
    }

    // --- Helpers ---

    private JsonNode createBill(String vendorName, String billDate, String dueDate, String vendorInvoiceNumber,
                                String notes, List<Map<String, Object>> lines) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("vendorName", vendorName);
        request.put("billDate", billDate);
        request.put("dueDate", dueDate);
        if (vendorInvoiceNumber != null) {
            request.put("vendorInvoiceNumber", vendorInvoiceNumber);
        }
        if (notes != null) {
            request.put("notes", notes);
        }
        request.put("lines", lines);

        APIResponse response = post("/api/bills", request);
        assertThat(response.status()).isEqualTo(201);

        return parse(response);
    }

    private int billTransactionCount(Bill bill) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM transactions WHERE source_document_type = 'BILL' AND source_document_id = ?""",
                Integer.class, bill.getId());
    }

    private JsonNode getPage(String path) throws Exception {
        APIResponse response = get(path);
        assertThat(response.status()).as(path).isEqualTo(200);
        return parse(response);
    }

    private List<String> billNumbers(JsonNode page) {
        List<String> numbers = new ArrayList<>();
        page.get("items").forEach(item -> numbers.add(item.get("billNumber").asText()));
        return numbers;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private APIResponse get(String path) {
        return apiContext.get(path,
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken));
    }

    private APIResponse post(String path, Object data) {
        return apiContext.post(path,
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .setHeader("Content-Type", "application/json")
                        .setData(data));
    }

    private void assertApiError(APIResponse response, int status, String code, String message) throws Exception {
        assertThat(response.status()).as(response.text()).isEqualTo(status);
        JsonNode body = parse(response);
        assertThat(body.get("error").asText()).as(response.text()).isEqualTo(code);
        assertThat(body.get("message").asText()).as(response.text()).isEqualTo(message);
    }

    private JsonNode parse(APIResponse response) throws Exception {
        return objectMapper.readTree(response.text());
    }

    private String authenticateViaDeviceFlow() throws Exception {
        Map<String, String> codeRequest = new HashMap<>();
        codeRequest.put("clientId", "bill-api-test");

        APIResponse codeResponse = apiContext.post("/api/device/code",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(codeRequest));

        assertThat(codeResponse.ok()).isTrue();

        JsonNode codeData = objectMapper.readTree(codeResponse.text());
        String deviceCode = codeData.get("deviceCode").asText();
        String userCode = codeData.get("userCode").asText();

        loginAsAdmin();
        navigateTo("/device?code=" + userCode);
        waitForPageLoad();

        page.locator("input[name='deviceName']").fill("Bill API Test Device");
        page.locator("button[type='submit']:has-text('Otorisasi Perangkat')").click();
        waitForPageLoad();

        Map<String, String> tokenRequest = new HashMap<>();
        tokenRequest.put("deviceCode", deviceCode);

        AtomicReference<String> tokenRef = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(2)).until(() -> {
            APIResponse tokenResponse = apiContext.post("/api/device/token",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(tokenRequest));
            if (tokenResponse.ok()) {
                JsonNode tokenData = objectMapper.readTree(tokenResponse.text());
                tokenRef.set(tokenData.get("accessToken").asText());
                return true;
            }
            return false;
        });

        return tokenRef.get();
    }
}
