package com.safepath.backend.dto.weather;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Public response DTO for weather lookup.
 * Explicitly separates current observations from forecast values.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WeatherDto {

    private Double latitude;
    private Double longitude;
    private boolean available;

    private CurrentObservation observation;
    private ForecastContext forecast;
    private WeatherUnits units;

    private String source;
    private OffsetDateTime retrievedAt;
    private boolean cached;
    private String disclaimer;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CurrentObservation {
        private Double temperatureC;
        private Double precipitationMm;
        private Double windSpeedKmh;
        private Double relativeHumidity;
        private Double visibilityKm;
        private Integer weatherCode;
        private String condition;
        private String icon;
        private Boolean isDay;
        private String observedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ForecastContext {
        private Double precipitationProbabilityPercent;
        private String forecastTimestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeatherUnits {
        @Builder.Default
        private String temperature = "°C";

        @Builder.Default
        private String precipitation = "mm";

        @Builder.Default
        private String windSpeed = "km/h";

        @Builder.Default
        private String relativeHumidity = "%";

        @Builder.Default
        private String visibility = "km";

        @Builder.Default
        private String precipitationProbability = "%";
    }
}
