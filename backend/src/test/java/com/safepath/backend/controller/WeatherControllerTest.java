package com.safepath.backend.controller;

import com.safepath.backend.dto.weather.WeatherDto;
import com.safepath.backend.service.WeatherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherControllerTest {

    @Mock
    private WeatherService weatherService;

    @InjectMocks
    private WeatherController weatherController;

    @Test
    @DisplayName("getWeather returns 200 OK with weather DTO for valid coordinates")
    void testGetWeather_Success() {
        double lat = 12.9716;
        double lon = 77.5946;

        WeatherDto mockDto = WeatherDto.builder()
                .latitude(lat)
                .longitude(lon)
                .available(true)
                .observation(WeatherDto.CurrentObservation.builder()
                        .temperatureC(25.0)
                        .condition("Clear sky")
                        .icon("weather-sunny")
                        .build())
                .source("Open-Meteo (open-meteo.com)")
                .disclaimer(WeatherService.WEATHER_DISCLAIMER)
                .build();

        when(weatherService.getWeather(lat, lon)).thenReturn(mockDto);

        ResponseEntity<WeatherDto> response = weatherController.getWeather(lat, lon);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isAvailable()).isTrue();
        assertThat(response.getBody().getObservation().getTemperatureC()).isEqualTo(25.0);
        verify(weatherService).getWeather(lat, lon);
    }

    @Test
    @DisplayName("getWeather returns 200 OK with unavailable response when service indicates provider failure")
    void testGetWeather_Unavailable() {
        double lat = 12.9716;
        double lon = 77.5946;

        WeatherDto unavailableDto = WeatherDto.builder()
                .latitude(lat)
                .longitude(lon)
                .available(false)
                .message("Weather data temporarily unavailable")
                .disclaimer(WeatherService.WEATHER_DISCLAIMER)
                .build();

        when(weatherService.getWeather(lat, lon)).thenReturn(unavailableDto);

        ResponseEntity<WeatherDto> response = weatherController.getWeather(lat, lon);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isAvailable()).isFalse();
        assertThat(response.getBody().getMessage()).contains("unavailable");
    }
}
