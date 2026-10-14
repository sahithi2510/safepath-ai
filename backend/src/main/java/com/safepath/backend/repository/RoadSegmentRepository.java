package com.safepath.backend.repository;

import com.safepath.backend.entity.RoadSegment;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoadSegmentRepository extends JpaRepository<RoadSegment, UUID> {

    @Query(value = "SELECT rs.* FROM road_segments rs WHERE ST_DWithin(rs.geometry::geography, :location::geography, :radiusMeters) AND rs.walkable = true", nativeQuery = true)
    List<RoadSegment> findWalkableSegmentsWithinRadius(@Param("location") Point location, @Param("radiusMeters") double radiusMeters);

    @Query(value = "SELECT rs.* FROM road_segments rs ORDER BY rs.geometry <-> :location LIMIT 1", nativeQuery = true)
    RoadSegment findNearestSegment(@Param("location") Point location);

    @Query(value = "SELECT rs.* FROM road_segments rs WHERE ST_Intersects(rs.geometry, :bbox) AND rs.walkable = true LIMIT :limit", nativeQuery = true)
    List<RoadSegment> findSegmentsInBoundingBox(@Param("bbox") Geometry bbox, @Param("limit") int limit);
}

