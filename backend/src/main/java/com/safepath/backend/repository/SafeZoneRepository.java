package com.safepath.backend.repository;

import com.safepath.backend.entity.SafeZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for SafeZone entities with PostGIS spatial queries.
 */
@Repository
public interface SafeZoneRepository extends JpaRepository<SafeZone, UUID> {

    /**
     * Find safe zones within a given radius using PostGIS ST_DWithin.
     *
     * @param lon longitude of search center
     * @param lat latitude of search center
     * @param radiusMeters search radius in meters
     * @param limit max results
     * @return safe zones ordered by distance
     */
    @Query(value = """
        SELECT *, ST_Distance(
            geometry::geography,
            ST_MakePoint(:lon, :lat)::geography
        ) AS distance_m
        FROM safe_zones
        WHERE geometry IS NOT NULL
        AND ST_DWithin(
            geometry::geography,
            ST_MakePoint(:lon, :lat)::geography,
            :radiusMeters
        )
        ORDER BY distance_m ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<SafeZone> findNearby(
            @Param("lon") double lon,
            @Param("lat") double lat,
            @Param("radiusMeters") double radiusMeters,
            @Param("limit") int limit);

    /**
     * Find safe zones of specific types within a given radius.
     */
    @Query(value = """
        SELECT *, ST_Distance(
            geometry::geography,
            ST_MakePoint(:lon, :lat)::geography
        ) AS distance_m
        FROM safe_zones
        WHERE geometry IS NOT NULL
        AND zone_type IN (:types)
        AND ST_DWithin(
            geometry::geography,
            ST_MakePoint(:lon, :lat)::geography,
            :radiusMeters
        )
        ORDER BY distance_m ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<SafeZone> findNearbyByTypes(
            @Param("lon") double lon,
            @Param("lat") double lat,
            @Param("radiusMeters") double radiusMeters,
            @Param("types") List<String> types,
            @Param("limit") int limit);
}
