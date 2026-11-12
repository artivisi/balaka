package com.artivisi.accountingfinance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
// No profile on purpose; the storage path has no default, so it is supplied here.
@TestPropertySource(properties = "app.storage.documents.path=target/test-documents")
class ApplicationContextTest {

    @Test
    void contextLoads() {
        // Just verify the application context loads successfully with Testcontainers
    }
}
