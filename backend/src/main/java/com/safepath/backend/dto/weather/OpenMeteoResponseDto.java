package com.safepath.backend.dto.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Direct response structure from Open-Meteo API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenMeteoResponseDto {

    private Double latitude;
    private Double longitude;

    @JsonProperty("generationtime_ms")
    private Double generationtimeMs;

    @JsonProperty("utc_offset_seconds")
    private Integer utcOffsetSeconds;

    private String timezone;

    @JsonProperty("timezone_abbreviation")
    private String timezoneAbbreviation;

    private Double elevation;

    @JsonProperty("current_units")
    private Map<String, String> currentUnits;

    private CurrentWeather current;

    @JsonProperty("hourly_units")
    private Map<String, String> hourlyUnits;

    private HourlyWeather hourly;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CurrentWeather {
        private String time;
        private Integer interval;

        @JsonProperty("temperature_2m")
        private Double temperature2m;

        @JsonProperty("relative_humidity_2m")
        private Double relativeHumidity2m;

        private Double precipitation;

        @JsonProperty("weather_code")
        private Integer weatherCode;

        @JsonProperty("wind_speed_10m")
        private Double windSpeed10m;

        @JsonProperty("is_day")
        private Integer isDay;

        private Double visibility;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HourlyWeather {
        private List<String> time;

        @JsonProperty("precipitation_probability")
        private List<Double> precipitationProbability;

        private List<Double> visibility;
    }
}
