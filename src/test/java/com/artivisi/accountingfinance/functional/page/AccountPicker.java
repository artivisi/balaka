package com.artivisi.accountingfinance.functional.page;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.RequestOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Drives an accountPicker combobox (text input + hidden id input + result buttons under
 * the same parent element) and selects one account by its exact code.
 */
public final class AccountPicker {

    private static final ObjectMapper JSON = new ObjectMapper();

    private AccountPicker() {
    }

    /**
     * Types the account code and clicks the result whose code equals it exactly. Fails when
     * the picker does not offer that account, or when the selection does not reach the
     * picker's text and hidden inputs.
     */
    public static void select(Page page, Locator input, String accountCode) {
        String inputId = input.getAttribute("id");
        input.click();
        input.fill(accountCode);
        var container = input.locator("xpath=..");
        var result = container.locator("[data-testid='account-picker-result']")
                .filter(new Locator.FilterOptions().setHas(page.locator(".font-medium",
                        new Page.LocatorOptions().setHasText(exact(accountCode)))));
        try {
            assertThat(result).hasCount(1);
            result.click();
            assertThat(input).hasValue(Pattern.compile("^" + escape(accountCode) + " - .+"));
            assertThat(container.locator("input[type='hidden']")).not().hasValue("");
        } catch (AssertionError e) {
            throw new AssertionError("Account picker #" + inputId + " did not select account " + accountCode
                    + ": " + e.getMessage(), e);
        }
    }

    /**
     * Account codes the server offers for a picker query, in the order the picker lists them.
     */
    public static List<String> searchCodes(Page page, String baseUrl, String query) {
        var response = page.request().get(baseUrl + "/accounts/search",
                RequestOptions.create().setQueryParam("q", query));
        if (response.status() != 200) {
            throw new IllegalStateException("GET /accounts/search?q=" + query + " returned " + response.status());
        }
        List<String> codes = new ArrayList<>();
        try {
            for (JsonNode node : JSON.readTree(response.text())) {
                codes.add(node.get("code").asText());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unparseable /accounts/search?q=" + query + " response", e);
        }
        return codes;
    }

    private static Pattern exact(String text) {
        return Pattern.compile("^" + escape(text) + "$");
    }

    // Playwright evaluates the pattern as a JavaScript RegExp, which has no \Q...\E quoting.
    private static String escape(String text) {
        return text.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0");
    }
}
