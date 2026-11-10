package com.artivisi.accountingfinance.config;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The document storage root and the proxy header handling are configurable from
 * the environment, which is what container and non-Ansible deployments need.
 *
 * <p>The placeholder is resolved by the {@link Environment}, so setting the
 * variable as a property is the same lookup the process environment takes.
 */
@SpringBootTest(properties = "APP_DOCUMENT_STORAGE_PATH=target/documents-from-env")
@Import(TestcontainersConfiguration.class)
@DisplayName("Application properties")
class ApplicationPropertiesTest {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("the document storage root follows APP_DOCUMENT_STORAGE_PATH")
    void documentStorageRootIsConfigurable() {
        assertThat(environment.getProperty("app.storage.documents.path"))
                .isEqualTo("target/documents-from-env");
    }

    @Test
    @DisplayName("forwarded headers are honoured so redirects behind a proxy keep https")
    void forwardedHeadersUseTheFrameworkStrategy() {
        assertThat(environment.getProperty("server.forward-headers-strategy"))
                .isEqualTo("framework");
    }
}
