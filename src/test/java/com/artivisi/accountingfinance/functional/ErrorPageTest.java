package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DisplayName("Error Pages - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class ErrorPageTest extends PlaywrightTestBase {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("404 page renders for non-existent URL")
    void shouldDisplay404Page() {
        loginAsAdmin();

        navigateTo("/this-page-does-not-exist-anywhere");
        waitForPageLoad();

        assertThat(page.locator("#error-404-page")).isVisible();

        String pageContent = page.content();
        assertThat(pageContent).contains("404");
        assertThat(pageContent).contains("Kembali ke Dashboard");

        log.info("404 error page rendered correctly");
    }

    @Test
    @DisplayName("403 page renders for unauthorized access")
    void shouldDisplay403Page() {
        // Ensure staff user is active before login — other tests may have toggled it
        var staff = userRepository.findByUsername("staff")
                .orElseThrow(() -> new AssertionError("Test user 'staff' not found in seed data"));
        if (!Boolean.TRUE.equals(staff.getActive())) {
            staff.setActive(true);
            userRepository.save(staff);
            log.info("Re-activated staff user before 403 test");
        }

        login("staff", "password");

        navigateTo("/users");
        waitForPageLoad();

        assertThat(page.locator("#access-denied-page")).isVisible();

        String pageContent = page.content();
        assertThat(pageContent).contains("403");
        assertThat(pageContent).contains("Akses Ditolak");
        assertThat(pageContent).contains("Kembali ke Dashboard");

        log.info("403 error page rendered correctly");
    }
}
