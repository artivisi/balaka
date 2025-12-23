package com.artivisi.accountingfinance.config;

import com.artivisi.accountingfinance.service.telegram.TelegramApiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Configuration for Telegram Bot API HTTP client using Spring's HTTP Interface.
 */
@Configuration
@ConditionalOnProperty(prefix = "telegram.bot", name = "enabled", havingValue = "true", matchIfMissing = false)
public class TelegramApiConfig {

    @Bean
    public RestClient telegramRestClient(TelegramConfig telegramConfig) {
        // Use method parameter injection to avoid storing mutable object reference
        String baseUrl = String.format("https://api.telegram.org/bot%s", telegramConfig.getToken());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public TelegramApiClient telegramApiClient(RestClient telegramRestClient) {
        RestClientAdapter adapter = RestClientAdapter.create(telegramRestClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        
        return factory.createClient(TelegramApiClient.class);
    }
}
