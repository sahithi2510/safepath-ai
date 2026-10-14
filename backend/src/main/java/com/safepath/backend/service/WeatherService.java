package com.safepath.backend.service;

import com.safepath.backend.config.WeatherProperties;
import com.safepath.backend.dto.weather.OpenMeteoResponseDto;
import com.safepath.backend.dto.weather.WeatherDto;
import com.safepath.backend.dto.weather.WmoWeatherCode;
import com.safepath.backend.entity.WeatherObservation;
import com.safepath.backend.repository.WeatherObservationRepository;
import com.safepath.backend.service.weather.OpenMeteoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for retrieving ambient weather conditions.
 *
 * Implements:
 * - In-memory caching with configurable TTL to respect provider request bounds.
 * - Honest distinction between current observations and forecast values.
 * - Non-fatal fallback to historical DB observations when provider is unavailable.
 * - Never fabricates weather measurements.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherService {

    private final OpenMeteoClient openMeteoClient;
    private final WeatherObservationRepository weatherObservationRepository;
    private final GeometryFactory geometryFactory;
    private final WeatherProperties properties;

    public static final String WEATHER_DISCLAIMER =
            "Weather context represents ambient environmental conditions (temperature, precipitation, wind, visibility) from Open-Meteo. " +
            "It does not measure personal safety or crime risk directly.";

    private final Map<String, CachedWeatherEntry> cache = new ConcurrentHashMap<>();

    private record CachedWeatherEntry(WeatherDto dto, Instant expiresAt) {}

    /**
     * Retrieves weather context for a validated geographic coordinate.
     *
     * @param latitude latitude in degrees [-90.0, 90.0]
     * @param longitude longitude in degrees [-180.0, 180.0]
     * @return WeatherDto containing observation, forecast, units, and attribution
     */
    public WeatherDto getWeather(double latitude, double longitude) {
        validateCoordinates(latitude, longitude);

        String cacheKey = toCacheKey(latitude, longitude);
        Instant now = Instant.now();

        // 1. Check in-memory cache
        CachedWeatherEntry cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            log.debug("Returning cached weather for key: {}", cacheKey);
            WeatherDto response = copyDto(cached.dto());
            response.setCached(true);
            return response;
        }

        // 2. If weather integration is disabled, return explicit unavailable response
        if (!properties.isEnabled()) {
            log.debug("Weather integration disabled in configuration.");
            return buildUnavailableResponse(latitude, longitude, "Weather integration is disabled in configuration.");
        }

        // 3. Attempt live query from provider
        try {
            OpenMeteoResponseDto providerResponse = openMeteoClient.fetchWeather(latitude, longitude);
            WeatherDto dto = mapToWeatherDto(latitude, longitude, providerResponse);

            // Cache live result
            int ttlMinutes = Math.max(1, properties.getCacheTtlMinutes());
            cache.put(cacheKey, new CachedWeatherEntry(dto, now.plus(ttlMinutes, ChronoUnit.MINUTES)));

            // Asynchronously / safely persist observation to DB for provenance & fallback
            persistObservationSafely(latitude, longitude, dto);

            return dto;

        } catch (Exception e) {
            log.warn("Failed to retrieve live weather for lat={}, lon={}: {}. Attempting fallback.",
                    latitude, longitude, e.getMessage());

            // 4. Fallback 1: Return expired cached entry if available
            if (cached != null) {
                log.info("Serving stale cached weather as fallback for key: {}", cacheKey);
                WeatherDto stale = copyDto(cached.dto());
                stale.setCached(true);
                stale.setMessage("Live provider unreachable; serving previously cached observation.");
                return stale;
            }

            // 5. Fallback 2: Check database for recent observation within 5km
            try {
                Optional<WeatherObservation> dbObservation =
                        weatherObservationRepository.findLatestNearby(longitude, latitude, 5000.0);

                if (dbObservation.isPresent()) {
                    WeatherObservation obs = dbObservation.get();
                    if (obs.getObservedAt() != null &&
                            obs.getObservedAt().isAfter(OffsetDateTime.now().minusHours(3))) {
                        log.info("Serving recent DB observation from {} as fallback", obs.getObservedAt());
                        return mapDbToWeatherDto(latitude, longitude, obs);
                    }
                }
            } catch (Exception dbEx) {
                log.warn("DB weather fallback query failed: {}", dbEx.getMessage());
            }

            // 6. Final fallback: Honest unavailable response without fabricated measurements
            return buildUnavailableResponse(latitude, longitude,
                    "Weather data temporarily unavailable from provider.");
        }
    }

    /**
     * Clears in-memory weather cache (primarily for testing and cache invalidation).
     */
    public void clearCache() {
        cache.clear();
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90.0 and 90.0. Given: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180.0 and 180.0. Given: " + longitude);
        }
    }

    private String toCacheKey(double latitude, double longitude) {
        // Quantize coordinates to 2 decimal places (~1.1 km spatial resolution)
        return String.format(Locale.ROOT, "%.2f,%.2f", latitude, longitude);
    }

    private WeatherDto mapToWeatherDto(double lat, double lon, OpenMeteoResponseDto providerResponse) {
        OpenMeteoResponseDto.CurrentWeather current = providerResponse.getCurrent();

        Double temp = current.getTemperature2m();
        Double precip = current.getPrecipitation();
        Double wind = current.getWindSpeed10m();
        Double humidity = current.getRelativeHumidity2m();
        Double visibilityMeters = current.getVisibility();
        Double visibilityKm = visibilityMeters != null ? Math.round((visibilityMeters / 1000.0) * 10.0) / 10.0 : null;
        Integer code = current.getWeatherCode();
        Boolean isDay = current.getIsDay() != null ? current.getIsDay() == 1 : null;
        String condition = WmoWeatherCode.getDescription(code);
        String icon = WmoWeatherCode.getIconName(code, isDay);
        String observedAt = current.getTime();

        WeatherDto.CurrentObservation observation = WeatherDto.CurrentObservation.builder()
                .temperatureC(temp)
                .precipitationMm(precip)
                .windSpeedKmh(wind)
                .relativeHumidity(humidity)
                .visibilityKm(visibilityKm)
                .weatherCode(code)
                .condition(condition)
                .icon(icon)
                .isDay(isDay)
                .observedAt(observedAt)
                .build();

        WeatherDto.ForecastContext forecast = null;
        if (providerResponse.getHourly() != null &&
                providerResponse.getHourly().getPrecipitationProbability() != null &&
                !providerResponse.getHourly().getPrecipitationProbability().isEmpty()) {

            Double precipProb = providerResponse.getHourly().getPrecipitationProbability().get(0);
            String forecastTime = null;
            if (providerResponse.getHourly().getTime() != null &&
                    !providerResponse.getHourly().getTime().isEmpty()) {
                forecastTime = providerResponse.getHourly().getTime().get(0);
            }

            forecast = WeatherDto.ForecastContext.builder()
                    .precipitationProbabilityPercent(precipProb)
                    .forecastTimestamp(forecastTime)
                    .build();
        }

        return WeatherDto.builder()
                .latitude(lat)
                .longitude(lon)
                .available(true)
                .observation(observation)
                .forecast(forecast)
                .units(WeatherDto.WeatherUnits.builder().build())
                .source("Open-Meteo (open-meteo.com)")
                .retrievedAt(OffsetDateTime.now())
                .cached(false)
                .disclaimer(WEATHER_DISCLAIMER)
                .build();
    }

    private WeatherDto mapDbToWeatherDto(double lat, double lon, WeatherObservation obs) {
        String condition = obs.getWeatherDescription() != null
                ? obs.getWeatherDescription()
                : WmoWeatherCode.getDescription(obs.getWeatherCode());

        Boolean isDay = obs.getIsNight() != null ? !obs.getIsNight() : null;
        String icon = WmoWeatherCode.getIconName(obs.getWeatherCode(), isDay);

        WeatherDto.CurrentObservation observation = WeatherDto.CurrentObservation.builder()
                .temperatureC(obs.getTemperatureC())
                .precipitationMm(obs.getPrecipitationMm())
                .windSpeedKmh(obs.getWindSpeedKmh())
                .relativeHumidity(obs.getRelativeHumidity())
                .visibilityKm(obs.getVisibilityKm())
                .weatherCode(obs.getWeatherCode())
                .condition(condition)
                .icon(icon)
                .isDay(isDay)
                .observedAt(obs.getObservedAt() != null ? obs.getObservedAt().toString() : null)
                .build();

        return WeatherDto.builder()
                .latitude(lat)
                .longitude(lon)
                .available(true)
                .observation(observation)
                .units(WeatherDto.WeatherUnits.builder().build())
                .source(obs.getSource() != null ? obs.getSource() : "open-meteo")
                .retrievedAt(obs.getFetchedAt() != null ? obs.getFetchedAt() : OffsetDateTime.now())
                .cached(true)
                .message("Live provider unreachable; serving historical database observation.")
                .disclaimer(WEATHER_DISCLAIMER)
                .build();
    }

    private WeatherDto buildUnavailableResponse(double lat, double lon, String message) {
        return WeatherDto.builder()
                .latitude(lat)
                .longitude(lon)
                .available(false)
                .message(message)
                .disclaimer(WEATHER_DISCLAIMER)
                .source("Open-Meteo (open-meteo.com)")
                .retrievedAt(OffsetDateTime.now())
                .units(WeatherDto.WeatherUnits.builder().build())
                .build();
    }

    private WeatherDto copyDto(WeatherDto src) {
        if (src == null) return null;
        return WeatherDto.builder()
                .latitude(src.getLatitude())
                .longitude(src.getLongitude())
                .available(src.isAvailable())
                .observation(src.getObservation())
                .forecast(src.getForecast())
                .units(src.getUnits())
                .source(src.getSource())
                .retrievedAt(src.getRetrievedAt())
                .cached(src.isCached())
                .disclaimer(src.getDisclaimer())
                .message(src.getMessage())
                .build();
    }

    private void persistObservationSafely(double lat, double lon, WeatherDto dto) {
        if (dto == null || dto.getObservation() == null) {
            return;
        }
        try {
            WeatherDto.CurrentObservation obsDto = dto.getObservation();
            OffsetDateTime observedAt = parseObservedTime(obsDto.getObservedAt());

            WeatherObservation entity = WeatherObservation.builder()
                    .latitude(lat)
                    .longitude(lon)
                    .geometry(geometryFactory.createPoint(new Coordinate(lon, lat)))
                    .observedAt(observedAt)
                    .temperatureC(obsDto.getTemperatureC())
                    .precipitationMm(obsDto.getPrecipitationMm())
                    .windSpeedKmh(obsDto.getWindSpeedKmh())
                    .relativeHumidity(obsDto.getRelativeHumidity())
                    .visibilityKm(obsDto.getVisibilityKm())
                    .weatherCode(obsDto.getWeatherCode())
                    .isNight(obsDto.getIsDay() != null ? !obsDto.getIsDay() : false)
                    .weatherDescription(obsDto.getCondition())
                    .source("open-meteo")
                    .build();

            weatherObservationRepository.save(entity);
            log.debug("Persisted weather observation for lat={}, lon={}", lat, lon);
        } catch (Exception e) {
            log.warn("Could not persist weather observation to database (ignoring non-critical persistence failure): {}",
                    e.getMessage());
        }
    }

    private OffsetDateTime parseObservedTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        try {
            if (timeStr.length() == 16) {
                // "YYYY-MM-DDTHH:MM" format
                return OffsetDateTime.parse(timeStr + ":00Z");
            }
            return OffsetDateTime.parse(timeStr);
        } catch (Exception e) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
