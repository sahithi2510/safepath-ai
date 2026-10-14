package com.safepath.backend.service.ml;

import com.safepath.backend.dto.ml.PredictRiskRequestDto;
import com.safepath.backend.dto.ml.RiskFeaturesDto;
import com.safepath.backend.entity.RoadSegment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Prepares ML feature vectors (15 features) from road segment entities and temporal context.
 * Adheres strictly to documented defaults and conventions in docs/ml-pipeline.md.
 */
@Service
public class RoadSegmentFeatureService {

    /**
     * Extracts a RiskFeaturesDto from a RoadSegment using the current system time.
     *
     * @param segment road segment entity
     * @return 15-feature DTO
     */
    public RiskFeaturesDto extractFeatures(RoadSegment segment) {
        return extractFeatures(segment, LocalDateTime.now());
    }

    /**
     * Extracts a RiskFeaturesDto from a RoadSegment for a specific evaluation timestamp.
     *
     * @param segment  road segment entity
     * @param dateTime reference evaluation time
     * @return 15-feature DTO
     */
    public RiskFeaturesDto extractFeatures(RoadSegment segment, LocalDateTime dateTime) {
        return extractFeatures(segment, dateTime, null, null);
    }

    /**
     * Extracts a RiskFeaturesDto from a RoadSegment for a specific evaluation timestamp with optional weather context.
     *
     * @param segment         road segment entity
     * @param dateTime        reference evaluation time
     * @param precipitationMm precipitation in mm (or null if unobserved)
     * @param visibilityKm    visibility in km (or null if unobserved)
     * @return 15-feature DTO
     */
    public RiskFeaturesDto extractFeatures(RoadSegment segment, LocalDateTime dateTime, Double precipitationMm, Double visibilityKm) {
        if (segment == null) {
            return new RiskFeaturesDto();
        }

        LocalDateTime time = dateTime != null ? dateTime : LocalDateTime.now();

        // Python convention: 0 = Monday, 6 = Sunday (Java getDayOfWeek() returns 1=Monday to 7=Sunday)
        int dayOfWeek = time.getDayOfWeek().getValue() - 1;
        int hourOfDay = time.getHour();

        Double lightingScore = null;
        if (segment.getIsLit() != null) {
            lightingScore = segment.getIsLit() ? 1.0 : 0.0;
        }

        Double activityProxy = estimateActivityProxy(segment.getRoadType());

        return RiskFeaturesDto.builder()
                .hourOfDay(hourOfDay)
                .dayOfWeek(dayOfWeek)
                .lightingScore(lightingScore)
                .activityProxy(activityProxy)
                .weatherPrecipitation(precipitationMm)
                .weatherVisibility(visibilityKm)
                // Spatial density, emergency proximities, and reports are left null
                // when unobserved so the ML service applies documented defaults and tracks imputation.
                .build();
    }

    /**
     * Prepares batch prediction requests for a collection of road segments.
     *
     * @param segments collection of road segments
     * @param dateTime reference evaluation timestamp
     * @return list of prediction requests with stable segment identifiers
     */
    public List<PredictRiskRequestDto> prepareBatchRequests(List<RoadSegment> segments, LocalDateTime dateTime) {
        return prepareBatchRequests(segments, dateTime, null, null);
    }

    /**
     * Prepares batch prediction requests for a collection of road segments with weather context.
     *
     * @param segments        collection of road segments
     * @param dateTime        reference evaluation timestamp
     * @param precipitationMm precipitation in mm (or null if unobserved)
     * @param visibilityKm    visibility in km (or null if unobserved)
     * @return list of prediction requests with stable segment identifiers
     */
    public List<PredictRiskRequestDto> prepareBatchRequests(List<RoadSegment> segments, LocalDateTime dateTime, Double precipitationMm, Double visibilityKm) {
        if (segments == null || segments.isEmpty()) {
            return Collections.emptyList();
        }

        List<PredictRiskRequestDto> requests = new ArrayList<>(segments.size());
        for (RoadSegment segment : segments) {
            String segmentId = resolveSegmentId(segment);
            RiskFeaturesDto features = extractFeatures(segment, dateTime, precipitationMm, visibilityKm);
            requests.add(PredictRiskRequestDto.builder()
                    .segmentId(segmentId)
                    .features(features)
                    .build());
        }
        return requests;
    }

    /**
     * Resolves a deterministic segment identifier string.
     *
     * @param segment road segment entity
     * @return UUID string or deterministic node-based identifier
     */
    public String resolveSegmentId(RoadSegment segment) {
        if (segment.getId() != null) {
            return segment.getId().toString();
        }
        return "node-" + segment.getStartNodeId() + "-" + segment.getEndNodeId();
    }

    private Double estimateActivityProxy(String roadType) {
        if (roadType == null) {
            return null;
        }
        switch (roadType.toLowerCase().trim()) {
            case "primary":
            case "trunk":
                return 0.8;
            case "secondary":
                return 0.6;
            case "tertiary":
                return 0.5;
            case "residential":
            case "living_street":
                return 0.4;
            case "pedestrian":
            case "footway":
            case "path":
                return 0.3;
            default:
                return null;
        }
    }
}
