package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.DeviceToken;
import com.artivisi.accountingfinance.exception.InvalidApiClientException;
import com.artivisi.accountingfinance.service.ApiClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * OAuth2 token endpoint for the client_credentials grant (RFC 6749 §4.4).
 * Serves headless machine-to-machine callers (e.g. subledger outbox
 * dispatchers) that cannot use the interactive device flow.
 */
@RestController
@RequestMapping("/api/oauth")
@Tag(name = "OAuth Token", description = "OAuth2 client_credentials token endpoint for unattended "
        + "service-to-service API access. Client credentials are managed under Pengaturan > API Klien.")
@RequiredArgsConstructor
@Slf4j
public class OAuthTokenApiController {

    private final ApiClientService apiClientService;

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "Issue an access token (client_credentials grant)",
            description = "Accepts grant_type=client_credentials with client_id/client_secret as "
                    + "form parameters or via HTTP Basic auth. Returns a short-lived opaque bearer "
                    + "token restricted to the client's registered scopes.")
    @ApiResponse(responseCode = "200", description = "Token issued")
    @ApiResponse(responseCode = "400", description = "Unsupported grant type")
    @ApiResponse(responseCode = "401", description = "Invalid client credentials")
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "client_secret", required = false) String clientSecret,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        if (!"client_credentials".equals(grantType)) {
            return oauthError(HttpStatus.BAD_REQUEST, "unsupported_grant_type",
                    "Only grant_type=client_credentials is supported");
        }

        // RFC 6749 §2.3.1: client may authenticate via HTTP Basic instead of body params
        if ((clientId == null || clientSecret == null) && authorization != null && authorization.startsWith("Basic ")) {
            String[] decoded = decodeBasicAuth(authorization);
            if (decoded != null) {
                clientId = decoded[0];
                clientSecret = decoded[1];
            }
        }

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return oauthError(HttpStatus.UNAUTHORIZED, "invalid_client",
                    "Client authentication required (body params or HTTP Basic)");
        }

        try {
            DeviceToken token = apiClientService.issueToken(clientId.trim(), clientSecret);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(Map.of(
                            "access_token", token.getTokenHash(), // plaintext copy, never persisted
                            "token_type", "Bearer",
                            "expires_in", apiClientService.getTokenExpiryMinutes() * 60L,
                            "scope", token.getScopes()));
        } catch (InvalidApiClientException e) {
            return oauthError(HttpStatus.UNAUTHORIZED, "invalid_client", "Client authentication failed");
        }
    }

    private ResponseEntity<Map<String, Object>> oauthError(HttpStatus status, String error, String description) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(Map.of("error", error, "error_description", description));
    }

    private String[] decodeBasicAuth(String authorization) {
        try {
            String decoded = new String(
                    Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator < 0) {
                return null;
            }
            return new String[]{decoded.substring(0, separator), decoded.substring(separator + 1)};
        } catch (IllegalArgumentException e) {
            log.warn("Malformed Basic authorization header on token endpoint");
            return null;
        }
    }
}
