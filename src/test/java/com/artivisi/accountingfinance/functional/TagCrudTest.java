package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.entity.Tag;
import com.artivisi.accountingfinance.entity.TagType;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TagRepository;
import com.artivisi.accountingfinance.repository.TagTypeRepository;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tag CRUD under /tags/types/{typeId}/tags. Each test creates its own tag type (unique code),
 * so the tag list only holds that test's tags.
 */
@DisplayName("Tag CRUD Tests")
@Import(ServiceTestDataInitializer.class)
class TagCrudTest extends PlaywrightTestBase {

    @Autowired private TagTypeRepository tagTypeRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TransactionService transactionService;

    private TagType tagType;

    @BeforeEach
    void createTagType() {
        String code = uniqueCode();
        tagType = new TagType();
        tagType.setCode(code);
        tagType.setName("Jenis " + code);
        tagType.setActive(true);
        tagType = tagTypeRepository.save(tagType);
    }

    @Test
    @DisplayName("Tag list shows the type in title and breadcrumb and each tag's code, name, description and status")
    void shouldDisplayTagListPage() {
        Tag tag = saveTag(tagType, "LST", "Daftar Label", "Deskripsi label");
        loginAsAdmin();

        navigateTo("/tags/types/" + tagType.getId() + "/tags");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Label: " + tagType.getName());
        assertThat(page.locator("section nav a")).hasAttribute("href", "/tags/types");
        assertThat(page.locator("section nav a")).hasText("Label Transaksi");
        assertThat(page.locator("section nav span.text-gray-900")).hasText(tagType.getName());
        assertThat(page.locator("#btn-new-tag")).hasAttribute("href", "/tags/types/" + tagType.getId() + "/tags/new");
        assertThat(page.locator("#tag-table tbody tr[data-testid^='tag-row-']")).hasCount(1);
        assertThat(row(tag.getCode()).locator("td").nth(0)).hasText("LST");
        assertThat(row(tag.getCode()).locator("td").nth(1)).hasText("Daftar Label");
        assertThat(row(tag.getCode()).locator("td").nth(2)).hasText("Deskripsi label");
        assertThat(row(tag.getCode()).locator("td").nth(3)).hasText("Aktif");
    }

    @Test
    @DisplayName("Kelola Label on the tag type list opens that type's tags")
    void shouldNavigateFromTagTypeToTagList() {
        loginAsAdmin();
        navigateTo("/tags/types?search=" + tagType.getCode());
        waitForPageLoad();

        page.locator("[data-testid='tag-type-manage-" + tagType.getCode() + "']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/tags/types/" + tagType.getId() + "/tags");
        assertThat(page.locator("#page-title")).hasText("Label: " + tagType.getName());
    }

    @Test
    @DisplayName("Creating a tag stores it under the tag type and lists it")
    void shouldCreateNewTag() {
        loginAsAdmin();
        navigateTo("/tags/types/" + tagType.getId() + "/tags/new");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Label Baru");

        page.locator("#code").fill("MKT");
        page.locator("#name").fill("Marketing");
        page.locator("#description").fill("Departemen Marketing");
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/tags/types/" + tagType.getId() + "/tags");
        assertThat(page.getByText("Label berhasil ditambahkan")).isVisible();
        assertThat(row("MKT").locator("td").nth(1)).hasText("Marketing");
        List<Tag> tags = tagRepository.findByTagTypeIdOrderByName(tagType.getId());
        assertThat(tags).extracting(Tag::getCode, Tag::getName, Tag::getDescription, Tag::isActive)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("MKT", "Marketing", "Departemen Marketing", true));
    }

    @Test
    @DisplayName("A code already used in the same type is rejected; the same code in another type is allowed")
    void shouldShowValidationErrorForDuplicateCode() {
        saveTag(tagType, "DUPTAG", "Duplikat 1", null);
        loginAsAdmin();
        navigateTo("/tags/types/" + tagType.getId() + "/tags/new");
        waitForPageLoad();

        page.locator("#code").fill("DUPTAG");
        page.locator("#name").fill("Duplikat 2");
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page.locator("#code ~ p.text-red-600")).hasText("Kode label sudah digunakan dalam tipe ini: DUPTAG");
        assertThat(tagRepository.findByTagTypeIdOrderByName(tagType.getId())).extracting(Tag::getName)
                .containsExactly("Duplikat 1");

        TagType otherType = new TagType();
        otherType.setCode(uniqueCode());
        otherType.setName("Jenis Lain");
        otherType.setActive(true);
        otherType = tagTypeRepository.save(otherType);
        navigateTo("/tags/types/" + otherType.getId() + "/tags/new");
        waitForPageLoad();
        page.locator("#code").fill("DUPTAG");
        page.locator("#name").fill("Duplikat Tipe Lain");
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page.getByText("Label berhasil ditambahkan")).isVisible();
        assertThat(tagRepository.findByTagTypeIdOrderByName(otherType.getId())).extracting(Tag::getName)
                .containsExactly("Duplikat Tipe Lain");
    }

    @Test
    @DisplayName("Editing changes the tag's name, description and status")
    void shouldEditTag() {
        Tag tag = saveTag(tagType, "EDTAG", "Untuk Edit", "Lama");
        loginAsAdmin();
        navigateTo("/tags/types/" + tagType.getId() + "/tags");
        waitForPageLoad();

        row("EDTAG").locator("a:has-text('Edit')").click();
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Edit Label");
        assertThat(page.locator("#code")).hasValue("EDTAG");
        assertThat(page.locator("#name")).hasValue("Untuk Edit");

        page.locator("#name").fill("Sudah Diedit");
        page.locator("#description").fill("Baru");
        page.locator("#active").uncheck();
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/tags/types/" + tagType.getId() + "/tags");
        assertThat(page.getByText("Label berhasil diubah")).isVisible();
        assertThat(row("EDTAG").locator("td").nth(1)).hasText("Sudah Diedit");
        assertThat(row("EDTAG").locator("td").nth(3)).hasText("Nonaktif");
        Tag updated = tagRepository.findById(tag.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Sudah Diedit");
        assertThat(updated.getDescription()).isEqualTo("Baru");
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    @DisplayName("An unused tag is deleted; a tag used by a transaction is kept with an error")
    void shouldDeleteTagWithoutTransactions() {
        Tag unused = saveTag(tagType, "DELTAG", "Untuk Hapus", null);
        Tag used = saveTag(tagType, "USED", "Dipakai", null);
        tagDraftTransaction(used);
        loginAsAdmin();
        page.onDialog(dialog -> dialog.accept());
        navigateTo("/tags/types/" + tagType.getId() + "/tags");
        waitForPageLoad();

        row("DELTAG").locator("button:has-text('Hapus')").click();
        waitForPageLoad();

        assertThat(page.getByText("Label berhasil dihapus")).isVisible();
        assertThat(row("DELTAG")).hasCount(0);
        assertThat(tagRepository.findById(unused.getId())).isEmpty();

        row("USED").locator("button:has-text('Hapus')").click();
        waitForPageLoad();

        assertThat(page.getByText("Label tidak dapat dihapus karena digunakan oleh 1 transaksi")).isVisible();
        assertThat(row("USED")).isVisible();
        assertThat(tagRepository.findById(used.getId())).isPresent();
    }

    @Test
    @DisplayName("A user with only TAG_VIEW sees no write actions and gets 403 on create, edit and delete")
    void viewOnlyUserCannotChangeTags() {
        Tag tag = saveTag(tagType, "VIEW", "Hanya Lihat", null);
        login("staff", "password");
        navigateTo("/tags/types/" + tagType.getId() + "/tags");
        waitForPageLoad();

        assertThat(row("VIEW").locator("td").nth(1)).hasText("Hanya Lihat");
        assertThat(page.locator("#btn-new-tag")).hasCount(0);
        assertThat(row("VIEW").locator("a:has-text('Edit')")).hasCount(0);
        assertThat(row("VIEW").locator("button:has-text('Hapus')")).hasCount(0);

        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        String base = "/tags/types/" + tagType.getId() + "/tags";
        assertThat(post(base + "/new", csrf, FormData.create().set("code", "NEW").set("name", "X").set("active", "true")).status())
                .isEqualTo(403);
        assertThat(post(base + "/" + tag.getId() + "/edit", csrf,
                FormData.create().set("code", "VIEW").set("name", "Diubah").set("active", "true")).status()).isEqualTo(403);
        assertThat(post(base + "/" + tag.getId() + "/delete", csrf, FormData.create()).status()).isEqualTo(403);

        assertThat(tagRepository.findByTagTypeIdOrderByName(tagType.getId())).extracting(Tag::getName)
                .containsExactly("Hanya Lihat");
    }

    private APIResponse post(String path, String csrf, FormData form) {
        return page.request().post(baseUrl() + path,
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf).setForm(form).setMaxRedirects(0));
    }

    private Locator row(String code) {
        return page.locator("[data-testid='tag-row-" + code + "']");
    }

    private Tag saveTag(TagType type, String code, String name, String description) {
        Tag tag = new Tag();
        tag.setTagType(type);
        tag.setCode(code);
        tag.setName(name);
        tag.setDescription(description);
        tag.setActive(true);
        return tagRepository.save(tag);
    }

    /** A DRAFT electricity payment dated 2104-01-10 carrying the tag. */
    private void tagDraftTransaction(Tag tag) {
        JournalTemplate template = templateRepository.findByTemplateNameWithLines("Bayar Beban Listrik")
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: Bayar Beban Listrik"));
        JournalTemplateLine bankLine = template.getLines().stream()
                .filter(l -> "BANK".equals(l.getAccountHint())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Template Bayar Beban Listrik has no BANK line"));
        Transaction transaction = new Transaction();
        transaction.setJournalTemplate(template);
        transaction.setTransactionDate(LocalDate.of(2104, 1, 10));
        transaction.setAmount(new BigDecimal("50000"));
        transaction.setDescription("Transaksi berlabel " + tag.getCode());
        Transaction created = transactionService.create(transaction, Map.of(bankLine.getId(),
                accountRepository.findByAccountCode("1.1.02")
                        .orElseThrow(() -> new IllegalStateException("Seeded account missing: 1.1.02")).getId()));
        transactionService.assignTags(created, List.of(tag.getId()));
    }

    private static String uniqueCode() {
        return "TC" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
