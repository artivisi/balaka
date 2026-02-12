package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.DeviceCode;
import com.artivisi.accountingfinance.entity.DeviceToken;
import com.artivisi.accountingfinance.service.DeviceAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * REST API for OAuth 2.0 Device Authorization Flow.
 * Implements RFC 8628: https://tools.ietf.org/html/rfc8628
 */
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
@Slf4j
public class DeviceAuthApiController {

    private final DeviceAuthService deviceAuthService;

    /**
     * Step 1: Request device and user codes.
     * POST /api/device/code
     */
    @PostMapping("/code")
    public ResponseEntity<DeviceCodeResponse> requestDeviceCode(
            @Valid @RequestBody DeviceCodeRequest request,
            HttpServletRequest httpRequest) {

        log.info("Device code request from client: {}", request.clientId());

        // Build base URL from request
        String scheme = httpRequest.getScheme();
        String serverName = httpRequest.getServerName();
        int serverPort = httpRequest.getServerPort();
        String baseUrl = scheme + "://" + serverName +
                (serverPort == 80 || serverPort == 443 ? "" : ":" + serverPort);

        DeviceCode deviceCode = deviceAuthService.createDeviceCode(request.clientId(), baseUrl);

        DeviceCodeResponse response = new DeviceCodeResponse(
                deviceCode.getDeviceCode(),
                deviceCode.getUserCode(),
                deviceCode.getVerificationUri(),
                deviceCode.getVerificationUri() + "?code=" + deviceCode.getUserCode(),
                900, // 15 minutes in seconds
                5    // Poll every 5 seconds
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Step 2: Poll for access token.
     * POST /api/device/token
     */
    @PostMapping("/token")
    public ResponseEntity<?> pollForToken(@Valid @RequestBody TokenRequest request) {

        log.debug("Token poll for device code");

        try {
            Optional<DeviceToken> token = deviceAuthService.pollForToken(request.deviceCode());

            if (token.isPresent()) {
                DeviceToken deviceToken = token.get();

                // Extract plaintext token (stored temporarily in tokenHash field)
                String accessToken = deviceToken.getTokenHash();

                TokenResponse response = new TokenResponse(
                        accessToken,
                        "Bearer",
                        2592000, // 30 days in seconds
                        deviceToken.getScopes()
                );

                log.info("Issued access token for device");
                return ResponseEntity.ok(response);
            }

            // Still pending
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("authorization_pending",
                            "The authorization request is still pending"));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("invalid_request", e.getMessage()));

        } catch (IllegalStateException e) {
            String error = e.getMessage().contains("expired") ? "expired_token" : "access_denied";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(error, e.getMessage()));
        }
    }

    // ============================================
    // DTOs
    // ============================================

    /**
     * Request: Device code request
     */
    public record DeviceCodeRequest(
            @NotBlank(message = "Client ID is required")
            String clientId
    ) {}

    /**
     * Response: Device code response (RFC 8628)
     */
    public record DeviceCodeResponse(
            String deviceCode,
            String userCode,
            String verificationUri,
            String verificationUriComplete,
            int expiresIn,
            int interval
    ) {}

    /**
     * Request: Token request
     */
    public record TokenRequest(
            @NotBlank(message = "Device code is required")
            String deviceCode
    ) {}

    /**
     * Response: Token response (RFC 8628)
     */
    public record TokenResponse(
            String accessToken,
            String tokenType,
            int expiresIn,
            String scope
    ) {}

    /**
     * Response: Error response (RFC 8628)
     */
    public record ErrorResponse(
            String error,
            String errorDescription
    ) {}
}
