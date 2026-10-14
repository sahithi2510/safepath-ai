package com.safepath.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Community safety report submitted by a user.
 *
 * Report states: PENDING → VERIFIED | REJECTED
 *
 * IMPORTANT: Unverified reports (PENDING) must NOT directly influence
 * the safety scoring model. Only VERIFIED reports may contribute.
 * See docs/PROJECT-CONTRACT.md, Section 7.
 */
@Entity
@Table(name = "community_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 50)
    private ReportType reportType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    /**
     * PostGIS Point geometry.
     * SRID 4326 (WGS84).
     */
    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geometry;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_precision", length = 30)
    @Builder.Default
    private LocationPrecision locationPrecision = LocationPrecision.UNKNOWN;

    @Column(name = "road_segment_id")
    private UUID roadSegmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "verification_note", columnDefinition = "TEXT")
    private String verificationNote;

    @Column(name = "incident_datetime")
    private OffsetDateTime incidentDatetime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public boolean hasLocation() {
        return latitude != null && longitude != null && geometry != null;
    }

    public enum ReportType {
        HARASSMENT,
        SUSPICIOUS_ACTIVITY,
        POOR_LIGHTING,
        UNSAFE_ROAD,
        BROKEN_STREETLIGHT,
        CROWD_ISSUE,
        ROAD_BLOCKAGE,
        OTHER
    }

    public enum ReportStatus {
        PENDING,
        VERIFIED,
        REJECTED
    }

    public enum LocationPrecision {
        EXACT_COORDINATE,
        LOCALITY,
        UNKNOWN
    }
}
