package com.safepath.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Open-Meteo weather integration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "safepath.weather")
public class WeatherProperties {

    /**
     * Base URL for the Open-Meteo REST API.
     */
    private String baseUrl = "https://api.open-meteo.com/v1";

    /**
     * Read timeout in milliseconds for HTTP requests.
     */
    private int timeoutMs = 4000;

    /**
     * Connection timeout in milliseconds for HTTP requests.
     */
    private int connectTimeoutMs = 3000;

    /**
     * In-memory cache time-to-live in minutes.
     */
    private int cacheTtlMinutes = 30;

    /**
     * Whether weather integration is enabled.
     */
    private boolean enabled = true;
}
