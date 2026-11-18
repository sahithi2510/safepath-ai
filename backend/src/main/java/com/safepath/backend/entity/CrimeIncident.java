package com.safepath.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * CrimeIncident entity.
 * Maps directly to the PostGIS crime_incidents table.
 * Governed strictly by AGENTS.md §2 (Data Integrity).
 */
@Entity
@Table(name = "crime_incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrimeIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "crime_type", nullable = false, length = 100)
    private String crimeType;

    @Column(name = "crime_subtype", length = 100)
    private String crimeSubtype;

    @Column(nullable = false)
    private Integer severity;

    @Column(name = "incident_datetime")
    private OffsetDateTime incidentDatetime;

    @Column(name = "incident_date")
    private LocalDate incidentDate;

    @Column(nullable = false, length = 255)
    private String source;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geometry;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_precision", nullable = false)
    private LocationPrecision locationPrecision;

    @Column(name = "location_confidence")
    private Float locationConfidence;

    @Column(name = "original_address", columnDefinition = "TEXT")
    private String originalAddress;

    @Column(length = 255)
    private String district;

    @Column(length = 255)
    private String locality;

    @Column(name = "police_station_name", length = 255)
    private String policeStationName;

    @Column(length = 255)
    private String city;

    @Column(length = 255)
    private String state;

    @Column(length = 100)
    @Builder.Default
    private String country = "India";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public enum LocationPrecision {
        EXACT_COORDINATE,
        ADDRESS_GEOCODED,
        LOCALITY,
        POLICE_STATION,
        DISTRICT,
        UNKNOWN
    }
}
