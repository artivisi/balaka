package com.artivisi.accountingfinance.ui;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.artivisi.accountingfinance.TestcontainersConfiguration;

import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class PlaywrightTestBase {

    protected static Playwright playwright;
    protected static Browser browser;
    protected BrowserContext context;
    protected Page page;

    @LocalServerPort
    protected int port;

    protected static final Path SCREENSHOTS_DIR = Paths.get("target/screenshots");
    protected static final Path MANUAL_SCREENSHOTS_DIR = Paths.get("docs/screenshots");

    // Configure via system properties:
    // -Dplaywright.headless=false  (default: true)
    // -Dplaywright.slowmo=100      (default: 0)
    private static final boolean HEADLESS = Boolean.parseBoolean(
            System.getProperty("playwright.headless", "true"));
    private static final int SLOW_MO = Integer.parseInt(
            System.getProperty("playwright.slowmo", "0"));

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(HEADLESS)
                .setSlowMo(SLOW_MO));
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
        page.setDefaultTimeout(5000);
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

    protected void navigateTo(String path) {
        page.navigate(baseUrl() + path);
    }

    protected void login(String username, String password) {
        navigateTo("/login");
        waitForPageLoad();
        page.fill("input[name='username']", username);
        page.fill("input[name='password']", password);
        page.click("button[type='submit']");
        page.waitForURL("**/dashboard");
    }

    protected void loginAsAdmin() {
        login("admin", "admin");
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

    protected void takeManualScreenshot(String name) {
        MANUAL_SCREENSHOTS_DIR.toFile().mkdirs();
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(MANUAL_SCREENSHOTS_DIR.resolve(name + ".png"))
                .setFullPage(false));
    }

    protected void takeManualScreenshot(String name, int width, int height) {
        MANUAL_SCREENSHOTS_DIR.toFile().mkdirs();
        page.setViewportSize(width, height);
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(MANUAL_SCREENSHOTS_DIR.resolve(name + ".png"))
                .setFullPage(false));
        page.setViewportSize(1920, 1080);
    }

    protected void waitForPageLoad() {
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    protected void clickAndWait(String selector) {
        page.click(selector);
        waitForPageLoad();
    }
}
