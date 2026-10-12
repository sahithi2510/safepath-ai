package com.safepath.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "road_segments")
@Data
@NoArgsConstructor
public class RoadSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "osm_way_id")
    private Long osmWayId;

    @Column(columnDefinition = "geometry(LineString, 4326)", nullable = false)
    private LineString geometry;

    @Column(name = "start_node_id", nullable = false)
    private Long startNodeId;

    @Column(name = "end_node_id", nullable = false)
    private Long endNodeId;

    @Column(name = "start_point", columnDefinition = "geometry(Point, 4326)", nullable = false)
    private Point startPoint;

    @Column(name = "end_point", columnDefinition = "geometry(Point, 4326)", nullable = false)
    private Point endPoint;

    @Column(name = "length_meters", nullable = false)
    private Double lengthMeters;

    @Column(name = "road_type", length = 50)
    private String roadType;

    @Column(name = "road_name")
    private String roadName;

    @Column(name = "is_lit")
    private Boolean isLit;

    @Column(name = "max_speed")
    private Integer maxSpeed;

    @Column(name = "is_oneway", nullable = false)
    private Boolean isOneway = false;

    @Column(nullable = false)
    private Boolean walkable = true;

    @Column(name = "estimated_walk_time_s")
    private Double estimatedWalkTimeS;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
