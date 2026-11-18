package com.safepath.backend.repository;

import com.safepath.backend.entity.CrimeIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CrimeIncidentRepository extends JpaRepository<CrimeIncident, UUID> {

    @Query(value = """
        SELECT c.* FROM crime_incidents c
        WHERE c.latitude IS NOT NULL
          AND c.longitude IS NOT NULL
          AND c.latitude BETWEEN :minLat AND :maxLat
          AND c.longitude BETWEEN :minLon AND :maxLon
        ORDER BY c.severity DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<CrimeIncident> findInBoundingBox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon,
            @Param("limit") int limit);

    @Query(value = """
        SELECT c.* FROM crime_incidents c
        WHERE c.geometry IS NOT NULL
          AND ST_DWithin(
                c.geometry::geography,
                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                :radiusMeters
          )
        ORDER BY c.severity DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<CrimeIncident> findNearby(
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("radiusMeters") double radiusMeters,
            @Param("limit") int limit);
}
