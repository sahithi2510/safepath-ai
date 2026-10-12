package com.safepath.backend.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration.
 * Access Swagger UI at /swagger-ui.html after starting the backend.
 */
@Configuration
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT token from POST /api/auth/login"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI safePathOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SafePath AI API")
                        .description("""
                            SafePath AI — Intelligent AI-Powered Women Safety Route Planner.
                            
                            This API provides:
                            - User authentication (JWT)
                            - Safety-aware route planning (Fastest / Balanced / Safest)
                            - Community safety reports
                            - Nearby safe zones (police stations, hospitals, pharmacies)
                            - Emergency contacts and SOS logging
                            
                            **Note:** All safety scores and risk levels are relative, application-defined
                            classifications. See API documentation and project limitations for details.
                            """)
                        .version("0.0.1-SNAPSHOT")
                        .license(new License().name("MIT")));
    }
}
