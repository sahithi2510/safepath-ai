package com.safepath.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Weather observation entity.
 * Records ambient weather conditions (observed or provider-interpolated) for a geographic location.
 * Provides historical provenance and offline caching fallback.
 */
@Entity
@Table(name = "weather_observations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeatherObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point geometry;

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;

    @Column(name = "temperature_c")
    private Double temperatureC;

    @Column(name = "precipitation_mm")
    private Double precipitationMm;

    @Column(name = "visibility_km")
    private Double visibilityKm;

    @Column(name = "weather_code")
    private Integer weatherCode;

    @Column(name = "is_night")
    private Boolean isNight;

    @Column(name = "wind_speed_kmh")
    private Double windSpeedKmh;

    @Column(name = "relative_humidity")
    private Double relativeHumidity;

    @Column(name = "weather_description", length = 100)
    private String weatherDescription;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String source = "open-meteo";

    @CreationTimestamp
    @Column(name = "fetched_at", nullable = false, updatable = false)
    private OffsetDateTime fetchedAt;
}
