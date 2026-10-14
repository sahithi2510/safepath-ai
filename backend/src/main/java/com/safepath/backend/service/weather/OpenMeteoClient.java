package com.safepath.backend.service.weather;

import com.safepath.backend.dto.weather.OpenMeteoResponseDto;

/**
 * Client interface for fetching weather data from Open-Meteo REST API.
 */
public interface OpenMeteoClient {

    /**
     * Fetches current weather and hourly precipitation probability for a given coordinate.
     *
     * @param latitude latitude in degrees [-90, 90]
     * @param longitude longitude in degrees [-180, 180]
     * @return provider response DTO
     */
    OpenMeteoResponseDto fetchWeather(double latitude, double longitude);
}
