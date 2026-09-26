package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Tag;
import com.artivisi.accountingfinance.entity.TagType;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.TagRepository;
import com.artivisi.accountingfinance.repository.TagTypeRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tag type CRUD under /tags/types. Every test works on tag types it creates with unique codes;
 * the list is paginated and shared, so rows are located through the search parameter.
 */
@DisplayName("Tag Type CRUD Tests")
@Import(ServiceTestDataInitializer.class)
class TagTypeCrudTest extends PlaywrightTestBase {

    @Autowired private TagTypeRepository tagTypeRepository;
    @Autowired private TagRepository tagRepository;

    @Test
    @DisplayName("List shows each tag type's code, name, description and status with manage, edit and delete actions")
    void shouldDisplayTagTypeListPage() {
        TagType type = saveType(uniqueCode(), "Daftar Tipe", "Deskripsi daftar", true);
        loginAsAdmin();

        navigateTo("/tags/types?search=" + type.getCode());
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Label Transaksi");
        assertThat(page.locator("#btn-new-tag-type")).hasAttribute("href", "/tags/types/new");
        Locator row = row(type.getCode());
        assertThat(row.locator("td")).hasText(new String[] {
                type.getCode(), "Daftar Tipe", "Deskripsi daftar", "Aktif", "Kelola Label Edit Hapus"});
        assertThat(page.locator("[data-testid='tag-type-manage-" + type.getCode() + "']"))
                .hasAttribute("href", "/tags/types/" + type.getId() + "/tags");
        assertThat(row.locator("a:has-text('Edit')")).hasAttribute("href", "/tags/types/" + type.getId() + "/edit");
    }

    @Test
    @DisplayName("Master Data sidebar link opens the tag type list")
    void shouldNavigateFromSidebar() {
        loginAsAdmin();
        navigateTo("/dashboard");
        waitForPageLoad();

        page.locator("#nav-group-master").click();
        page.locator("#nav-tags").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/tags/types");
        assertThat(page.locator("#page-title")).hasText("Label Transaksi");
    }

    @Test
    @DisplayName("Creating a tag type stores it and lists it")
    void shouldCreateNewTagType() {
        String code = uniqueCode();
        loginAsAdmin();
        navigateTo("/tags/types/new");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Tipe Label Baru");
        assertThat(page.locator("#active")).isChecked();

        page.locator("#code").fill(code);
        page.locator("#name").fill("Tipe Baru " + code);
        page.locator("#description").fill("Tipe label baru untuk test");
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/tags/types");
        assertThat(page.getByText("Tipe label berhasil ditambahkan")).isVisible();
        TagType saved = findType(code);
        assertThat(saved.getName()).isEqualTo("Tipe Baru " + code);
        assertThat(saved.getDescription()).isEqualTo("Tipe label baru untuk test");
        assertThat(saved.isActive()).isTrue();

        navigateTo("/tags/types?search=" + code);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='tag-type-name-" + code + "']")).hasText("Tipe Baru " + code);
    }

    @Test
    @DisplayName("Blank code and name are rejected with their messages and nothing is stored")
    void shouldShowValidationErrorForEmptyCode() {
        String name = "Tanpa Kode " + uniqueCode();
        loginAsAdmin();
        navigateTo("/tags/types/new");
        waitForPageLoad();

        page.locator("#name").fill(name);
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Tipe Label Baru");
        assertThat(page.locator("#code ~ p.text-red-600")).hasText("Kode tipe label wajib diisi");
        assertThat(page.locator("#name")).hasValue(name);
        assertThat(tagTypeRepository.findAll().stream().filter(t -> name.equals(t.getName()))).isEmpty();

        page.locator("#code").fill(uniqueCode());
        page.locator("#name").fill("");
        page.locator("#btn-save").click();
        waitForPageLoad();
        assertThat(page.locator("#name ~ p.text-red-600")).hasText("Nama tipe label wajib diisi");
    }

    @Test
    @DisplayName("A code already used by another tag type is rejected on the code field")
    void shouldShowValidationErrorForDuplicateCode() {
        TagType existing = saveType(uniqueCode(), "Duplikat Asli", null, true);
        loginAsAdmin();
        navigateTo("/tags/types/new");
        waitForPageLoad();

        page.locator("#code").fill(existing.getCode());
        page.locator("#name").fill("Duplikat Lain");
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page.locator("#code ~ p.text-red-600"))
                .hasText("Kode tipe label sudah digunakan: " + existing.getCode());
        assertThat(tagTypeRepository.findAll().stream().filter(t -> existing.getCode().equals(t.getCode())))
                .extracting(TagType::getName).containsExactly("Duplikat Asli");
    }

    @Test
    @DisplayName("Editing changes name, description and status of that tag type only")
    void shouldEditTagType() {
        TagType type = saveType(uniqueCode(), "Untuk Edit", "Lama", true);
        TagType other = saveType(uniqueCode(), "Tidak Diubah", "Tetap", true);
        loginAsAdmin();
        navigateTo("/tags/types?search=" + type.getCode());
        waitForPageLoad();

        row(type.getCode()).locator("a:has-text('Edit')").click();
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Edit Tipe Label");
        assertThat(page.locator("#code")).hasValue(type.getCode());
        assertThat(page.locator("#name")).hasValue("Untuk Edit");

        page.locator("#name").fill("Sudah Diedit");
        page.locator("#description").fill("Baru");
        page.locator("#active").uncheck();
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/tags/types");
        assertThat(page.getByText("Tipe label berhasil diubah")).isVisible();
        TagType updated = tagTypeRepository.findById(type.getId()).orElseThrow();
        assertThat(updated.getCode()).isEqualTo(type.getCode());
        assertThat(updated.getName()).isEqualTo("Sudah Diedit");
        assertThat(updated.getDescription()).isEqualTo("Baru");
        assertThat(updated.isActive()).isFalse();
        TagType untouched = tagTypeRepository.findById(other.getId()).orElseThrow();
        assertThat(untouched.getName()).isEqualTo("Tidak Diubah");
        assertThat(untouched.isActive()).isTrue();

        navigateTo("/tags/types?search=" + type.getCode());
        waitForPageLoad();
        assertThat(row(type.getCode()).locator("td").nth(3)).hasText("Nonaktif");
    }

    @Test
    @DisplayName("A tag type without tags is deleted; one that still has tags is kept with an error")
    void shouldDeleteTagTypeWithoutTags() {
        TagType empty = saveType(uniqueCode(), "Untuk Hapus", null, true);
        TagType used = saveType(uniqueCode(), "Masih Dipakai", null, true);
        Tag tag = new Tag();
        tag.setTagType(used);
        tag.setCode("T" + uniqueCode());
        tag.setName("Label Dipakai");
        tag.setActive(true);
        tagRepository.save(tag);
        loginAsAdmin();
        page.onDialog(dialog -> dialog.accept());

        navigateTo("/tags/types?search=" + empty.getCode());
        waitForPageLoad();
        row(empty.getCode()).locator("button:has-text('Hapus')").click();
        waitForPageLoad();

        assertThat(page.getByText("Tipe label berhasil dihapus")).isVisible();
        assertThat(tagTypeRepository.findById(empty.getId())).isEmpty();
        navigateTo("/tags/types?search=" + empty.getCode());
        waitForPageLoad();
        assertThat(row(empty.getCode())).hasCount(0);

        navigateTo("/tags/types?search=" + used.getCode());
        waitForPageLoad();
        row(used.getCode()).locator("button:has-text('Hapus')").click();
        waitForPageLoad();

        assertThat(page.getByText("Tipe label tidak dapat dihapus karena masih memiliki 1 label")).isVisible();
        assertThat(tagTypeRepository.findById(used.getId())).isPresent();
    }

    @Test
    @DisplayName("Typing in the search box narrows the list to matching codes and names")
    void shouldSearchTagTypes() {
        String prefix = uniqueCode().substring(0, 6);
        TagType first = saveType(prefix + "A", "Cari Pertama", null, true);
        TagType second = saveType(prefix + "B", "Cari Kedua", null, true);
        loginAsAdmin();
        navigateTo("/tags/types");
        waitForPageLoad();

        page.locator("#search-input").pressSequentially(prefix);
        assertThat(page.locator("#tag-type-table tbody tr[data-testid^='tag-type-row-']")).hasCount(2);
        assertThat(row(first.getCode())).isVisible();
        assertThat(row(second.getCode())).isVisible();

        page.locator("#search-input").fill("");
        page.locator("#search-input").pressSequentially(second.getCode());
        assertThat(page.locator("#tag-type-table tbody tr[data-testid^='tag-type-row-']")).hasCount(1);
        assertThat(row(second.getCode())).isVisible();
    }

    @Test
    @DisplayName("A user with only TAG_VIEW sees no write actions and gets 403 on create, edit and delete")
    void viewOnlyUserCannotChangeTagTypes() {
        TagType type = saveType(uniqueCode(), "Hanya Lihat", null, true);
        login("staff", "password");
        navigateTo("/tags/types?search=" + type.getCode());
        waitForPageLoad();

        assertThat(row(type.getCode()).locator("td").nth(1)).hasText("Hanya Lihat");
        assertThat(page.locator("#btn-new-tag-type")).hasCount(0);
        assertThat(row(type.getCode()).locator("a:has-text('Edit')")).hasCount(0);
        assertThat(row(type.getCode()).locator("button:has-text('Hapus')")).hasCount(0);

        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        String newCode = uniqueCode();
        assertThat(post("/tags/types/new", csrf, FormData.create().set("code", newCode).set("name", "X").set("active", "true")).status())
                .isEqualTo(403);
        assertThat(post("/tags/types/" + type.getId() + "/edit", csrf,
                FormData.create().set("code", type.getCode()).set("name", "Diubah").set("active", "true")).status())
                .isEqualTo(403);
        assertThat(post("/tags/types/" + type.getId() + "/delete", csrf, FormData.create()).status()).isEqualTo(403);

        assertThat(tagTypeRepository.findAll().stream().filter(t -> newCode.equals(t.getCode()))).isEmpty();
        TagType unchanged = tagTypeRepository.findById(type.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Hanya Lihat");
    }

    private APIResponse post(String path, String csrf, FormData form) {
        return page.request().post(baseUrl() + path,
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf).setForm(form).setMaxRedirects(0));
    }

    private Locator row(String code) {
        return page.locator("[data-testid='tag-type-row-" + code + "']");
    }

    private TagType findType(String code) {
        List<TagType> matches = tagTypeRepository.findAll().stream().filter(t -> code.equals(t.getCode())).toList();
        assertThat(matches).as("tag type %s", code).hasSize(1);
        return matches.getFirst();
    }

    private TagType saveType(String code, String name, String description, boolean active) {
        TagType type = new TagType();
        type.setCode(code);
        type.setName(name);
        type.setDescription(description);
        type.setActive(active);
        return tagTypeRepository.save(type);
    }

    private static String uniqueCode() {
        return "TT" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
