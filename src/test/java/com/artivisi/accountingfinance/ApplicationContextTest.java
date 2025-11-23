package com.artivisi.accountingfinance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApplicationContextTest {

    @Test
    void contextLoads() {
        // Just verify the application context loads successfully with Testcontainers
    }
}
