package com.safepath.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — verifies the Spring application context loads without errors.
 */
@SpringBootTest
@ActiveProfiles("test")
class SafePathBackendApplicationTests {

    @Test
    void contextLoads() {
        // If this test passes, the application context is valid.
        // No assertions needed — context loading failure will fail the test.
    }
}
