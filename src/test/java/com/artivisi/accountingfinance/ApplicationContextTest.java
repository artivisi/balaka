package com.artivisi.accountingfinance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
// The storage path has no default, so the test states it explicitly.
@TestPropertySource(properties = "app.storage.documents.path=target/test-documents")
class ApplicationContextTest {

    @Test
    void contextLoads() {
        // Just verify the application context loads successfully with Testcontainers
    }
}
