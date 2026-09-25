package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.DraftTransaction;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.DraftTransactionRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Draft transaction review (list, filter, detail, approve, reject, delete) through /drafts and
 * /drafts/api. Drafts arrive from Telegram/API, so each test inserts its own drafts dated 2060.
 */
@DisplayName("Draft Transaction Controller Tests")
@Import(ServiceTestDataInitializer.class)
class DraftTransactionControllerFunctionalTest extends PlaywrightTestBase {

    private static final String TPL_TELEKOM = "Bayar Beban Telekomunikasi";

    @Autowired private DraftTransactionRepository draftRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private TransactionRepository transactionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String csrfHeader;
    private String csrfToken;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
        csrfHeader = page.locator("meta[name='_csrf_header']").getAttribute("content");
        csrfToken = page.locator("meta[name='_csrf']").getAttribute("content");
    }

    @Test
    @DisplayName("List shows drafts newest first with their status; the status filter narrows the table")
    void shouldListAndFilterDraftsByStatus() {
        String marker = "Toko " + uid();
        DraftTransaction pending = draft(marker + " pending", DraftTransaction.Status.PENDING);
        DraftTransaction approved = draft(marker + " approved", DraftTransaction.Status.APPROVED);
        DraftTransaction rejected = draft(marker + " rejected", DraftTransaction.Status.REJECTED);

        navigateTo("/drafts");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Draft Transaksi");
        assertThat(row(pending)).containsText(marker + " pending");
        assertThat(row(pending)).containsText("Menunggu Review");
        assertThat(row(approved)).containsText("Disetujui");
        assertThat(row(rejected)).containsText("Ditolak");
        assertThat(page.locator("#drafts-list-content"))
                .containsText(draftRepository.countByStatus(DraftTransaction.Status.PENDING) + " menunggu review");

        page.locator("#filter-status").selectOption("PENDING");
        assertThat(row(approved)).hasCount(0);
        assertThat(row(rejected)).hasCount(0);
        assertThat(row(pending)).isVisible();
        assertThat(page).hasURL(Pattern.compile(".*/drafts\\?status=PENDING.*"));

        navigateTo("/drafts?status=REJECTED");
        waitForPageLoad();
        assertThat(row(rejected)).isVisible();
        assertThat(row(pending)).hasCount(0);
        assertThat(row(approved)).hasCount(0);
    }

    @Test
    @DisplayName("Pagination keeps page size and status filter")
    void shouldPaginateWithSizeAndStatus() {
        String marker = "Halaman " + uid();
        DraftTransaction oldest = draft(marker + " 1", DraftTransaction.Status.PENDING);
        DraftTransaction middle = draft(marker + " 2", DraftTransaction.Status.PENDING);
        DraftTransaction newest = draft(marker + " 3", DraftTransaction.Status.PENDING);

        navigateTo("/drafts?status=PENDING&size=2");
        waitForPageLoad();
        assertThat(row(newest)).isVisible();
        assertThat(row(middle)).isVisible();
        assertThat(row(oldest)).hasCount(0);
        assertThat(page.locator("#draft-table tbody tr")).hasCount(2);

        page.locator("a[href*='page=1']").first().click();
        waitForPageLoad();
        assertThat(page).hasURL(Pattern.compile(".*page=1.*"));
        assertThat(page).hasURL(Pattern.compile(".*size=2.*"));
        assertThat(page).hasURL(Pattern.compile(".*status=PENDING.*"));
        assertThat(row(oldest)).isVisible();
        assertThat(row(newest)).hasCount(0);
        assertThat(row(middle)).hasCount(0);
    }

    @Test
    @DisplayName("Approving from the detail page creates a DRAFT transaction with the chosen template, amount and description")
    void shouldApproveDraftIntoTransaction() {
        JournalTemplate template = template(TPL_TELEKOM);
        DraftTransaction draft = draft("Telkomsel " + uid(), DraftTransaction.Status.PENDING);
        String description = "Pulsa kantor " + uid();

        navigateTo("/drafts/" + draft.getId());
        waitForPageLoad();
        assertThat(page.locator("#status-badge-pending")).isVisible();
        assertThat(page.locator("#draft-detail-content")).containsText(draft.getMerchantName());
        assertThat(page.locator("#draft-detail-content")).containsText(draft.getAmountFormatted());
        assertThat(page.locator("#draft-detail-content")).containsText(draft.getSourceReference());
        assertThat(page.locator("#description")).hasValue(draft.getMerchantName());
        assertThat(page.locator("#templateId option[value='" + template.getId() + "']")).hasText(TPL_TELEKOM);

        page.locator("#templateId").selectOption(template.getId().toString());
        page.locator("#description").fill(description);
        page.locator("#amount").fill("88500");
        page.locator("#btn-approve").click();
        page.waitForURL(Pattern.compile(".*/transactions/[0-9a-f-]{36}/edit$"));

        DraftTransaction approved = draftRepository.findById(draft.getId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(DraftTransaction.Status.APPROVED);
        assertThat(approved.getProcessedBy()).isEqualTo("admin");
        UUID transactionId = approved.getTransaction().getId();
        assertThat(page).hasURL(baseUrl() + "/transactions/" + transactionId + "/edit");

        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow();
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.DRAFT);
        assertThat(transaction.getJournalTemplate().getId()).isEqualTo(template.getId());
        assertThat(transaction.getAmount()).isEqualByComparingTo("88500");
        assertThat(transaction.getDescription()).isEqualTo(description);
        assertThat(transaction.getTransactionDate()).isEqualTo(draft.getTransactionDate());
        assertThat(transaction.getReferenceNumber()).isEqualTo(draft.getSourceReference());

        navigateTo("/drafts/" + draft.getId());
        waitForPageLoad();
        assertThat(page.locator("#status-badge-approved")).isVisible();
        assertThat(page.locator("a[href='/transactions/" + transactionId + "']")).isVisible();
        assertThat(page.locator("#btn-approve")).hasCount(0);

        APIResponse again = page.request().post(baseUrl() + "/drafts/" + draft.getId() + "/approve",
                RequestOptions.create().setMaxRedirects(0).setForm(FormData.create()
                        .set("_csrf", csrfToken).set("templateId", template.getId().toString())));
        assertThat(again.status()).isEqualTo(409);
        assertThat(draftRepository.findById(draft.getId()).orElseThrow().getTransaction().getId()).isEqualTo(transactionId);
    }

    @Test
    @DisplayName("Rejecting from the detail page stores the reason and reviewer; a processed draft cannot be rejected again")
    void shouldRejectDraftWithReason() {
        DraftTransaction draft = draft("Indomaret " + uid(), DraftTransaction.Status.PENDING);

        navigateTo("/drafts/" + draft.getId());
        waitForPageLoad();
        page.locator("#reason").fill("Bukan pengeluaran kantor");
        page.locator("#btn-reject").click();
        page.waitForURL(baseUrl() + "/drafts");

        DraftTransaction rejected = draftRepository.findById(draft.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(DraftTransaction.Status.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Bukan pengeluaran kantor");
        assertThat(rejected.getProcessedBy()).isEqualTo("admin");
        assertThat(rejected.getTransaction()).isNull();

        navigateTo("/drafts/" + draft.getId());
        waitForPageLoad();
        assertThat(page.locator("#status-badge-rejected")).isVisible();
        assertThat(page.locator("#draft-detail-content")).containsText("Bukan pengeluaran kantor");
        assertThat(page.locator("#draft-detail-content")).containsText("Ditolak oleh admin");
        assertThat(page.locator("#btn-reject")).hasCount(0);

        APIResponse again = page.request().post(baseUrl() + "/drafts/api/" + draft.getId() + "/reject",
                withCsrf().setData(Map.of("reason", "Lagi")));
        assertThat(again.status()).isEqualTo(409);
        assertThat(draftRepository.findById(draft.getId()).orElseThrow().getRejectionReason()).isEqualTo("Bukan pengeluaran kantor");
    }

    @Test
    @DisplayName("Pending drafts are deleted from the list; processed drafts cannot be deleted")
    void shouldDeleteOnlyPendingDrafts() {
        String marker = "Hapus " + uid();
        DraftTransaction pending = draft(marker + " pending", DraftTransaction.Status.PENDING);
        DraftTransaction rejected = draft(marker + " rejected", DraftTransaction.Status.REJECTED);

        navigateTo("/drafts");
        waitForPageLoad();
        assertThat(row(rejected).locator("button[title='Hapus']")).hasCount(0);
        page.onceDialog(dialog -> dialog.accept());
        row(pending).locator("button[title='Hapus']").click();
        assertThat(row(pending)).hasCount(0);
        assertThat(draftRepository.findById(pending.getId())).isEmpty();

        assertThat(page.request().delete(baseUrl() + "/drafts/" + rejected.getId(), withCsrf()).status()).isEqualTo(409);
        assertThat(draftRepository.findById(rejected.getId())).isPresent();
    }

    @Test
    @DisplayName("/drafts/api lists by status, returns one draft, approves it and rejects unknown ids")
    void shouldServeDraftApi() throws Exception {
        String marker = "Api " + uid();
        DraftTransaction pending = draft(marker + " pending", DraftTransaction.Status.PENDING);
        DraftTransaction rejected = draft(marker + " rejected", DraftTransaction.Status.REJECTED);

        Set<UUID> pendingIds = ids(getJson("/drafts/api?status=PENDING&size=5"));
        assertThat(pendingIds).contains(pending.getId()).doesNotContain(rejected.getId());
        assertThat(ids(getJson("/drafts/api?status=REJECTED&size=5"))).contains(rejected.getId()).doesNotContain(pending.getId());

        JsonNode single = getJson("/drafts/api/" + pending.getId());
        assertThat(single.get("merchantName").asText()).isEqualTo(marker + " pending");
        assertThat(single.get("amount").decimalValue()).isEqualByComparingTo(pending.getAmount());
        assertThat(single.get("status").asText()).isEqualTo("PENDING");
        assertThat(single.get("transactionDate").asText()).isEqualTo(pending.getTransactionDate().toString());

        APIResponse approve = page.request().post(baseUrl() + "/drafts/api/" + pending.getId() + "/approve",
                withCsrf().setData(Map.of("templateId", template(TPL_TELEKOM).getId().toString())));
        assertThat(approve.status()).as(approve.text()).isEqualTo(200);
        DraftTransaction approved = draftRepository.findById(pending.getId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(DraftTransaction.Status.APPROVED);
        Transaction transaction = transactionRepository.findById(approved.getTransaction().getId()).orElseThrow();
        assertThat(transaction.getAmount()).isEqualByComparingTo(pending.getAmount());
        assertThat(transaction.getDescription()).isEqualTo(pending.getMerchantName());

        assertThat(page.request().get(baseUrl() + "/drafts/api/" + UUID.randomUUID()).status()).isEqualTo(400);
    }

    // ==================== helpers ====================

    private DraftTransaction draft(String merchant, DraftTransaction.Status status) {
        DraftTransaction draft = new DraftTransaction();
        draft.setSource(DraftTransaction.Source.MANUAL);
        draft.setMerchantName(merchant);
        draft.setSourceReference("REF-" + uid());
        draft.setTransactionDate(LocalDate.of(2060, 2, 3));
        draft.setAmount(new BigDecimal("75250"));
        draft.setStatus(status);
        if (status != DraftTransaction.Status.PENDING) {
            draft.setProcessedBy("reviewer");
        }
        if (status == DraftTransaction.Status.REJECTED) {
            draft.setRejectionReason("Duplikat");
        }
        return draftRepository.save(draft);
    }

    private com.microsoft.playwright.Locator row(DraftTransaction draft) {
        return page.locator("#draft-row-" + draft.getId());
    }

    private Set<UUID> ids(JsonNode page) {
        Set<UUID> ids = new HashSet<>();
        for (JsonNode node : page.get("content")) {
            ids.add(UUID.fromString(node.get("id").asText()));
        }
        return ids;
    }

    private JsonNode getJson(String path) throws Exception {
        APIResponse response = page.request().get(baseUrl() + path);
        assertThat(response.status()).as(path + ": " + response.text()).isEqualTo(200);
        return objectMapper.readTree(response.text());
    }

    private RequestOptions withCsrf() {
        return RequestOptions.create().setHeader(csrfHeader, csrfToken);
    }

    private JournalTemplate template(String name) {
        return templateRepository.findByTemplateNameAndIsCurrentVersionTrue(name)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + name));
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
