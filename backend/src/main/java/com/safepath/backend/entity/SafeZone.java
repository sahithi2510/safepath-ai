package com.safepath.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Safe zone entity.
 * Represents police stations, hospitals, pharmacies, public transport stops,
 * and other safety-relevant facilities.
 *
 * Data sourced from OpenStreetMap where possible.
 * Do NOT fabricate safe zone locations.
 * See docs/PROJECT-CONTRACT.md.
 */
@Entity
@Table(name = "safe_zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SafeZone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 50)
    private ZoneType zoneType;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /**
     * PostGIS Point geometry (SRID 4326).
     * Used for ST_DWithin spatial queries.
     */
    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geometry;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "operating_hours", length = 255)
    private String operatingHours;

    @Column(name = "osm_node_id")
    private Long osmNodeId;

    @Column(name = "is_24h")
    private Boolean is24h;

    /** Source of this data (osm, manual, etc.) */
    @Column(length = 100)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum ZoneType {
        POLICE_STATION,
        HOSPITAL,
        PHARMACY,
        PUBLIC_TRANSPORT,
        OTHER
    }

    public enum VerificationStatus {
        VERIFIED,
        UNVERIFIED,
        UNAVAILABLE
    }
}
