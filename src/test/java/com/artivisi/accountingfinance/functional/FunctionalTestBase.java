package com.artivisi.accountingfinance.functional;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class FunctionalTestBase {

    protected static Playwright playwright;
    protected static Browser browser;
    protected BrowserContext context;
    protected Page page;

    @LocalServerPort
    protected int port;

    protected static final Path SCREENSHOTS_DIR = Paths.get("target/screenshots/functional");

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setSlowMo(50));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void createContextAndPage() {
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setLocale("id-ID"));
        page = context.newPage();
        SCREENSHOTS_DIR.toFile().mkdirs();
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected String apiUrl(String path) {
        return baseUrl() + path;
    }

    protected void navigateTo(String path) {
        page.navigate(baseUrl() + path);
        waitForPageLoad();
    }

    protected void login(String username, String password) {
        navigateTo("/login");
        page.fill("input[name='username']", username);
        page.fill("input[name='password']", password);
        page.click("button[type='submit']");
        page.waitForURL("**/dashboard");
        waitForPageLoad();
    }

    protected void loginAsAdmin() {
        login("admin", "admin123");
    }

    protected void takeScreenshot(String name) {
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(SCREENSHOTS_DIR.resolve(name + ".png"))
                .setFullPage(false));
    }

    protected void takeFullPageScreenshot(String name) {
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(SCREENSHOTS_DIR.resolve(name + ".png"))
                .setFullPage(true));
    }

    protected void waitForPageLoad() {
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    protected void waitForSelector(String selector) {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(10000));
    }

    protected void waitForSelectorHidden(String selector) {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(10000));
    }

    protected void clickAndWait(String selector) {
        page.click(selector);
        waitForPageLoad();
    }

    protected void fillForm(String selector, String value) {
        page.fill(selector, value);
    }

    protected void selectOption(String selector, String value) {
        page.selectOption(selector, value);
    }

    protected void clearAndFill(String selector, String value) {
        page.locator(selector).clear();
        page.fill(selector, value);
    }

    protected void assertPageContainsText(String text) {
        assertThat(page.locator("body")).containsText(text);
    }

    protected void assertElementVisible(String selector) {
        assertThat(page.locator(selector)).isVisible();
    }

    protected void assertElementNotVisible(String selector) {
        assertThat(page.locator(selector)).not().isVisible();
    }

    protected void assertCurrentUrl(String path) {
        assertThat(page).hasURL(baseUrl() + path);
    }

    protected void assertErrorMessage(String message) {
        assertThat(page.locator(".error, .alert-danger, [role='alert']")).containsText(message);
    }

    protected void assertSuccessMessage(String message) {
        assertThat(page.locator(".success, .alert-success, [role='status']")).containsText(message);
    }

    protected String getInputValue(String selector) {
        return page.inputValue(selector);
    }

    protected String getTextContent(String selector) {
        return page.locator(selector).textContent();
    }

    protected int getElementCount(String selector) {
        return page.locator(selector).count();
    }

    protected void submitForm() {
        page.click("button[type='submit']");
        waitForPageLoad();
    }

    protected void clickButton(String text) {
        page.click("button:has-text('" + text + "')");
        waitForPageLoad();
    }

    protected void clickLink(String text) {
        page.click("a:has-text('" + text + "')");
        waitForPageLoad();
    }

    protected APIResponse apiGet(String path) {
        return page.request().get(apiUrl(path));
    }

    protected APIResponse apiPost(String path, String body) {
        return page.request().post(apiUrl(path),
            RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(body));
    }

    protected APIResponse apiPut(String path, String body) {
        return page.request().put(apiUrl(path),
            RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(body));
    }

    protected APIResponse apiDelete(String path) {
        return page.request().delete(apiUrl(path));
    }
}
