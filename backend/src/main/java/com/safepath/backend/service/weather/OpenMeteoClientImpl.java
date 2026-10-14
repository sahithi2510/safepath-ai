package com.safepath.backend.service.weather;

import com.safepath.backend.dto.weather.OpenMeteoResponseDto;
import com.safepath.backend.exception.WeatherClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;

/**
 * REST client implementation querying the Open-Meteo weather API.
 * Uses timeouts configured in WeatherProperties and performs input coordinate validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenMeteoClientImpl implements OpenMeteoClient {

    private final RestClient weatherRestClient;

    @Override
    public OpenMeteoResponseDto fetchWeather(double latitude, double longitude) {
        validateCoordinates(latitude, longitude);

        log.debug("Fetching Open-Meteo weather for lat={}, lon={}", latitude, longitude);

        try {
            OpenMeteoResponseDto response = weatherRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/forecast")
                            .queryParam("latitude", String.format(Locale.ROOT, "%.6f", latitude))
                            .queryParam("longitude", String.format(Locale.ROOT, "%.6f", longitude))
                            .queryParam("current", "temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,is_day,visibility")
                            .queryParam("hourly", "precipitation_probability")
                            .queryParam("forecast_days", "1")
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(OpenMeteoResponseDto.class);

            if (response == null || response.getCurrent() == null) {
                log.warn("Open-Meteo returned null response or missing current weather for lat={}, lon={}", latitude, longitude);
                throw new WeatherClientException("Open-Meteo returned null or missing current weather observation.", HttpStatus.BAD_GATEWAY);
            }

            return response;

        } catch (ResourceAccessException e) {
            log.error("Timeout or connection failure communicating with Open-Meteo: {}", e.getMessage());
            throw new WeatherClientException("Open-Meteo weather provider connection timed out or unreachable.", e, HttpStatus.GATEWAY_TIMEOUT);
        } catch (RestClientResponseException e) {
            log.error("Open-Meteo returned HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new WeatherClientException("Open-Meteo returned HTTP error: " + e.getStatusCode(), e, HttpStatus.BAD_GATEWAY);
        } catch (WeatherClientException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error querying Open-Meteo: {}", e.getMessage(), e);
            throw new WeatherClientException("Unexpected error communicating with Open-Meteo weather service.", e, HttpStatus.BAD_GATEWAY);
        }
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90.0 and 90.0. Given: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180.0 and 180.0. Given: " + longitude);
        }
    }
}
