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
 *
 * All queries preserve coordinate precision and geometry reference system (SRID 4326).
 */
@Repository
public interface SafeZoneRepository extends JpaRepository<SafeZone, UUID> {

    /**
     * Find safe zones within a given radius using PostGIS ST_DWithin on geography.
     *
     * @param lon longitude of search center
     * @param lat latitude of search center
     * @param radiusMeters search radius in meters
     * @param limit max results
     * @return safe zones ordered by distance ascending
     */
    @Query(value = """
        SELECT sz.*
        FROM safe_zones sz
        WHERE sz.geometry IS NOT NULL
        AND ST_DWithin(
            sz.geometry::geography,
            ST_MakePoint(:lon, :lat)::geography,
            :radiusMeters
        )
        ORDER BY ST_Distance(sz.geometry::geography, ST_MakePoint(:lon, :lat)::geography) ASC
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
        SELECT sz.*
        FROM safe_zones sz
        WHERE sz.geometry IS NOT NULL
        AND sz.zone_type IN (:types)
        AND ST_DWithin(
            sz.geometry::geography,
            ST_MakePoint(:lon, :lat)::geography,
            :radiusMeters
        )
        ORDER BY ST_Distance(sz.geometry::geography, ST_MakePoint(:lon, :lat)::geography) ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<SafeZone> findNearbyByTypes(
            @Param("lon") double lon,
            @Param("lat") double lat,
            @Param("radiusMeters") double radiusMeters,
            @Param("types") List<String> types,
            @Param("limit") int limit);

    /**
     * Find safe zones within a geographic bounding box envelope.
     *
     * @param minLon Western longitude
     * @param minLat Southern latitude
     * @param maxLon Eastern longitude
     * @param maxLat Northern latitude
     * @param limit maximum results
     * @return safe zones in the viewport
     */
    @Query(value = """
        SELECT sz.*
        FROM safe_zones sz
        WHERE sz.geometry IS NOT NULL
        AND ST_Intersects(sz.geometry, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))
        ORDER BY sz.name ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<SafeZone> findInBoundingBox(
            @Param("minLon") double minLon,
            @Param("minLat") double minLat,
            @Param("maxLon") double maxLon,
            @Param("maxLat") double maxLat,
            @Param("limit") int limit);

    /**
     * Find safe zones of specific types within a geographic bounding box envelope.
     */
    @Query(value = """
        SELECT sz.*
        FROM safe_zones sz
        WHERE sz.geometry IS NOT NULL
        AND sz.zone_type IN (:types)
        AND ST_Intersects(sz.geometry, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))
        ORDER BY sz.name ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<SafeZone> findInBoundingBoxByTypes(
            @Param("minLon") double minLon,
            @Param("minLat") double minLat,
            @Param("maxLon") double maxLon,
            @Param("maxLat") double maxLat,
            @Param("types") List<String> types,
            @Param("limit") int limit);
}
