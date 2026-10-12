package com.safepath.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the FastAPI ML inference service.
 */
@Data
@Component
@ConfigurationProperties(prefix = "safepath.ml-service")
public class MlServiceProperties {

    /**
     * Base URL for the FastAPI ML inference service.
     */
    private String url = "http://localhost:8001";

    /**
     * Socket read timeout in milliseconds.
     */
    private int timeoutMs = 5000;

    /**
     * TCP connection timeout in milliseconds.
     */
    private int connectTimeoutMs = 3000;

    /**
     * Maximum allowed batch size for chunking requests.
     */
    private int maxBatchSize = 500;

    /**
     * Whether ML inference integration is active.
     */
    private boolean enabled = true;
}
