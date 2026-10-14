package com.safepath.backend.dto.weather;

/**
 * World Meteorological Organization (WMO) Weather Interpretation Codes (WW).
 * Standardized mapping to human-readable weather descriptions and icon identifiers.
 */
public final class WmoWeatherCode {

    private WmoWeatherCode() {}

    public static String getDescription(Integer code) {
        if (code == null) {
            return "Unknown";
        }
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45 -> "Fog";
            case 48 -> "Depositing rime fog";
            case 51 -> "Light drizzle";
            case 53 -> "Moderate drizzle";
            case 55 -> "Dense drizzle";
            case 56 -> "Light freezing drizzle";
            case 57 -> "Dense freezing drizzle";
            case 61 -> "Slight rain";
            case 63 -> "Moderate rain";
            case 65 -> "Heavy rain";
            case 66 -> "Light freezing rain";
            case 67 -> "Heavy freezing rain";
            case 71 -> "Slight snow fall";
            case 73 -> "Moderate snow fall";
            case 75 -> "Heavy snow fall";
            case 77 -> "Snow grains";
            case 80 -> "Slight rain showers";
            case 81 -> "Moderate rain showers";
            case 82 -> "Violent rain showers";
            case 85 -> "Slight snow showers";
            case 86 -> "Heavy snow showers";
            case 95 -> "Thunderstorm";
            case 96 -> "Thunderstorm with slight hail";
            case 99 -> "Thunderstorm with heavy hail";
            default -> "Weather code " + code;
        };
    }

    public static String getIconName(Integer code, Boolean isDay) {
        boolean day = isDay == null || isDay;
        if (code == null) {
            return day ? "weather-cloudy" : "weather-night";
        }
        return switch (code) {
            case 0 -> day ? "weather-sunny" : "weather-night";
            case 1, 2 -> day ? "weather-partly-cloudy" : "weather-night-partly-cloudy";
            case 3 -> "weather-cloudy";
            case 45, 48 -> "weather-fog";
            case 51, 53, 55, 56, 57 -> "weather-rainy";
            case 61, 63 -> "weather-rainy";
            case 65 -> "weather-pouring";
            case 66, 67, 71, 73, 75, 77 -> "weather-snowy";
            case 80, 81 -> "weather-rainy";
            case 82 -> "weather-pouring";
            case 85, 86 -> "weather-snowy-heavy";
            case 95, 96, 99 -> "weather-lightning";
            default -> day ? "weather-sunny" : "weather-night";
        };
    }
}
