package com.safepath.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "route_requests")
@Data
@NoArgsConstructor
public class RouteRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "start_point", columnDefinition = "geometry(Point, 4326)", nullable = false)
    private Point startPoint;

    @Column(name = "end_point", columnDefinition = "geometry(Point, 4326)", nullable = false)
    private Point endPoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "preference", nullable = false)
    private RoutePreference preference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
