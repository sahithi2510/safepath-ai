package com.safepath.backend.repository;

import com.safepath.backend.entity.CommunityReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for CommunityReport entities.
 * Includes spatial queries using PostGIS.
 */
@Repository
public interface CommunityReportRepository extends JpaRepository<CommunityReport, UUID> {

    /** Get reports by a specific user (all statuses). */
    Page<CommunityReport> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find VERIFIED reports near a location within a given radius.
     *
     * Uses PostGIS ST_DWithin for spatial filtering.
     * Only VERIFIED reports are returned — see docs/PROJECT-CONTRACT.md Section 7.
     *
     * @param lon longitude
     * @param lat latitude
     * @param radiusMeters search radius in meters
     * @param limit maximum results
     * @return list of nearby verified reports
     */
    @Query(value = """
        SELECT * FROM community_reports
        WHERE status = 'VERIFIED'
        AND geometry IS NOT NULL
        AND ST_DWithin(
            geometry::geography,
            ST_MakePoint(:lon, :lat)::geography,
            :radiusMeters
        )
        ORDER BY ST_Distance(
            geometry::geography,
            ST_MakePoint(:lon, :lat)::geography
        ) ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<CommunityReport> findVerifiedNearby(
            @Param("lon") double lon,
            @Param("lat") double lat,
            @Param("radiusMeters") double radiusMeters,
            @Param("limit") int limit);

    /** Count reports by status. */
    long countByStatus(CommunityReport.ReportStatus status);
}
