package com.safepath.backend.service;

import com.safepath.backend.dto.HeatmapDto;
import com.safepath.backend.dto.RouteDto;
import com.safepath.backend.dto.ml.PredictRiskRequestDto;
import com.safepath.backend.dto.ml.PredictRiskResponseDto;
import com.safepath.backend.entity.RoadSegment;
import com.safepath.backend.repository.RoadSegmentRepository;
import com.safepath.backend.service.ml.MlServiceClient;
import com.safepath.backend.service.ml.RoadSegmentFeatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service providing road-segment-level safety heatmap data.
 *
 * Enforces geographic bounding box limits, transparent data provenance,
 * distinct UNSCORED states on missing or offline ML predictions, and
 * non-probabilistic safety score representations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HeatmapService {

    public static final double MAX_BBOX_SPAN_DEGREES = 0.15; // Approx 16.5 km span
    public static final int DEFAULT_SEGMENT_LIMIT = 500;
    public static final int MAX_SEGMENT_LIMIT = 1000;

    private static final String HEATMAP_DISCLAIMER =
            "Street segment risk scores are relative model estimates derived from spatial/temporal features. " +
            "They do NOT represent verified physical probabilities of crime occurrence, nor does low risk guarantee safety.";

    private final RoadSegmentRepository roadSegmentRepository;
    private final RoadSegmentFeatureService roadSegmentFeatureService;
    private final MlServiceClient mlServiceClient;
    private final GeometryFactory geometryFactory;

    /**
     * Retrieves road segments within the specified geographic bounding box,
     * augmented with relative-risk ML predictions and color encodings.
     */
    @Transactional(readOnly = true)
    public HeatmapDto.Response getHeatmap(Double minLat, Double minLon, Double maxLat, Double maxLon, Integer limit) {
        validateBoundingBox(minLat, minLon, maxLat, maxLon);

        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, MAX_SEGMENT_LIMIT) : DEFAULT_SEGMENT_LIMIT;

        Coordinate[] ring = new Coordinate[]{
                new Coordinate(minLon, minLat),
                new Coordinate(maxLon, minLat),
                new Coordinate(maxLon, maxLat),
                new Coordinate(minLon, maxLat),
                new Coordinate(minLon, minLat)
        };
        Polygon bboxPolygon = geometryFactory.createPolygon(ring);
        bboxPolygon.setSRID(4326);

        List<RoadSegment> segments = roadSegmentRepository.findSegmentsInBoundingBox(bboxPolygon, effectiveLimit);
        String bboxStr = String.format("[%.4f, %.4f, %.4f, %.4f]", minLat, minLon, maxLat, maxLon);

        if (segments == null || segments.isEmpty()) {
            return HeatmapDto.Response.builder()
                    .boundingBox(bboxStr)
                    .totalSegments(0)
                    .scoredSegments(0)
                    .unscoredSegments(0)
                    .segments(Collections.emptyList())
                    .mlServiceAvailable(true)
                    .isSyntheticDemonstration(null)
                    .modelVersion(null)
                    .timestamp(OffsetDateTime.now())
                    .disclaimer(HEATMAP_DISCLAIMER)
                    .build();
        }

        // Fetch ML risk predictions for segments in batch
        Map<String, PredictRiskResponseDto> predictionsMap = Collections.emptyMap();
        boolean mlServiceAvailable = false;
        Boolean isSyntheticDemonstration = null;
        String modelVersion = null;

        try {
            List<PredictRiskRequestDto> batchRequests = roadSegmentFeatureService.prepareBatchRequests(segments, LocalDateTime.now());
            predictionsMap = mlServiceClient.predictRiskBatch(batchRequests);

            for (PredictRiskResponseDto pred : predictionsMap.values()) {
                if (pred != null) {
                    if (pred.getIsSyntheticDemonstration() != null) {
                        isSyntheticDemonstration = pred.getIsSyntheticDemonstration();
                    }
                    if (pred.getModelVersion() != null) {
                        modelVersion = pred.getModelVersion();
                    }
                }
            }
            mlServiceAvailable = !predictionsMap.isEmpty();
        } catch (Exception e) {
            log.warn("ML risk predictions unavailable for safety heatmap: {}. Segments will be returned as UNSCORED.", e.getMessage());
            mlServiceAvailable = false;
        }

        List<HeatmapDto.SegmentRisk> segmentRisks = new ArrayList<>();
        int scoredCount = 0;
        int unscoredCount = 0;

        for (RoadSegment segment : segments) {
            String segKey = roadSegmentFeatureService.resolveSegmentId(segment);
            PredictRiskResponseDto pred = predictionsMap.get(segKey);

            // Extract coordinates and GeoJSON
            List<RouteDto.CoordinateDto> coordList = new ArrayList<>();
            List<List<Double>> geoJsonCoords = new ArrayList<>();
            if (segment.getGeometry() != null) {
                Coordinate[] coords = segment.getGeometry().getCoordinates();
                for (Coordinate c : coords) {
                    coordList.add(RouteDto.CoordinateDto.builder()
                            .latitude(c.getY())
                            .longitude(c.getX())
                            .build());
                    geoJsonCoords.add(List.of(c.getX(), c.getY())); // GeoJSON is [lon, lat]
                }
            }

            Map<String, Object> geoJson = new HashMap<>();
            geoJson.put("type", "LineString");
            geoJson.put("coordinates", geoJsonCoords);

            Double riskScore = null;
            Double safetyScore = null;
            String riskLevel = "UNSCORED";
            String scoringStatus = mlServiceAvailable ? "UNSCORED" : "UNAVAILABLE";
            String notes = null;

            if (mlServiceAvailable && pred != null && pred.getRiskScore() != null) {
                riskScore = Math.max(0.0, Math.min(1.0, pred.getRiskScore()));
                if (pred.getSafetyScore() != null) {
                    safetyScore = Math.round(pred.getSafetyScore() * 1000.0) / 10.0;
                    riskLevel = deriveRiskCategory(pred.getSafetyScore());
                } else {
                    double derivedSafety = 1.0 - riskScore;
                    safetyScore = Math.round(derivedSafety * 1000.0) / 10.0;
                    riskLevel = deriveRiskCategory(derivedSafety);
                }
                scoringStatus = "SCORED";
                scoredCount++;
                notes = isSyntheticDemonstration != null && isSyntheticDemonstration
                        ? "Scored using synthetic demonstration ML model " + (modelVersion != null ? modelVersion : "v1")
                        : "Scored using ML model " + (modelVersion != null ? modelVersion : "v1");
            } else {
                unscoredCount++;
                notes = mlServiceAvailable
                        ? "Segment not scored by ML service"
                        : "ML service offline; risk score unavailable";
            }

            String colorHex = mapRiskToColor(riskLevel);

            segmentRisks.add(HeatmapDto.SegmentRisk.builder()
                    .segmentId(segKey)
                    .roadName(segment.getRoadName() != null ? segment.getRoadName() : "Unnamed Street")
                    .roadType(segment.getRoadType() != null ? segment.getRoadType() : "walkway")
                    .lengthMeters(segment.getLengthMeters())
                    .isLit(segment.getIsLit())
                    .geometry(geoJson)
                    .coordinates(coordList)
                    .riskScore(riskScore)
                    .safetyScore(safetyScore)
                    .riskLevel(riskLevel)
                    .scoringStatus(scoringStatus)
                    .colorHex(colorHex)
                    .isSyntheticDemonstration(isSyntheticDemonstration)
                    .modelVersion(modelVersion)
                    .notes(notes)
                    .build());
        }

        return HeatmapDto.Response.builder()
                .boundingBox(bboxStr)
                .totalSegments(segments.size())
                .scoredSegments(scoredCount)
                .unscoredSegments(unscoredCount)
                .segments(segmentRisks)
                .mlServiceAvailable(mlServiceAvailable)
                .isSyntheticDemonstration(isSyntheticDemonstration)
                .modelVersion(modelVersion)
                .timestamp(OffsetDateTime.now())
                .disclaimer(HEATMAP_DISCLAIMER)
                .build();
    }

    public void validateBoundingBox(Double minLat, Double minLon, Double maxLat, Double maxLon) {
        if (minLat == null || minLon == null || maxLat == null || maxLon == null) {
            throw new IllegalArgumentException("Bounding box parameters (minLat, minLon, maxLat, maxLon) are required.");
        }
        if (minLat < -90.0 || minLat > 90.0 || maxLat < -90.0 || maxLat > 90.0) {
            throw new IllegalArgumentException("Latitude coordinates must be between -90.0 and 90.0.");
        }
        if (minLon < -180.0 || minLon > 180.0 || maxLon < -180.0 || maxLon > 180.0) {
            throw new IllegalArgumentException("Longitude coordinates must be between -180.0 and 180.0.");
        }
        if (minLat > maxLat) {
            throw new IllegalArgumentException(String.format("minLat (%.4f) cannot be greater than maxLat (%.4f).", minLat, maxLat));
        }
        if (minLon > maxLon) {
            throw new IllegalArgumentException(String.format("minLon (%.4f) cannot be greater than maxLon (%.4f).", minLon, maxLon));
        }
        double latSpan = maxLat - minLat;
        double lonSpan = maxLon - minLon;
        if (latSpan > MAX_BBOX_SPAN_DEGREES || lonSpan > MAX_BBOX_SPAN_DEGREES) {
            throw new IllegalArgumentException(String.format(
                    "Bounding box span is too large (lat span: %.4f, lon span: %.4f, max allowed: %.2f degrees). Zoom in to view street-level risk.",
                    latSpan, lonSpan, MAX_BBOX_SPAN_DEGREES));
        }
    }

    public String deriveRiskCategory(Double safetyScoreFraction) {
        if (safetyScoreFraction == null) {
            return "UNSCORED";
        }
        double riskFraction = 1.0 - safetyScoreFraction;
        if (riskFraction < 0.2) return "VERY_LOW";
        if (riskFraction < 0.4) return "LOW";
        if (riskFraction < 0.6) return "MODERATE";
        if (riskFraction < 0.8) return "HIGH";
        return "VERY_HIGH";
    }

    public String mapRiskToColor(String riskLevel) {
        if (riskLevel == null) return "#8E8E93";
        switch (riskLevel) {
            case "VERY_LOW":
            case "LOW":
                return "#34C759"; // Green
            case "MODERATE":
                return "#FF9500"; // Amber
            case "HIGH":
                return "#FF3B30"; // Red
            case "VERY_HIGH":
                return "#D70015"; // Dark Red
            case "UNSCORED":
            case "UNAVAILABLE":
            default:
                return "#8E8E93"; // Slate Gray
        }
    }
}
