package com.safepath.backend.service.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safepath.backend.dto.weather.OpenMeteoResponseDto;
import com.safepath.backend.exception.WeatherClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenMeteoClientTest {

    private MockRestServiceServer mockServer;
    private OpenMeteoClient openMeteoClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.open-meteo.com/v1");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        openMeteoClient = new OpenMeteoClientImpl(builder.build());
    }

    @Test
    @DisplayName("fetchWeather parses valid Open-Meteo response structure correctly")
    void testFetchWeather_Success() throws Exception {
        double lat = 12.9716;
        double lon = 77.5946;

        OpenMeteoResponseDto mockResponse = OpenMeteoResponseDto.builder()
                .latitude(12.97)
                .longitude(77.59)
                .current(OpenMeteoResponseDto.CurrentWeather.builder()
                        .time("2026-10-14T12:00")
                        .temperature2m(24.5)
                        .relativeHumidity2m(68.0)
                        .precipitation(0.5)
                        .weatherCode(61)
                        .windSpeed10m(12.3)
                        .isDay(1)
                        .visibility(18500.0)
                        .build())
                .hourly(OpenMeteoResponseDto.HourlyWeather.builder()
                        .time(List.of("2026-10-14T12:00", "2026-10-14T13:00"))
                        .precipitationProbability(List.of(20.0, 45.0))
                        .build())
                .build();

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/forecast?latitude=12.971600&longitude=77.594600")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(mockResponse), MediaType.APPLICATION_JSON));

        OpenMeteoResponseDto result = openMeteoClient.fetchWeather(lat, lon);

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.getCurrent()).isNotNull();
        assertThat(result.getCurrent().getTemperature2m()).isEqualTo(24.5);
        assertThat(result.getCurrent().getPrecipitation()).isEqualTo(0.5);
        assertThat(result.getCurrent().getWeatherCode()).isEqualTo(61);
        assertThat(result.getCurrent().getWindSpeed10m()).isEqualTo(12.3);
        assertThat(result.getCurrent().getVisibility()).isEqualTo(18500.0);
        assertThat(result.getCurrent().getIsDay()).isEqualTo(1);
        assertThat(result.getHourly().getPrecipitationProbability()).containsExactly(20.0, 45.0);
    }

    @Test
    @DisplayName("fetchWeather rejects coordinates out of geographic bounds")
    void testFetchWeather_InvalidCoordinates() {
        assertThatThrownBy(() -> openMeteoClient.fetchWeather(95.0, 77.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude must be between -90.0 and 90.0");

        assertThatThrownBy(() -> openMeteoClient.fetchWeather(-91.0, 77.0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> openMeteoClient.fetchWeather(12.0, 185.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude must be between -180.0 and 180.0");

        assertThatThrownBy(() -> openMeteoClient.fetchWeather(12.0, -185.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fetchWeather maps socket timeout to WeatherClientException with GATEWAY_TIMEOUT")
    void testFetchWeather_Timeout() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/forecast")))
                .andRespond(withException(new java.net.SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> openMeteoClient.fetchWeather(12.9716, 77.5946))
                .isInstanceOf(WeatherClientException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    @DisplayName("fetchWeather maps HTTP 500/503 provider errors to WeatherClientException with BAD_GATEWAY")
    void testFetchWeather_HttpServerError() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/forecast")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> openMeteoClient.fetchWeather(12.9716, 77.5946))
                .isInstanceOf(WeatherClientException.class)
                .hasMessageContaining("HTTP error: 500");
    }

    @Test
    @DisplayName("fetchWeather throws WeatherClientException when current weather block is missing")
    void testFetchWeather_MissingCurrentBlock() throws Exception {
        OpenMeteoResponseDto emptyResponse = OpenMeteoResponseDto.builder()
                .latitude(12.97)
                .longitude(77.59)
                .current(null)
                .build();

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/forecast")))
                .andRespond(withSuccess(objectMapper.writeValueAsString(emptyResponse), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> openMeteoClient.fetchWeather(12.9716, 77.5946))
                .isInstanceOf(WeatherClientException.class)
                .hasMessageContaining("missing current weather");
    }
}
