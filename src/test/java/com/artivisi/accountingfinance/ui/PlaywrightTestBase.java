package com.artivisi.accountingfinance.ui;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.security.LoginAttemptService;

import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("functional")
public abstract class PlaywrightTestBase {

    protected static Playwright playwright;
    protected static Browser browser;
    protected BrowserContext context;
    protected Page page;

    @LocalServerPort
    protected int port;

    @Autowired
    protected LoginAttemptService loginAttemptService;

    protected static final Path SCREENSHOTS_DIR = Paths.get("target/screenshots");
    protected static final Path MANUAL_SCREENSHOTS_DIR = Paths.get("target", "user-manual", "screenshots");

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
        // Reset login attempts to prevent lockout from previous tests
        loginAttemptService.resetAllAttempts();

        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setLocale("id-ID"));
        page = context.newPage();
        page.setDefaultTimeout(5000);

        // Capture browser console errors for debugging test failures
        page.onConsoleMessage(msg -> {
            if (msg.type().equals("error")) {
                System.err.println("BROWSER ERROR: " + msg.text());
            }
        });

        // Disable CSS animations and transitions for stable tests
        // This prevents Alpine.js x-collapse animation from causing flaky tests
        page.addInitScript("""
            () => {
              const style = document.createElement('style');
              style.innerHTML = '*, *::before, *::after { \
                transition-duration: 0s !important; \
                transition-delay: 0s !important; \
                animation-duration: 0s !important; \
                animation-delay: 0s !important; \
              }';
              document.head.appendChild(style);
            }""");
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
        page.waitForURL("**/dashboard", new Page.WaitForURLOptions().setTimeout(10000));
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
