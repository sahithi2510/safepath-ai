package com.safepath.backend.service;

import com.safepath.backend.config.WeatherProperties;
import com.safepath.backend.dto.weather.OpenMeteoResponseDto;
import com.safepath.backend.dto.weather.WeatherDto;
import com.safepath.backend.entity.WeatherObservation;
import com.safepath.backend.exception.WeatherClientException;
import com.safepath.backend.repository.WeatherObservationRepository;
import com.safepath.backend.service.weather.OpenMeteoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    @Mock
    private OpenMeteoClient openMeteoClient;

    @Mock
    private WeatherObservationRepository weatherObservationRepository;

    @Spy
    private GeometryFactory geometryFactory = new GeometryFactory();

    @Spy
    private WeatherProperties properties = new WeatherProperties();

    @InjectMocks
    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setCacheTtlMinutes(30);
        weatherService.clearCache();
    }

    @Test
    @DisplayName("getWeather constructs complete WeatherDto separating observation and forecast")
    void testGetWeather_Success() {
        double lat = 12.9716;
        double lon = 77.5946;

        OpenMeteoResponseDto mockResponse = OpenMeteoResponseDto.builder()
                .latitude(12.97)
                .longitude(77.59)
                .current(OpenMeteoResponseDto.CurrentWeather.builder()
                        .time("2026-10-14T12:00")
                        .temperature2m(26.2)
                        .relativeHumidity2m(65.0)
                        .precipitation(1.2)
                        .weatherCode(61) // Slight rain
                        .windSpeed10m(15.0)
                        .isDay(1)
                        .visibility(12000.0) // 12 km
                        .build())
                .hourly(OpenMeteoResponseDto.HourlyWeather.builder()
                        .time(List.of("2026-10-14T12:00", "2026-10-14T13:00"))
                        .precipitationProbability(List.of(75.0, 80.0))
                        .build())
                .build();

        when(openMeteoClient.fetchWeather(lat, lon)).thenReturn(mockResponse);

        WeatherDto result = weatherService.getWeather(lat, lon);

        assertThat(result).isNotNull();
        assertThat(result.isAvailable()).isTrue();
        assertThat(result.isCached()).isFalse();
        assertThat(result.getSource()).contains("Open-Meteo");
        assertThat(result.getDisclaimer()).contains("environmental conditions");

        // Current observation
        WeatherDto.CurrentObservation obs = result.getObservation();
        assertThat(obs).isNotNull();
        assertThat(obs.getTemperatureC()).isEqualTo(26.2);
        assertThat(obs.getPrecipitationMm()).isEqualTo(1.2);
        assertThat(obs.getWindSpeedKmh()).isEqualTo(15.0);
        assertThat(obs.getRelativeHumidity()).isEqualTo(65.0);
        assertThat(obs.getVisibilityKm()).isEqualTo(12.0);
        assertThat(obs.getWeatherCode()).isEqualTo(61);
        assertThat(obs.getCondition()).isEqualTo("Slight rain");
        assertThat(obs.getIcon()).isEqualTo("weather-rainy");
        assertThat(obs.getIsDay()).isTrue();
        assertThat(obs.getObservedAt()).isEqualTo("2026-10-14T12:00");

        // Forecast
        WeatherDto.ForecastContext forecast = result.getForecast();
        assertThat(forecast).isNotNull();
        assertThat(forecast.getPrecipitationProbabilityPercent()).isEqualTo(75.0);
        assertThat(forecast.getForecastTimestamp()).isEqualTo("2026-10-14T12:00");

        // Verify entity was saved to DB for historical provenance
        verify(weatherObservationRepository, times(1)).save(any(WeatherObservation.class));
    }

    @Test
    @DisplayName("getWeather serves from in-memory cache on subsequent requests within TTL")
    void testGetWeather_CacheHit() {
        double lat = 12.9716;
        double lon = 77.5946;

        OpenMeteoResponseDto mockResponse = OpenMeteoResponseDto.builder()
                .current(OpenMeteoResponseDto.CurrentWeather.builder()
                        .time("2026-10-14T12:00")
                        .temperature2m(22.0)
                        .precipitation(0.0)
                        .weatherCode(0)
                        .build())
                .build();

        when(openMeteoClient.fetchWeather(lat, lon)).thenReturn(mockResponse);

        // First call - should query client
        WeatherDto first = weatherService.getWeather(lat, lon);
        assertThat(first.isCached()).isFalse();

        // Second call with identical coordinate - should hit cache
        WeatherDto second = weatherService.getWeather(lat, lon);
        assertThat(second.isCached()).isTrue();
        assertThat(second.getObservation().getTemperatureC()).isEqualTo(22.0);

        // Verify provider client called only once
        verify(openMeteoClient, times(1)).fetchWeather(lat, lon);
    }

    @Test
    @DisplayName("getWeather falls back to recent DB observation when provider fails")
    void testGetWeather_FallbackToDb() {
        double lat = 12.9716;
        double lon = 77.5946;

        when(openMeteoClient.fetchWeather(lat, lon))
                .thenThrow(new WeatherClientException("Gateway Timeout", HttpStatus.GATEWAY_TIMEOUT));

        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        WeatherObservation dbObs = WeatherObservation.builder()
                .latitude(lat)
                .longitude(lon)
                .geometry(point)
                .temperatureC(21.0)
                .precipitationMm(0.1)
                .windSpeedKmh(10.0)
                .weatherCode(2)
                .weatherDescription("Partly cloudy")
                .isNight(false)
                .observedAt(OffsetDateTime.now().minusMinutes(45))
                .source("open-meteo")
                .build();

        when(weatherObservationRepository.findLatestNearby(eq(lon), eq(lat), eq(5000.0)))
                .thenReturn(Optional.of(dbObs));

        WeatherDto result = weatherService.getWeather(lat, lon);

        assertThat(result).isNotNull();
        assertThat(result.isAvailable()).isTrue();
        assertThat(result.isCached()).isTrue();
        assertThat(result.getMessage()).contains("historical database observation");
        assertThat(result.getObservation().getTemperatureC()).isEqualTo(21.0);
        assertThat(result.getObservation().getCondition()).isEqualTo("Partly cloudy");
    }

    @Test
    @DisplayName("getWeather returns honest unavailable state when provider fails and no DB cache exists")
    void testGetWeather_ProviderFailsNoFallback_NeverFabricates() {
        double lat = 12.9716;
        double lon = 77.5946;

        when(openMeteoClient.fetchWeather(lat, lon))
                .thenThrow(new WeatherClientException("Open-Meteo connection refused", HttpStatus.BAD_GATEWAY));
        when(weatherObservationRepository.findLatestNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());

        WeatherDto result = weatherService.getWeather(lat, lon);

        assertThat(result).isNotNull();
        assertThat(result.isAvailable()).isFalse();
        assertThat(result.getObservation()).isNull();
        assertThat(result.getForecast()).isNull();
        assertThat(result.getMessage()).contains("unavailable");
        assertThat(result.getDisclaimer()).isNotEmpty();
    }

    @Test
    @DisplayName("getWeather returns unavailable when integration is disabled in properties")
    void testGetWeather_Disabled() {
        properties.setEnabled(false);

        WeatherDto result = weatherService.getWeather(12.9716, 77.5946);

        assertThat(result).isNotNull();
        assertThat(result.isAvailable()).isFalse();
        assertThat(result.getMessage()).contains("disabled in configuration");
        verify(openMeteoClient, never()).fetchWeather(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("getWeather validates coordinates and throws IllegalArgumentException on invalid input")
    void testGetWeather_InvalidCoordinates() {
        assertThatThrownBy(() -> weatherService.getWeather(91.0, 77.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> weatherService.getWeather(12.0, 185.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
