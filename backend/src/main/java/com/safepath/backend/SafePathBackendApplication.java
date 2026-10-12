package com.safepath.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SafePath AI — Spring Boot Backend Application
 * Main entry point for the backend service.
 */
@SpringBootApplication
@EnableScheduling
public class SafePathBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SafePathBackendApplication.class, args);
    }
}
