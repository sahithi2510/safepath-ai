package com.safepath.backend.repository;

import com.safepath.backend.entity.WeatherObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for WeatherObservation entities with PostGIS spatial queries.
 */
@Repository
public interface WeatherObservationRepository extends JpaRepository<WeatherObservation, UUID> {

    /**
     * Finds the most recent weather observation recorded within a spatial radius.
     *
     * @param lon longitude of center
     * @param lat latitude of center
     * @param radiusMeters search radius in meters
     * @return latest nearby observation if available
     */
    @Query(value = """
        SELECT wo.*
        FROM weather_observations wo
        WHERE wo.geometry IS NOT NULL
        AND ST_DWithin(
            wo.geometry::geography,
            ST_MakePoint(:lon, :lat)::geography,
            :radiusMeters
        )
        ORDER BY wo.observed_at DESC, ST_Distance(wo.geometry::geography, ST_MakePoint(:lon, :lat)::geography) ASC
        LIMIT 1
        """, nativeQuery = true)
    Optional<WeatherObservation> findLatestNearby(
            @Param("lon") double lon,
            @Param("lat") double lat,
            @Param("radiusMeters") double radiusMeters);
}
