package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.ApiClient;
import com.artivisi.accountingfinance.entity.DeviceToken;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.exception.InvalidApiClientException;
import com.artivisi.accountingfinance.repository.ApiClientRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service accounts for the OAuth2 client_credentials grant (issue #28).
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ApiClientService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiClientRepository apiClientRepository;
    private final DeviceAuthService deviceAuthService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.api-client.token-expiry-minutes:60}")
    private int tokenExpiryMinutes;

    /**
     * Creation result carrying the plaintext secret — shown exactly once.
     */
    public record CreatedClient(ApiClient client, String clientSecret) {}

    @Transactional(readOnly = true)
    public List<ApiClient> findAll() {
        return apiClientRepository.findAllWithUser();
    }

    public CreatedClient create(String name, String scopes, User user, String createdBy) {
        String clientId = generateClientId(name);
        String secret = generateSecret();

        ApiClient client = new ApiClient();
        client.setClientId(clientId);
        client.setClientSecretHash(passwordEncoder.encode(secret));
        client.setName(name);
        client.setScopes(scopes);
        client.setUser(user);
        client.setCreatedBy(createdBy);

        ApiClient saved = apiClientRepository.save(client);
        log.info("Created API client {} ({}) for user {}",
                LogSanitizer.sanitize(clientId), LogSanitizer.sanitize(name), LogSanitizer.username(user.getUsername()));
        return new CreatedClient(saved, secret);
    }

    public void deactivate(UUID id, String deactivatedBy) {
        ApiClient client = apiClientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("API client tidak ditemukan"));
        client.setActive(false);
        apiClientRepository.save(client);
        log.info("Deactivated API client {} by {}",
                LogSanitizer.sanitize(client.getClientId()), LogSanitizer.username(deactivatedBy));
    }

    /**
     * RFC 6749 §4.4 token issuance: validate client credentials and mint a
     * short-lived bearer token restricted to the client's registered scopes.
     */
    public DeviceToken issueToken(String clientId, String clientSecret) {
        ApiClient client = apiClientRepository.findActiveByClientId(clientId)
                .orElseThrow(InvalidApiClientException::new);

        if (!passwordEncoder.matches(clientSecret, client.getClientSecretHash())) {
            log.warn("client_credentials authentication failed for client {}", LogSanitizer.sanitize(clientId));
            throw new InvalidApiClientException();
        }

        client.setLastUsedAt(LocalDateTime.now());
        apiClientRepository.save(client);

        return deviceAuthService.createServiceToken(
                client.getUser(), client.getClientId(), client.getName(),
                client.getScopes(), tokenExpiryMinutes);
    }

    public int getTokenExpiryMinutes() {
        return tokenExpiryMinutes;
    }

    private String generateClientId(String name) {
        String slug = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.length() > 38) {
            slug = slug.substring(0, 38);
        }
        String clientId = slug + "-" + HexFormat.of().formatHex(randomBytes(4));
        if (apiClientRepository.existsByClientId(clientId)) {
            throw new IllegalStateException("Client ID collision, retry: " + clientId);
        }
        return clientId;
    }

    private String generateSecret() {
        return HexFormat.of().formatHex(randomBytes(32));
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
