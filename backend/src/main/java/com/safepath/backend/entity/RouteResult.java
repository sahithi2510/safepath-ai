package com.safepath.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.LineString;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "route_results")
@Data
@NoArgsConstructor
public class RouteResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private RouteRequest routeRequest;

    @Column(name = "route_line", columnDefinition = "geometry(LineString, 4326)", nullable = false)
    private LineString routeLine;

    @Column(name = "total_distance_meters")
    private Double totalDistanceMeters;

    @Column(name = "estimated_duration_seconds")
    private Double estimatedDurationSeconds;

    @Column(name = "safety_score")
    private Double safetyScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
